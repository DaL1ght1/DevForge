package model

import (
	"encoding/json"
	"reflect"
	"testing"
)

const backendRequest = `{
  "contractVersion": 4,
  "jobId": "11111111-1111-1111-1111-111111111111",
  "serviceId": "22222222-2222-2222-2222-222222222222",
  "applicationId": "33333333-3333-3333-3333-333333333333",
  "serviceName": "shop",
  "templateName": null,
  "templateVersion": null,
  "artifactDigest": null,
  "packageName": null,
  "className": null,
  "description": "d",
  "variables": null,
  "features": null,
  "services": [{
    "serviceId": "22222222-2222-2222-2222-222222222222",
    "serviceName": "billing",
    "templateName": "spring",
    "templateVersion": "1.0.0",
    "artifactDigest": "abc",
    "packageName": "com.devforge.billing",
    "className": "Billing",
    "description": null,
    "variables": {"JAVA_VERSION": "21"},
    "features": {"database": ["postgresql"]},
    "port": 8080
  }],
  "installationId": 123456789012,
  "ownerLogin": "acme",
  "ownerType": "Organization",
  "visibility": "private"
}`

func TestProvisionRequestMatchesBackendContract(t *testing.T) {
	var req ProvisionRequest
	if err := json.Unmarshal([]byte(backendRequest), &req); err != nil {
		t.Fatal(err)
	}
	want := ProvisionRequest{
		ContractVersion: ContractVersion,
		JobID:           "11111111-1111-1111-1111-111111111111",
		ServiceID:       "22222222-2222-2222-2222-222222222222",
		ApplicationID:   "33333333-3333-3333-3333-333333333333",
		ServiceName:     "shop",
		Description:     "d",
		Services: []ApplicationService{{
			ServiceID:       "22222222-2222-2222-2222-222222222222",
			ServiceName:     "billing",
			TemplateName:    "spring",
			TemplateVersion: "1.0.0",
			ArtifactDigest:  "abc",
			PackageName:     "com.devforge.billing",
			ClassName:       "Billing",
			Variables:       map[string]string{"JAVA_VERSION": "21"},
			Features:        map[string][]string{"database": {"postgresql"}},
			Port:            8080,
		}},
		InstallationID: 123456789012,
		OwnerLogin:     "acme",
		OwnerType:      OwnerTypeOrganization,
		Visibility:     "private",
	}
	if !reflect.DeepEqual(req, want) {
		t.Fatalf("decoded %+v, want %+v", req, want)
	}
	if !req.IsApplication() {
		t.Fatal("request with services must be an application request")
	}
}

func TestProvisionResponseUsesBackendFieldNames(t *testing.T) {
	out, err := json.Marshal(ProvisionResponse{JobID: "j", ServiceID: "s", ApplicationID: "a", RepositoryID: 9, Status: "COMPLETED"})
	if err != nil {
		t.Fatal(err)
	}
	var fields map[string]any
	_ = json.Unmarshal(out, &fields)
	for _, key := range []string{"jobId", "serviceId", "applicationId", "repositoryId", "status"} {
		if _, ok := fields[key]; !ok {
			t.Fatalf("response JSON %s lacks %q", out, key)
		}
	}
}
