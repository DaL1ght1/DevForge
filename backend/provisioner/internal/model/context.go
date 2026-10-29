package model

const ContractVersion = 2
const (
	OwnerTypeUser         = "User"
	OwnerTypeOrganization = "Organization"
)

type ProvisionRequest struct {
	ContractVersion int    `json:"contractVersion"`
	JobID           string `json:"jobId"`
	ServiceID       string `json:"serviceId"`
	ServiceName     string `json:"serviceName"`
	TemplateName    string `json:"templateName"`
	TemplateVersion string `json:"templateVersion"`
	PackageName     string `json:"packageName"`
	ClassName       string `json:"className"`
	Description     string `json:"description"`
	DatabaseType    string `json:"databaseType"`
	InstallationID  int64  `json:"installationId"`
	OwnerLogin      string `json:"ownerLogin"`
	OwnerType       string `json:"ownerType"`
	Visibility      string `json:"visibility"`
}

type ProvisionResponse struct {
	JobID         string `json:"jobId,omitempty"`
	ServiceID     string `json:"serviceId"`
	ServiceName   string `json:"serviceName"`
	OutputPath    string `json:"outputPath,omitempty"`
	RepositoryURL string `json:"repositoryUrl,omitempty"`
	RepositoryID  int64  `json:"repositoryId,omitempty"`
	Status        string `json:"status"`
	ErrorMessage  string `json:"errorMessage,omitempty"`
}

type GenerationRequest struct {
	ServiceName     string `json:"serviceName"`
	TemplateName    string `json:"templateName"`
	TemplateVersion string `json:"templateVersion"`
	PackageName     string `json:"packageName"`
	ClassName       string `json:"className"`
	Description     string `json:"description"`
	DatabaseType    string `json:"databaseType"`
}
