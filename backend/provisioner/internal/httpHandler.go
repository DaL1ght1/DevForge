package httpHandler

import (
	"devforge/provisioner/internal/generator"
	"devforge/provisioner/internal/git"
	"devforge/provisioner/internal/github"
	"devforge/provisioner/internal/model"
	"encoding/json"
	"log"
	"net/http"
	"os"
	"regexp"
)

var (
	validServiceName  = regexp.MustCompile(`^[a-z0-9][a-z0-9-]{0,62}$`)
	validTemplateName = regexp.MustCompile(`^[a-zA-Z0-9][a-zA-Z0-9_-]{0,62}$`)
	validPackageName  = regexp.MustCompile(`^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)*$`)
	validClassName    = regexp.MustCompile(`^[A-Z][A-Za-z0-9]*$`)
)

type Handler struct {
	gen             *generator.ProjectGenerator
	ghClient        *github.Client
	gitPusher       *git.Pusher
	outputWorkspace string
}

func NewHandler(gen *generator.ProjectGenerator, ghClient *github.Client, gitPusher *git.Pusher, workspace string) *Handler {
	return &Handler{
		gen:             gen,
		ghClient:        ghClient,
		gitPusher:       gitPusher,
		outputWorkspace: workspace,
	}
}

func (h *Handler) Provision(w http.ResponseWriter, r *http.Request) {
	var req model.GenerationRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "invalid request body", http.StatusBadRequest)
		return
	}
	switch {
	case !validServiceName.MatchString(req.ServiceName):
		http.Error(w, "invalid serviceName", http.StatusBadRequest)
		return
	case !validTemplateName.MatchString(req.TemplateName):
		http.Error(w, "invalid templateName", http.StatusBadRequest)
		return
	case !validPackageName.MatchString(req.PackageName):
		http.Error(w, "invalid packageName", http.StatusBadRequest)
		return
	case !validClassName.MatchString(req.ClassName):
		http.Error(w, "invalid className", http.StatusBadRequest)
		return
	}
	workDir, err := os.MkdirTemp(h.outputWorkspace, "provision-*")
	if err != nil {
		log.Printf("Failed to create work dir: %v", err)
		http.Error(w, "failed to create work directory", http.StatusInternalServerError)
		return
	}
	defer func(path string) {
		err := os.RemoveAll(path)
		if err != nil {
			log.Printf("Failed to clear directory: %v", err)
		}
	}(workDir)
	outPath, err := h.gen.Generate(req, workDir)
	if err != nil {
		log.Printf("Generation error for %s: %v", req.ServiceName, err)
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	log.Printf("1. Project generated at: %s", outPath)
	cloneURL, htmlURL, err := h.ghClient.CreateRepository(r.Context(), req.ServiceName, req.Description)
	if err != nil {
		log.Printf("GitHub repo creation failed: %v", err)
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	log.Printf("2. GitHub repository created: %s", htmlURL)

	if err := h.gitPusher.PushDirectory(outPath, cloneURL); err != nil {
		log.Printf("Git push failed: %v", err)
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	log.Printf("3. Successfully pushed code to: %s", cloneURL)
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(model.ProvisionResponse{
		ServiceName:   req.ServiceName,
		RepositoryURL: htmlURL,
		Status:        "READY",
	})
}
func (h *Handler) Health(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write([]byte(`{"status":"UP"}`))
}
