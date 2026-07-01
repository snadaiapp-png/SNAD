-- ============================================================
-- SANAD Platform - Flyway migration V22
-- ------------------------------------------------------------
-- Stage 05 §14 — Create the idempotency_records table.
--
-- This table stores persistent idempotency state for sensitive
-- commands (POST, PUT, PATCH operations that create or mutate
-- business state). It enables:
--   - Same-request replay (same key + same fingerprint → replay response)
--   - Payload-mismatch detection (same key + different fingerprint → 409)
--   - Concurrent duplicate suppression (DB-level unique constraint +
--     SELECT FOR UPDATE)
--   - Failure recovery (PROCESSING timeout → safe retry)
--
-- Conventions (consistent with V1, V2, V3, V4):
--   - UUID primary keys
--   - snake_case column names
--   - explicit constraint names with platform prefix
--   - created_at and updated_at as TIMESTAMP WITH TIME ZONE (UTC)
--   - status stored as STRING (Hibernate EnumType.STRING)
--
-- Compatible with PostgreSQL 14+ (production target).
-- Compatible with H2 (MODE=PostgreSQL) for the local profile.
-- ============================================================

CREATE TABLE idempotency_records (
    id                      UUID            NOT NULL,
    tenant_id               UUID            NOT NULL,

    -- Client-supplied idempotency key
    idempotency_key         VARCHAR(255)    NOT NULL,

    -- Operation scope
    operation               VARCHAR(100)    NOT NULL,
    route                   VARCHAR(500)    NOT NULL,
    resource_type           VARCHAR(100),

    -- Request fingerprint (SHA-256 of canonical request)
    request_fingerprint     VARCHAR(64)     NOT NULL,

    -- Status
    status                  VARCHAR(30)     NOT NULL,

    -- Stored response (for replay)
    response_status         INTEGER,
    response_headers        TEXT,
    response_body           TEXT,

    -- Lifecycle timestamps
    locked_at               TIMESTAMP WITH TIME ZONE,
    processing_started_at   TIMESTAMP WITH TIME ZONE,
    completed_at            TIMESTAMP WITH TIME ZONE,
    expires_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,

    -- Ownership and error tracking
    owner_request_id        VARCHAR(128),
    error_code              VARCHAR(50),
    error_detail            VARCHAR(1000),

    -- Hash chain (optional, for tamper evidence on idempotency state)
    CONSTRAINT pk_idempotency_records              PRIMARY KEY (id),
    CONSTRAINT fk_idempotency_records_tenant       FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT uk_idempotency_tenant_op_route_key  UNIQUE (tenant_id, operation, route, idempotency_key),
    CONSTRAINT ck_idempotency_status               CHECK (status IN ('PROCESSING','COMPLETED','FAILED_RETRYABLE','FAILED_FINAL','EXPIRED'))
);

-- Indexes for tenant-scoped queries
CREATE INDEX idx_idempotency_tenant_key         ON idempotency_records (tenant_id, idempotency_key);
CREATE INDEX idx_idempotency_tenant_status      ON idempotency_records (tenant_id, status);
CREATE INDEX idx_idempotency_tenant_expires     ON idempotency_records (tenant_id, expires_at);
CREATE INDEX idx_idempotency_tenant_op_created  ON idempotency_records (tenant_id, operation, created_at);
