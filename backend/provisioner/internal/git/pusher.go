package git

import (
	"fmt"
	"time"

	"github.com/go-git/go-git/v5"
	"github.com/go-git/go-git/v5/config"
	"github.com/go-git/go-git/v5/plumbing"
	"github.com/go-git/go-git/v5/plumbing/object"
	"github.com/go-git/go-git/v5/plumbing/transport/http"
)

type Pusher struct {
	token string
}

func NewPusher(token string) *Pusher {
	return &Pusher{token: token}
}

func (p *Pusher) PushDirectory(projectDir, remoteURL string) error {
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

	auth := &http.BasicAuth{
		Username: "oauth2",
		Password: p.token,
	}

	err = repo.Push(&git.PushOptions{
		RemoteName: "origin",
		RefSpecs:   []config.RefSpec{"refs/heads/main:refs/heads/main"},
		Auth:       auth,
	})
	if err != nil {
		return fmt.Errorf("git push failed: %w", err)
	}

	return nil
}
