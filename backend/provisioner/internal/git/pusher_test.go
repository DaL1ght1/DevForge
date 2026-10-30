package git

import (
	"io"
	"testing"

	"github.com/go-git/go-git/v5/plumbing/filemode"

	"devforge/provisioner/internal/generator"
)

func TestCommitProjectBuildsSingleCommitOnMainInMemory(t *testing.T) {
	project := &generator.Project{Files: []generator.File{
		{Path: "README.md", Content: []byte("# demo"), Mode: 0o644},
		{Path: "src/main/App.java", Content: []byte("class App {}"), Mode: 0o644},
		{Path: "mvnw", Content: []byte("#!/bin/sh"), Mode: 0o755},
	}}

	repo, err := commitProject(project)
	if err != nil {
		t.Fatalf("commitProject: %v", err)
	}
	head, err := repo.Head()
	if err != nil {
		t.Fatal(err)
	}
	if head.Name() != mainBranch {
		t.Fatalf("HEAD = %s, want %s", head.Name(), mainBranch)
	}
	commit, err := repo.CommitObject(head.Hash())
	if err != nil {
		t.Fatal(err)
	}
	if commit.NumParents() != 0 || commit.Message != commitMessage {
		t.Fatalf("unexpected commit %+v", commit)
	}
	tree, err := commit.Tree()
	if err != nil {
		t.Fatal(err)
	}
	for _, f := range project.Files {
		entry, err := tree.FindEntry(f.Path)
		if err != nil {
			t.Fatalf("%s missing from commit: %v", f.Path, err)
		}
		if f.Mode == 0o755 && entry.Mode != filemode.Executable {
			t.Fatalf("%s mode = %v, want executable", f.Path, entry.Mode)
		}
		file, _ := tree.File(f.Path)
		reader, _ := file.Reader()
		content, _ := io.ReadAll(reader)
		_ = reader.Close()
		if string(content) != string(f.Content) {
			t.Fatalf("%s content = %q", f.Path, content)
		}
	}
}
