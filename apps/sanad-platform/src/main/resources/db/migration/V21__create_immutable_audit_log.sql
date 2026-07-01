-- ============================================================
-- SANAD Platform - Flyway migration V21
-- ------------------------------------------------------------
-- Stage 05 §5 — Create the immutable audit_events table.
--
-- This migration is H2+PostgreSQL compatible. It creates the
-- audit_events table with all mandatory columns for:
--   - Tenant scoping (tenant_id)
--   - Actor attribution (actor_type, actor_user_id, actor_service, actor_display_name)
--   - Session/request/correlation identity (session_id, jwt_id, request_id,
--     correlation_id, trace_id)
--   - Action and resource identity (action, category, resource_type,
--     resource_id, operation)
--   - Outcome (outcome, http_status, error_code, failure_reason)
--   - Source (source_ip, user_agent, channel)
--   - State change (before_state, after_state, changed_fields) — JSONB
--   - Metadata (metadata) — JSONB
--   - Hash chain (previous_hash, event_hash, hash_algorithm, schema_version)
--   - Timestamps (occurred_at, recorded_at, created_at)
--
-- Conventions (consistent with V1, V2, V3, V4):
--   - UUID primary keys
--   - snake_case column names
--   - explicit constraint names with platform prefix
--   - created_at and occurred_at as TIMESTAMP WITH TIME ZONE (UTC)
--   - status/outcome stored as STRING (Hibernate EnumType.STRING)
--   - FK references named fk_<child>_<parent>
--
-- Compatible with PostgreSQL 14+ (production target).
-- Compatible with H2 (MODE=PostgreSQL) for the local profile.
--
-- NOTE: JSONB columns use PostgreSQL native JSONB. On H2, the columns
-- are created as TEXT and contain JSON strings — the application layer
-- serializes/deserializes JSON in both cases. The JPA entity uses
-- @JdbcTypeCode(SqlTypes.JSON) which works on both.
-- ============================================================

CREATE TABLE audit_events (
    id                    UUID            NOT NULL,
    tenant_id             UUID            NOT NULL,

    -- Actor attribution
    actor_type            VARCHAR(30)     NOT NULL,
    actor_user_id         UUID,
    actor_service         VARCHAR(200),
    actor_display_name    VARCHAR(200),

    -- Session and request identity
    session_id            VARCHAR(128),
    jwt_id                VARCHAR(128),
    request_id            VARCHAR(128),
    correlation_id        VARCHAR(128),
    trace_id              VARCHAR(128),

    -- Action and resource
    action                VARCHAR(100)    NOT NULL,
    category              VARCHAR(50),
    resource_type         VARCHAR(100)    NOT NULL,
    resource_id           VARCHAR(128),
    operation             VARCHAR(50)     NOT NULL,

    -- Outcome
    outcome               VARCHAR(20)     NOT NULL,
    http_status           INTEGER,
    error_code            VARCHAR(50),
    failure_reason        VARCHAR(1000),

    -- Source
    source_ip             VARCHAR(45),
    user_agent            VARCHAR(500),
    channel               VARCHAR(50),

    -- State change (JSON)
    before_state          TEXT,
    after_state           TEXT,
    changed_fields        TEXT,

    -- Metadata (JSON)
    metadata              TEXT,

    -- Hash chain
    previous_hash         VARCHAR(64),
    event_hash            VARCHAR(64)     NOT NULL,
    hash_algorithm        VARCHAR(20)     NOT NULL DEFAULT 'SHA-256',
    schema_version        INTEGER         NOT NULL DEFAULT 1,

    -- Timestamps
    occurred_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_audit_events          PRIMARY KEY (id),
    CONSTRAINT fk_audit_events_tenant   FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_audit_events_actor    CHECK (actor_type IN ('USER','SERVICE','SYSTEM','BACKGROUND_JOB','EXTERNAL_INTEGRATION')),
    CONSTRAINT ck_audit_events_outcome  CHECK (outcome IN ('SUCCESS','FAILURE','DENIED','PARTIAL'))
);

-- Indexes for tenant-scoped queries
CREATE INDEX idx_audit_events_tenant_occurred       ON audit_events (tenant_id, occurred_at);
CREATE INDEX idx_audit_events_tenant_actor          ON audit_events (tenant_id, actor_user_id, occurred_at);
CREATE INDEX idx_audit_events_tenant_resource       ON audit_events (tenant_id, resource_type, resource_id);
CREATE INDEX idx_audit_events_tenant_correlation    ON audit_events (tenant_id, correlation_id);
CREATE INDEX idx_audit_events_tenant_request        ON audit_events (tenant_id, request_id);
CREATE INDEX idx_audit_events_tenant_hash           ON audit_events (tenant_id, event_hash);
CREATE INDEX idx_audit_events_tenant_action         ON audit_events (tenant_id, action, occurred_at);
CREATE INDEX idx_audit_events_tenant_outcome        ON audit_events (tenant_id, outcome, occurred_at);
