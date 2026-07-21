-- Forward-only reconciliation for Production databases where the historical
-- V20260713.1 Flyway row exists but crm_idempotency_records is absent.
--
-- This migration is intentionally idempotent. It does not modify Flyway
-- history and does not remove or rewrite existing business data.

CREATE TABLE IF NOT EXISTS crm_idempotency_records (
    id                          UUID PRIMARY KEY,
    tenant_id                   UUID NOT NULL,
    principal_id                UUID NOT NULL,
    endpoint                    VARCHAR(255) NOT NULL,
    idempotency_key             VARCHAR(255) NOT NULL,
    request_fingerprint_sha256  CHAR(64) NOT NULL,
    response_status             INTEGER NOT NULL DEFAULT 0,
    response_body_json          TEXT,
    response_headers_json       TEXT,
    content_type                VARCHAR(255),
    created_at                  TIMESTAMP NOT NULL,
    expires_at                  TIMESTAMP NOT NULL
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conname = 'crm_idempotency_records_unique'
           AND conrelid = 'crm_idempotency_records'::regclass
    ) THEN
        ALTER TABLE crm_idempotency_records
            ADD CONSTRAINT crm_idempotency_records_unique
            UNIQUE (tenant_id, principal_id, endpoint, idempotency_key);
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_crm_idempotency_records_tenant
    ON crm_idempotency_records (tenant_id, expires_at);

CREATE INDEX IF NOT EXISTS idx_crm_idempotency_records_expires
    ON crm_idempotency_records (expires_at);
