package model

const ContractVersion = 4
const MinContractVersion = 2

const (
	OwnerTypeUser         = "User"
	OwnerTypeOrganization = "Organization"
)

type ProvisionRequest struct {
	ContractVersion int                  `json:"contractVersion"`
	JobID           string               `json:"jobId"`
	ServiceID       string               `json:"serviceId"`
	ApplicationID   string               `json:"applicationId,omitempty"`
	ServiceName     string               `json:"serviceName"`
	TemplateName    string               `json:"templateName"`
	TemplateVersion string               `json:"templateVersion"`
	ArtifactDigest  string               `json:"artifactDigest,omitempty"`
	PackageName     string               `json:"packageName"`
	ClassName       string               `json:"className"`
	Description     string               `json:"description"`
	Variables       map[string]string    `json:"variables,omitempty"`
	Features        map[string][]string  `json:"features,omitempty"`
	Services        []ApplicationService `json:"services,omitempty"`
	InstallationID  int64                `json:"installationId"`
	OwnerLogin      string               `json:"ownerLogin"`
	OwnerType       string               `json:"ownerType"`
	Visibility      string               `json:"visibility"`
}

func (r ProvisionRequest) IsApplication() bool {
	return len(r.Services) > 0
}

type ProvisionResponse struct {
	JobID         string `json:"jobId,omitempty"`
	ServiceID     string `json:"serviceId"`
	ApplicationID string `json:"applicationId,omitempty"`
	ServiceName   string `json:"serviceName"`
	OutputPath    string `json:"outputPath,omitempty"`
	RepositoryURL string `json:"repositoryUrl,omitempty"`
	RepositoryID  int64  `json:"repositoryId,omitempty"`
	Status        string `json:"status"`
	ErrorMessage  string `json:"errorMessage,omitempty"`
}

type GenerationRequest struct {
	ServiceName     string               `json:"serviceName"`
	TemplateName    string               `json:"templateName"`
	TemplateVersion string               `json:"templateVersion"`
	ArtifactDigest  string               `json:"artifactDigest,omitempty"`
	PackageName     string               `json:"packageName"`
	ClassName       string               `json:"className"`
	Description     string               `json:"description"`
	Variables       map[string]string    `json:"variables,omitempty"`
	Features        map[string][]string  `json:"features,omitempty"`
	Services        []ApplicationService `json:"services,omitempty"`
}

type ApplicationService struct {
	ServiceID       string              `json:"serviceId,omitempty"`
	ServiceName     string              `json:"serviceName"`
	TemplateName    string              `json:"templateName"`
	TemplateVersion string              `json:"templateVersion"`
	ArtifactDigest  string              `json:"artifactDigest,omitempty"`
	PackageName     string              `json:"packageName"`
	ClassName       string              `json:"className"`
	Description     string              `json:"description"`
	Variables       map[string]string   `json:"variables,omitempty"`
	Features        map[string][]string `json:"features,omitempty"`
	Port            int                 `json:"port"`
}

func (s ApplicationService) GenerationRequest() GenerationRequest {
	return GenerationRequest{
		ServiceName:     s.ServiceName,
		TemplateName:    s.TemplateName,
		TemplateVersion: s.TemplateVersion,
		ArtifactDigest:  s.ArtifactDigest,
		PackageName:     s.PackageName,
		ClassName:       s.ClassName,
		Description:     s.Description,
		Variables:       s.Variables,
		Features:        s.Features,
	}
}
