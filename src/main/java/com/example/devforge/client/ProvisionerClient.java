package com.example.devforge.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ProvisionerClient {

    private final RestClient restClient;

    public ProvisionerClient(@Value("${provisioner.url}") String provisionerUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(provisionerUrl)
                .build();
    }

    public record GenerateProjectRequest(
            String serviceName,
            String templateName,
            String packageName,
            String className,
            String description,
            String databaseType
    ) {}

    public record GenerateProjectResponse(
            String serviceName,
            String outputPath,
            String status
    ) {}

    public GenerateProjectResponse generate(GenerateProjectRequest request) {
        return restClient.post()
                .uri("/api/v1/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(GenerateProjectResponse.class);
    }
}