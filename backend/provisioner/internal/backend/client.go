package backend

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"
)

type Client struct {
	baseURL string
	token   string
	http    *http.Client
}

func NewClient(baseURL, token string) *Client {
	return &Client{
		baseURL: strings.TrimRight(baseURL, "/"),
		token:   token,
		http:    &http.Client{Timeout: 15 * time.Second},
	}
}

func (c *Client) UserToken(ctx context.Context, jobID string) (string, error) {
	endpoint := c.baseURL + "/internal/provisioning/jobs/" + url.PathEscape(jobID) + "/user-token"
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, endpoint, bytes.NewReader(nil))
	if err != nil {
		return "", fmt.Errorf("building user token request: %w", err)
	}
	req.Header.Set("X-Internal-Token", c.token)
	req.Header.Set("Accept", "application/json")

	resp, err := c.http.Do(req)
	if err != nil {
		return "", fmt.Errorf("requesting user token: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()
	body, _ := io.ReadAll(io.LimitReader(resp.Body, 64<<10))
	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("backend refused user token (%d): %s", resp.StatusCode, strings.TrimSpace(string(body)))
	}
	var payload struct {
		Token string `json:"token"`
	}
	if err := json.Unmarshal(body, &payload); err != nil || payload.Token == "" {
		return "", fmt.Errorf("backend returned an invalid user token response")
	}
	return payload.Token, nil
}

const maxArtifactBytes = 20 << 20

var ErrNotFound = errors.New("not found")

type JobClaim struct {
	Proceed      bool  `json:"proceed"`
	Attempt      int   `json:"attempt"`
	RepositoryID int64 `json:"repositoryId"`
}

func (c *Client) ClaimJob(ctx context.Context, jobID string) (JobClaim, error) {
	var claim JobClaim
	body, status, err := c.do(ctx, http.MethodPost, "/internal/provisioning/jobs/"+url.PathEscape(jobID)+"/claim", nil, 64<<10)
	if err != nil {
		return claim, err
	}
	if status == http.StatusNotFound {
		return JobClaim{Proceed: false}, nil
	}
	if status != http.StatusOK {
		return claim, fmt.Errorf("backend refused job claim (%d): %s", status, strings.TrimSpace(string(body)))
	}
	if err := json.Unmarshal(body, &claim); err != nil {
		return claim, fmt.Errorf("backend returned an invalid job claim: %w", err)
	}
	return claim, nil
}

func (c *Client) RecordRepository(ctx context.Context, jobID string, repositoryID int64) error {
	payload, _ := json.Marshal(map[string]int64{"repositoryId": repositoryID})
	body, status, err := c.do(ctx, http.MethodPut, "/internal/provisioning/jobs/"+url.PathEscape(jobID)+"/repository", payload, 64<<10)
	if err != nil {
		return err
	}
	if status != http.StatusNoContent && status != http.StatusOK {
		return fmt.Errorf("backend refused repository record (%d): %s", status, strings.TrimSpace(string(body)))
	}
	return nil
}

func (c *Client) TemplateArtifact(ctx context.Context, digest string) ([]byte, error) {
	body, status, err := c.do(ctx, http.MethodGet, "/internal/templates/artifacts/"+url.PathEscape(digest), nil, maxArtifactBytes+1)
	if err != nil {
		return nil, err
	}
	if status == http.StatusNotFound {
		return nil, fmt.Errorf("template artifact %s: %w", digest, ErrNotFound)
	}
	if status != http.StatusOK {
		return nil, fmt.Errorf("backend refused template artifact (%d)", status)
	}
	if len(body) > maxArtifactBytes {
		return nil, fmt.Errorf("template artifact %s exceeds %d bytes", digest, maxArtifactBytes)
	}
	return body, nil
}

func (c *Client) do(ctx context.Context, method, path string, payload []byte, limit int64) ([]byte, int, error) {
	req, err := http.NewRequestWithContext(ctx, method, c.baseURL+path, bytes.NewReader(payload))
	if err != nil {
		return nil, 0, fmt.Errorf("building %s %s: %w", method, path, err)
	}
	req.Header.Set("X-Internal-Token", c.token)
	if payload != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	resp, err := c.http.Do(req)
	if err != nil {
		return nil, 0, fmt.Errorf("%s %s: %w", method, path, err)
	}
	defer func() { _ = resp.Body.Close() }()
	body, err := io.ReadAll(io.LimitReader(resp.Body, limit))
	if err != nil {
		return nil, resp.StatusCode, fmt.Errorf("reading %s %s: %w", method, path, err)
	}
	return body, resp.StatusCode, nil
}
