CREATE TABLE IF NOT EXISTS application
(
    id               UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    workspace_id     UUID         NOT NULL REFERENCES workspace (id) ON DELETE CASCADE,
    owner_id         UUID         REFERENCES users (id) ON DELETE SET NULL,
    name             VARCHAR(40)  NOT NULL,
    description      TEXT,
    status           VARCHAR(30)  NOT NULL DEFAULT 'CREATING',
    repository_owner VARCHAR(100),
    repository_url   VARCHAR(255),
    repository_id    BIGINT,
    failure_reason   TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_application_workspace_name UNIQUE (workspace_id, name)
);

CREATE INDEX IF NOT EXISTS idx_application_workspace_created
    ON application (workspace_id, created_at DESC);

ALTER TABLE app_service
    ADD COLUMN IF NOT EXISTS application_id UUID REFERENCES application (id) ON DELETE CASCADE;
ALTER TABLE app_service
    ADD COLUMN IF NOT EXISTS port INT;

CREATE INDEX IF NOT EXISTS idx_app_service_application_id
    ON app_service (application_id);

ALTER TABLE provisioning_job
    ADD COLUMN IF NOT EXISTS application_id UUID REFERENCES application (id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_provisioning_job_application_id
    ON provisioning_job (application_id);
