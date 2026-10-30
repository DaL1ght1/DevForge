package com.example.devforge.template;

import com.example.devforge.entity.BuildTool;
import com.example.devforge.entity.TemplateFramework;
import com.example.devforge.entity.TemplateLanguage;
import java.util.List;
import java.util.Set;

public record TemplateManifest(
    int schemaVersion,
    String name,
    String version,
    TemplateLanguage language,
    TemplateFramework framework,
    BuildTool buildTool,
    String description,
    List<Variable> variables,
    List<FeatureGroup> features) {

  public static final Set<String> RESERVED_VARIABLES =
      Set.of("SERVICE_NAME", "CLASS_NAME", "PACKAGE_NAME", "PACKAGE_PATH", "DESCRIPTION");

  public record Variable(
      String name,
      String label,
      VariableType type,
      boolean required,
      String defaultValue,
      List<String> options,
      String pattern) {}

  public enum VariableType {
    STRING,
    ENUM,
    BOOLEAN
  }

  public record FeatureGroup(
      String id, String label, FeatureKind kind, boolean required, List<FeatureOption> options) {}

  public enum FeatureKind {
    CHOICE,
    MULTI
  }

  public record FeatureOption(
      String id,
      String label,
      String description,
      List<String> requires,
      List<String> conflicts,
      List<Dependency> dependencies) {}

  public record Dependency(String group, String artifact, String version, String scope) {}
}
