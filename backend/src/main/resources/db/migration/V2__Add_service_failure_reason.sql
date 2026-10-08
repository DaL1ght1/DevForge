ALTER TABLE app_service
    ADD COLUMN IF NOT EXISTS failure_reason TEXT;
