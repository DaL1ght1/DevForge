package generator

import (
	"devforge/provisioner/internal/model"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestProjectGenerator(t *testing.T) {
	tempTemplateDir, err := os.MkdirTemp("", "templates-*")
	if err != nil {
		t.Fatal(err)
	}

	defer func(path string) {
		err := os.RemoveAll(path)
		if err != nil {
			t.Fatalf("Failed to remove temp directory: %v", err)
		}
	}(tempTemplateDir)

	springBootTemplate := filepath.Join(tempTemplateDir, "spring-boot-maven")
	codeDir := filepath.Join(springBootTemplate, "src", "main", "java", "__PACKAGE_PATH__")
	if err := os.MkdirAll(codeDir, 0755); err != nil {
		t.Fatal(err)
	}

	pomContent := `<project><artifactId>{{SERVICE_NAME}}</artifactId><groupId>{{PACKAGE_NAME}}</groupId></project>`
	err = os.WriteFile(filepath.Join(springBootTemplate, "pom.xml"), []byte(pomContent), 0644)
	if err != nil {
		t.Fatalf("Failed to write template file: %v", err)
		return
	}

	appContent := `package {{PACKAGE_NAME}}; public class {{CLASS_NAME}}Application {}`
	err = os.WriteFile(filepath.Join(codeDir, "__CLASS_NAME__Application.java.template"), []byte(appContent), 0644)
	if err != nil {
		t.Fatalf("Failed to write template file: %v", err)
	}

	tempOutputDir, err := os.MkdirTemp("", "output-*")
	if err != nil {
		t.Fatal(err)
	}
	defer func(path string) {
		err := os.RemoveAll(path)
		if err != nil {
			t.Fatalf("Failed to remove temp directory: %v", err)
		}
	}(tempOutputDir)

	gen := NewProjectGenerator(tempTemplateDir)

	req := model.GenerationRequest{
		ServiceName:  "order-service",
		TemplateName: "spring-boot-maven",
		PackageName:  "com.devforge.orderservice",
		ClassName:    "OrderService",
		Description:  "Order microservice",
		DatabaseType: "POSTGRESQL",
	}

	projectDir, err := gen.Generate(req, tempOutputDir)
	if err != nil {
		t.Fatalf("Generate failed: %v", err)
	}

	pomData, err := os.ReadFile(filepath.Join(projectDir, "pom.xml"))
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(string(pomData), "<artifactId>order-service</artifactId>") {
		t.Errorf("Expected pom.xml to contain replaced service name, got: %s", string(pomData))
	}

	expectedJavaFile := filepath.Join(projectDir, "src", "main", "java", "com", "devforge", "orderservice", "OrderServiceApplication.java")
	javaData, err := os.ReadFile(expectedJavaFile)
	if err != nil {
		t.Fatalf("Expected file to exist at %s: %v", expectedJavaFile, err)
	}
	if !strings.Contains(string(javaData), "public class OrderServiceApplication") {
		t.Errorf("Expected OrderServiceApplication in file, got: %s", string(javaData))
	}
}

func TestProjectGeneratorReturnsErrorForMissingTemplate(t *testing.T) {
	gen := NewProjectGenerator(t.TempDir())

	_, err := gen.Generate(model.GenerationRequest{
		ServiceName:  "missing-service",
		TemplateName: "does-not-exist",
	}, t.TempDir())
	if err == nil {
		t.Fatal("expected missing template to return an error")
	}
	if !strings.Contains(err.Error(), "template not found") {
		t.Fatalf("unexpected error: %v", err)
	}
}

func TestProjectGeneratorReplacesAllVariablesAndPathTokens(t *testing.T) {
	templateRoot := t.TempDir()
	template := filepath.Join(templateRoot, "go-gin")
	sourceDir := filepath.Join(template, "__PACKAGE_PATH__")
	if err := os.MkdirAll(sourceDir, 0755); err != nil {
		t.Fatal(err)
	}
	content := "{{SERVICE_NAME}}|{{CLASS_NAME}}|{{PACKAGE_NAME}}|{{DESCRIPTION}}|{{DATABASE_TYPE}}"
	if err := os.WriteFile(filepath.Join(sourceDir, "__SERVICE_NAME__.txt.template"), []byte(content), 0644); err != nil {
		t.Fatal(err)
	}

	projectDir, err := NewProjectGenerator(templateRoot).Generate(model.GenerationRequest{
		ServiceName:  "catalog-service",
		TemplateName: "go-gin",
		PackageName:  "com.devforge.catalog",
		ClassName:    "CatalogService",
		Description:  "Catalog API",
		DatabaseType: "NONE",
	}, t.TempDir())
	if err != nil {
		t.Fatalf("Generate failed: %v", err)
	}

	output := filepath.Join(projectDir, "com", "devforge", "catalog", "catalog-service.txt")
	data, err := os.ReadFile(output)
	if err != nil {
		t.Fatalf("expected generated file: %v", err)
	}
	expected := "catalog-service|CatalogService|com.devforge.catalog|Catalog API|NONE"
	if string(data) != expected {
		t.Fatalf("unexpected generated content: %q", data)
	}
}
