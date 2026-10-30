package com.example.devforge.template;

import com.example.devforge.entity.BuildTool;
import com.example.devforge.entity.TemplateFramework;
import com.example.devforge.entity.TemplateLanguage;
import com.example.devforge.exception.InvalidTemplateManifestException;
import com.example.devforge.template.TemplateManifest.Dependency;
import com.example.devforge.template.TemplateManifest.FeatureGroup;
import com.example.devforge.template.TemplateManifest.FeatureKind;
import com.example.devforge.template.TemplateManifest.FeatureOption;
import com.example.devforge.template.TemplateManifest.Variable;
import com.example.devforge.template.TemplateManifest.VariableType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

@Component
public class TemplateManifestParser {

  public static final Pattern FEATURE_ID = Pattern.compile("^[a-z0-9][a-z0-9-]{0,49}$");

  private static final Pattern VARIABLE_NAME = Pattern.compile("^[A-Z][A-Z0-9_]{0,49}$");
  private static final Set<String> DEPENDENCY_SCOPES =
      Set.of(
          "compile",
          "runtime",
          "provided",
          "test",
          "test-runtime",
          "annotation-processor",
          "development",
          "dev");

  public TemplateManifest parse(String yamlText) {
    Object loaded;
    try {
      loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(yamlText);
    } catch (YAMLException e) {
      throw new InvalidTemplateManifestException("not valid YAML");
    }
    if (!(loaded instanceof Map<?, ?> raw)) {
      throw new InvalidTemplateManifestException("manifest must be a map");
    }
    int schemaVersion = schemaVersion(raw.get("schemaVersion"));
    List<FeatureGroup> features = features(raw.get("features"));
    if (schemaVersion < 2 && !features.isEmpty()) {
      throw new InvalidTemplateManifestException("features require schemaVersion 2");
    }
    return new TemplateManifest(
        schemaVersion,
        required(raw, "name"),
        required(raw, "version"),
        enumValue(TemplateLanguage.class, required(raw, "language")),
        enumValue(TemplateFramework.class, required(raw, "framework")),
        enumValue(BuildTool.class, required(raw, "buildTool", "build_tool")),
        optional(raw, "description"),
        variables(raw.get("variables")),
        features);
  }

  private static int schemaVersion(Object value) {
    if (value == null) return 1;
    if (value instanceof Integer i && (i == 1 || i == 2)) return i;
    throw new InvalidTemplateManifestException("schemaVersion must be 1 or 2");
  }

  private static List<Variable> variables(Object value) {
    List<Variable> variables = new ArrayList<>();
    Set<String> names = new HashSet<>();
    for (Map<?, ?> raw : maps(value, "variables")) {
      String name = required(raw, "name");
      if (!VARIABLE_NAME.matcher(name).matches()) {
        throw new InvalidTemplateManifestException("invalid variable name " + name);
      }
      if (!names.add(name)) {
        throw new InvalidTemplateManifestException("duplicate variable " + name);
      }
      if (TemplateManifest.RESERVED_VARIABLES.contains(name)) continue;

      List<String> options = strings(raw.get("options"), "options of " + name);
      String typeText = optional(raw, "type");
      VariableType type =
          typeText != null
              ? enumValue(VariableType.class, typeText)
              : options.isEmpty() ? VariableType.STRING : VariableType.ENUM;
      if (type == VariableType.BOOLEAN) options = List.of("true", "false");
      if (type == VariableType.ENUM && options.isEmpty()) {
        throw new InvalidTemplateManifestException("enum variable " + name + " needs options");
      }
      String defaultValue = scalar(raw.get("default"));
      if (defaultValue != null && !options.isEmpty() && !options.contains(defaultValue)) {
        throw new InvalidTemplateManifestException("default of " + name + " is not an option");
      }
      String pattern = optional(raw, "pattern");
      if (pattern != null) {
        try {
          Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
          throw new InvalidTemplateManifestException("invalid pattern for " + name);
        }
      }
      variables.add(
          new Variable(
              name,
              optional(raw, "label") != null ? optional(raw, "label") : name,
              type,
              Boolean.TRUE.equals(raw.get("required")),
              defaultValue,
              List.copyOf(options),
              pattern));
    }
    return List.copyOf(variables);
  }

