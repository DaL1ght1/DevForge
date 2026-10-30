package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"strings"
	"unicode"

	"devforge/provisioner/internal/generator"
	"devforge/provisioner/internal/model"
)

func main() {
	log.SetFlags(0)

	templatesRoot := flag.String("templates-root", "backend/templates", "root directory containing templates")
	templateName := flag.String("template", "", "template directory name")
	outputDir := flag.String("output", "", "directory where the generated project is written")
	serviceName := flag.String("service-name", "ci-service", "generated service name (3-20 chars: a-z, 0-9, hyphen)")
	version := flag.String("version", "ci", "template version recorded in .devforge.yaml")
	features := keyValues{}
	variables := keyValues{}
	flag.Var(features, "feature", "selected feature as group=option (repeatable)")
	flag.Var(variables, "var", "template variable as NAME=value (repeatable)")
	listCI := flag.Bool("list-ci", false, "print the CI build matrix (templates x ci profiles) as JSON and exit")
	spec := flag.String("spec", "", "JSON file with a full generation request, e.g. a multi-service application")
	flag.Parse()

	if *spec != "" {
		if *outputDir == "" {
			flag.Usage()
			os.Exit(2)
		}
		projectDir, err := generateFromSpec(*templatesRoot, *spec, *outputDir)
		if err != nil {
			log.Fatalf("generate from %s: %v", *spec, err)
		}
		fmt.Println(projectDir)
		return
	}

	if *listCI {
		if err := writeCIMatrix(*templatesRoot, os.Stdout); err != nil {
			log.Fatalf("list ci matrix: %v", err)
		}
		return
	}

	if *templateName == "" || *outputDir == "" {
		flag.Usage()
		os.Exit(2)
	}

	name := strings.ToLower(strings.TrimSpace(*serviceName))
	if !validServiceName(name) {
		log.Fatalf("invalid -service-name %q: must be 3-20 characters of a-z, 0-9 or hyphen", name)
	}

	req := model.GenerationRequest{
		ServiceName:     name,
		TemplateName:    *templateName,
		TemplateVersion: *version,
		PackageName:     "com.devforge." + packageSegment(name),
		ClassName:       pascalCase(name),
		Description:     "CI generated project",
		Variables:       variables.single(),
		Features:        features,
	}

	if err := os.MkdirAll(filepath.Clean(*outputDir), 0o755); err != nil {
		log.Fatalf("create output directory: %v", err)
	}

	projectDir, err := generator.NewProjectGenerator(*templatesRoot).Generate(req, *outputDir)
	if err != nil {
		log.Fatalf("generate template %q: %v", *templateName, err)
	}
	fmt.Println(projectDir)
}

func generateFromSpec(templatesRoot, specPath, outputDir string) (string, error) {
	data, err := os.ReadFile(specPath)
	if err != nil {
		return "", err
	}
	var req model.GenerationRequest
	if err := json.Unmarshal(data, &req); err != nil {
		return "", err
	}
	for i, s := range req.Services {
		if s.PackageName == "" {
			req.Services[i].PackageName = "com.devforge." + packageSegment(s.ServiceName)
		}
		if s.ClassName == "" {
			req.Services[i].ClassName = pascalCase(s.ServiceName)
		}
	}
	if err := os.MkdirAll(filepath.Clean(outputDir), 0o755); err != nil {
		return "", err
	}
	return generator.NewProjectGenerator(templatesRoot).Generate(req, outputDir)
}

type keyValues map[string][]string

func (kv keyValues) String() string { return fmt.Sprint(map[string][]string(kv)) }

func (kv keyValues) Set(s string) error {
	key, value, ok := strings.Cut(s, "=")
	if !ok || key == "" || value == "" {
		return fmt.Errorf("expected KEY=VALUE, got %q", s)
	}
	kv[key] = append(kv[key], value)
	return nil
}

func (kv keyValues) single() map[string]string {
	out := make(map[string]string, len(kv))
	for k, v := range kv {
		out[k] = v[len(v)-1]
	}
	return out
}

func validServiceName(s string) bool {
	if len(s) < 3 || len(s) > 20 {
		return false
	}
	for _, r := range s {
		switch {
		case r >= 'a' && r <= 'z', r >= '0' && r <= '9', r == '-':
		default:
			return false
		}
	}
	return true
}

func packageSegment(s string) string {
	var b strings.Builder
	for _, r := range s {
		if (r >= 'a' && r <= 'z') || (r >= '0' && r <= '9') {
			b.WriteRune(r)
		}
	}
	seg := b.String()
	if seg == "" {
		return "app"
	}
	if seg[0] >= '0' && seg[0] <= '9' {
		return "s" + seg
	}
	return seg
}

func pascalCase(s string) string {
	parts := strings.FieldsFunc(s, func(r rune) bool { return r == '-' || r == '_' })
	var b strings.Builder
	for _, p := range parts {
		runes := []rune(p)
		runes[0] = unicode.ToUpper(runes[0])
		b.WriteString(string(runes))
	}
	if b.Len() == 0 {
		return "Application"
	}
	return b.String()
}
