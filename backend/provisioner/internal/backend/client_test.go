package backend

import (
	"context"
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
