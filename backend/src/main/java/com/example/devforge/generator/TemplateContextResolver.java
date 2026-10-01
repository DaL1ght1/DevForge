package com.example.devforge.generator;

import com.example.devforge.dto.AppServiceCreationDto;
import com.example.devforge.generator.model.TemplateContext;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

@Component
public class TemplateContextResolver {

    private static final String BASE_PACKAGE = "com.devforge";

    public TemplateContext resolve(AppServiceCreationDto dto) {
        String serviceName = dto.name().trim().toLowerCase();
        String className = toPascalCase(serviceName);

        String sanitizedPkg = serviceName.replaceAll("[^a-zA-Z0-9]", "");
        String packageName = BASE_PACKAGE + "." + sanitizedPkg;
        String packagePath = packageName.replace('.', '/');

        return new TemplateContext(
                serviceName,
                className,
                packageName,
                packagePath,
                dto.description(),
                dto.databaseType()
        );
    }

    private String toPascalCase(String input) {
        if (input == null || input.isBlank()) {
            return "Application";
        }
        return Arrays.stream(input.split("[-_\\s]+"))
                .filter(part -> !part.isBlank())
                .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1).toLowerCase())
                .collect(Collectors.joining());
    }
}