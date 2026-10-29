package com.example.devforge.dto;

import com.example.devforge.entity.WorkspaceRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;

public record WorkspaceMemberAddDto(
    @NotBlank @Size(max = 100) String usernameOrEmail, @NotNull WorkspaceRole role)
    implements Serializable {}
