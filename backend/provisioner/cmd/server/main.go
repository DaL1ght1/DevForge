package main

import (
	"context"
	"errors"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"

	"github.com/bradleyfalzon/ghinstallation/v2"

	"devforge/provisioner/internal/config"
	"devforge/provisioner/internal/generator"
	"devforge/provisioner/internal/git"
	"devforge/provisioner/internal/github"
	provKafka "devforge/provisioner/internal/kafka"
)

func main() {
	cfg, err := config.Load()
	if err != nil {
		log.Fatalf("[Config Error] %v", err)
	}
	log.Printf("[Provisioner] Using templates: %s", cfg.TemplatesDir)
	log.Printf("[Provisioner] Using workspace: %s", cfg.WorkspaceDir)

	gen := generator.NewProjectGenerator(cfg.TemplatesDir)
	itr, err := ghinstallation.New(
		http.DefaultTransport,
		cfg.GithubAppID,
		cfg.GithubInstallationID,
		cfg.GithubAppPrivateKey,
	)
	if err != nil {
		log.Fatalf("GitHub App auth init failed: %v", err)
	}

	ghClient, err := github.NewClient(itr, cfg.GithubOwner)
	if err != nil {
		log.Fatalf("GitHub client init failed: %v", err)
	}

	gitPusher := git.NewPusher(itr)

	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancel()

	worker := provKafka.NewProvisionWorker(
		cfg.KafkaBrokers,
		cfg.RequestTopic,
		cfg.ResponseTopic,
		cfg.KafkaGroupID,
		gen,
		ghClient,
		gitPusher,
		cfg.WorkspaceDir,
	)
	go worker.Start(ctx)

	server := &http.Server{
		Addr: ":" + cfg.Port,
		Handler: http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if r.URL.Path == "/health" && r.Method == http.MethodGet {
				w.Header().Set("Content-Type", "application/json")
				w.WriteHeader(http.StatusOK)
				_, _ = w.Write([]byte(`{"status":"UP"}`))
				return
			}
			http.NotFound(w, r)
		}),
	}

	go func() {
		log.Printf("Provisioner listening on :%s (/health)", cfg.Port)
		if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatalf("HTTP server error: %v", err)
		}
	}()

	<-ctx.Done()
	log.Println("Shutting down provisioner...")
	_ = server.Shutdown(context.Background())
	worker.Close()
	log.Println("Provisioner stopped cleanly.")
}
