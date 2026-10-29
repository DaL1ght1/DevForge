package github

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"

	gh "github.com/google/go-github/v92/github"

	"devforge/provisioner/internal/model"
)

func newTestClient(t *testing.T, handler http.Handler) *Client {
	t.Helper()
	srv := httptest.NewServer(handler)
	t.Cleanup(srv.Close)
	baseURL := srv.URL + "/"
	c, err := NewClient(http.DefaultTransport, gh.WithURLs(&baseURL, &baseURL))
	if err != nil {
		t.Fatalf("NewClient: %v", err)
	}
	return c
}

const createdAcmeRepo = `{"id":42,"owner":{"login":"acme"},"clone_url":"https://github.com/acme/svc.git","html_url":"https://github.com/acme/svc"}`

func TestCreateOrganizationRepositoryUsesOrgEndpoint(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc("POST /orgs/acme/repos", func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusCreated)
		_, _ = w.Write([]byte(createdAcmeRepo))
	})
	repo, err := newTestClient(t, mux).CreateRepository(context.Background(), "acme", model.OwnerTypeOrganization, "svc", "desc", true)
	if err != nil {
		t.Fatalf("CreateRepository: %v", err)
	}
	if repo.ID != 42 || repo.CloneURL != "https://github.com/acme/svc.git" || repo.HTMLURL != "https://github.com/acme/svc" {
		t.Fatalf("unexpected repo: %+v", repo)
	}
}

func TestCreateUserRepositoryUsesAuthenticatedUserEndpoint(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc("POST /user/repos", func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusCreated)
		_, _ = w.Write([]byte(`{"id":7,"owner":{"login":"Alice"},"clone_url":"c","html_url":"h"}`))
	})
	repo, err := newTestClient(t, mux).CreateRepository(context.Background(), "alice", model.OwnerTypeUser, "svc", "", true)
	if err != nil {
		t.Fatalf("CreateRepository: %v", err)
	}
	if repo.ID != 7 {
		t.Fatalf("unexpected repo: %+v", repo)
	}
}

func TestRepositoryCreatedUnderUnexpectedOwnerIsRemoved(t *testing.T) {
	deleted := false
	mux := http.NewServeMux()
	mux.HandleFunc("POST /user/repos", func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusCreated)
		_, _ = w.Write([]byte(`{"id":9,"owner":{"login":"mallory"},"clone_url":"c","html_url":"h"}`))
	})
	mux.HandleFunc("GET /repos/mallory/svc", func(w http.ResponseWriter, _ *http.Request) {
		_, _ = w.Write([]byte(`{"id":9}`))
	})
	mux.HandleFunc("DELETE /repos/mallory/svc", func(w http.ResponseWriter, _ *http.Request) {
		deleted = true
		w.WriteHeader(http.StatusNoContent)
	})
	_, err := newTestClient(t, mux).CreateRepository(context.Background(), "alice", model.OwnerTypeUser, "svc", "", true)
	if err == nil {
		t.Fatal("expected an owner mismatch error")
	}
	if !deleted {
		t.Fatal("expected the wrongly-owned repository to be removed")
	}
}

func TestInstallationForbiddenOnUserAccountIsNotPermitted(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc("POST /user/repos", func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusForbidden)
		_, _ = w.Write([]byte(`{"message":"Resource not accessible by integration"}`))
	})
	_, err := newTestClient(t, mux).CreateRepository(context.Background(), "alice", model.OwnerTypeUser, "svc", "", true)
	if !errors.Is(err, ErrNotPermitted) {
		t.Fatalf("expected ErrNotPermitted, got %v", err)
	}
}

func TestCreateRepositoryNameTakenDoesNotReuseExisting(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc("POST /orgs/acme/repos", func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusUnprocessableEntity)
		_, _ = w.Write([]byte(`{"message":"Repository creation failed.","errors":[{"resource":"Repository","code":"custom","field":"name","message":"name already exists on this account"}]}`))
	})
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		t.Errorf("unexpected request after name conflict: %s %s", r.Method, r.URL.Path)
		w.WriteHeader(http.StatusInternalServerError)
	})
	_, err := newTestClient(t, mux).CreateRepository(context.Background(), "acme", model.OwnerTypeOrganization, "svc", "", true)
	if !errors.Is(err, ErrRepositoryExists) {
		t.Fatalf("expected ErrRepositoryExists, got %v", err)
	}
}

func TestCreateRepositoryServerErrorIsNotNameTaken(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc("POST /orgs/acme/repos", func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusBadGateway)
		_, _ = w.Write([]byte(`{"message":"bad gateway"}`))
	})
	_, err := newTestClient(t, mux).CreateRepository(context.Background(), "acme", model.OwnerTypeOrganization, "svc", "", true)
	if err == nil || errors.Is(err, ErrRepositoryExists) || errors.Is(err, ErrNotPermitted) {
		t.Fatalf("expected a plain error, got %v", err)
	}
}

func TestDeleteRepositorySkipsWhenIDDoesNotMatch(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /repos/acme/svc", func(w http.ResponseWriter, _ *http.Request) {
		_, _ = w.Write([]byte(`{"id":7}`))
	})
	mux.HandleFunc("DELETE /repos/acme/svc", func(w http.ResponseWriter, _ *http.Request) {
		t.Error("delete must not be called for a repository with a different id")
		w.WriteHeader(http.StatusNoContent)
	})
	if err := newTestClient(t, mux).DeleteRepository(context.Background(), "acme", "svc", 42); err == nil {
		t.Fatal("expected an error for mismatched repository id")
	}
}

func TestDeleteRepositoryDeletesWhenIDMatches(t *testing.T) {
	deleted := false
	mux := http.NewServeMux()
	mux.HandleFunc("GET /repos/acme/svc", func(w http.ResponseWriter, _ *http.Request) {
		_, _ = w.Write([]byte(`{"id":42}`))
	})
	mux.HandleFunc("DELETE /repos/acme/svc", func(w http.ResponseWriter, _ *http.Request) {
		deleted = true
		w.WriteHeader(http.StatusNoContent)
	})
	if err := newTestClient(t, mux).DeleteRepository(context.Background(), "acme", "svc", 42); err != nil {
		t.Fatalf("DeleteRepository: %v", err)
	}
	if !deleted {
		t.Fatal("expected repository to be deleted")
	}
}
