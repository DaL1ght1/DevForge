package config

import (
	"fmt"
	"log"
	"os"
	"path/filepath"
	"strings"

	"github.com/joho/godotenv"
)

type Config struct {
	Port          string
	TemplatesDir  string
	WorkspaceDir  string
	GithubToken   string
	GithubOwner   string
	GithubIsOrg   bool
	KafkaBrokers  []string
	RequestTopic  string
	ResponseTopic string
	KafkaGroupID  string
}

func loadDotEnv() (string, bool) {
	dir, err := os.Getwd()
	if err != nil {
		return "", false
	}
	for {
		candidate := filepath.Join(dir, ".env")
		if _, err := os.Stat(candidate); err == nil {
			if err := godotenv.Load(candidate); err != nil {
				log.Printf("[Config] Found %s but failed to load it: %v", candidate, err)
				return "", false
			}
			return candidate, true
		}
		parent := filepath.Dir(dir)
		if parent == dir {
			return "", false
		}
		dir = parent
	}
}

func Load() (*Config, error) {
	if path, ok := loadDotEnv(); ok {
		log.Printf("[Config] Successfully loaded environment from %s", path)
	} else {
		log.Println("[Config] No .env file found, falling back to system environment variables")
	}

	ghToken := os.Getenv("GITHUB_TOKEN")
	if ghToken == "" {
		return nil, fmt.Errorf("GITHUB_TOKEN environment variable is required")
	}

	ghOwner := os.Getenv("GITHUB_OWNER")
	if ghOwner == "" {
		return nil, fmt.Errorf("GITHUB_OWNER environment variable is required")
	}

	templatesDir := getEnvOrDefault("TEMPLATES_DIR", "../templates")
	workspaceDir := getEnvOrDefault("WORKSPACE_DIR", "../workspace")
	port := getEnvOrDefault("PORT", "8081")

	kafkaBootstrap := getEnvOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
	brokers := strings.Split(kafkaBootstrap, ",")

	reqTopic := getEnvOrDefault("REQUEST_TOPIC", "provisioning")
	if !strings.HasSuffix(reqTopic, "-requestTopic") {
		reqTopic += "-requestTopic"
	}

	respTopic := getEnvOrDefault("RESPONSE_TOPIC", "provisioning")
	if !strings.HasSuffix(respTopic, "-responseTopic") {
		respTopic += "-responseTopic"
	}

	kafkaGroupID := getEnvOrDefault("GROUP_ID", "devforge-go-provisioner")

	return &Config{
		Port:          port,
		TemplatesDir:  templatesDir,
		WorkspaceDir:  workspaceDir,
		GithubToken:   ghToken,
		GithubOwner:   ghOwner,
		GithubIsOrg:   os.Getenv("GITHUB_IS_ORG") == "true",
		KafkaBrokers:  brokers,
		RequestTopic:  reqTopic,
		ResponseTopic: respTopic,
		KafkaGroupID:  kafkaGroupID,
	}, nil
}

func getEnvOrDefault(key, fallback string) string {
	if val := os.Getenv(key); val != "" {
		return val
	}
	return fallback
}
