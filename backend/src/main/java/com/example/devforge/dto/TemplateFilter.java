package com.example.devforge.dto;

import com.example.devforge.entity.BuildTool;
import com.example.devforge.entity.TemplateFramework;


public record TemplateFilter(
        TemplateFramework framework,
        BuildTool buildTool
) {
}