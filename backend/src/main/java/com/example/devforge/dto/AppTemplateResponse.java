package com.example.devforge.dto;

import com.example.devforge.entity.BuildTool;
import com.example.devforge.entity.TemplateFramework;
import com.example.devforge.entity.TemplateLanguage;
import java.time.Instant;
import java.util.UUID;

public record AppTemplateResponse(
    UUID id,
    String name,
    TemplateLanguage language,
    TemplateFramework framework,
    BuildTool buildTool,
    String databaseType,
    Instant createdAt) {}
