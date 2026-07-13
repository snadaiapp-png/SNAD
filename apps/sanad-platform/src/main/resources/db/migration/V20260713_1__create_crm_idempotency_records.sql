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
-- This migration is written in portable SQL that works on both
-- PostgreSQL and H2 (PostgreSQL compatibility mode) so it can run
-- in the repository's automated test profile without modification.
-- ============================================================

CREATE TABLE IF NOT EXISTS crm_idempotency_records (
    id                          UUID PRIMARY KEY,
    tenant_id                   UUID NOT NULL,
    principal_id                UUID NOT NULL,
    endpoint                    VARCHAR(255) NOT NULL,
    idempotency_key             VARCHAR(255) NOT NULL,
    request_fingerprint_sha256  CHAR(64) NOT NULL,
    response_status             INTEGER NOT NULL DEFAULT 0,
    response_body_json          CLOB,
    response_headers_json       CLOB,
    content_type                VARCHAR(255),
    created_at                  TIMESTAMP NOT NULL,
    expires_at                  TIMESTAMP NOT NULL,
    CONSTRAINT crm_idempotency_records_unique
        UNIQUE (tenant_id, principal_id, endpoint, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_crm_idempotency_records_tenant
    ON crm_idempotency_records (tenant_id, expires_at);

CREATE INDEX IF NOT EXISTS idx_crm_idempotency_records_expires
    ON crm_idempotency_records (expires_at);
