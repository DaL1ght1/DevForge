package main

import (
	"context"
	"errors"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"devforge/provisioner/internal/artifacts"
	"devforge/provisioner/internal/backend"
	"devforge/provisioner/internal/config"
	"devforge/provisioner/internal/generator"
	"devforge/provisioner/internal/git"
	"devforge/provisioner/internal/github"
	"devforge/provisioner/internal/httpapi"
	provKafka "devforge/provisioner/internal/kafka"
)

func main() {
	cfg, err := config.Load()
	if err != nil {
		log.Fatalf("[Config Error] %v", err)
	}

	gen := generator.NewProjectGenerator(cfg.TemplatesDir)
	deps := provKafka.Dependencies{
		Generator:       gen,
		Clients:         provKafka.InstallationClients(github.NewClientFactory(cfg.GithubAppID, cfg.GithubAppPrivateKey)),
		TokenClient:     provKafka.UserTokenClients,
		Pusher:          git.NewPusher(),
		PerInstallation: cfg.PerInstallation,
		JobTimeout:      cfg.JobTimeout,
	}
	if cfg.InternalAPIToken != "" {
		backendClient := backend.NewClient(cfg.BackendInternalURL, cfg.InternalAPIToken)
		deps.UserTokens = backendClient
		deps.Jobs = backendClient
		gen.WithArtifacts(artifacts.NewStore(backendClient.TemplateArtifact, cfg.ArtifactCacheSize))
		log.Printf("[Provisioner] Templates, job claims and user tokens via %s", cfg.BackendInternalURL)
	} else {
		if info, err := os.Stat(cfg.TemplatesDir); err != nil || !info.IsDir() {
			log.Fatalf("[Config Error] INTERNAL_API_TOKEN is required to fetch templates from the backend (no local templates at %s)", cfg.TemplatesDir)
		}
		log.Printf("[Provisioner] INTERNAL_API_TOKEN not set: rendering templates from %s without job claims", cfg.TemplatesDir)
	}
	log.Printf("[Provisioner] %d consumers, %d concurrent jobs per installation, job timeout %s",
		cfg.Concurrency, cfg.PerInstallation, cfg.JobTimeout)

	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancel()

	worker := provKafka.NewProvisionWorker(provKafka.Options{
		Brokers:       cfg.KafkaBrokers,
		RequestTopic:  cfg.RequestTopic,
		ResponseTopic: cfg.ResponseTopic,
		GroupID:       cfg.KafkaGroupID,
		Concurrency:   cfg.Concurrency,
		Partitions:    cfg.Partitions,
	}, deps)
	workerDone := make(chan struct{})
	go func() {
		worker.Start(ctx)
		close(workerDone)
	}()

	server := &http.Server{
		Addr:              ":" + cfg.Port,
		Handler:           httpapi.NewHandler(gen, cfg.InternalAPIToken),
		ReadHeaderTimeout: 10 * time.Second,
	}

	go func() {
		log.Printf("Provisioner listening on :%s (/health, /internal/render)", cfg.Port)
		if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatalf("HTTP server error: %v", err)
		}
	}()

	<-ctx.Done()
	log.Println("Shutting down provisioner...")
	_ = server.Shutdown(context.Background())
	<-workerDone
	worker.Close()
	log.Println("Provisioner stopped cleanly.")
}
