-- ============================================================
-- SNAD Platform — CRM Idempotency Records
-- ------------------------------------------------------------
-- Branch: crm/003-stable-api-contracts
-- Gate:   CRM-G2 — API Contract and Concurrency Gate
--
-- TEXT is supported by PostgreSQL and by H2 in PostgreSQL compatibility
-- mode. CLOB is intentionally not used because PostgreSQL has no CLOB type.
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
    response_headers_json       TEXT,
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
