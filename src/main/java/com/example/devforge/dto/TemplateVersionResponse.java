package com.example.devforge.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record TemplateVersionResponse(
        UUID id,
        String version,
        String sourcePath,
        boolean active,
        Instant createdAt
) implements Serializable {
}