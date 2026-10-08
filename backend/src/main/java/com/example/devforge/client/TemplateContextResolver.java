package com.example.devforge.client;

import com.example.devforge.client.model.TemplateContext;
import com.example.devforge.dto.AppServiceCreationDto;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class TemplateContextResolver {

  private static final String BASE_PACKAGE = "com.devforge";
  private static final Set<String> JAVA_KEYWORDS =
      Set.of(
          "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
          "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
          "finally", "float", "for", "goto", "if", "implements", "import", "instanceof",
          "int", "interface", "long", "native", "new", "package", "private", "protected",
          "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized",
          "this", "throw", "throws", "transient", "try", "void", "volatile", "while", "true",
          "false", "null", "_");

  public TemplateContext resolve(AppServiceCreationDto dto) {
    String serviceName = dto.name().trim().toLowerCase();
    String className = toPascalCase(serviceName);

    String sanitizedPkg = serviceName.replaceAll("[^a-zA-Z0-9]", "");
    if (sanitizedPkg.isBlank()) {
      sanitizedPkg = "service";
    }
    if (Character.isDigit(sanitizedPkg.charAt(0)) || JAVA_KEYWORDS.contains(sanitizedPkg)) {
      sanitizedPkg = "service" + sanitizedPkg;
    }
    String packageName = BASE_PACKAGE + "." + sanitizedPkg;
    String packagePath = packageName.replace('.', '/');

    return new TemplateContext(
        serviceName, className, packageName, packagePath, dto.description(), dto.databaseType());
  }

  private String toPascalCase(String input) {
    if (input == null || input.isBlank()) {
      return "Application";
    }
    String result = Arrays.stream(input.split("[-_\\s]+"))
        .filter(part -> !part.isBlank())
        .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1).toLowerCase())
        .collect(Collectors.joining());
    if (result.isBlank() || Character.isDigit(result.charAt(0)) || JAVA_KEYWORDS.contains(result.toLowerCase())) {
      return "App" + result;
    }
    return result;
  }
}
