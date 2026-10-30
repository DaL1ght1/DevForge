package generator

import (
	"context"
	"io/fs"
	"strings"
	"testing"
	"testing/fstest"

	"devforge/provisioner/internal/model"
)

func nodeTemplate() fstest.MapFS {
	return fstest.MapFS{
		"template.yaml":                 {Data: []byte("schemaVersion: 2\nfeatures:\n  - id: database\n    kind: choice\n    options:\n      - id: postgresql\n      - id: mongodb\n")},
		"base/package.json":             {Data: []byte(`{"name":"svc"}`)},
		"base/.github/workflows/ci.yml": {Data: []byte("name: CI")},
	}
}

func appService2(name string, port int, features map[string][]string) model.ApplicationService {
	return model.ApplicationService{
		ServiceName:     name,
		TemplateName:    "node",
		TemplateVersion: "1.0.0",
		ArtifactDigest:  "node",
		PackageName:     "com.devforge." + strings.ReplaceAll(name, "-", ""),
		ClassName:       "Svc",
		Features:        features,
		Port:            port,
	}
}

func shopRequest() model.GenerationRequest {
	return model.GenerationRequest{
		ServiceName: "shop",
		Description: "Shop & co",
		Services: []model.ApplicationService{
			appService2("orders", 8080, map[string][]string{"database": {"postgresql"}}),
			appService2("billing-api", 8081, map[string][]string{"database": {"postgresql"}}),
			appService2("catalog", 8082, map[string][]string{"database": {"mongodb"}}),
		},
	}
}

func appGenerator() *ProjectGenerator {
	return NewProjectGenerator("unused").WithArtifacts(mapArtifacts{"node": nodeTemplate()})
}

func TestApplicationPlacesServicesUnderTheirFoldersWithSharedInfrastructure(t *testing.T) {
	project, err := appGenerator().Render(context.Background(), shopRequest())
	if err != nil {
		t.Fatalf("Render: %v", err)
	}
	got := files(t, project)

	for _, p := range []string{"services/orders/package.json", "services/billing-api/package.json", "services/catalog/package.json", "services/orders/.devforge.yaml"} {
		if _, ok := got[p]; !ok {
			t.Fatalf("missing %s", p)
		}
	}
	if _, ok := got["services/orders/.github/workflows/ci.yml"]; ok {
		t.Fatal("nested service workflows must be dropped in a monorepo")
	}
	compose := string(got["compose.yaml"].Content)
	if strings.Count(compose, "image: postgres:16-alpine") != 1 {
		t.Fatalf("expected one shared postgres container:\n%s", compose)
	}
	for _, want := range []string{
		`"8081:8080"`,
		"jdbc:postgresql://postgres:5432/billing_api",
		"mongodb://mongodb:27017/catalog",
		"build: ./services/orders",
	} {
		if !strings.Contains(compose, want) {
			t.Fatalf("compose.yaml lacks %q:\n%s", want, compose)
		}
	}
	if strings.Contains(compose, "mysql") || strings.Contains(compose, "kafka") {
		t.Fatalf("unused infrastructure rendered:\n%s", compose)
	}
	initSQL := string(got["infra/postgres/init.sql"].Content)
	if !strings.Contains(initSQL, `CREATE DATABASE "orders" OWNER app;`) || !strings.Contains(initSQL, `"billing_api"`) {
		t.Fatalf("init.sql = %q", initSQL)
	}
	if _, ok := got["infra/mysql/init.sql"]; ok {
		t.Fatal("mysql init script rendered without mysql services")
	}
	ci := string(got[".github/workflows/ci.yml"].Content)
	if !strings.Contains(ci, "billing-api: services/billing-api/**") || !strings.Contains(ci, "${{ matrix.service }}") {
		t.Fatalf("ci.yml = %s", ci)
	}
	if got["compose.yaml"].Origin != OriginApplication || got["services/orders/package.json"].Origin != OriginBase {
		t.Fatal("origins not preserved")
	}
	if !strings.Contains(string(got[".devforge.yaml"].Content), "application: shop") {
		t.Fatalf("root manifest = %s", got[".devforge.yaml"].Content)
	}
	if !strings.Contains(string(got["README.md"].Content), "Shop & co") {
		t.Fatal("README lacks the description")
	}
}

func TestApplicationRejectsDuplicateNamesAndPorts(t *testing.T) {
	cases := map[string]func(*model.GenerationRequest){
		"duplicate name": func(r *model.GenerationRequest) { r.Services[1].ServiceName = "orders" },
		"duplicate port": func(r *model.GenerationRequest) { r.Services[1].Port = 8080 },
		"path escape":    func(r *model.GenerationRequest) { r.Services[0].ServiceName = "../x" },
		"privileged":     func(r *model.GenerationRequest) { r.Services[0].Port = 80 },
	}
	for name, mutate := range cases {
		t.Run(name, func(t *testing.T) {
			req := shopRequest()
			mutate(&req)
			if _, err := appGenerator().Render(context.Background(), req); err == nil {
				t.Fatal("expected an error")
			}
		})
	}
}

func TestApplicationReportsWhichServiceFailed(t *testing.T) {
	req := shopRequest()
	req.Services[2].Features = map[string][]string{"database": {"oracle"}}
	_, err := appGenerator().Render(context.Background(), req)
	if err == nil || !strings.Contains(err.Error(), `service "catalog"`) {
		t.Fatalf("expected catalog failure, got %v", err)
	}
}

func TestScaffoldIsEmbedded(t *testing.T) {
	if _, err := fs.Stat(scaffoldFS, "scaffold/monorepo/.gitignore"); err != nil {
		t.Fatalf("dotfiles must be embedded: %v", err)
	}
}
