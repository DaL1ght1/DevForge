CREATE TABLE IF NOT EXISTS workspace
(
    id                UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    type              VARCHAR(20)  NOT NULL,
    name              VARCHAR(100) NOT NULL,
    slug              VARCHAR(120) NOT NULL,
    personal_owner_id UUID         REFERENCES users (id) ON DELETE CASCADE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_workspace_slug UNIQUE (slug),
    CONSTRAINT ck_workspace_type CHECK (type IN ('PERSONAL', 'ORGANIZATION')),
    CONSTRAINT ck_workspace_personal_owner CHECK (
        (type = 'PERSONAL' AND personal_owner_id IS NOT NULL)
            OR (type = 'ORGANIZATION' AND personal_owner_id IS NULL))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_workspace_personal_owner
    ON workspace (personal_owner_id)
    WHERE type = 'PERSONAL';

CREATE TABLE IF NOT EXISTS workspace_member
(
    id           UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    workspace_id UUID        NOT NULL REFERENCES workspace (id) ON DELETE CASCADE,
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role         VARCHAR(20) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_workspace_member UNIQUE (workspace_id, user_id),
    CONSTRAINT ck_workspace_member_role CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER'))
);

CREATE INDEX IF NOT EXISTS idx_workspace_member_user_id
    ON workspace_member (user_id);

INSERT INTO workspace (type, name, slug, personal_owner_id)
SELECT 'PERSONAL', u.username, u.username, u.id
FROM users u
WHERE NOT EXISTS (SELECT 1
                  FROM workspace w
                  WHERE w.type = 'PERSONAL'
                    AND w.personal_owner_id = u.id);

INSERT INTO workspace_member (workspace_id, user_id, role)
SELECT w.id, w.personal_owner_id, 'OWNER'
FROM workspace w
WHERE w.type = 'PERSONAL'
ON CONFLICT (workspace_id, user_id) DO NOTHING;

ALTER TABLE app_service
    ADD COLUMN IF NOT EXISTS workspace_id UUID REFERENCES workspace (id) ON DELETE CASCADE;
ALTER TABLE app_service
    ADD COLUMN IF NOT EXISTS repository_owner VARCHAR(100);
ALTER TABLE app_service
    ADD COLUMN IF NOT EXISTS repository_id BIGINT;

UPDATE app_service s
SET workspace_id = w.id
FROM workspace w
WHERE w.type = 'PERSONAL'
  AND w.personal_owner_id = s.owner_id;

INSERT INTO workspace (type, name, slug)
SELECT 'ORGANIZATION', 'Unassigned services', 'unassigned-services'
WHERE EXISTS (SELECT 1 FROM app_service WHERE workspace_id IS NULL)
ON CONFLICT (slug) DO NOTHING;

UPDATE app_service
SET workspace_id = (SELECT id FROM workspace WHERE slug = 'unassigned-services')
WHERE workspace_id IS NULL;

ALTER TABLE app_service
    ALTER COLUMN workspace_id SET NOT NULL;

ALTER TABLE app_service
    DROP CONSTRAINT IF EXISTS app_service_name_key;
ALTER TABLE app_service
    DROP CONSTRAINT IF EXISTS uk_app_service_name;
ALTER TABLE app_service
    ADD CONSTRAINT uk_app_service_workspace_name UNIQUE (workspace_id, name);

CREATE INDEX IF NOT EXISTS idx_app_service_workspace_created
    ON app_service (workspace_id, created_at DESC);

ALTER TABLE provisioning_job
    ADD COLUMN IF NOT EXISTS installation_id BIGINT;

CREATE TABLE IF NOT EXISTS github_account
(
    id                       UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    user_id                  UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    github_user_id           BIGINT       NOT NULL,
    login                    VARCHAR(100) NOT NULL,
    access_token_enc         TEXT,
    access_token_expires_at  TIMESTAMPTZ,
    refresh_token_enc        TEXT,
    refresh_token_expires_at TIMESTAMPTZ,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_github_account_user UNIQUE (user_id),
    CONSTRAINT uk_github_account_github_user UNIQUE (github_user_id)
);

CREATE TABLE IF NOT EXISTS github_installation
(
    id                   UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    installation_id      BIGINT       NOT NULL,
    account_id           BIGINT       NOT NULL,
    account_login        VARCHAR(100) NOT NULL,
    account_type         VARCHAR(20)  NOT NULL,
    repository_selection VARCHAR(20),
    status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    workspace_id         UUID         NOT NULL REFERENCES workspace (id) ON DELETE CASCADE,
    connected_by_user_id UUID         REFERENCES users (id) ON DELETE SET NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_github_installation UNIQUE (installation_id),
    CONSTRAINT ck_github_installation_account_type CHECK (account_type IN ('USER', 'ORGANIZATION')),
    CONSTRAINT ck_github_installation_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REMOVED'))
);

CREATE INDEX IF NOT EXISTS idx_github_installation_workspace
    ON github_installation (workspace_id);

CREATE TABLE IF NOT EXISTS github_webhook_delivery
(
    delivery_id VARCHAR(100) PRIMARY KEY,
    event       VARCHAR(50) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
