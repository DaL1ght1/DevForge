package generator

import (
	"context"
	"embed"
	"errors"
	"fmt"
	"io/fs"
	"slices"
	"sort"
	"strings"
	"sync"

	"go.yaml.in/yaml/v3"

	"devforge/provisioner/internal/model"
)

const (
	OriginApplication     = "application"
	maxApplicationService = 10
	applicationRenderJobs = 4
	servicesDir           = "services"
)

//go:embed all:scaffold/monorepo
var scaffoldFS embed.FS

var (
	databaseOptions = []string{"postgresql", "mysql", "mongodb"}
	brokerOptions   = []string{"kafka", "rabbitmq"}
)

type appService struct {
	Name         string
	Path         string
	Port         int
	Template     string
	Version      string
	Database     string
	DatabaseName string
	Broker       string
	Features     map[string][]string
}

type appData struct {
	Name        string
	Description string
	Services    []appService
	Postgres    []string
	MySQL       []string
	Mongo       []string
	Kafka       bool
	RabbitMQ    bool
}

func validateApplication(req model.GenerationRequest) error {
	if len(req.Services) > maxApplicationService {
		return fmt.Errorf("an application has at most %d services", maxApplicationService)
	}
	names := map[string]bool{}
	ports := map[int]bool{}
	for _, s := range req.Services {
		if !singlePathElement(s.ServiceName) {
			return fmt.Errorf("invalid service name %q", s.ServiceName)
		}
		if names[s.ServiceName] {
			return fmt.Errorf("duplicate service name %q", s.ServiceName)
		}
		if s.Port < 1024 || s.Port > 65535 || ports[s.Port] {
			return fmt.Errorf("invalid or duplicate port %d for %q", s.Port, s.ServiceName)
		}
		names[s.ServiceName] = true
		ports[s.Port] = true
	}
	return nil
}

func (g *ProjectGenerator) renderApplication(ctx context.Context, req model.GenerationRequest) (*Project, error) {
	if err := validateApplication(req); err != nil {
		return nil, err
	}
	projects := make([]*Project, len(req.Services))
	errs := make([]error, len(req.Services))
	slots := make(chan struct{}, applicationRenderJobs)
	var wg sync.WaitGroup
	for i, svc := range req.Services {
		wg.Go(func() {
			slots <- struct{}{}
			defer func() { <-slots }()
			projects[i], errs[i] = g.renderService(ctx, svc.GenerationRequest())
			if errs[i] != nil {
				errs[i] = fmt.Errorf("service %q: %w", svc.ServiceName, errs[i])
			}
		})
	}
	wg.Wait()
	if err := errors.Join(errs...); err != nil {
		return nil, err
	}

	data := applicationData(req)
	files := map[string]File{}
	for i, svc := range req.Services {
		prefix := servicesDir + "/" + svc.ServiceName + "/"
		for _, f := range projects[i].Files {
			if strings.HasPrefix(f.Path, ".github/") {
				continue
			}
			f.Path = prefix + f.Path
			files[f.Path] = f
		}
	}
	scaffold, err := renderScaffold(data)
	if err != nil {
		return nil, err
	}
	for _, f := range scaffold {
		files[f.Path] = f
	}
	manifest, err := applicationManifest(req)
	if err != nil {
		return nil, err
	}
	files[manifest.Path] = manifest

	project := &Project{Files: make([]File, 0, len(files))}
	total := 0
	for _, f := range files {
		total += len(f.Content)
		project.Files = append(project.Files, f)
	}
	if total > maxProjectBytes {
		return nil, fmt.Errorf("generated application exceeds %d bytes", maxProjectBytes)
	}
	sort.Slice(project.Files, func(i, j int) bool { return project.Files[i].Path < project.Files[j].Path })
	return project, nil
}

func applicationData(req model.GenerationRequest) appData {
	data := appData{Name: req.ServiceName, Description: req.Description}
	for _, s := range req.Services {
		svc := appService{
			Name:         s.ServiceName,
			Path:         servicesDir + "/" + s.ServiceName,
			Port:         s.Port,
			Template:     s.TemplateName,
			Version:      s.TemplateVersion,
			Database:     firstSelected(s.Features, databaseOptions),
			DatabaseName: strings.ReplaceAll(s.ServiceName, "-", "_"),
			Broker:       firstSelected(s.Features, brokerOptions),
			Features:     s.Features,
		}
		switch svc.Database {
		case "postgresql":
			data.Postgres = append(data.Postgres, svc.DatabaseName)
		case "mysql":
			data.MySQL = append(data.MySQL, svc.DatabaseName)
		case "mongodb":
			data.Mongo = append(data.Mongo, svc.DatabaseName)
		}
		switch svc.Broker {
		case "kafka":
			data.Kafka = true
		case "rabbitmq":
			data.RabbitMQ = true
		}
		data.Services = append(data.Services, svc)
	}
	return data
}

func firstSelected(features map[string][]string, candidates []string) string {
	groups := make([]string, 0, len(features))
	for g := range features {
		groups = append(groups, g)
	}
	sort.Strings(groups)
	for _, g := range groups {
		for _, option := range features[g] {
			if slices.Contains(candidates, option) {
				return option
			}
		}
	}
	return ""
}

func renderScaffold(data appData) ([]File, error) {
	root, err := fs.Sub(scaffoldFS, "scaffold/monorepo")
	if err != nil {
		return nil, err
	}
	funcs := templateFuncs(selection{features: map[string][]string{}})
	var files []File
	err = fs.WalkDir(root, ".", func(p string, d fs.DirEntry, err error) error {
		if err != nil || d.IsDir() {
			return err
		}
		content, err := fs.ReadFile(root, p)
		if err != nil {
			return err
		}
		target := p
		if before, ok := strings.CutSuffix(p, templateSuffix); ok {
			target = before
			if content, err = executeTemplate(p, content, funcs, data); err != nil {
				return err
			}
			if len(strings.TrimSpace(string(content))) == 0 {
				return nil
			}
		}
		files = append(files, File{Path: target, Content: content, Mode: 0o644, Origin: OriginApplication})
		return nil
	})
	return files, err
}

type applicationManifestFile struct {
	Application string                       `yaml:"application"`
	Services    []applicationManifestService `yaml:"services"`
}

type applicationManifestService struct {
	Name     string              `yaml:"name"`
	Path     string              `yaml:"path"`
	Port     int                 `yaml:"port"`
	Template string              `yaml:"template"`
	Version  string              `yaml:"version"`
	Features map[string][]string `yaml:"features,omitempty"`
}

func applicationManifest(req model.GenerationRequest) (File, error) {
	m := applicationManifestFile{Application: req.ServiceName}
	for _, s := range req.Services {
		m.Services = append(m.Services, applicationManifestService{
			Name:     s.ServiceName,
			Path:     servicesDir + "/" + s.ServiceName,
			Port:     s.Port,
			Template: s.TemplateName,
			Version:  s.TemplateVersion,
			Features: s.Features,
		})
	}
	content, err := yaml.Marshal(m)
	if err != nil {
		return File{}, err
	}
	return File{Path: ".devforge.yaml", Content: content, Mode: 0o644, Origin: OriginDevForge}, nil
}
