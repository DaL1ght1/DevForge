package com.example.devforge.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.devforge.entity.BuildTool;
import com.example.devforge.exception.InvalidTemplateManifestException;
import com.example.devforge.template.TemplateManifest.FeatureKind;
import com.example.devforge.template.TemplateManifest.VariableType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class TemplateManifestParserTest {

  private static final String HEADER =
      """
      name: spring
      version: 1.0.0
      language: JAVA
      framework: SPRING_BOOT
      buildTool: MAVEN
      """;

  private final TemplateManifestParser parser = new TemplateManifestParser();

  @Test
  void parsesSchemaV2Features() {
    TemplateManifest manifest =
        parser.parse(
            "schemaVersion: 2\n"
                + HEADER
                + """
                variables:
                  - name: SERVICE_NAME
                    required: true
                  - name: JAVA_VERSION
                    options: [21, 25]
                    default: 21
                features:
                  - id: database
                    label: Database
                    kind: choice
                    options:
                      - id: postgresql
                        label: PostgreSQL
                        dependencies:
                          - { group: org.postgresql, artifact: postgresql, scope: runtime }
                  - id: extras
                    options:
                      - id: testcontainers
                        requires: [database]
                """);

    assertThat(manifest.schemaVersion()).isEqualTo(2);
    assertThat(manifest.buildTool()).isEqualTo(BuildTool.MAVEN);
    // SERVICE_NAME is reserved and never offered to users.
    assertThat(manifest.variables())
        .singleElement()
        .satisfies(
            v -> {
              assertThat(v.name()).isEqualTo("JAVA_VERSION");
              assertThat(v.type()).isEqualTo(VariableType.ENUM);
              assertThat(v.options()).containsExactly("21", "25");
              assertThat(v.defaultValue()).isEqualTo("21");
            });
    assertThat(manifest.features()).hasSize(2);
    assertThat(manifest.features().getFirst().kind()).isEqualTo(FeatureKind.CHOICE);
    assertThat(manifest.features().get(1).kind()).isEqualTo(FeatureKind.MULTI);
    assertThat(manifest.features().getFirst().options().getFirst().dependencies())
        .singleElement()
        .satisfies(d -> assertThat(d.scope()).isEqualTo("runtime"));
  }

  @Test
  void legacyManifestDefaultsToSchemaV1WithoutFeatures() {
    TemplateManifest manifest = parser.parse(HEADER + "variables:\n  - name: SERVICE_NAME\n");

    assertThat(manifest.schemaVersion()).isEqualTo(1);
    assertThat(manifest.variables()).isEmpty();
    assertThat(manifest.features()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "features:\n  - id: db\n    options:\n      - id: pg\n",
        "schemaVersion: 2\nfeatures:\n  - id: db\n    options:\n      - id: db\n",
        "schemaVersion: 2\nfeatures:\n  - id: db\n    options:\n      - id: pg\n        requires: [redis]\n",
        "schemaVersion: 2\nfeatures:\n  - id: db\n    options: []\n",
        "schemaVersion: 2\nfeatures:\n  - id: Bad_Id\n    options:\n      - id: pg\n",
        "schemaVersion: 2\nfeatures:\n  - id: db\n    options:\n      - id: pg\n        dependencies:\n          - { artifact: x, scope: weird }\n",
        "variables:\n  - name: MODE\n    options: [a, b]\n    default: c\n",
        "variables:\n  - name: MODE\n    type: enum\n",
        "schemaVersion: 3\n",
      })
  void rejectsInvalidManifests(String body) {
    assertThatThrownBy(() -> parser.parse(HEADER + body))
        .isInstanceOf(InvalidTemplateManifestException.class);
  }

  static Stream<Path> repositoryTemplates() throws IOException {
    try (Stream<Path> dirs = Files.list(Path.of("templates"))) {
      List<Path> list = dirs.filter(Files::isDirectory).sorted().toList();
      return list.stream();
    }
  }

  /** Guards template authors: every template in the repository must pass sync validation. */
  @ParameterizedTest
  @MethodSource("repositoryTemplates")
  void everyRepositoryTemplateIsValid(Path dir) throws IOException {
    TemplateManifest manifest = parser.parse(Files.readString(dir.resolve("template.yaml")));

    if (manifest.schemaVersion() >= 2) {
      assertThat(dir.resolve("base")).isDirectory();
      Path features = dir.resolve("features");
      if (Files.isDirectory(features)) {
        List<String> optionIds =
            manifest.features().stream()
                .flatMap(g -> g.options().stream())
                .map(TemplateManifest.FeatureOption::id)
                .toList();
        try (Stream<Path> overlays = Files.list(features)) {
          assertThat(overlays.map(p -> p.getFileName().toString())).isSubsetOf(optionIds);
        }
      }
    }
  }
}
