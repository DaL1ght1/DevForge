package backend

import (
	"context"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestUserTokenSendsInternalTokenAndParsesResponse(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost || r.URL.Path != "/api/v1/internal/provisioning/jobs/job-1/user-token" {
			t.Errorf("unexpected request %s %s", r.Method, r.URL.Path)
		}
		if r.Header.Get("X-Internal-Token") != "secret" {
			t.Errorf("missing internal token header")
		}
		_, _ = w.Write([]byte(`{"token":"ghu_abc"}`))
	}))
	defer srv.Close()

	token, err := NewClient(srv.URL+"/api/v1/", "secret").UserToken(context.Background(), "job-1")
	if err != nil || token != "ghu_abc" {
		t.Fatalf("got %q, %v", token, err)
	}
}

func TestUserTokenRefusalIsAnError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusConflict)
		_, _ = w.Write([]byte(`{"message":"Provisioning job is not in progress"}`))
	}))
	defer srv.Close()

	_, err := NewClient(srv.URL, "secret").UserToken(context.Background(), "job-1")
	if err == nil || !strings.Contains(err.Error(), "409") {
		t.Fatalf("expected a 409 error, got %v", err)
	}
}

func TestClaimJobParsesClaimAndTreatsMissingJobAsSkip(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost || r.Header.Get("X-Internal-Token") != "secret" {
			t.Errorf("unexpected request %s %s", r.Method, r.URL.Path)
		}
		if strings.Contains(r.URL.Path, "gone") {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		_, _ = w.Write([]byte(`{"proceed":true,"attempt":2,"repositoryId":55}`))
	}))
	defer srv.Close()
	client := NewClient(srv.URL, "secret")

	claim, err := client.ClaimJob(context.Background(), "job-1")
	if err != nil || !claim.Proceed || claim.Attempt != 2 || claim.RepositoryID != 55 {
		t.Fatalf("got %+v, %v", claim, err)
	}
	claim, err = client.ClaimJob(context.Background(), "gone")
	if err != nil || claim.Proceed {
		t.Fatalf("missing job should be skipped, got %+v, %v", claim, err)
	}
}

func TestRecordRepositorySendsRepositoryID(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, _ := io.ReadAll(r.Body)
		if r.Method != http.MethodPut || r.URL.Path != "/internal/provisioning/jobs/job-1/repository" || string(body) != `{"repositoryId":9}` {
			t.Errorf("unexpected request %s %s %s", r.Method, r.URL.Path, body)
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	defer srv.Close()

	if err := NewClient(srv.URL, "secret").RecordRepository(context.Background(), "job-1", 9); err != nil {
		t.Fatal(err)
	}
}

func TestTemplateArtifactReturnsBytesAndReportsMissing(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if strings.HasSuffix(r.URL.Path, "/missing") {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		_, _ = w.Write([]byte("zipbytes"))
	}))
	defer srv.Close()
	client := NewClient(srv.URL, "secret")

	data, err := client.TemplateArtifact(context.Background(), "abc")
	if err != nil || string(data) != "zipbytes" {
		t.Fatalf("got %q, %v", data, err)
	}
	if _, err := client.TemplateArtifact(context.Background(), "missing"); !errors.Is(err, ErrNotFound) {
		t.Fatalf("expected ErrNotFound, got %v", err)
	}
}
