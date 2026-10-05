package config

import (
	"fmt"
	"log"
	"os"
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

func Load() (*Config, error) {

	if err := godotenv.Load("../.env", "../../.env", ".env"); err != nil {
		log.Println("[Config] No .env file found or failed to load, falling back to system environment variables")
	} else {
		log.Println("[Config] Successfully loaded environment from .env file")
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
		reqTopic = reqTopic + "-requestTopic"
	}

	respTopic := getEnvOrDefault("RESPONSE_TOPIC", "provisioning")
	if !strings.HasSuffix(respTopic, "-responseTopic") {
		respTopic = respTopic + "-responseTopic"
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
