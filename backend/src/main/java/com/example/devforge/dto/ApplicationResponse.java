package com.example.devforge.dto;

import com.example.devforge.entity.ServiceStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ApplicationResponse(
    UUID id,
    String name,
    String description,
    ServiceStatus status,
    String repositoryUrl,
    String repositoryOwner,
    String failureReason,
    WorkspaceSummaryResponse workspace,
    Instant createdAt,
    Instant updatedAt,
    List<ServiceSummary> services) {

  public record ServiceSummary(
      UUID id,
      String name,
      String path,
      Integer port,
      ServiceStatus status,
      String templateName,
      String templateVersion,
      String repositoryUrl) {}
}
