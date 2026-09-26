-- 1. Insert a default Developer User
-- Required because AppService has an owner_id foreign key that cannot be null.
INSERT INTO users (id, keycloak_id, username, email, first_name, last_name, role)
VALUES ('11111111-1111-1111-1111-111111111111',
        '5edf9415-1b39-4a0d-a051-aa84ee2cb739',
        'devforge_admin',
        'admin@devforge.local',
        'DevForge',
        'Admin',
        'ADMIN')
ON CONFLICT (username) DO NOTHING;

-- 2. Seed Default Spring Boot Template
INSERT INTO app_template (id,
                          name,
                          language,
                          framework,
                          build_tool,
                          database_type)
VALUES ('a0000000-0000-0000-0000-000000000001',
        'spring-boot-maven',
        'JAVA',
        'SPRING_BOOT',
        'MAVEN',
        'POSTGRESQL')
ON CONFLICT (name) DO NOTHING;

-- 3. Seed Version 1.0.0 for Spring Boot Template
INSERT INTO template_versions (id,
                               template_id,
                               version,
                               source_path,
                               active)
VALUES ('b0000000-0000-0000-0000-000000000001',
        'a0000000-0000-0000-0000-000000000001',
        '1.0.0',
        'templates/spring_Boot_Maven',
        true)
ON CONFLICT (template_id, version) DO NOTHING;