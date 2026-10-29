package github

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"strings"
	"time"

	"github.com/google/go-github/v92/github"

	"devforge/provisioner/internal/model"
)

var ErrRepositoryExists = errors.New("repository already exists")
var ErrNotPermitted = errors.New("not permitted to create repository for this owner")

type Client struct {
	ghClient *github.Client
}

type Repository struct {
	ID       int64
	CloneURL string
	HTMLURL  string
}

func NewClient(transport http.RoundTripper, opts ...github.ClientOptionsFunc) (*Client, error) {
	opts = append([]github.ClientOptionsFunc{
		github.WithTransport(transport),
		github.WithTimeout(30 * time.Second),
	}, opts...)
	gh, err := github.NewClient(opts...)
	if err != nil {
		return nil, fmt.Errorf("failed to create github client: %w", err)
	}

	return &Client{ghClient: gh}, nil
}

func NewTokenClient(token string, opts ...github.ClientOptionsFunc) (*Client, error) {
	return NewClient(http.DefaultTransport, append([]github.ClientOptionsFunc{github.WithAuthToken(token)}, opts...)...)
}

func (c *Client) CreateRepository(ctx context.Context, owner, ownerType, name, description string, private bool) (*Repository, error) {
	repo := &github.Repository{
		Name:        new(name),
		Description: new(description),
		Private:     new(private),
		AutoInit:    new(false),
	}
	org := owner
	if ownerType == model.OwnerTypeUser {
		org = ""
	}
	created, _, err := c.ghClient.Repositories.Create(ctx, org, repo)
	if err != nil {
		if isNameTaken(err) {
			return nil, fmt.Errorf("github repo %q in %q: %w", name, owner, ErrRepositoryExists)
		}
		if isNotPermitted(err) {
			return nil, fmt.Errorf("github repo %q in %q: %w: %v", name, owner, ErrNotPermitted, err)
		}
		return nil, fmt.Errorf("failed to create github repo %q: %w", name, err)
	}

	result := &Repository{
		ID:       created.GetID(),
		CloneURL: created.GetCloneURL(),
		HTMLURL:  created.GetHTMLURL(),
	}
	if actual := created.GetOwner().GetLogin(); !strings.EqualFold(actual, owner) {
		if delErr := c.DeleteRepository(ctx, actual, name, result.ID); delErr != nil {
			return nil, fmt.Errorf("repository was created under %q instead of %q and cleanup failed: %w", actual, owner, delErr)
		}
		return nil, fmt.Errorf("repository was created under %q instead of %q", actual, owner)
	}
	return result, nil
}
func (c *Client) DeleteRepository(ctx context.Context, owner, name string, expectedID int64) error {
	existing, _, err := c.ghClient.Repositories.Get(ctx, owner, name)
	if err != nil {
		return fmt.Errorf("failed to look up github repo %q before delete: %w", name, err)
	}
	if existing.GetID() != expectedID {
		return fmt.Errorf("refusing to delete github repo %q: id %d does not match created id %d",
			name, existing.GetID(), expectedID)
	}
	if _, err := c.ghClient.Repositories.Delete(ctx, owner, name); err != nil {
		return fmt.Errorf("failed to delete github repo %q: %w", name, err)
	}
	return nil
}

func isNameTaken(err error) bool {
	var ghErr *github.ErrorResponse
	if !errors.As(err, &ghErr) || ghErr.Response == nil || ghErr.Response.StatusCode != http.StatusUnprocessableEntity {
		return false
	}
	for _, e := range ghErr.Errors {
		if e.Field == "name" && strings.Contains(strings.ToLower(e.Message), "already exists") {
			return true
		}
	}
	return false
}

func isNotPermitted(err error) bool {
	var ghErr *github.ErrorResponse
	if !errors.As(err, &ghErr) || ghErr.Response == nil {
		return false
	}
	return ghErr.Response.StatusCode == http.StatusForbidden || ghErr.Response.StatusCode == http.StatusNotFound
}
