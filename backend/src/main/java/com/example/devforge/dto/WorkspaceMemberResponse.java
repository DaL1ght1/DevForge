package com.example.devforge.dto;

import com.example.devforge.entity.WorkspaceRole;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record WorkspaceMemberResponse(
    UUID userId, String username, String email, WorkspaceRole role, Instant joinedAt)
    implements Serializable {}
