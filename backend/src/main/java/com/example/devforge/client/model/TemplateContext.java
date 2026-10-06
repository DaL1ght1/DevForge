package com.example.devforge.client.model;

import java.util.Map;

public record TemplateContext(
        String serviceName,
        String className,
        String packageName,
        String packagePath,
        String description,
        String databaseType
) {
    public Map<String, String> asMap() {
        return Map.of(
                "SERVICE_NAME", serviceName,
                "CLASS_NAME", className,
                "PACKAGE_NAME", packageName,
                "PACKAGE_PATH", packagePath,
                "DESCRIPTION", description != null ? description : "",
                "DATABASE_TYPE", databaseType != null ? databaseType : "NONE"
        );
    }
}