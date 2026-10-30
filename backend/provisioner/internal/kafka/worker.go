package kafka

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"sync"
	"time"

	"devforge/provisioner/internal/backend"
	"devforge/provisioner/internal/generator"
	"devforge/provisioner/internal/git"
	"devforge/provisioner/internal/github"
	"devforge/provisioner/internal/model"

	kafka "github.com/segmentio/kafka-go"
)

const (
	statusCompleted = "COMPLETED"
	statusFailed    = "FAILED"
	statusSkipped   = "SKIPPED"
)

type ProjectRenderer interface {
	Render(ctx context.Context, req model.GenerationRequest) (*generator.Project, error)
}

type RepoClient interface {
	CreateRepository(ctx context.Context, owner, ownerType, name, description string, private bool) (*github.Repository, error)
	GetRepository(ctx context.Context, owner, name string) (*github.Repository, error)
	DeleteRepository(ctx context.Context, owner, name string, expectedID int64) error
}

type ClientFactory interface {
	ForInstallation(installationID int64) (RepoClient, git.TokenSource, error)
}

type UserTokenSource interface {
	UserToken(ctx context.Context, jobID string) (string, error)
}

type RepoPusher interface {
	PushProject(ctx context.Context, project *generator.Project, remoteURL string, tokens git.TokenSource, force bool) error
}

type JobTracker interface {
	ClaimJob(ctx context.Context, jobID string) (backend.JobClaim, error)
	RecordRepository(ctx context.Context, jobID string, repositoryID int64) error
}

type Dependencies struct {
	Generator       ProjectRenderer
	Clients         ClientFactory
	TokenClient     func(token string) (RepoClient, error)
	UserTokens      UserTokenSource
	Pusher          RepoPusher
	Jobs            JobTracker
	PerInstallation int
	JobTimeout      time.Duration
	ClaimBackoff    time.Duration
}

type Options struct {
	Brokers       []string
	RequestTopic  string
	ResponseTopic string
	GroupID       string
	Concurrency   int
	Partitions    int
}

type ProvisionWorker struct {
	readers []*kafka.Reader
	writer  *kafka.Writer
	deps    Dependencies
	limiter *installationLimiter
}

func InstallationClients(f *github.ClientFactory) ClientFactory {
	return installationClients{f}
}

type installationClients struct{ f *github.ClientFactory }

func (c installationClients) ForInstallation(id int64) (RepoClient, git.TokenSource, error) {
	client, tokens, err := c.f.ForInstallation(id)
	if err != nil {
		return nil, nil, err
	}
	return client, tokens, nil
}

func UserTokenClients(token string) (RepoClient, error) {
	client, err := github.NewTokenClient(token)
	if err != nil {
		return nil, err
	}
	return client, nil
}

func NewProvisionWorker(opts Options, deps Dependencies) *ProvisionWorker {
	ensureTopics(opts.Brokers, max(opts.Partitions, 1), opts.RequestTopic, opts.ResponseTopic)

	readers := make([]*kafka.Reader, max(opts.Concurrency, 1))
	for i := range readers {
		readers[i] = kafka.NewReader(kafka.ReaderConfig{
			Brokers:                opts.Brokers,
			GroupID:                opts.GroupID,
			Topic:                  opts.RequestTopic,
			MinBytes:               1,
			MaxBytes:               10e6,
			MaxWait:                500 * time.Millisecond,
			StartOffset:            kafka.FirstOffset,
			WatchPartitionChanges:  true,
			PartitionWatchInterval: 5 * time.Second,
		})
	}
	writer := &kafka.Writer{
		Addr:     kafka.TCP(opts.Brokers...),
		Topic:    opts.ResponseTopic,
		Balancer: &kafka.Hash{},
	}
	return newWorker(readers, writer, deps)
}

func newWorker(readers []*kafka.Reader, writer *kafka.Writer, deps Dependencies) *ProvisionWorker {
	if deps.ClaimBackoff <= 0 {
		deps.ClaimBackoff = time.Second
	}
	return &ProvisionWorker{
		readers: readers,
		writer:  writer,
		deps:    deps,
		limiter: newInstallationLimiter(deps.PerInstallation),
	}
}

