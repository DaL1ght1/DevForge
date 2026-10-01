CREATE TABLE IF NOT EXISTS users
(
    id         UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    keycloak_id UUID        NOT NULL UNIQUE,
    username   VARCHAR(50)  NOT NULL UNIQUE,
    email      VARCHAR(100) NOT NULL UNIQUE,
    first_name VARCHAR(50),
    last_name  VARCHAR(50),
    role       VARCHAR(30)  NOT NULL DEFAULT 'DEVELOPER',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE TABLE if not exists app_template
(
    id            uuid primary key     DEFAULT gen_random_uuid(),
    name          varchar(20) not null unique,
    language      VARCHAR(30) not null,
    framework     VARCHAR(30) not null,
    build_tool    VARCHAR(30) not null,
    database_type varchar(20),
    created_at    TIMESTAMPTZ not null default now()
);

CREATE TABLE IF NOT EXISTS template_versions
(
    id          UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    template_id UUID         NOT NULL REFERENCES app_template (id) ON DELETE CASCADE,
    version     VARCHAR(30)  NOT NULL,
    source_path VARCHAR(500) NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_template_version UNIQUE (template_id, version)
);

CREATE TABLE if not exists app_service
(
    id                  uuid primary key     DEFAULT gen_random_uuid(),
    name                varchar(20) not null unique,
    description         TEXT,
    repository_url      varchar(255) unique,
    status              VARCHAR(30) not null default 'CREATING',
    template_version_id uuid        REFERENCES template_versions (id) ON DELETE SET NULL,
    owner_id            uuid        REFERENCES users (id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ not null default now(),
    updated_at          TIMESTAMPTZ not null default now()
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
