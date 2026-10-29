package com.example.devforge.dto;

import com.example.devforge.entity.GithubAccountType;
import com.example.devforge.entity.GithubInstallationStatus;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record GithubInstallationResponse(
    UUID id,
    long installationId,
    String accountLogin,
    GithubAccountType accountType,
    String repositorySelection,
    GithubInstallationStatus status,
    UUID workspaceId,
    Instant createdAt)
    implements Serializable {}
