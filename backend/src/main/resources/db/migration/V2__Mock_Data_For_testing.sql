INSERT INTO app_template (id,
                          name,
                          language,
                          framework,
                          build_tool,
                          database_type)
VALUES ('a0000000-0000-0000-0000-000000000001',
        'spring_Boot_Maven',
        'JAVA',
        'SPRING_BOOT',
        'MAVEN',
        'POSTGRESQL')
ON CONFLICT (name) DO NOTHING;

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