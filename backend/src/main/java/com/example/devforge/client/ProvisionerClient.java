package com.example.devforge.client;

import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Kafka contract with the Go provisioner; must match {@code provisioner/internal/model/context.go}.
 * The GitHub destination fields are resolved by the backend from a verified installation, never
 * from client input, and no credential ever travels in these messages.
 */
@Component
public class ProvisionerClient {

  public static final int CONTRACT_VERSION = 2;

  public record ProvisionRequest(
      int contractVersion,
      UUID jobId,
      UUID serviceId,
      String serviceName,
      String templateName,
      String templateVersion,
      String packageName,
      String className,
      String description,
      String databaseType,
      long installationId,
      String ownerLogin,
      String ownerType,
      String visibility) {}

  public record ProvisionResponse(
      UUID jobId,
      UUID serviceId,
      String serviceName,
      String outputPath,
      String repositoryUrl,
      Long repositoryId,
      String status,
      String errorMessage) {}
}
