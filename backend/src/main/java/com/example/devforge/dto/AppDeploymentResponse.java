package com.example.devforge.dto;

import java.time.Instant;
import java.util.UUID;

public record AppDeploymentResponse(
    UUID id,
    UUID serviceId,
    String environment,
    String version,
    String status,
    Instant deployedAt) {}
