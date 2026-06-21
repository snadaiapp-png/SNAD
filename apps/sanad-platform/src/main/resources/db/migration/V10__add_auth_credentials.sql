-- ============================================================
-- V10: Add authentication credentials and refresh tokens
-- EXEC-PROMPT-032A: Backend Authentication & Session Foundation
-- ============================================================
-- Adds credential storage for user authentication.
-- The column is nullable because existing users (created before
-- auth was enabled) have no password. A future password-setting
-- flow will populate it.
--
-- Security notes:
-- - Stores BCrypt hashes only (never plaintext passwords)
-- - BCrypt hashes include the salt and strength, so VARCHAR(255)
--   is more than sufficient (typical BCrypt hash is 60 chars)
-- - The column is never selected in API responses (excluded from
--   UserResponse DTOs)
-- ============================================================

ALTER TABLE users
    ADD COLUMN password_hash VARCHAR(255);

-- Add a column to track the last successful login timestamp.
-- This is useful for security auditing and inactive-user detection.
ALTER TABLE users
    ADD COLUMN last_login_at TIMESTAMP WITH TIME ZONE;

-- ============================================================
-- Refresh tokens table for session refresh with rotation
-- ============================================================
-- Each refresh token is a single-use opaque token.
-- On refresh, the old token is marked USED and a new one is issued.
-- If a USED token is presented again, all tokens for that user
-- are revoked (replay protection / token family invalidation).
--
-- The replaced_by_id column is a soft link (no FK constraint) to
-- avoid circular self-reference issues during backup/restore.
-- ============================================================

CREATE TABLE refresh_tokens (
    id              UUID            NOT NULL,
    tenant_id       UUID            NOT NULL,
    user_id         UUID            NOT NULL,
    token_hash      VARCHAR(255)    NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at         TIMESTAMP WITH TIME ZONE,
    replaced_by_id  UUID,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (tenant_id, user_id) REFERENCES users(tenant_id, id),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_refresh_tokens_status CHECK (status IN ('ACTIVE', 'USED', 'REVOKED'))
);

CREATE INDEX idx_refresh_tokens_user   ON refresh_tokens (tenant_id, user_id);
CREATE INDEX idx_refresh_tokens_status ON refresh_tokens (status);
