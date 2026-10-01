package generator_test

import (
	"os"
	"path/filepath"
	"strings"
	"testing"

	"devforge/provisioner/internal/generator"
	"devforge/provisioner/internal/model"
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

	gen := generator.NewProjectGenerator(tempTemplateDir)

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