  private static List<FeatureGroup> features(Object value) {
    List<FeatureGroup> groups = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    List<FeatureOption> allOptions = new ArrayList<>();
    for (Map<?, ?> raw : maps(value, "features")) {
      String id = featureId(required(raw, "id"), ids);
      String kindText = optional(raw, "kind");
      FeatureKind kind =
          kindText == null ? FeatureKind.MULTI : enumValue(FeatureKind.class, kindText);
      List<FeatureOption> options = new ArrayList<>();
      for (Map<?, ?> option : maps(raw.get("options"), "options of " + id)) {
        String optionId = featureId(required(option, "id"), ids);
        options.add(
            new FeatureOption(
                optionId,
                optional(option, "label") != null ? optional(option, "label") : optionId,
                optional(option, "description"),
                strings(option.get("requires"), "requires of " + optionId),
                strings(option.get("conflicts"), "conflicts of " + optionId),
                dependencies(option.get("dependencies"), optionId)));
      }
      if (options.isEmpty()) {
        throw new InvalidTemplateManifestException("feature group " + id + " has no options");
      }
      allOptions.addAll(options);
      groups.add(
          new FeatureGroup(
              id,
              optional(raw, "label") != null ? optional(raw, "label") : id,
              kind,
              Boolean.TRUE.equals(raw.get("required")),
              List.copyOf(options)));
    }
    for (FeatureOption option : allOptions) {
      for (String ref : concat(option.requires(), option.conflicts())) {
        if (!ids.contains(ref) || ref.equals(option.id())) {
          throw new InvalidTemplateManifestException(
              option.id() + " references unknown feature " + ref);
        }
      }
    }
    return List.copyOf(groups);
  }

  private static List<Dependency> dependencies(Object value, String optionId) {
    List<Dependency> deps = new ArrayList<>();
    for (Map<?, ?> raw : maps(value, "dependencies of " + optionId)) {
      String scope = optional(raw, "scope");
      if (scope != null && !DEPENDENCY_SCOPES.contains(scope)) {
        throw new InvalidTemplateManifestException(
            "unsupported dependency scope " + scope + " in " + optionId);
      }
      deps.add(
          new Dependency(
              optional(raw, "group"),
              required(raw, "artifact"),
              scalar(raw.get("version")),
              scope));
    }
    return List.copyOf(deps);
  }

  private static String featureId(String id, Set<String> seen) {
    if (!FEATURE_ID.matcher(id).matches()) {
      throw new InvalidTemplateManifestException("invalid feature id " + id);
    }
    if (!seen.add(id)) {
      throw new InvalidTemplateManifestException("duplicate feature id " + id);
    }
    return id;
  }

  private static List<String> concat(List<String> a, List<String> b) {
    List<String> all = new ArrayList<>(a);
    all.addAll(b);
    return all;
  }

  private static List<Map<?, ?>> maps(Object value, String field) {
    if (value == null) return List.of();
    if (!(value instanceof List<?> list)) {
      throw new InvalidTemplateManifestException(field + " must be a list");
    }
    List<Map<?, ?>> maps = new ArrayList<>();
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> map)) {
        throw new InvalidTemplateManifestException(field + " entries must be maps");
      }
      maps.add(map);
    }
    return maps;
  }

  private static List<String> strings(Object value, String field) {
    if (value == null) return List.of();
    if (!(value instanceof List<?> list)) {
      throw new InvalidTemplateManifestException(field + " must be a list");
    }
    List<String> out = new ArrayList<>();
    for (Object item : list) {
      String s = scalar(item);
      if (s == null || s.isBlank()) {
        throw new InvalidTemplateManifestException(field + " contains an empty value");
      }
      out.add(s);
    }
    return List.copyOf(out);
  }

  private static String scalar(Object value) {
    if (value == null) return null;
    if (value instanceof String || value instanceof Number || value instanceof Boolean) {
      return value.toString().trim();
    }
    throw new InvalidTemplateManifestException("expected a scalar value, got " + value);
  }

  private static String required(Map<?, ?> map, String... keys) {
    String value = optional(map, keys);
    if (value == null) throw new InvalidTemplateManifestException("missing " + keys[0]);
    return value;
  }

  private static String optional(Map<?, ?> map, String... keys) {
    for (String key : keys) {
      String value = scalar(map.get(key));
      if (value != null && !value.isBlank()) return value;
    }
    return null;
  }

  private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
    try {
      return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    } catch (IllegalArgumentException e) {
      throw new InvalidTemplateManifestException("invalid " + type.getSimpleName() + " " + value);
    }
  }
}
