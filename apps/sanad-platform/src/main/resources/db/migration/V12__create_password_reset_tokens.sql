-- ============================================================
-- V12: Password reset tokens for self-service account recovery
-- SANAD-GOV-UX-OPS-003 · AUTH-RECOVERY-001
-- ============================================================

CREATE TABLE password_reset_tokens (
    id              UUID            NOT NULL,
    tenant_id       UUID            NOT NULL,
    user_id         UUID            NOT NULL,
    token_hash      VARCHAR(255)    NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at         TIMESTAMP WITH TIME ZONE,
    ip_address      VARCHAR(45),
    CONSTRAINT pk_password_reset_tokens PRIMARY KEY (id),
    CONSTRAINT fk_password_reset_tokens_user
        FOREIGN KEY (tenant_id, user_id) REFERENCES users(tenant_id, id),
    CONSTRAINT uk_password_reset_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_password_reset_tokens_status
        CHECK (status IN ('ACTIVE', 'USED', 'REVOKED', 'EXPIRED'))
);

CREATE INDEX idx_password_reset_tokens_user
    ON password_reset_tokens (tenant_id, user_id);
CREATE INDEX idx_password_reset_tokens_status
    ON password_reset_tokens (status);
