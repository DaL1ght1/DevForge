package kafka

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"devforge/provisioner/internal/backend"
	"devforge/provisioner/internal/generator"
	"devforge/provisioner/internal/git"
	"devforge/provisioner/internal/github"
	"devforge/provisioner/internal/model"
)

type fakeJobs struct {
	claim    backend.JobClaim
	claimErr []error
	mu       sync.Mutex
	claims   int
	recorded []int64
}

func (j *fakeJobs) ClaimJob(context.Context, string) (backend.JobClaim, error) {
	j.mu.Lock()
	defer j.mu.Unlock()
	j.claims++
	if len(j.claimErr) > 0 {
		err := j.claimErr[0]
		j.claimErr = j.claimErr[1:]
		return backend.JobClaim{}, err
	}
	return j.claim, nil
}

func (j *fakeJobs) RecordRepository(_ context.Context, _ string, id int64) error {
	j.mu.Lock()
	defer j.mu.Unlock()
	j.recorded = append(j.recorded, id)
	return nil
}

func TestFinishedJobIsSkippedWithoutTouchingGithub(t *testing.T) {
	factory := &fakeFactory{clients: map[int64]*fakeClient{100: {repoID: 1}}}
	w := newTestWorker(t, Dependencies{
		Clients: factory,
		Pusher:  &fakePusher{},
		Jobs:    &fakeJobs{claim: backend.JobClaim{Proceed: false}},
	})

	resp := w.executeProvision(context.Background(), request("j1", 100, "acme", model.OwnerTypeOrganization))

	if resp.Status != statusSkipped || factory.calls != 0 {
		t.Fatalf("expected skip before GitHub, got %+v (factory calls %d)", resp, factory.calls)
	}
}

func TestRedeliveredJobReusesItsRepositoryAndForcePushes(t *testing.T) {
	client := &fakeClient{existing: &github.Repository{ID: 55, CloneURL: "https://github.com/acme/demo-j1.git", HTMLURL: "https://github.com/acme/demo-j1"}}
	pusher := &fakePusher{}
	jobs := &fakeJobs{claim: backend.JobClaim{Proceed: true, Attempt: 2, RepositoryID: 55}}
	w := newTestWorker(t, Dependencies{
		Clients: &fakeFactory{clients: map[int64]*fakeClient{100: client}},
		Pusher:  pusher,
		Jobs:    jobs,
	})

	resp := w.executeProvision(context.Background(), request("j1", 100, "acme", model.OwnerTypeOrganization))

	if resp.Status != statusCompleted || resp.RepositoryID != 55 {
		t.Fatalf("unexpected response %+v", resp)
	}
	if len(client.creates) != 0 || len(pusher.pushes) != 1 || !pusher.pushes[0].force {
		t.Fatalf("expected reuse with force push, creates=%v pushes=%+v", client.creates, pusher.pushes)
	}
	if len(jobs.recorded) != 0 {
		t.Fatalf("reused repository should not be re-recorded: %v", jobs.recorded)
	}
}

func TestClaimedRepositoryWithAnotherIDIsNeverReused(t *testing.T) {
	client := &fakeClient{
		existing:  &github.Repository{ID: 999},
		createErr: fmt.Errorf("x: %w", github.ErrRepositoryExists),
	}
	pusher := &fakePusher{}
	w := newTestWorker(t, Dependencies{
		Clients: &fakeFactory{clients: map[int64]*fakeClient{100: client}},
		Pusher:  pusher,
		Jobs:    &fakeJobs{claim: backend.JobClaim{Proceed: true, RepositoryID: 55}},
	})

	resp := w.executeProvision(context.Background(), request("j1", 100, "acme", model.OwnerTypeOrganization))

	if resp.Status != statusFailed || len(pusher.pushes) != 0 || len(client.deletes) != 0 {
		t.Fatalf("foreign repository was touched: %+v pushes=%v deletes=%v", resp, pusher.pushes, client.deletes)
	}
}

func TestNewRepositoryIsRecordedBeforePush(t *testing.T) {
	jobs := &fakeJobs{claim: backend.JobClaim{Proceed: true, Attempt: 1}}
	pusher := &fakePusher{}
	w := newTestWorker(t, Dependencies{
		Clients: &fakeFactory{clients: map[int64]*fakeClient{100: {repoID: 9}}},
		Pusher:  pusher,
		Jobs:    jobs,
	})

	resp := w.executeProvision(context.Background(), request("j1", 100, "acme", model.OwnerTypeOrganization))

	if resp.Status != statusCompleted || len(jobs.recorded) != 1 || jobs.recorded[0] != 9 || pusher.pushes[0].force {
		t.Fatalf("unexpected result %+v recorded=%v pushes=%+v", resp, jobs.recorded, pusher.pushes)
	}
}