func ensureTopics(brokers []string, partitions int, topics ...string) {
	if len(brokers) == 0 || len(topics) == 0 {
		return
	}

	checkCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	dialer := &kafka.Dialer{Timeout: 10 * time.Second}
	conn, err := dialer.DialLeader(checkCtx, "tcp", brokers[0], topics[0], 0)
	if err == nil {
		_ = conn.Close()
		return
	}

	conn, err = dialer.DialContext(checkCtx, "tcp", brokers[0])
	if err != nil {
		log.Printf("[Kafka Worker] Topic check failed: %v", err)
		return
	}
	defer func() {
		_ = conn.Close()
	}()

	configs := make([]kafka.TopicConfig, 0, len(topics))
	for _, topic := range topics {
		configs = append(configs, kafka.TopicConfig{
			Topic:             topic,
			NumPartitions:     partitions,
			ReplicationFactor: 1,
		})
	}
	if err := conn.CreateTopics(configs...); err != nil {
		log.Printf("[Kafka Worker] Topic creation/check failed: %v", err)
	}
}

func (w *ProvisionWorker) Start(ctx context.Context) {
	log.Printf("[Kafka Worker] Subscribed to topic %s with %d consumers", w.readers[0].Config().Topic, len(w.readers))
	var wg sync.WaitGroup
	for i, reader := range w.readers {
		wg.Add(1)
		go func() {
			defer wg.Done()
			w.consume(ctx, i, reader)
		}()
	}
	wg.Wait()
	log.Println("[Kafka Worker] Shutting down...")
}

func (w *ProvisionWorker) consume(ctx context.Context, id int, reader *kafka.Reader) {
	for {
		msg, err := reader.FetchMessage(ctx)
		if err != nil {
			if ctx.Err() != nil {
				return
			}
			log.Printf("[Kafka Worker %d] Read error: %v", id, err)
			select {
			case <-ctx.Done():
				return
			case <-time.After(time.Second):
			}
			continue
		}

		var req model.ProvisionRequest
		if err := json.Unmarshal(msg.Value, &req); err != nil {
			log.Printf("[Kafka Worker %d] Malformed JSON request: %v", id, err)
			if commitErr := reader.CommitMessages(ctx, msg); commitErr != nil {
				log.Printf("[Kafka Worker %d] Failed to commit malformed message: %v", id, commitErr)
			}
			continue
		}

		log.Printf("[Kafka Worker %d] Processing service [%s] (ID: %s, job: %s) for %s %q (installation %d)",
			id, req.ServiceName, req.ServiceID, req.JobID, req.OwnerType, req.OwnerLogin, req.InstallationID)
		resp := w.executeProvision(ctx, req)
		if ctx.Err() != nil {
			return
		}
		switch resp.Status {
		case statusSkipped:
			log.Printf("[Kafka Worker %d] Skipped job %s: already handled", id, req.JobID)
		case statusFailed:
			log.Printf("[Kafka Worker %d] Provisioning failed for service [%s] (ID: %s): %s",
				id, resp.ServiceName, resp.ServiceID, resp.ErrorMessage)
		}
		if resp.Status != statusSkipped {
			if err := w.publishResponse(ctx, resp); err != nil {
				continue
			}
		}
		if err := reader.CommitMessages(ctx, msg); err != nil {
			log.Printf("[Kafka Worker %d] Failed to commit offset for service [%s]: %v", id, resp.ServiceName, err)
		}
	}
}

func validateRequest(req model.ProvisionRequest) error {
	if req.ContractVersion < model.MinContractVersion || req.ContractVersion > model.ContractVersion {
		return fmt.Errorf("unsupported contract version %d (expected %d-%d)",
			req.ContractVersion, model.MinContractVersion, model.ContractVersion)
	}
	if req.JobID == "" || req.ServiceName == "" {
		return errors.New("request is missing jobId or serviceName")
	}
	if req.InstallationID <= 0 || req.OwnerLogin == "" {
		return errors.New("request is missing its GitHub destination")
	}
	if req.IsApplication() && req.ApplicationID == "" {
		return errors.New("application request is missing applicationId")
	}
	if req.OwnerType != model.OwnerTypeUser && req.OwnerType != model.OwnerTypeOrganization {
		return fmt.Errorf("unsupported owner type %q", req.OwnerType)
	}
	return nil
}

