package httpapi

import (
	"context"
	"crypto/subtle"
	"encoding/json"
	"errors"
	"log"
	"net/http"
	"slices"
	"time"
	"unicode/utf8"

	"devforge/provisioner/internal/generator"
	"devforge/provisioner/internal/model"
)

const (
	maxRequestBytes   = 1 << 20
	maxInlineFileSize = 256 << 10
	renderTimeout     = 10 * time.Second
)

type Renderer interface {
	Render(ctx context.Context, req model.GenerationRequest) (*generator.Project, error)
}

type PreviewFile struct {
	Path    string `json:"path"`
	Origin  string `json:"origin"`
	Size    int    `json:"size"`
	Binary  bool   `json:"binary"`
	Content string `json:"content,omitempty"`
	Omitted bool   `json:"omitted"`
}

type PreviewResponse struct {
	Files      []PreviewFile `json:"files"`
	TotalBytes int           `json:"totalBytes"`
}

type errorResponse struct {
	Message string `json:"message"`
}

func NewHandler(renderer Renderer, internalToken string) http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /health", func(w http.ResponseWriter, _ *http.Request) {
		writeJSON(w, http.StatusOK, map[string]string{"status": "UP"})
	})
	mux.HandleFunc("POST /internal/render", func(w http.ResponseWriter, r *http.Request) {
		if internalToken == "" || subtle.ConstantTimeCompare([]byte(r.Header.Get("X-Internal-Token")), []byte(internalToken)) != 1 {
			writeJSON(w, http.StatusUnauthorized, errorResponse{"invalid internal token"})
			return
		}
		var req model.GenerationRequest
		decoder := json.NewDecoder(http.MaxBytesReader(w, r.Body, maxRequestBytes))
		if err := decoder.Decode(&req); err != nil {
			writeJSON(w, http.StatusBadRequest, errorResponse{"malformed render request"})
			return
		}
		ctx, cancel := context.WithTimeout(r.Context(), renderTimeout)
		defer cancel()
		project, err := renderer.Render(ctx, req)
		if err != nil {
			status := http.StatusUnprocessableEntity
			if errors.Is(err, context.DeadlineExceeded) {
				status = http.StatusGatewayTimeout
			}
			log.Printf("[Preview] Render of %s failed: %v", req.TemplateName, err)
			writeJSON(w, status, errorResponse{err.Error()})
			return
		}
		writeJSON(w, http.StatusOK, toPreview(project))
	})
	return mux
}

func toPreview(project *generator.Project) PreviewResponse {
	resp := PreviewResponse{Files: make([]PreviewFile, 0, len(project.Files))}
	for _, f := range project.Files {
		file := PreviewFile{
			Path:   f.Path,
			Origin: f.Origin,
			Size:   len(f.Content),
			Binary: isBinary(f.Content),
		}
		switch {
		case file.Binary:
		case file.Size > maxInlineFileSize:
			file.Omitted = true
		default:
			file.Content = string(f.Content)
		}
		resp.TotalBytes += file.Size
		resp.Files = append(resp.Files, file)
	}
	return resp
}

func isBinary(content []byte) bool {
	if slices.Contains(content, 0) {
		return true
	}
	return !utf8.Valid(content)
}

func writeJSON(w http.ResponseWriter, status int, body any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(body)
}
