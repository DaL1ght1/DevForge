package model

type GenerationRequest struct {
	ServiceName  string `json:"serviceName"`
	TemplateName string `json:"templateName"`
	PackageName  string `json:"packageName"`
	ClassName    string `json:"className"`
	Description  string `json:"description"`
	DatabaseType string `json:"databaseType"`
}

type ProvisionResponse struct {
	ServiceName   string `json:"serviceName"`
	OutputPath    string `json:"outputPath"`
	RepositoryURL string `json:"repositoryUrl"`
	Status        string `json:"status"`
}
