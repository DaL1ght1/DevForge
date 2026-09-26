package com.example.devforge.dto;

import com.example.devforge.entity.ServiceStatus;
import com.example.devforge.entity.TemplateVersion;

import java.time.Instant;
import java.util.UUID;

public record AppServiceResponse(
        UUID id,
        String name,
        String description,
        String repositoryUrl,
        ServiceStatus status,
        TemplateVersion templateVersion,
        UserResponseDto owner,
        Instant createdAt,
        Instant updatedAt
) {
}