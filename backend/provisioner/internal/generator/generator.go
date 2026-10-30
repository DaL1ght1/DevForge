package generator

import (
	"context"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"strings"

	"devforge/provisioner/internal/model"
)

type ArtifactSource interface {
	Get(ctx context.Context, digest string) (fs.FS, error)
}

type ProjectGenerator struct {
	TemplatesRoot string
	Artifacts     ArtifactSource
}

func NewProjectGenerator(templatesRoot string) *ProjectGenerator {
	return &ProjectGenerator{
		TemplatesRoot: templatesRoot,
	}
}

func (g *ProjectGenerator) WithArtifacts(source ArtifactSource) *ProjectGenerator {
	g.Artifacts = source
	return g
}

func (g *ProjectGenerator) Render(ctx context.Context, req model.GenerationRequest) (*Project, error) {
	if len(req.Services) > 0 {
		return g.renderApplication(ctx, req)
	}
	return g.renderService(ctx, req)
}

func (g *ProjectGenerator) renderService(ctx context.Context, req model.GenerationRequest) (*Project, error) {
	fsys, err := g.templateFS(ctx, req)
	if err != nil {
		return nil, err
	}
	return Render(fsys, req)
}

func (g *ProjectGenerator) templateFS(ctx context.Context, req model.GenerationRequest) (fs.FS, error) {
	if req.ArtifactDigest != "" && g.Artifacts != nil {
		fsys, err := g.Artifacts.Get(ctx, req.ArtifactDigest)
		if err != nil {
			return nil, fmt.Errorf("loading template artifact: %w", err)
		}
		return fsys, nil
	}
	if !singlePathElement(req.TemplateName) {
		return nil, fmt.Errorf("invalid template name %q", req.TemplateName)
	}
	templatePath := filepath.Join(g.TemplatesRoot, req.TemplateName)
	if info, err := os.Stat(templatePath); err != nil || !info.IsDir() {
		return nil, fmt.Errorf("template not found: %s (no stored artifact, run template sync)", templatePath)
	}
	return os.DirFS(templatePath), nil
}

func (g *ProjectGenerator) Generate(req model.GenerationRequest, outputDir string) (string, error) {
	if !singlePathElement(req.ServiceName) {
		return "", fmt.Errorf("invalid service name %q", req.ServiceName)
	}
	project, err := g.Render(context.Background(), req)
	if err != nil {
		return "", err
	}
	projectDir := filepath.Join(outputDir, req.ServiceName)
	if err := WriteProject(project, projectDir); err != nil {
		return "", err
	}
	return projectDir, nil
}

func WriteProject(project *Project, dir string) error {
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return fmt.Errorf("failed to create project directory: %w", err)
	}
	for _, f := range project.Files {
		target := filepath.Join(dir, filepath.FromSlash(f.Path))
		if err := os.MkdirAll(filepath.Dir(target), 0o755); err != nil {
			return err
		}
		if err := os.WriteFile(target, f.Content, f.Mode); err != nil {
			return err
		}
	}
	return nil
}

func singlePathElement(name string) bool {
	return name != "" && name != "." && name != ".." && !strings.ContainsAny(name, `/\`)
}
