package main

import (
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
	databaseType := flag.String("database-type", "NONE", "database type variable")
	flag.Parse()

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
		DatabaseType:    *databaseType,
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
