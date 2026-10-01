package generator

import (
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"strings"

	"devforge/provisioner/internal/model"
)

type ProjectGenerator struct {
	TemplatesRoot string
}

func NewProjectGenerator(templatesRoot string) *ProjectGenerator {
	return &ProjectGenerator{
		TemplatesRoot: templatesRoot,
	}
}

func (g *ProjectGenerator) Generate(req model.GenerationRequest, outputDir string) (string, error) {
	templatePath := filepath.Join(g.TemplatesRoot, req.TemplateName)

	if _, err := os.Stat(templatePath); os.IsNotExist(err) {
		return "", fmt.Errorf("template not found: %s", templatePath)
	}

	projectDir := filepath.Join(outputDir, req.ServiceName)
	if err := os.MkdirAll(projectDir, 0755); err != nil {
		return "", fmt.Errorf("failed to create project directory: %w", err)
	}

	variables := map[string]string{
		"SERVICE_NAME":  req.ServiceName,
		"CLASS_NAME":    req.ClassName,
		"PACKAGE_NAME":  req.PackageName,
		"PACKAGE_PATH":  strings.ReplaceAll(req.PackageName, ".", "/"),
		"DESCRIPTION":   req.Description,
		"DATABASE_TYPE": req.DatabaseType,
	}

	err := filepath.WalkDir(templatePath, func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}

		relPath, err := filepath.Rel(templatePath, path)
		if err != nil {
			return err
		}

		if relPath == "." {
			return nil
		}
		resolvedRelPath := g.resolvePathTokens(relPath, variables)
		targetPath := filepath.Join(projectDir, resolvedRelPath)

		if d.IsDir() {
			return os.MkdirAll(targetPath, 0755)
		}

		return g.processAndWriteFile(path, targetPath, variables)
	})

	if err != nil {
		return "", fmt.Errorf("error walking template: %w", err)
	}

	return projectDir, nil
}

func (g *ProjectGenerator) resolvePathTokens(path string, vars map[string]string) string {
	res := path
	res = strings.ReplaceAll(res, "__PACKAGE_PATH__", vars["PACKAGE_PATH"])
	res = strings.ReplaceAll(res, "__CLASS_NAME__", vars["CLASS_NAME"])
	res = strings.ReplaceAll(res, "__SERVICE_NAME__", vars["SERVICE_NAME"])
	res = strings.TrimSuffix(res, ".template")
	return res
}

func (g *ProjectGenerator) processAndWriteFile(srcPath, destPath string, vars map[string]string) error {
	content, err := os.ReadFile(srcPath)
	if err != nil {
		return err
	}

	replacedContent := string(content)
	for key, val := range vars {
		placeholder := "{{" + key + "}}"
		replacedContent = strings.ReplaceAll(replacedContent, placeholder, val)
	}

	if err := os.MkdirAll(filepath.Dir(destPath), 0755); err != nil {
		return err
	}

	return os.WriteFile(destPath, []byte(replacedContent), 0644)
}