func TestClaimIsRetriedOnTransientBackendErrors(t *testing.T) {
	jobs := &fakeJobs{
		claim:    backend.JobClaim{Proceed: true},
		claimErr: []error{errors.New("connection refused")},
	}
	w := newTestWorker(t, Dependencies{
		Clients:      &fakeFactory{clients: map[int64]*fakeClient{100: {repoID: 1}}},
		Pusher:       &fakePusher{},
		Jobs:         jobs,
		ClaimBackoff: time.Millisecond,
	})

	resp := w.executeProvision(context.Background(), request("j1", 100, "acme", model.OwnerTypeOrganization))

	if resp.Status != statusCompleted || jobs.claims != 2 {
		t.Fatalf("expected success after one retry, got %+v claims=%d", resp, jobs.claims)
	}
}

type blockingPusher struct {
	active, peak atomic.Int32
	release      chan struct{}
}

func (p *blockingPusher) PushProject(context.Context, *generator.Project, string, git.TokenSource, bool) error {
	n := p.active.Add(1)
	for {
		peak := p.peak.Load()
		if n <= peak || p.peak.CompareAndSwap(peak, n) {
			break
		}
	}
	<-p.release
	p.active.Add(-1)
	return nil
}

func TestJobsForOneInstallationAreCapped(t *testing.T) {
	pusher := &blockingPusher{release: make(chan struct{})}
	w := newTestWorker(t, Dependencies{
		Clients:         &fakeFactory{clients: map[int64]*fakeClient{100: {repoID: 1}}},
		Pusher:          pusher,
		PerInstallation: 2,
	})

	var wg sync.WaitGroup
	for i := range 6 {
		wg.Add(1)
		go func() {
			defer wg.Done()
			w.executeProvision(context.Background(), request(fmt.Sprintf("j%d", i), 100, "acme", model.OwnerTypeOrganization))
		}()
	}
	deadline := time.Now().Add(2 * time.Second)
	for pusher.active.Load() < 2 && time.Now().Before(deadline) {
		time.Sleep(5 * time.Millisecond)
	}
	time.Sleep(50 * time.Millisecond)
	close(pusher.release)
	wg.Wait()

	if pusher.peak.Load() != 2 {
		t.Fatalf("peak concurrent pushes = %d, want 2", pusher.peak.Load())
	}
}

func TestJobTimeoutBoundsWaitingForCapacity(t *testing.T) {
	w := newTestWorker(t, Dependencies{
		Clients:         &fakeFactory{clients: map[int64]*fakeClient{100: {repoID: 1}}},
		Pusher:          &fakePusher{},
		PerInstallation: 1,
		JobTimeout:      20 * time.Millisecond,
	})
	release, err := w.limiter.acquire(context.Background(), 100)
	if err != nil {
		t.Fatal(err)
	}
	defer release()

	resp := w.executeProvision(context.Background(), request("j1", 100, "acme", model.OwnerTypeOrganization))

	if resp.Status != statusFailed || !strings.Contains(resp.ErrorMessage, "capacity") {
		t.Fatalf("expected capacity timeout, got %+v", resp)
	}
}

type recordingGenerator struct {
	got model.GenerationRequest
}

func (g *recordingGenerator) Render(_ context.Context, req model.GenerationRequest) (*generator.Project, error) {
	g.got = req
	return &generator.Project{Files: []generator.File{{Path: "compose.yaml", Content: []byte("services: {}"), Mode: 0o644}}}, nil
}

func TestApplicationRequestRendersAllServicesIntoOneRepository(t *testing.T) {
	gen := &recordingGenerator{}
	client := &fakeClient{repoID: 7}
	pusher := &fakePusher{}
	w := newTestWorker(t, Dependencies{
		Clients: &fakeFactory{clients: map[int64]*fakeClient{100: client}},
		Pusher:  pusher,
	})
	w.deps.Generator = gen
	req := request("j1", 100, "acme", model.OwnerTypeOrganization)
	req.ServiceName = "shop"
	req.ApplicationID = "app-1"
	req.Services = []model.ApplicationService{{ServiceName: "orders", Port: 8080}, {ServiceName: "web", Port: 8081}}

	resp := w.executeProvision(context.Background(), req)

	if resp.Status != statusCompleted || resp.ApplicationID != "app-1" {
		t.Fatalf("unexpected response %+v", resp)
	}
	if len(gen.got.Services) != 2 || len(client.creates) != 1 || client.creates[0].name != "shop" || len(pusher.pushes) != 1 {
		t.Fatalf("expected one repo named after the application, got creates=%v pushes=%d services=%d",
			client.creates, len(pusher.pushes), len(gen.got.Services))
	}
}

func TestApplicationRequestWithoutIDIsRejected(t *testing.T) {
	w := newTestWorker(t, Dependencies{Clients: &fakeFactory{}, Pusher: &fakePusher{}})
	req := request("j1", 100, "acme", model.OwnerTypeOrganization)
	req.Services = []model.ApplicationService{{ServiceName: "orders", Port: 8080}}

	if resp := w.executeProvision(context.Background(), req); resp.Status != statusFailed || !strings.Contains(resp.ErrorMessage, "applicationId") {
		t.Fatalf("expected rejection, got %+v", resp)
	}
}
