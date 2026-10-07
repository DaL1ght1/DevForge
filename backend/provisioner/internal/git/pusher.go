package git

import (
	"context"
	"fmt"
	"time"

	"github.com/go-git/go-git/v5"
	"github.com/go-git/go-git/v5/config"
	"github.com/go-git/go-git/v5/plumbing"
	"github.com/go-git/go-git/v5/plumbing/object"
	"github.com/go-git/go-git/v5/plumbing/transport/http"
)

type TokenSource interface {
	Token(ctx context.Context) (string, error)
}

type Pusher struct {
	tokens TokenSource
}

func NewPusher(tokens TokenSource) *Pusher {
	return &Pusher{tokens: tokens}
}

func (p *Pusher) PushDirectory(ctx context.Context, projectDir, remoteURL string) error {
	repo, err := git.PlainInit(projectDir, false)
	if err != nil {
		return fmt.Errorf("git init failed: %w", err)
	}

	worktree, err := repo.Worktree()
	if err != nil {
		return fmt.Errorf("failed to get worktree: %w", err)
	}
	if err := worktree.AddWithOptions(&git.AddOptions{All: true}); err != nil {
		return fmt.Errorf("git add failed: %w", err)
	}
	commitHash, err := worktree.Commit("chore: initial service scaffold by DevForge", &git.CommitOptions{
		Author: &object.Signature{
			Name:  "DevForge Bot",
			Email: "bot@devforge.internal",
			When:  time.Now(),
		},
	})
	if err != nil {
		return fmt.Errorf("git commit failed: %w", err)
	}
	headRef := plumbing.NewHashReference(plumbing.NewBranchReferenceName("main"), commitHash)
	if err := repo.Storer.SetReference(headRef); err != nil {
		return fmt.Errorf("failed to create main branch ref: %w", err)
	}
	_, err = repo.CreateRemote(&config.RemoteConfig{
		Name: "origin",
		URLs: []string{remoteURL},
	})
	if err != nil {
		return fmt.Errorf("failed to set remote origin: %w", err)
	}
	token, err := p.tokens.Token(ctx)
	if err != nil {
		return fmt.Errorf("failed to get github installation token: %w", err)
	}

	auth := &http.BasicAuth{
		Username: "x-access-token",
		Password: token,
	}

	err = repo.PushContext(ctx, &git.PushOptions{
		RemoteName: "origin",
		RefSpecs:   []config.RefSpec{"refs/heads/main:refs/heads/main"},
		Auth:       auth,
	})
	if err != nil {
		return fmt.Errorf("git push failed: %w", err)
	}

	return nil
}
