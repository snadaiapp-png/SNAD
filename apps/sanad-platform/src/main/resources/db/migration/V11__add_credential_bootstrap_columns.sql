-- ============================================================
-- V11: Credential bootstrap audit and forced rotation state
-- EXEC-PROMPT-032A
-- ============================================================

ALTER TABLE users
    ADD COLUMN password_set_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE users
    ADD COLUMN password_set_by VARCHAR(100);

ALTER TABLE users
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN users.password_set_at IS
    'UTC timestamp of the last controlled credential enrollment.';
COMMENT ON COLUMN users.password_set_by IS
    'Non-secret audit actor identifier for credential enrollment.';
COMMENT ON COLUMN users.must_change_password IS
    'Forces credential rotation after administrative bootstrap.';
