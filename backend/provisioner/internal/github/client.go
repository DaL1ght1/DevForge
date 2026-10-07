package github

import (
	"context"
	"fmt"
	"time"

	"github.com/google/go-github/v92/github"
)

type Client struct {
	ghClient *github.Client
	owner    string
	isOrg    bool
}

func NewClient(token, owner string, isOrg bool) (*Client, error) {
	gh, err := github.NewClient(
		github.WithAuthToken(token),
		github.WithTimeout(30*time.Second),
	)
	if err != nil {
		return nil, fmt.Errorf("failed to create github client: %w", err)
	}

	return &Client{ghClient: gh, owner: owner, isOrg: isOrg}, nil
}

func (c *Client) CreateRepository(ctx context.Context, name, description string) (cloneURL, htmlURL string, err error) {
	repo := &github.Repository{
		Name:        new(name),
		Description: new(description),
		Private:     new(true),
		AutoInit:    new(false),
	}

	org := ""
	if c.isOrg {
		org = c.owner
	}

	created, _, err := c.ghClient.Repositories.Create(ctx, org, repo)
	if err != nil {
		return "", "", fmt.Errorf("failed to create github repo %q: %w", name, err)
	}

	return created.GetCloneURL(), created.GetHTMLURL(), nil
}
