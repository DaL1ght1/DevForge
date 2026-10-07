package config

import (
	"fmt"
	"log"
	"os"
	"path/filepath"
	"strconv"
	"strings"

	"github.com/joho/godotenv"
)

type Config struct {
	Port                 string
	TemplatesDir         string
	WorkspaceDir         string
	GithubAppID          int64
	GithubInstallationID int64
	GithubAppPrivateKey  []byte
	GithubOwner          string
	KafkaBrokers         []string
	RequestTopic         string
	ResponseTopic        string
	KafkaGroupID         string
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
func loadGithubPrivateKey() ([]byte, error) {
	if path := os.Getenv("GITHUB_APP_PRIVATE_KEY_PATH"); path != "" {
		key, err := os.ReadFile(path)
		if err != nil {
			return nil, fmt.Errorf("reading GITHUB_APP_PRIVATE_KEY_PATH (%s): %w", path, err)
		}
		return key, nil
	}

	if inline := os.Getenv("GITHUB_APP_PRIVATE_KEY"); inline != "" {
		return []byte(strings.ReplaceAll(inline, `\n`, "\n")), nil
	}

	return nil, fmt.Errorf("GITHUB_APP_PRIVATE_KEY_PATH or GITHUB_APP_PRIVATE_KEY is required")
}

func requireInt64Env(key string) (int64, error) {
	raw := os.Getenv(key)
	if raw == "" {
		return 0, fmt.Errorf("%s environment variable is required", key)
	}
	val, err := strconv.ParseInt(raw, 10, 64)
	if err != nil {
		return 0, fmt.Errorf("%s must be a number, got %q", key, raw)
	}
	return val, nil
}

func Load() (*Config, error) {
	if path, ok := loadDotEnv(); ok {
		log.Printf("[Config] Successfully loaded environment from %s", path)
	} else {
		log.Println("[Config] No .env file found, falling back to system environment variables")
	}

	ghAppID, err := requireInt64Env("GITHUB_APP_ID")
	if err != nil {
		return nil, err
	}

	ghInstallationID, err := requireInt64Env("GITHUB_APP_INSTALLATION_ID")
	if err != nil {
		return nil, err
	}

	ghPrivateKey, err := loadGithubPrivateKey()
	if err != nil {
		return nil, err
	}

	ghOwner := os.Getenv("GITHUB_OWNER")
	if ghOwner == "" {
		return nil, fmt.Errorf("GITHUB_OWNER environment variable is required")
	}

	templatesDir := getEnvOrDefault("TEMPLATES_DIR", "../templates")
	workspaceDir := getEnvOrDefault("WORKSPACE_DIR", "../workspace")
	port := getEnvOrDefault("PORT", "8081")

	kafkaBootstrap := getEnvOrDefault("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092")
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
		Port:                 port,
		TemplatesDir:         templatesDir,
		WorkspaceDir:         workspaceDir,
		GithubAppID:          ghAppID,
		GithubInstallationID: ghInstallationID,
		GithubAppPrivateKey:  ghPrivateKey,
		GithubOwner:          ghOwner,
		KafkaBrokers:         brokers,
		RequestTopic:         reqTopic,
		ResponseTopic:        respTopic,
		KafkaGroupID:         kafkaGroupID,
	}, nil
}

func getEnvOrDefault(key, fallback string) string {
	if val := os.Getenv(key); val != "" {
		return val
	}
	return fallback
}
