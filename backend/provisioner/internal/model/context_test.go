package model

import (
	"encoding/json"
	"testing"
)

const backendRequest = `{
  "contractVersion": 2,
  "jobId": "11111111-1111-1111-1111-111111111111",
  "serviceId": "22222222-2222-2222-2222-222222222222",
  "serviceName": "billing",
  "templateName": "spring",
  "templateVersion": "1.0.0",
  "packageName": "com.acme.billing",
  "className": "Billing",
  "description": "d",
  "databaseType": "POSTGRESQL",
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
		ServiceName:     "billing",
		TemplateName:    "spring",
		TemplateVersion: "1.0.0",
		PackageName:     "com.acme.billing",
		ClassName:       "Billing",
		Description:     "d",
		DatabaseType:    "POSTGRESQL",
		InstallationID:  123456789012,
		OwnerLogin:      "acme",
		OwnerType:       OwnerTypeOrganization,
		Visibility:      "private",
	}
	if req != want {
		t.Fatalf("decoded %+v, want %+v", req, want)
	}
}

func TestProvisionResponseUsesBackendFieldNames(t *testing.T) {
	out, err := json.Marshal(ProvisionResponse{JobID: "j", ServiceID: "s", RepositoryID: 9, Status: "COMPLETED"})
	if err != nil {
		t.Fatal(err)
	}
	var fields map[string]any
	_ = json.Unmarshal(out, &fields)
	for _, key := range []string{"jobId", "serviceId", "repositoryId", "status"} {
		if _, ok := fields[key]; !ok {
			t.Fatalf("response JSON %s lacks %q", out, key)
		}
	}
}
