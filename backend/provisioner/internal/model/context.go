package model

type ProvisionRequest struct {
	ServiceID       string `json:"serviceId"`
	ServiceName     string `json:"serviceName"`
	TemplateName    string `json:"templateName"`
	TemplateVersion string `json:"templateVersion"`
	PackageName     string `json:"packageName"`
	ClassName       string `json:"className"`
	Description     string `json:"description"`
	DatabaseType    string `json:"databaseType"`
}

type ProvisionResponse struct {
	ServiceID     string `json:"serviceId"`
	ServiceName   string `json:"serviceName"`
	OutputPath    string `json:"outputPath,omitempty"`
	RepositoryURL string `json:"repositoryUrl,omitempty"`
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
