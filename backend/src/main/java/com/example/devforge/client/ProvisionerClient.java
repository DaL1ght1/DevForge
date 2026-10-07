package com.example.devforge.client;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ProvisionerClient {

  public record ProvisionRequest(
      UUID serviceId,
      String serviceName,
      String templateName,
      String packageName,
      String className,
      String description,
      String databaseType) {}

  public record ProvisionResponse(
      UUID serviceId, String serviceName, String outputPath, String repositoryUrl, String status) {}
}
