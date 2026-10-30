package git

import (
	"context"
	"fmt"
	"os"
	"path"
	"time"

	"github.com/go-git/go-billy/v5/memfs"
	"github.com/go-git/go-git/v5"
	"github.com/go-git/go-git/v5/config"
	"github.com/go-git/go-git/v5/plumbing"
	"github.com/go-git/go-git/v5/plumbing/object"
	"github.com/go-git/go-git/v5/plumbing/transport/http"
	"github.com/go-git/go-git/v5/storage/memory"

	"devforge/provisioner/internal/generator"
)

type TokenSource interface {
	Token(ctx context.Context) (string, error)
}

type StaticToken string

func (t StaticToken) Token(context.Context) (string, error) { return string(t), nil }

type Pusher struct{}

func NewPusher() *Pusher {
	return &Pusher{}
}

const (
	commitMessage = "chore: initial service scaffold by DevForge"
	mainBranch    = "refs/heads/main"
)

func (p *Pusher) PushProject(ctx context.Context, project *generator.Project, remoteURL string, tokens TokenSource, force bool) error {
	repo, err := commitProject(project)
	if err != nil {
		return err
	}
	if _, err := repo.CreateRemote(&config.RemoteConfig{Name: "origin", URLs: []string{remoteURL}}); err != nil {
		return fmt.Errorf("failed to set remote origin: %w", err)
	}
	token, err := tokens.Token(ctx)
	if err != nil {
		return fmt.Errorf("failed to get github token: %w", err)
	}
	err = repo.PushContext(ctx, &git.PushOptions{
		RemoteName: "origin",
		RefSpecs:   []config.RefSpec{config.RefSpec(mainBranch + ":" + mainBranch)},
		Auth:       &http.BasicAuth{Username: "x-access-token", Password: token},
		Force:      force,
	})
	if err != nil {
		return fmt.Errorf("git push failed: %w", err)
	}
	return nil
}

func commitProject(project *generator.Project) (*git.Repository, error) {
	worktreeFS := memfs.New()
	repo, err := git.InitWithOptions(memory.NewStorage(), worktreeFS, git.InitOptions{
		DefaultBranch: plumbing.ReferenceName(mainBranch),
	})
	if err != nil {
		return nil, fmt.Errorf("git init failed: %w", err)
	}
	for _, f := range project.Files {
		if err := worktreeFS.MkdirAll(path.Dir(f.Path), 0o755); err != nil {
			return nil, err
		}
		file, err := worktreeFS.OpenFile(f.Path, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, f.Mode)
		if err != nil {
			return nil, err
		}
		if _, err := file.Write(f.Content); err != nil {
			_ = file.Close()
			return nil, err
		}
		if err := file.Close(); err != nil {
			return nil, err
		}
	}
	worktree, err := repo.Worktree()
	if err != nil {
		return nil, fmt.Errorf("failed to get worktree: %w", err)
	}
	if err := worktree.AddWithOptions(&git.AddOptions{All: true}); err != nil {
		return nil, fmt.Errorf("git add failed: %w", err)
	}
	if _, err := worktree.Commit(commitMessage, &git.CommitOptions{
		Author: &object.Signature{Name: "DevForge Bot", Email: "bot@devforge.internal", When: time.Now()},
	}); err != nil {
		return nil, fmt.Errorf("git commit failed: %w", err)
	}
	return repo, nil
}
