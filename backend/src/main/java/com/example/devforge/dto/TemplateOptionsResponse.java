package com.example.devforge.dto;

import com.example.devforge.template.TemplateManifest;
import java.io.Serializable;
import java.util.List;
import java.util.UUID;

public record TemplateOptionsResponse(
    UUID templateId,
    UUID templateVersionId,
    String version,
    String contentHash,
    int schemaVersion,
    List<TemplateManifest.Variable> variables,
    List<TemplateManifest.FeatureGroup> features)
    implements Serializable {}
