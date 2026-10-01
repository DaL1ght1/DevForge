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

    public record ProvisionRequest(
            String serviceName,
            String templateName,
            String packageName,
            String className,
            String description,
            String databaseType
    ) {}

    public record ProvisionResponse(
            String serviceName,
            String outputPath,
            String repositoryUrl,
            String status
    ) {}

    public ProvisionResponse provision(ProvisionRequest request) {
        return restClient.post()
                .uri("/api/v1/provision")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ProvisionResponse.class);
    }
}