package com.example.devforge.dto;

import com.example.devforge.entity.WorkspaceRole;
import com.example.devforge.entity.WorkspaceType;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record WorkspaceResponse(
    UUID id, String name, String slug, WorkspaceType type, WorkspaceRole role, Instant createdAt)
    implements Serializable {}
