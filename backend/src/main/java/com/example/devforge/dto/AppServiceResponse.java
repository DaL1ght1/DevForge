package com.example.devforge.dto;

import com.example.devforge.entity.ServiceStatus;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record AppServiceResponse(
    UUID id,
    String name,
    String description,
    String repositoryUrl,
    String failureReason,
    ServiceStatus status,
    TemplateVersionResponse templateVersion,
    UserResponseDto owner,
    Instant createdAt,
    Instant updatedAt)
    implements Serializable {}
