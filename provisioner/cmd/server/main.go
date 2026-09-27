package main

import (
	"devforge/provisioner/internal/generator"
	"log"
	"net/http"
	"os"

	"devforge/provisioner/internal"
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

	gen := generator.NewProjectGenerator(templatesDir)
	newHandler := httpHandler.NewHandler(gen, outputWorkspace)
	mux := http.NewServeMux()
	mux.HandleFunc("POST /api/v1/generate", newHandler.Generate)
	mux.HandleFunc("GET /health", newHandler.Health)

	port := os.Getenv("PORT")
	if port == "" {
		port = "8081"
	}

	log.Printf("Provisioner starting on :%s (templates: %s, workspace: %s)", port, templatesDir, outputWorkspace)
	if err := http.ListenAndServe(":"+port, mux); err != nil {
		log.Fatalf("Server stopped: %v", err)
	}
}
