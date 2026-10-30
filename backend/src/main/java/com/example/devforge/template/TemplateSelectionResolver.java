package com.example.devforge.template;

import com.example.devforge.exception.InvalidTemplateSelectionException;
import com.example.devforge.template.TemplateManifest.FeatureGroup;
import com.example.devforge.template.TemplateManifest.FeatureKind;
import com.example.devforge.template.TemplateManifest.FeatureOption;
import com.example.devforge.template.TemplateManifest.Variable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class TemplateSelectionResolver {

  private static final int MAX_VALUE_LENGTH = 200;

  public record ResolvedSelection(
      Map<String, String> variables, Map<String, List<String>> features) {}

  public ResolvedSelection resolve(
      TemplateManifest manifest,
      Map<String, String> variables,
      Map<String, List<String>> features) {
    return new ResolvedSelection(
        resolveVariables(manifest, variables == null ? Map.of() : variables),
        resolveFeatures(manifest, features == null ? Map.of() : features));
  }

  private static Map<String, String> resolveVariables(
      TemplateManifest manifest, Map<String, String> provided) {
    for (String name : provided.keySet()) {
      if (TemplateManifest.RESERVED_VARIABLES.contains(name)) {
        throw new InvalidTemplateSelectionException(name + " is set by DevForge");
      }
      if (manifest.variables().stream().noneMatch(v -> v.name().equals(name))) {
        throw new InvalidTemplateSelectionException("Unknown template variable " + name);
      }
    }
    Map<String, String> resolved = new LinkedHashMap<>();
    for (Variable variable : manifest.variables()) {
      String value = provided.get(variable.name());
      if (value == null || value.isBlank()) value = variable.defaultValue();
      if (value == null || value.isBlank()) {
        if (variable.required()) {
          throw new InvalidTemplateSelectionException(variable.name() + " is required");
        }
        continue;
      }
      value = value.trim();
      if (value.length() > MAX_VALUE_LENGTH
          || value.codePoints().anyMatch(Character::isISOControl)) {
        throw new InvalidTemplateSelectionException(variable.name() + " has an invalid value");
      }
      if (!variable.options().isEmpty() && !variable.options().contains(value)) {
        throw new InvalidTemplateSelectionException(
            variable.name() + " must be one of " + variable.options());
      }
      if (variable.pattern() != null && !Pattern.matches(variable.pattern(), value)) {
        throw new InvalidTemplateSelectionException(
            variable.name() + " does not match " + variable.pattern());
      }
      resolved.put(variable.name(), value);
    }
    return resolved;
  }

  private static Map<String, List<String>> resolveFeatures(
      TemplateManifest manifest, Map<String, List<String>> requested) {
    for (String group : requested.keySet()) {
      if (manifest.features().stream().noneMatch(g -> g.id().equals(group))) {
        throw new InvalidTemplateSelectionException("Unknown feature group " + group);
      }
    }
    Map<String, List<String>> resolved = new LinkedHashMap<>();
    List<FeatureOption> picked = new ArrayList<>();
    Set<String> selectedIds = new HashSet<>();
    for (FeatureGroup group : manifest.features()) {
      Set<String> chosen = new LinkedHashSet<>(requested.getOrDefault(group.id(), List.of()));
      for (String id : chosen) {
        if (group.options().stream().noneMatch(o -> o.id().equals(id))) {
          throw new InvalidTemplateSelectionException(
              "Unknown option " + id + " for " + group.label());
        }
      }
      if (group.kind() == FeatureKind.CHOICE && chosen.size() > 1) {
        throw new InvalidTemplateSelectionException(group.label() + " accepts a single option");
      }
      if (group.required() && chosen.isEmpty()) {
        throw new InvalidTemplateSelectionException(group.label() + " is required");
      }
      List<String> ordered = new ArrayList<>();
      for (FeatureOption option : group.options()) {
        if (chosen.contains(option.id())) {
          ordered.add(option.id());
          picked.add(option);
        }
      }
      if (!ordered.isEmpty()) {
        resolved.put(group.id(), List.copyOf(ordered));
        selectedIds.add(group.id());
        selectedIds.addAll(ordered);
      }
    }
    for (FeatureOption option : picked) {
      for (String required : option.requires()) {
        if (!selectedIds.contains(required)) {
          throw new InvalidTemplateSelectionException(
              option.label() + " requires " + labelOf(manifest, required));
        }
      }
      for (String conflict : option.conflicts()) {
        if (selectedIds.contains(conflict)) {
          throw new InvalidTemplateSelectionException(
              option.label() + " cannot be combined with " + labelOf(manifest, conflict));
        }
      }
    }
    return resolved;
  }

  private static String labelOf(TemplateManifest manifest, String id) {
    for (FeatureGroup group : manifest.features()) {
      if (group.id().equals(id)) return group.label();
      for (FeatureOption option : group.options()) {
        if (option.id().equals(id)) return option.label();
      }
    }
    return id;
  }
}