func (w *ProvisionWorker) executeProvision(ctx context.Context, req model.ProvisionRequest) model.ProvisionResponse {
	resp := model.ProvisionResponse{
		JobID:         req.JobID,
		ServiceID:     req.ServiceID,
		ApplicationID: req.ApplicationID,
		ServiceName:   req.ServiceName,
		Status:        statusFailed,
	}
	if err := validateRequest(req); err != nil {
		resp.ErrorMessage = fmt.Sprintf("invalid provisioning request: %v", err)
		return resp
	}
	if w.deps.JobTimeout > 0 {
		var cancel context.CancelFunc
		ctx, cancel = context.WithTimeout(ctx, w.deps.JobTimeout)
		defer cancel()
	}

	release, err := w.limiter.acquire(ctx, req.InstallationID)
	if err != nil {
		resp.ErrorMessage = fmt.Sprintf("timed out waiting for installation capacity: %v", err)
		return resp
	}
	defer release()

	claim, err := w.claim(ctx, req.JobID)
	if err != nil {
		resp.ErrorMessage = fmt.Sprintf("could not claim provisioning job: %v", err)
		return resp
	}
	if !claim.Proceed {
		resp.Status = statusSkipped
		return resp
	}

	project, err := w.deps.Generator.Render(ctx, model.GenerationRequest{
		ServiceName:     req.ServiceName,
		TemplateName:    req.TemplateName,
		TemplateVersion: req.TemplateVersion,
		ArtifactDigest:  req.ArtifactDigest,
		PackageName:     req.PackageName,
		ClassName:       req.ClassName,
		Description:     req.Description,
		Variables:       req.Variables,
		Features:        req.Features,
		Services:        req.Services,
	})
	if err != nil {
		resp.ErrorMessage = fmt.Sprintf("generator failed: %v", err)
		return resp
	}

	client, tokens, err := w.deps.Clients.ForInstallation(req.InstallationID)
	if err != nil {
		resp.ErrorMessage = fmt.Sprintf("github auth failed: %v", err)
		return resp
	}

	repo, reused, err := ensureRepository(ctx, client, req, claim.RepositoryID)
	if errors.Is(err, github.ErrNotPermitted) && req.OwnerType == model.OwnerTypeUser && w.deps.UserTokens != nil {
		log.Printf("[Kafka Worker] Installation token cannot create repos for user %q; using the owner's user token", req.OwnerLogin)
		client, tokens, err = w.userTokenClient(ctx, req.JobID)
		if err != nil {
			resp.ErrorMessage = fmt.Sprintf("github user authorization failed: %v", err)
			return resp
		}
		repo, reused, err = ensureRepository(ctx, client, req, claim.RepositoryID)
	}
	if err != nil {
		if errors.Is(err, github.ErrRepositoryExists) {
			resp.ErrorMessage = fmt.Sprintf("a repository named %q already exists in %s", req.ServiceName, req.OwnerLogin)
			return resp
		}
		resp.ErrorMessage = fmt.Sprintf("github repo creation failed: %v", err)
		return resp
	}
	if !reused && w.deps.Jobs != nil {
		if err := w.deps.Jobs.RecordRepository(ctx, req.JobID, repo.ID); err != nil {
			log.Printf("[Kafka Worker] Could not record repository %d for job %s: %v", repo.ID, req.JobID, err)
		}
	}

	if err := w.deps.Pusher.PushProject(ctx, project, repo.CloneURL, tokens, reused); err != nil {
		cleanupCtx, cancel := context.WithTimeout(context.WithoutCancel(ctx), 30*time.Second)
		defer cancel()
		if deleteErr := client.DeleteRepository(cleanupCtx, req.OwnerLogin, req.ServiceName, repo.ID); deleteErr != nil {
			log.Printf("[Kafka Worker] Failed to clean up repository [%s]: %v", req.ServiceName, deleteErr)
		}
		resp.ErrorMessage = fmt.Sprintf("git push failed: %v", err)
		return resp
	}

	resp.Status = statusCompleted
	resp.RepositoryURL = repo.HTMLURL
	resp.RepositoryID = repo.ID
	return resp
}

