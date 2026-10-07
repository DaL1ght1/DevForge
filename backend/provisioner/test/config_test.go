package test

import (
	"devforge/provisioner/internal/config"
	"os"
	"path/filepath"
	"testing"
)

func TestLoadRequiresGitHubCredentials(t *testing.T) {
	t.Setenv("GITHUB_TOKEN", "")
	t.Setenv("GITHUB_OWNER", "")

	_, err := config.Load()
	if err == nil {
		t.Fatal("expected missing GitHub credentials to fail")
	}
	if err.Error() != "GITHUB_TOKEN environment variable is required" {
		t.Fatalf("unexpected error: %v", err)
	}
}

func TestLoadAppliesDefaultsAndNormalizesTopics(t *testing.T) {
	t.Setenv("GITHUB_TOKEN", "token")
	t.Setenv("GITHUB_OWNER", "owner")
	t.Setenv("TEMPLATES_DIR", "")
	t.Setenv("WORKSPACE_DIR", "")
	t.Setenv("PORT", "")
	t.Setenv("KAFKA_BOOTSTRAP_SERVERS", "broker-1:9092,broker-2:9092")
	t.Setenv("REQUEST_TOPIC", "requests")
	t.Setenv("RESPONSE_TOPIC", "responses")
	t.Setenv("GROUP_ID", "test-group")
	t.Setenv("GITHUB_IS_ORG", "true")

	cfg, err := config.Load()
	if err != nil {
		t.Fatalf("Load failed: %v", err)
	}

	if cfg.Port != "8081" || cfg.TemplatesDir != "../templates" || cfg.WorkspaceDir != "../workspace" {
		t.Fatalf("unexpected defaults: %+v", cfg)
	}
	if cfg.RequestTopic != "requests-requestTopic" || cfg.ResponseTopic != "responses-responseTopic" {
		t.Fatalf("unexpected topics: %+v", cfg)
	}
	if len(cfg.KafkaBrokers) != 2 || cfg.KafkaBrokers[1] != "broker-2:9092" {
		t.Fatalf("unexpected brokers: %+v", cfg.KafkaBrokers)
	}
	if !cfg.GithubIsOrg || cfg.KafkaGroupID != "test-group" {
		t.Fatalf("unexpected flags/group: %+v", cfg)
	}
}

func TestLoadDoesNotDuplicateTopicSuffixes(t *testing.T) {
	t.Setenv("GITHUB_TOKEN", "token")
	t.Setenv("GITHUB_OWNER", "owner")
	t.Setenv("REQUEST_TOPIC", "requests-requestTopic")
	t.Setenv("RESPONSE_TOPIC", "responses-responseTopic")

	cfg, err := config.Load()
	if err != nil {
		t.Fatalf("Load failed: %v", err)
	}
	if cfg.RequestTopic != "requests-requestTopic" || cfg.ResponseTopic != "responses-responseTopic" {
		t.Fatalf("topic suffix was duplicated: %+v", cfg)
	}
}

func TestLoadReadsEnvironmentFromNearestWorkingDirectory(t *testing.T) {
	root := t.TempDir()
	child := filepath.Join(root, "nested")
	if err := os.Mkdir(child, 0755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(root, ".env"), []byte("GITHUB_TOKEN=file-token\nGITHUB_OWNER=file-owner\nPORT=9090\n"), 0600); err != nil {
		t.Fatal(err)
	}
	t.Chdir(child)
	restoreEnv := func(key string) {
		value, present := os.LookupEnv(key)
		err := os.Unsetenv(key)
		if err != nil {
			t.Fatalf("Failed to unset env var %s: %v", key, err)
		}
		t.Cleanup(func() {
			if present {
				_ = os.Setenv(key, value)
			} else {
				_ = os.Unsetenv(key)
			}
		})
	}
	restoreEnv("GITHUB_TOKEN")
	restoreEnv("GITHUB_OWNER")
	restoreEnv("PORT")

	cfg, err := config.Load()
	if err != nil {
		t.Fatalf("Load failed: %v", err)
	}
	if cfg.GithubToken != "file-token" || cfg.GithubOwner != "file-owner" || cfg.Port != "9090" {
		t.Fatalf("dotenv values were not loaded: %+v", cfg)
	}
}
