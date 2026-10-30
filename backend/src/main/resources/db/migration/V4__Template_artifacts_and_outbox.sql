CREATE TABLE IF NOT EXISTS template_artifact
(
    digest     VARCHAR(64) PRIMARY KEY,
    size_bytes INT         NOT NULL,
    data       BYTEA       NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE template_versions
    ADD COLUMN IF NOT EXISTS artifact_digest VARCHAR(64) REFERENCES template_artifact (digest);

ALTER TABLE provisioning_job
    ADD COLUMN IF NOT EXISTS repository_id BIGINT;

CREATE TABLE IF NOT EXISTS outbox_event
(
    id          UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    topic       VARCHAR(255) NOT NULL,
    message_key VARCHAR(255) NOT NULL,
    payload     TEXT         NOT NULL,
    job_id      UUID,
    attempts    INT          NOT NULL DEFAULT 0,
    last_error  TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    sent_at     TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_outbox_event_unsent
    ON outbox_event (created_at)
    WHERE sent_at IS NULL;