func (w *ProvisionWorker) claim(ctx context.Context, jobID string) (backend.JobClaim, error) {
	if w.deps.Jobs == nil {
		return backend.JobClaim{Proceed: true}, nil
	}
	var lastErr error
	backoff := w.deps.ClaimBackoff
	for attempt := 0; attempt < 3; attempt++ {
		claim, err := w.deps.Jobs.ClaimJob(ctx, jobID)
		if err == nil {
			return claim, nil
		}
		lastErr = err
		select {
		case <-ctx.Done():
			return backend.JobClaim{}, ctx.Err()
		case <-time.After(backoff):
		}
		backoff *= 2
	}
	return backend.JobClaim{}, lastErr
}

func ensureRepository(ctx context.Context, client RepoClient, req model.ProvisionRequest, claimedID int64) (*github.Repository, bool, error) {
	if claimedID != 0 {
		if existing, err := client.GetRepository(ctx, req.OwnerLogin, req.ServiceName); err == nil && existing.ID == claimedID {
			return existing, true, nil
		}
	}
	private := req.Visibility != "public"
	repo, err := client.CreateRepository(ctx, req.OwnerLogin, req.OwnerType, req.ServiceName, req.Description, private)
	if errors.Is(err, github.ErrRepositoryExists) && claimedID != 0 {
		if existing, getErr := client.GetRepository(ctx, req.OwnerLogin, req.ServiceName); getErr == nil && existing.ID == claimedID {
			return existing, true, nil
		}
	}
	return repo, false, err
}

func (w *ProvisionWorker) userTokenClient(ctx context.Context, jobID string) (RepoClient, git.TokenSource, error) {
	token, err := w.deps.UserTokens.UserToken(ctx, jobID)
	if err != nil {
		return nil, nil, err
	}
	client, err := w.deps.TokenClient(token)
	if err != nil {
		return nil, nil, err
	}
	return client, git.StaticToken(token), nil
}

func (w *ProvisionWorker) publishResponse(ctx context.Context, resp model.ProvisionResponse) error {
	payload, err := json.Marshal(resp)
	if err != nil {
		log.Printf("[Kafka Worker] Serialization error: %v", err)
		return err
	}

	err = w.writer.WriteMessages(ctx, kafka.Message{
		Key:   []byte(resp.ServiceID),
		Value: payload,
	})
	if err != nil {
		log.Printf("[Kafka Worker] Failed to send response for service [%s]: %v", resp.ServiceName, err)
		return err
	}
	log.Printf("[Kafka Worker] Published completion event for [%s] with status [%s]", resp.ServiceName, resp.Status)
	return nil
}

func (w *ProvisionWorker) Close() {
	for _, r := range w.readers {
		_ = r.Close()
	}
	_ = w.writer.Close()
}

type installationLimiter struct {
	limit int
	mu    sync.Mutex
	slots map[int64]chan struct{}
}

func newInstallationLimiter(limit int) *installationLimiter {
	return &installationLimiter{limit: limit, slots: map[int64]chan struct{}{}}
}

func (l *installationLimiter) acquire(ctx context.Context, installationID int64) (func(), error) {
	if l.limit <= 0 {
		return func() {}, nil
	}
	l.mu.Lock()
	slot, ok := l.slots[installationID]
	if !ok {
		slot = make(chan struct{}, l.limit)
		l.slots[installationID] = slot
	}
	l.mu.Unlock()
	select {
	case slot <- struct{}{}:
		return func() { <-slot }, nil
	case <-ctx.Done():
		return nil, ctx.Err()
	}
}
