package com.example.devforge.dto;

import com.example.devforge.entity.BuildTool;
import com.example.devforge.entity.TemplateFramework;
import com.example.devforge.entity.TemplateLanguage;
import java.io.Serializable;
import lombok.Builder;

@Builder
public record TemplateData(
    String name,
    String stableKey,
    String version,
    TemplateLanguage language,
    TemplateFramework framework,
    BuildTool buildTool,
    String databaseType,
    String manifest,
    String hash,
    String relativePath)
    implements Serializable {}
