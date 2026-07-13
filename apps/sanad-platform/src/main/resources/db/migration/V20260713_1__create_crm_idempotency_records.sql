-- ============================================================
-- SNAD Platform — CRM Idempotency Records
-- ------------------------------------------------------------
-- Branch: crm/003-stable-api-contracts
-- Gate:   CRM-G2 — API Contract and Concurrency Gate
--
-- Stores one row per idempotent CRM operation. The composite
-- UNIQUE constraint on (tenant_id, principal_id, endpoint, idempotency_key)
-- is what makes idempotency race-safe: two concurrent requests with the
-- same key cannot both succeed.
--
-- Retention: rows are eligible for cleanup after expires_at. A periodic
-- cleanup job (or a manual VACUUM) is expected; the API does NOT
-- automatically delete rows.
-- ============================================================

CREATE TABLE IF NOT EXISTS crm_idempotency_records (
    id                          UUID PRIMARY KEY,
    tenant_id                   UUID NOT NULL,
    principal_id                UUID NOT NULL,
    endpoint                    VARCHAR(255) NOT NULL,
    idempotency_key             VARCHAR(255) NOT NULL,
    request_fingerprint_sha256  CHAR(64) NOT NULL,
    response_status             INTEGER NOT NULL DEFAULT 0,
    response_body_json          TEXT,
    created_at                  TIMESTAMP NOT NULL,
    expires_at                  TIMESTAMP NOT NULL,

    CONSTRAINT crm_idempotency_records_unique
        UNIQUE (tenant_id, principal_id, endpoint, idempotency_key)
);

-- Tenant-scoped lookup index (the most common access pattern).
CREATE INDEX IF NOT EXISTS idx_crm_idempotency_records_tenant
    ON crm_idempotency_records (tenant_id, expires_at);

-- Cleanup index (vacuum rows past their retention window).
CREATE INDEX IF NOT EXISTS idx_crm_idempotency_records_expires
    ON crm_idempotency_records (expires_at);

-- Add a version column to crm_pipelines if it does not already exist.
-- The other CRM entities (accounts, contacts, leads, opportunities,
-- activities, custom_field_definitions) already have a `version` column
-- from V20260702_1__create_unified_crm_core.sql. Pipelines was the
-- only editable CRM entity missing the column.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'crm_pipelines'
          AND column_name = 'version'
    ) THEN
        ALTER TABLE crm_pipelines
            ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
    END IF;
END $$;

-- Add response_headers_json and content_type columns for full replay
ALTER TABLE crm_idempotency_records ADD COLUMN IF NOT EXISTS response_headers_json TEXT;
ALTER TABLE crm_idempotency_records ADD COLUMN IF NOT EXISTS content_type VARCHAR(255);
