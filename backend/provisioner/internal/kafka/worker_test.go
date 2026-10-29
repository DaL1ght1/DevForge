package kafka

import (
	"context"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"

	"devforge/provisioner/internal/git"
	"devforge/provisioner/internal/github"
	"devforge/provisioner/internal/model"
)

type fakeGenerator struct{}

func (fakeGenerator) Generate(req model.GenerationRequest, outputDir string) (string, error) {
	dir := filepath.Join(outputDir, req.ServiceName)
	return dir, os.MkdirAll(dir, 0o755)
}

type createCall struct{ owner, ownerType, name string }

type fakeClient struct {
	name      string
	createErr error
	repoID    int64

	mu      sync.Mutex
	creates []createCall
	deletes []int64
}

func (c *fakeClient) CreateRepository(_ context.Context, owner, ownerType, name, _ string, _ bool) (*github.Repository, error) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.creates = append(c.creates, createCall{owner, ownerType, name})
	if c.createErr != nil {
		return nil, c.createErr
	}
	return &github.Repository{
		ID:       c.repoID,
		CloneURL: fmt.Sprintf("https://github.com/%s/%s.git", owner, name),
		HTMLURL:  fmt.Sprintf("https://github.com/%s/%s", owner, name),
	}, nil
}

func (c *fakeClient) DeleteRepository(_ context.Context, _, _ string, expectedID int64) error {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.deletes = append(c.deletes, expectedID)
	return nil
}

type fakeFactory struct {
	clients map[int64]*fakeClient
	mu      sync.Mutex
	calls   int
}

func (f *fakeFactory) ForInstallation(id int64) (RepoClient, git.TokenSource, error) {
	f.mu.Lock()
	f.calls++
	f.mu.Unlock()
	c, ok := f.clients[id]
	if !ok {
		return nil, nil, fmt.Errorf("unknown installation %d", id)
	}
	return c, git.StaticToken(fmt.Sprintf("inst-token-%d", id)), nil
}

type push struct{ url, token string }

type fakePusher struct {
	err    error
	mu     sync.Mutex
	pushes []push
}

func (p *fakePusher) PushDirectory(ctx context.Context, _, remoteURL string, tokens git.TokenSource) error {
	token, _ := tokens.Token(ctx)
	p.mu.Lock()
	p.pushes = append(p.pushes, push{remoteURL, token})
	p.mu.Unlock()
	return p.err
}

type fakeUserTokens struct {
	token string
	calls []string
}

func (u *fakeUserTokens) UserToken(_ context.Context, jobID string) (string, error) {
	u.calls = append(u.calls, jobID)
	return u.token, nil
}

func request(job string, installation int64, owner, ownerType string) model.ProvisionRequest {
	return model.ProvisionRequest{
		ContractVersion: model.ContractVersion,
		JobID:           job,
		ServiceID:       "svc-" + job,
		ServiceName:     "demo-" + job,
		InstallationID:  installation,
		OwnerLogin:      owner,
		OwnerType:       ownerType,
		Visibility:      "private",
	}
}

func newTestWorker(t *testing.T, deps Dependencies) *ProvisionWorker {
	t.Helper()
	deps.Generator = fakeGenerator{}
	deps.Workspace = t.TempDir()
	return &ProvisionWorker{deps: deps}
}

func TestConcurrentJobsForDifferentInstallationsStayIsolated(t *testing.T) {
	org := &fakeClient{name: "acme", repoID: 1}
	user := &fakeClient{name: "alice", repoID: 2}
	pusher := &fakePusher{}
	w := newTestWorker(t, Dependencies{
		Clients: &fakeFactory{clients: map[int64]*fakeClient{100: org, 200: user}},
		Pusher:  pusher,
	})

	var wg sync.WaitGroup
	for i := 0; i < 20; i++ {
		wg.Add(2)
		go func(i int) {
			defer wg.Done()
			resp := w.executeProvision(context.Background(), request(fmt.Sprintf("o%d", i), 100, "acme", model.OwnerTypeOrganization))
			if resp.Status != "COMPLETED" || !strings.HasPrefix(resp.RepositoryURL, "https://github.com/acme/") {
				t.Errorf("org job: unexpected response %+v", resp)
			}
		}(i)
		go func(i int) {
			defer wg.Done()
			resp := w.executeProvision(context.Background(), request(fmt.Sprintf("u%d", i), 200, "alice", model.OwnerTypeUser))
			if resp.Status != "COMPLETED" || !strings.HasPrefix(resp.RepositoryURL, "https://github.com/alice/") {
				t.Errorf("user job: unexpected response %+v", resp)
			}
		}(i)
	}
	wg.Wait()

	for _, c := range org.creates {
		if c.owner != "acme" || c.ownerType != model.OwnerTypeOrganization {
			t.Fatalf("organization client was used for %+v", c)
		}
	}
	for _, c := range user.creates {
		if c.owner != "alice" || c.ownerType != model.OwnerTypeUser {
			t.Fatalf("user client was used for %+v", c)
		}
	}
	for _, p := range pusher.pushes {
		owner := strings.Split(strings.TrimPrefix(p.url, "https://github.com/"), "/")[0]
		want := map[string]string{"acme": "inst-token-100", "alice": "inst-token-200"}[owner]
		if p.token != want {
			t.Fatalf("push to %s used token %q, want %q", p.url, p.token, want)
		}
	}
	if len(org.creates) != 20 || len(user.creates) != 20 || len(pusher.pushes) != 40 {
		t.Fatalf("unexpected call counts: org=%d user=%d pushes=%d", len(org.creates), len(user.creates), len(pusher.pushes))
	}
}

