package github

import (
	"context"
	"fmt"
	"net/http"
	"time"

	"github.com/google/go-github/v92/github"
)

type Client struct {
	ghClient *github.Client
	owner    string
}

func NewClient(transport http.RoundTripper, owner string) (*Client, error) {
	gh, err := github.NewClient(
		github.WithTransport(transport),
		github.WithTimeout(30*time.Second),
	)
	if err != nil {
		return nil, fmt.Errorf("failed to create github client: %w", err)
	}

	return &Client{ghClient: gh, owner: owner}, nil
}

func (c *Client) CreateRepository(ctx context.Context, name, description string) (cloneURL, htmlURL string, err error) {
	repo := &github.Repository{
		Name:        new(name),
		Description: new(description),
		Private:     new(true),
		AutoInit:    new(false),
	}
	created, _, err := c.ghClient.Repositories.Create(ctx, c.owner, repo)
	if err != nil {
		return "", "", fmt.Errorf("failed to create github repo %q: %w", name, err)
	}

	return created.GetCloneURL(), created.GetHTMLURL(), nil
}
