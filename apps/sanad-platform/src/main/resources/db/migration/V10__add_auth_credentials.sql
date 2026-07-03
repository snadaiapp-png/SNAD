-- ============================================================
-- V10: Add authentication credentials and refresh tokens
-- EXEC-PROMPT-032A: Backend Authentication & Session Foundation
-- ============================================================

ALTER TABLE users
    ADD COLUMN password_hash VARCHAR(255);

ALTER TABLE users
    ADD COLUMN last_login_at TIMESTAMP WITH TIME ZONE;

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
    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (tenant_id, user_id) REFERENCES users(tenant_id, id),
    CONSTRAINT fk_refresh_tokens_replaced_by
        FOREIGN KEY (replaced_by_id) REFERENCES refresh_tokens(id),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_refresh_tokens_status
        CHECK (status IN ('ACTIVE', 'USED', 'REVOKED'))
);

CREATE INDEX idx_refresh_tokens_user
    ON refresh_tokens (tenant_id, user_id);
CREATE INDEX idx_refresh_tokens_status
    ON refresh_tokens (status);
CREATE INDEX idx_refresh_tokens_replaced_by
    ON refresh_tokens (replaced_by_id);
