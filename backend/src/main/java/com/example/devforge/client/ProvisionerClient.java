package com.example.devforge.client;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Kafka contract with the Go provisioner; must match {@code provisioner/internal/model/context.go}.
 * The GitHub destination fields are resolved by the backend from a verified installation, never
 * from client input, and no credential ever travels in these messages.
 */
@Component
public class ProvisionerClient {

  public static final int CONTRACT_VERSION = 4;

  public record ProvisionRequest(
      int contractVersion,
      UUID jobId,
      UUID serviceId,
      UUID applicationId,
      String serviceName,
      String templateName,
      String templateVersion,
      String artifactDigest,
      String packageName,
      String className,
      String description,
      Map<String, String> variables,
      Map<String, List<String>> features,
      List<ApplicationServiceSpec> services,
      long installationId,
      String ownerLogin,
      String ownerType,
      String visibility) {}

  public record RenderRequest(
      String serviceName,
      String templateName,
      String templateVersion,
      String artifactDigest,
      String packageName,
      String className,
      String description,
      Map<String, String> variables,
      Map<String, List<String>> features,
      List<ApplicationServiceSpec> services) {}

  public record ApplicationServiceSpec(
      UUID serviceId,
      String serviceName,
      String templateName,
      String templateVersion,
      String artifactDigest,
      String packageName,
      String className,
      String description,
      Map<String, String> variables,
      Map<String, List<String>> features,
      int port) {}

  public record RenderedFile(
      String path, String origin, int size, boolean binary, String content, boolean omitted) {}

  public record RenderResponse(List<RenderedFile> files, long totalBytes) {}

  public record ProvisionResponse(
      UUID jobId,
      UUID serviceId,
      UUID applicationId,
      String serviceName,
      String outputPath,
      String repositoryUrl,
      Long repositoryId,
      String status,
      String errorMessage) {}
}
