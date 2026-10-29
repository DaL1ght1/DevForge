package com.example.devforge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class WorkspaceMigrationTest {

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

  @Test
  void existingUsersAndServicesMoveIntoPersonalWorkspaces() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    flyway(dataSource, "2").migrate();

    UUID alice = insertUser(jdbc, "alice");
    UUID bob = insertUser(jdbc, "bob");
    UUID version = insertTemplateVersion(jdbc);
    jdbc.update(
        "INSERT INTO app_service (name, template_version_id, owner_id) VALUES (?, ?, ?)",
        "billing",
        version,
        alice);
    jdbc.update(
        "INSERT INTO app_service (name, template_version_id, owner_id) VALUES (?, ?, NULL)",
        "orphan",
        version);

    flyway(dataSource, "3").migrate();

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM workspace WHERE type = 'PERSONAL'", Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                """
                SELECT m.role FROM workspace_member m JOIN workspace w ON w.id = m.workspace_id
                WHERE w.personal_owner_id = ? AND m.user_id = ?
                """,
                String.class,
                bob,
                bob))
        .isEqualTo("OWNER");
    assertThat(
            jdbc.queryForObject(
                """
                SELECT w.personal_owner_id FROM app_service s JOIN workspace w ON w.id = s.workspace_id
                WHERE s.name = 'billing'
                """,
                UUID.class))
        .isEqualTo(alice);
    assertThat(
            jdbc.queryForObject(
                """
                SELECT w.slug FROM app_service s JOIN workspace w ON w.id = s.workspace_id
                WHERE s.name = 'orphan'
                """,
                String.class))
        .isEqualTo("unassigned-services");

    // Names are now unique per workspace, not globally.
    jdbc.update(
        """
        INSERT INTO app_service (name, template_version_id, owner_id, workspace_id)
        SELECT 'billing', ?, ?, id FROM workspace WHERE personal_owner_id = ?
        """,
        version,
        bob,
        bob);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM app_service WHERE name = 'billing'", Integer.class))
        .isEqualTo(2);
  }

  private static Flyway flyway(DriverManagerDataSource dataSource, String target) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .target(target)
        .load();
  }

  private static UUID insertUser(JdbcTemplate jdbc, String username) {
    return jdbc.queryForObject(
        "INSERT INTO users (keycloak_id, username, email) VALUES (?, ?, ?) RETURNING id",
        UUID.class,
        UUID.randomUUID(),
        username,
        username + "@example.com");
  }

  private static UUID insertTemplateVersion(JdbcTemplate jdbc) {
    UUID template =
        jdbc.queryForObject(
            """
            INSERT INTO app_template (stable_key, name, language, framework, build_tool)
            VALUES ('spring', 'Spring', 'JAVA', 'SPRING_BOOT', 'MAVEN') RETURNING id
            """,
            UUID.class);
    return jdbc.queryForObject(
        """
        INSERT INTO template_versions
          (template_id, version, source_path, content_hash, manifest, artifact_reference)
        VALUES (?, '1.0.0', 'templates/spring', 'hash', 'variables: []', 'ref') RETURNING id
        """,
        UUID.class,
        template);
  }
}
