package com.example.devforge.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.io.Serializable;
import java.util.UUID;

public record GithubInstallationLinkDto(
    @NotNull UUID workspaceId, @NotNull @Positive Long installationId) implements Serializable {}
