package com.example.devforge.client;


import org.springframework.stereotype.Component;


import java.util.UUID;

@Component
public class ProvisionerClient {

    public record ProvisionRequest(
            UUID serviceId,
            String serviceName,
            String templateName,
            String packageName,
            String className,
            String description,
            String databaseType
    ) {}

    public record ProvisionResponse(
            UUID serviceId,
            String serviceName,
            String outputPath,
            String repositoryUrl,
            String status
    ) {}


}