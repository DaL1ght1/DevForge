package generator

import (
	"context"
	"errors"
	"io/fs"
	"strings"
	"testing"
	"testing/fstest"

	"devforge/provisioner/internal/model"
)

const v2Manifest = `
schemaVersion: 2
features:
  - id: database
    kind: choice
    options:
      - id: postgresql
        dependencies:
          - { group: org.postgresql, artifact: postgresql, scope: runtime }
          - { group: org.springframework.boot, artifact: spring-boot-starter-data-jpa }
      - id: mysql
        dependencies:
          - { group: com.mysql, artifact: mysql-connector-j, scope: runtime }
          - { group: org.springframework.boot, artifact: spring-boot-starter-data-jpa }
  - id: extras
    kind: multi
    options:
      - id: openapi
        dependencies:
          - { group: org.springdoc, artifact: springdoc-openapi-starter-webmvc-ui, version: "2.6.0" }
      - id: actuator
`

func v2Template() fstest.MapFS {
	return fstest.MapFS{
		"template.yaml": {Data: []byte(v2Manifest)},
		"base/pom.xml.tmpl": {Data: []byte(`<artifactId>[[ .ServiceName ]]</artifactId><description>[[ xml .Description ]]</description>
[[- range .Dependencies ]]
<dep>[[ .Group ]]:[[ .Artifact ]][[ with .Scope ]]:[[ . ]][[ end ]]</dep>
[[- end ]]`)},
		"base/src/__PACKAGE_PATH__/App.java.tmpl":             {Data: []byte(`package [[ .PackageName ]]; // db=[[ selected "database" ]]`)},
		"base/src/test/Containers.java.tmpl":                  {Data: []byte(`[[ if has "database" ]]containers for [[ selected "database" ]][[ end ]]`)},
		"base/.github/workflows/ci.yml":                       {Data: []byte("tags: ${{ steps.meta.outputs.tags }} {{is_default_branch}}")},
		"base/mvnw":                                           {Data: []byte("#!/bin/sh"), Mode: 0o755},
		"features/postgresql/src/main/schema.sql":             {Data: []byte("-- postgres")},
		"features/openapi/src/__PACKAGE_PATH__/Api.java.tmpl": {Data: []byte(`package [[ .PackageName ]].api;`)},
		"features/mysql/src/main/schema.sql":                  {Data: []byte("-- mysql")},
	}
}

func v2Request(features map[string][]string) model.GenerationRequest {
	return model.GenerationRequest{
		ServiceName:     "orders",
		TemplateName:    "spring",
		TemplateVersion: "2.0.0",
		PackageName:     "com.devforge.orders",
		ClassName:       "Orders",
		Description:     "Orders & <stuff>",
		Features:        features,
	}
}

func files(t *testing.T, p *Project) map[string]File {
	t.Helper()
	out := map[string]File{}
	for _, f := range p.Files {
		out[f.Path] = f
	}
	return out
}

func TestRenderV2AppliesSelectedFeatureOverlaysAndDependencies(t *testing.T) {
	project, err := Render(v2Template(), v2Request(map[string][]string{
		"database": {"postgresql"},
		"extras":   {"actuator", "openapi"},
	}))
	if err != nil {
		t.Fatalf("Render: %v", err)
	}
	got := files(t, project)

	pom := string(got["pom.xml"].Content)
	wantPom := `<artifactId>orders</artifactId><description>Orders &amp; &lt;stuff&gt;</description>
<dep>org.postgresql:postgresql:runtime</dep>
<dep>org.springframework.boot:spring-boot-starter-data-jpa</dep>
<dep>org.springdoc:springdoc-openapi-starter-webmvc-ui</dep>`
	if pom != wantPom {
		t.Fatalf("pom.xml =\n%s\nwant\n%s", pom, wantPom)
	}
	if c := string(got["src/com/devforge/orders/App.java"].Content); c != "package com.devforge.orders; // db=postgresql" {
		t.Fatalf("App.java = %q", c)
	}
	if f, ok := got["src/main/schema.sql"]; !ok || string(f.Content) != "-- postgres" || f.Origin != "feature:postgresql" {
		t.Fatalf("schema.sql = %+v", f)
	}
	if _, ok := got["src/com/devforge/orders/Api.java"]; !ok {
		t.Fatal("openapi overlay not rendered")
	}
	if c := string(got[".github/workflows/ci.yml"].Content); c != "tags: ${{ steps.meta.outputs.tags }} {{is_default_branch}}" {
		t.Fatalf("non-template file was modified: %q", c)
	}
	if got["mvnw"].Mode != 0o755 {
		t.Fatalf("mvnw mode = %v, want executable", got["mvnw"].Mode)
	}
	if _, ok := got["template.yaml"]; ok {
		t.Fatal("manifest must not be copied into v2 projects")
	}
	devforge := string(got[".devforge.yaml"].Content)
	if !strings.Contains(devforge, "- postgresql") || !strings.Contains(devforge, "- openapi") {
		t.Fatalf(".devforge.yaml lacks features:\n%s", devforge)
	}
}

func TestRenderV2OmitsTemplatesThatRenderEmpty(t *testing.T) {
	project, err := Render(v2Template(), v2Request(nil))
	if err != nil {
		t.Fatalf("Render: %v", err)
	}
	got := files(t, project)
	if _, ok := got["src/test/Containers.java"]; ok {
		t.Fatal("empty template output should be omitted")
	}
	if _, ok := got["src/main/schema.sql"]; ok {
		t.Fatal("unselected overlay rendered")
	}
	if strings.Contains(string(got["pom.xml"].Content), "<dep>") {
		t.Fatal("dependencies rendered without features")
	}
}

