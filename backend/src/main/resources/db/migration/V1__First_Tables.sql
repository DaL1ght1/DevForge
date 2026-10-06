CREATE TABLE IF NOT EXISTS users
(
    id          UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    keycloak_id UUID         NOT NULL UNIQUE,
    username    VARCHAR(50)  NOT NULL UNIQUE,
    email       VARCHAR(100) NOT NULL UNIQUE,
    first_name  VARCHAR(50),
    last_name   VARCHAR(50),
    role        VARCHAR(30)  NOT NULL DEFAULT 'DEVELOPER',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS app_template
(
    id            UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    stable_key    VARCHAR(100) NOT NULL,
    name          VARCHAR(20)  NOT NULL UNIQUE,
    language      VARCHAR(30)  NOT NULL,
    framework     VARCHAR(30)  NOT NULL,
    build_tool    VARCHAR(30)  NOT NULL,
    database_type VARCHAR(20),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_app_template_stable_key UNIQUE (stable_key)
);

CREATE TABLE IF NOT EXISTS template_versions
(
    id                 UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    template_id        UUID         NOT NULL REFERENCES app_template (id) ON DELETE CASCADE,
    version            VARCHAR(30)  NOT NULL,
    source_path        VARCHAR(500) NOT NULL,
    content_hash       VARCHAR(64)  NOT NULL,
    manifest           TEXT         NOT NULL,
    artifact_reference VARCHAR(500) NOT NULL,
    active             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_template_version UNIQUE (template_id, version),
    CONSTRAINT uk_template_version_hash UNIQUE (template_id, content_hash)
);

CREATE TABLE IF NOT EXISTS app_service
(
    id                  UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    name                VARCHAR(20) NOT NULL UNIQUE,
    description         TEXT,
    repository_url      VARCHAR(255) UNIQUE,
    status              VARCHAR(30) NOT NULL DEFAULT 'CREATING',
    template_version_id UUID        REFERENCES template_versions (id) ON DELETE SET NULL,
    owner_id            UUID        REFERENCES users (id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS app_deployment
(
    id          UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    service_id  UUID        NOT NULL REFERENCES app_service (id) ON DELETE CASCADE,
    environment VARCHAR(20) NOT NULL,
    version     VARCHAR(20) NOT NULL,
    status      VARCHAR(30) NOT NULL DEFAULT 'DEPLOYING',
    deployed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS provisioning_job
(
    id                  UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    service_id          UUID        NOT NULL REFERENCES app_service (id) ON DELETE CASCADE,
    provisioning_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    attempt             INT         NOT NULL DEFAULT 0,
    error_message       TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_app_template_stable_key
    ON app_template (stable_key);
CREATE UNIQUE INDEX IF NOT EXISTS uk_template_version_hash
    ON template_versions (template_id, content_hash);
CREATE INDEX IF NOT EXISTS idx_app_service_template_version_id
    ON app_service (template_version_id);
CREATE INDEX IF NOT EXISTS idx_app_service_owner_id
    ON app_service (owner_id);
CREATE INDEX IF NOT EXISTS idx_app_deployment_service_env_deployed
    ON app_deployment (service_id, environment, deployed_at DESC);
CREATE INDEX IF NOT EXISTS idx_provisioning_job_service_id
    ON provisioning_job (service_id);
CREATE INDEX IF NOT EXISTS idx_provisioning_job_active
    ON provisioning_job (created_at)
    WHERE provisioning_status IN ('PENDING', 'GENERATING');
