package httpapi

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"devforge/provisioner/internal/generator"
	"devforge/provisioner/internal/model"
)

type fakeRenderer struct {
	got model.GenerationRequest
	err error
}

func (f *fakeRenderer) Render(_ context.Context, req model.GenerationRequest) (*generator.Project, error) {
	f.got = req
	if f.err != nil {
		return nil, f.err
	}
	return &generator.Project{Files: []generator.File{
		{Path: "README.md", Content: []byte("# orders"), Origin: generator.OriginBase},
		{Path: "logo.png", Content: []byte{0x89, 'P', 'N', 'G', 0}, Origin: "feature:branding"},
		{Path: "big.txt", Content: []byte(strings.Repeat("a", maxInlineFileSize+1)), Origin: generator.OriginBase},
	}}, nil
}

func post(t *testing.T, h http.Handler, token, body string) *httptest.ResponseRecorder {
	t.Helper()
	req := httptest.NewRequest(http.MethodPost, "/internal/render", strings.NewReader(body))
	if token != "" {
		req.Header.Set("X-Internal-Token", token)
	}
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	return rec
}

func TestRenderReturnsFilesWithInlineTextOnly(t *testing.T) {
	renderer := &fakeRenderer{}
	rec := post(t, NewHandler(renderer, "secret"), "secret",
		`{"serviceName":"orders","templateName":"spring","artifactDigest":"abc","features":{"database":["postgresql"]}}`)

	if rec.Code != http.StatusOK {
		t.Fatalf("status %d: %s", rec.Code, rec.Body)
	}
	if renderer.got.ArtifactDigest != "abc" || renderer.got.Features["database"][0] != "postgresql" {
		t.Fatalf("request not passed through: %+v", renderer.got)
	}
	if !strings.Contains(rec.Body.String(), `"omitted":false`) {
		t.Fatalf("omitted must always be present for the Java client: %s", rec.Body)
	}
	var resp PreviewResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatal(err)
	}
	byPath := map[string]PreviewFile{}
	for _, f := range resp.Files {
		byPath[f.Path] = f
	}
	if f := byPath["README.md"]; f.Content != "# orders" || f.Binary || f.Origin != "base" {
		t.Fatalf("README = %+v", f)
	}
	if f := byPath["logo.png"]; !f.Binary || f.Content != "" || f.Origin != "feature:branding" {
		t.Fatalf("logo = %+v", f)
	}
	if f := byPath["big.txt"]; !f.Omitted || f.Content != "" || f.Size != maxInlineFileSize+1 {
		t.Fatalf("big = %+v", f)
	}
	if resp.TotalBytes != 8+5+maxInlineFileSize+1 {
		t.Fatalf("totalBytes = %d", resp.TotalBytes)
	}
}

func TestRenderRequiresTheInternalToken(t *testing.T) {
	for _, tc := range []struct{ configured, sent string }{{"secret", ""}, {"secret", "wrong"}, {"", ""}} {
		rec := post(t, NewHandler(&fakeRenderer{}, tc.configured), tc.sent, `{}`)
		if rec.Code != http.StatusUnauthorized {
			t.Fatalf("configured=%q sent=%q: status %d", tc.configured, tc.sent, rec.Code)
		}
	}
}

func TestRenderErrorsAreReportedAsUnprocessable(t *testing.T) {
	rec := post(t, NewHandler(&fakeRenderer{err: errors.New("unknown option \"oracle\"")}, "s"), "s", `{"templateName":"spring"}`)
	if rec.Code != http.StatusUnprocessableEntity || !strings.Contains(rec.Body.String(), "oracle") {
		t.Fatalf("status %d: %s", rec.Code, rec.Body)
	}
	if rec := post(t, NewHandler(&fakeRenderer{}, "s"), "s", `not json`); rec.Code != http.StatusBadRequest {
		t.Fatalf("malformed body status %d", rec.Code)
	}
}

func TestHealth(t *testing.T) {
	rec := httptest.NewRecorder()
	NewHandler(&fakeRenderer{}, "").ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/health", nil))
	if rec.Code != http.StatusOK || !strings.Contains(rec.Body.String(), "UP") {
		t.Fatalf("health = %d %s", rec.Code, rec.Body)
	}
}