func TestRenderV2RejectsInvalidSelections(t *testing.T) {
	cases := map[string]map[string][]string{
		"unknown group":      {"cache": {"redis"}},
		"unknown option":     {"database": {"oracle"}},
		"two options choice": {"database": {"postgresql", "mysql"}},
	}
	for name, features := range cases {
		t.Run(name, func(t *testing.T) {
			if _, err := Render(v2Template(), v2Request(features)); err == nil {
				t.Fatal("expected an error")
			}
		})
	}
}

func TestRenderV2FailsOnMissingVariable(t *testing.T) {
	fsys := v2Template()
	fsys["base/Dockerfile.tmpl"] = &fstest.MapFile{Data: []byte(`FROM eclipse-temurin:[[ .Vars.JAVA_VERSION ]]`)}
	if _, err := Render(fsys, v2Request(nil)); err == nil || !strings.Contains(err.Error(), "JAVA_VERSION") {
		t.Fatalf("expected missing variable error, got %v", err)
	}

	req := v2Request(nil)
	req.Variables = map[string]string{"JAVA_VERSION": "21"}
	project, err := Render(fsys, req)
	if err != nil {
		t.Fatalf("Render: %v", err)
	}
	if c := string(files(t, project)["Dockerfile"].Content); c != "FROM eclipse-temurin:21" {
		t.Fatalf("Dockerfile = %q", c)
	}
}

func TestRenderRejectsPathsEscapingTheProject(t *testing.T) {
	fsys := fstest.MapFS{"__SERVICE_NAME__/x.txt": {Data: []byte("x")}}
	req := v2Request(nil)
	req.ServiceName = ".."
	if _, err := Render(fsys, req); err == nil {
		t.Fatal("expected path escape to be rejected")
	}
}

func TestRenderLegacyKeepsPlaceholderSubstitution(t *testing.T) {
	fsys := fstest.MapFS{
		"template.yaml":    {Data: []byte("name: legacy\nvariables:\n  - name: SERVICE_NAME\n")},
		"main.go.template": {Data: []byte(`// {{SERVICE_NAME}} {{CLASS_NAME}}`)},
		"go.mod":           {Data: []byte(`module {{PACKAGE_NAME}}`)},
	}
	project, err := Render(fsys, v2Request(nil))
	if err != nil {
		t.Fatalf("Render: %v", err)
	}
	got := files(t, project)
	if c := string(got["main.go"].Content); c != "// orders Orders" {
		t.Fatalf("main.go = %q", c)
	}
	if c := string(got["go.mod"].Content); c != "module com.devforge.orders" {
		t.Fatalf("go.mod = %q", c)
	}
}

func TestGradleConfiguration(t *testing.T) {
	for scope, want := range map[string]string{"": "implementation", "runtime": "runtimeOnly", "test": "testImplementation"} {
		if got, err := gradleConfiguration(scope); err != nil || got != want {
			t.Fatalf("gradleConfiguration(%q) = %q, %v", scope, got, err)
		}
	}
	if _, err := gradleConfiguration("bogus"); err == nil {
		t.Fatal("expected unsupported scope error")
	}
}

func TestRenderV2FillsManifestDefaults(t *testing.T) {
	fsys := v2Template()
	fsys["template.yaml"] = &fstest.MapFile{Data: []byte(v2Manifest + "variables:\n  - name: JAVA_VERSION\n    default: 21\n")}
	fsys["base/Dockerfile.tmpl"] = &fstest.MapFile{Data: []byte(`FROM eclipse-temurin:[[ .Vars.JAVA_VERSION ]]`)}

	project, err := Render(fsys, v2Request(nil))
	if err != nil {
		t.Fatalf("Render: %v", err)
	}
	got := files(t, project)
	if c := string(got["Dockerfile"].Content); c != "FROM eclipse-temurin:21" {
		t.Fatalf("Dockerfile = %q", c)
	}
	if !strings.Contains(string(got[".devforge.yaml"].Content), `JAVA_VERSION: "21"`) {
		t.Fatalf(".devforge.yaml lacks the defaulted variable:\n%s", got[".devforge.yaml"].Content)
	}
}

type mapArtifacts map[string]fs.FS

func (m mapArtifacts) Get(_ context.Context, digest string) (fs.FS, error) {
	if f, ok := m[digest]; ok {
		return f, nil
	}
	return nil, errors.New("unknown artifact")
}

func TestProjectGeneratorPrefersArtifactOverDisk(t *testing.T) {
	gen := NewProjectGenerator(t.TempDir()).WithArtifacts(mapArtifacts{"abc": v2Template()})
	req := v2Request(nil)
	req.ArtifactDigest = "abc"

	project, err := gen.Render(context.Background(), req)
	if err != nil {
		t.Fatalf("Render: %v", err)
	}
	if _, ok := files(t, project)["pom.xml"]; !ok {
		t.Fatal("artifact template was not rendered")
	}

	req.ArtifactDigest = "missing"
	if _, err := gen.Render(context.Background(), req); err == nil {
		t.Fatal("expected unknown artifact to fail instead of falling back to disk")
	}
}