func TestPersonalAccountFallsBackToOwnerUserToken(t *testing.T) {
	installation := &fakeClient{createErr: fmt.Errorf("wrapped: %w", github.ErrNotPermitted)}
	userClient := &fakeClient{repoID: 5}
	userTokens := &fakeUserTokens{token: "ghu_owner"}
	pusher := &fakePusher{}
	var tokenClientGot string
	w := newTestWorker(t, Dependencies{
		Clients: &fakeFactory{clients: map[int64]*fakeClient{200: installation}},
		TokenClient: func(token string) (RepoClient, error) {
			tokenClientGot = token
			return userClient, nil
		},
		UserTokens: userTokens,
		Pusher:     pusher,
	})

	resp := w.executeProvision(context.Background(), request("j1", 200, "alice", model.OwnerTypeUser))

	if resp.Status != "COMPLETED" || resp.RepositoryID != 5 || resp.JobID != "j1" {
		t.Fatalf("unexpected response %+v", resp)
	}
	if len(userTokens.calls) != 1 || userTokens.calls[0] != "j1" || tokenClientGot != "ghu_owner" {
		t.Fatalf("user token not requested for this job: calls=%v client=%q", userTokens.calls, tokenClientGot)
	}
	if len(pusher.pushes) != 1 || pusher.pushes[0].token != "ghu_owner" {
		t.Fatalf("push did not use the user token: %+v", pusher.pushes)
	}
}

func TestOrganizationNeverFallsBackToUserToken(t *testing.T) {
	installation := &fakeClient{createErr: github.ErrNotPermitted}
	userTokens := &fakeUserTokens{token: "ghu_owner"}
	w := newTestWorker(t, Dependencies{
		Clients:    &fakeFactory{clients: map[int64]*fakeClient{100: installation}},
		UserTokens: userTokens,
		Pusher:     &fakePusher{},
	})

	resp := w.executeProvision(context.Background(), request("j1", 100, "acme", model.OwnerTypeOrganization))

	if resp.Status != "FAILED" || len(userTokens.calls) != 0 {
		t.Fatalf("expected failure without user token, got %+v (calls %v)", resp, userTokens.calls)
	}
}

func TestLegacyRequestWithoutDestinationIsRejected(t *testing.T) {
	factory := &fakeFactory{clients: map[int64]*fakeClient{}}
	w := newTestWorker(t, Dependencies{Clients: factory, Pusher: &fakePusher{}})

	legacy := model.ProvisionRequest{ServiceID: "s", ServiceName: "demo"}
	resp := w.executeProvision(context.Background(), legacy)

	if resp.Status != "FAILED" || !strings.Contains(resp.ErrorMessage, "contract version") || factory.calls != 0 {
		t.Fatalf("expected rejection before any GitHub call, got %+v", resp)
	}
}

func TestNameConflictFailsWithoutTouchingExistingRepo(t *testing.T) {
	client := &fakeClient{createErr: fmt.Errorf("x: %w", github.ErrRepositoryExists)}
	pusher := &fakePusher{}
	w := newTestWorker(t, Dependencies{
		Clients: &fakeFactory{clients: map[int64]*fakeClient{100: client}},
		Pusher:  pusher,
	})

	resp := w.executeProvision(context.Background(), request("j1", 100, "acme", model.OwnerTypeOrganization))

	if resp.Status != "FAILED" || !strings.Contains(resp.ErrorMessage, "already exists in acme") {
		t.Fatalf("unexpected response %+v", resp)
	}
	if len(client.deletes) != 0 || len(pusher.pushes) != 0 {
		t.Fatalf("existing repository was touched: deletes=%v pushes=%v", client.deletes, pusher.pushes)
	}
}

func TestPushFailureDeletesOnlyTheCreatedRepository(t *testing.T) {
	client := &fakeClient{repoID: 77}
	w := newTestWorker(t, Dependencies{
		Clients: &fakeFactory{clients: map[int64]*fakeClient{100: client}},
		Pusher:  &fakePusher{err: errors.New("network down")},
	})

	resp := w.executeProvision(context.Background(), request("j1", 100, "acme", model.OwnerTypeOrganization))

	if resp.Status != "FAILED" || len(client.deletes) != 1 || client.deletes[0] != 77 {
		t.Fatalf("expected cleanup of repo 77 only, got %+v deletes=%v", resp, client.deletes)
	}
}
