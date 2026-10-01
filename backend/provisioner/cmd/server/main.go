package main

import (
	"log"
	"net/http"
	"os"

	httpHandler "devforge/provisioner/internal"
	"devforge/provisioner/internal/generator"
	"devforge/provisioner/internal/git"
	"devforge/provisioner/internal/github"
)

func main() {
	templatesDir := os.Getenv("TEMPLATES_DIR")
	if templatesDir == "" {
		templatesDir = "../templates"
	}

	outputWorkspace := os.Getenv("WORKSPACE_DIR")
	if outputWorkspace == "" {
		outputWorkspace = "../workspace"
	}
	if err := os.MkdirAll(outputWorkspace, 0755); err != nil {
		log.Fatalf("cannot create workspace dir: %v", err)
	}

	ghToken := os.Getenv("GITHUB_TOKEN")
	if ghToken == "" {
		log.Fatal("GITHUB_TOKEN environment variable is required")
	}

	ghOwner := os.Getenv("GITHUB_OWNER")
	if ghOwner == "" {
		log.Fatal("GITHUB_OWNER environment variable is required")
	}

	isOrg := os.Getenv("GITHUB_IS_ORG") == "true"

	gen := generator.NewProjectGenerator(templatesDir)
	ghClient, _ := github.NewClient(ghToken, ghOwner, isOrg)
	gitPusher := git.NewPusher(ghToken)

	handler := httpHandler.NewHandler(gen, ghClient, gitPusher, outputWorkspace)

	mux := http.NewServeMux()
	mux.HandleFunc("POST /api/v1/provision", handler.Provision)
	mux.HandleFunc("GET /health", handler.Health)

	port := os.Getenv("PORT")
	if port == "" {
		port = "8081"
	}

	log.Printf("Provisioner listening on :%s (templates: %s, workspace: %s)", port, templatesDir, outputWorkspace)
	if err := http.ListenAndServe(":"+port, mux); err != nil {
		log.Fatalf("Server stopped: %v", err)
	}
}
