package com.example.devforge.dto;

import com.example.devforge.entity.WorkspaceType;
import java.io.Serializable;
import java.util.UUID;

public record WorkspaceSummaryResponse(UUID id, String name, String slug, WorkspaceType type)
    implements Serializable {}
