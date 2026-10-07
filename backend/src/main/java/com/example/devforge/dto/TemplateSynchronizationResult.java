package com.example.devforge.dto;

import java.io.Serializable;
import lombok.Builder;

@Builder
public record TemplateSynchronizationResult(
    int scanned, int synchronizedTemplates, int unchanged, int failed) implements Serializable {}
