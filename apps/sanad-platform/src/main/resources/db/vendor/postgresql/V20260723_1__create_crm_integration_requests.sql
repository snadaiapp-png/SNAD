-- ============================================================
-- SNAD Platform — CRM-009 — V20260723.1
-- Create CRM Integration Requests Table + Seed Capabilities
-- ------------------------------------------------------------
-- Forward-only. Fail-closed. Tenant-scoped. PostgreSQL 16 native.
-- JSONB for payload/result. Tenant-scoped idempotency unique.
-- No IF NOT EXISTS (drift masking prohibited).
-- ============================================================

-- ============================================================
-- PRECONDITIONS — fail closed on partial/conflicting state
-- ============================================================
DO $precondition$
DECLARE
    target_table_count     INTEGER;
    conflicting_index       INTEGER;
    conflicting_capability  INTEGER;
    failed_history          INTEGER;
BEGIN
    -- Target table MUST be absent (no partial state)
    SELECT COUNT(*)
      INTO target_table_count
      FROM information_schema.tables
     WHERE table_schema = 'public'
       AND table_name = 'crm_integration_requests';
    IF target_table_count <> 0 THEN
        RAISE EXCEPTION
            'V20260723.1 precondition failed: crm_integration_requests already exists (expected 0)';
    END IF;

    -- No conflicting index names
    SELECT COUNT(*)
      INTO conflicting_index
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND indexname IN (
           'crm_integration_tenant_status_idx',
           'crm_integration_correlation_idx'
       );
    IF conflicting_index <> 0 THEN
        RAISE EXCEPTION
            'V20260723.1 precondition failed: % conflicting indexes already exist',
            conflicting_index;
    END IF;

    -- No conflicting capabilities with same code but different data
    SELECT COUNT(*)
      INTO conflicting_capability
      FROM access_capabilities
     WHERE code IN ('CRM.WORKFLOW.EXECUTE', 'CRM.AI.READ', 'CRM.AI.CONFIRM')
       AND (id NOT IN ('a0000009-0000-0000-0000-000000000901',
                       'a0000009-0000-0000-0000-000000000902')
            OR name NOT IN ('Execute CRM Workflows', 'Read CRM AI Insights', 'Confirm CRM AI Recommendations'));
    IF conflicting_capability > 0 THEN
        RAISE EXCEPTION
            'V20260723.1 precondition failed: % capabilities with conflicting data already exist',
            conflicting_capability;
    END IF;

    -- No failed Flyway history rows
    SELECT COUNT(*)
      INTO failed_history
      FROM flyway_schema_history
     WHERE success = FALSE;
    IF failed_history > 0 THEN
        RAISE EXCEPTION
            'V20260723.1 refuses to apply over % failed Flyway history rows',
            failed_history;
    END IF;
END
$precondition$;

-- ============================================================
-- DDL — create table with fail-closed constraints
-- ============================================================
CREATE TABLE crm_integration_requests (
    id                      UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    actor_id                UUID NOT NULL,
    integration_type        VARCHAR(80) NOT NULL,
    contract_name           VARCHAR(120) NOT NULL,
    contract_version        VARCHAR(40) NOT NULL,
    correlation_id          VARCHAR(160) NOT NULL,
    causation_id            VARCHAR(160) NOT NULL,
    idempotency_key         VARCHAR(200) NOT NULL,
    source_entity_type      VARCHAR(80) NOT NULL,
    source_entity_id        UUID NOT NULL,
    source_entity_version   BIGINT NOT NULL CHECK (source_entity_version >= 0),
    required_capability     VARCHAR(160) NOT NULL,
    data_classification     VARCHAR(80) NOT NULL,
    payload                 JSONB NOT NULL DEFAULT '{}'::jsonb,
    result_payload          JSONB,
    status                  VARCHAR(40) NOT NULL,
    external_reference      UUID,
    error_code              VARCHAR(120),
    requested_at            TIMESTAMPTZ NOT NULL,
    expires_at              TIMESTAMPTZ NOT NULL,
    completed_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_crm_integration_requests PRIMARY KEY (id),
    CONSTRAINT crm_integration_expiry_ck CHECK (expires_at > requested_at),
    CONSTRAINT crm_integration_status_ck CHECK (
        status IN ('PENDING','DISPATCHED','ACCEPTED','RUNNING','COMPLETED',
                   'REJECTED','POLICY_DENIED','UNSAFE_OUTPUT','TIMED_OUT',
                   'UNAVAILABLE','CANCELLED','EXPIRED')
    ),
    CONSTRAINT crm_integration_type_ck CHECK (
        integration_type IN ('WORKFLOW','AI')
    ),
    CONSTRAINT crm_integration_classification_ck CHECK (
        data_classification IN ('PUBLIC','INTERNAL','CONFIDENTIAL','RESTRICTED')
    ),
    CONSTRAINT crm_integration_terminal_ck CHECK (
        (status NOT IN ('COMPLETED','REJECTED','POLICY_DENIED','UNSAFE_OUTPUT',
                        'TIMED_OUT','UNAVAILABLE','CANCELLED','EXPIRED'))
        OR (completed_at IS NOT NULL)
    ),
    CONSTRAINT crm_integration_non_terminal_ck CHECK (
        (status IN ('COMPLETED','REJECTED','POLICY_DENIED','UNSAFE_OUTPUT',
                    'TIMED_OUT','UNAVAILABLE','CANCELLED','EXPIRED'))
        OR (completed_at IS NULL)
    ),
    CONSTRAINT crm_integration_payload_ck CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT crm_integration_result_payload_ck CHECK (
        result_payload IS NULL OR jsonb_typeof(result_payload) = 'object'
    ),
    CONSTRAINT crm_integration_tenant_idempotency_uq
        UNIQUE (tenant_id, integration_type, idempotency_key)
);

-- Tenant-leading indexes
CREATE INDEX crm_integration_tenant_status_idx
    ON crm_integration_requests (tenant_id, status, created_at DESC);

CREATE INDEX crm_integration_correlation_idx
    ON crm_integration_requests (tenant_id, correlation_id);

CREATE INDEX crm_integration_tenant_entity_idx
    ON crm_integration_requests (tenant_id, source_entity_type, source_entity_id, created_at DESC);

-- ============================================================
-- DML — seed 2 CRM-009 capabilities (deterministic, fail-closed)
-- ============================================================
INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
VALUES ('a0000009-0000-0000-0000-000000000901', 'CRM.WORKFLOW.EXECUTE',
        'Execute CRM Workflows', 'Dispatch and inspect governed CRM workflow requests',
        'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
VALUES ('a0000009-0000-0000-0000-000000000902', 'CRM.AI.READ',
        'Read CRM AI Insights', 'Request governed advisory CRM AI outputs',
        'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ============================================================
-- POSTCONDITIONS — verify exact schema, types, capabilities
-- ============================================================
DO $postcondition$
DECLARE
    table_exists         INTEGER;
    payload_type         TEXT;
    payload_udt          TEXT;
    result_type          TEXT;
    result_udt           TEXT;
    expected_indexes     INTEGER;
    cap_count            INTEGER;
BEGIN
    SELECT COUNT(*) INTO table_exists
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_name = 'crm_integration_requests';
    IF table_exists <> 1 THEN
        RAISE EXCEPTION 'V20260723.1 postcondition failed: crm_integration_requests not created';
    END IF;

    -- payload must be JSONB NOT NULL
    SELECT data_type, udt_name INTO payload_type, payload_udt
      FROM information_schema.columns
     WHERE table_schema = 'public'
       AND table_name = 'crm_integration_requests'
       AND column_name = 'payload';
    IF payload_type IS DISTINCT FROM 'jsonb' OR payload_udt IS DISTINCT FROM 'jsonb' THEN
        RAISE EXCEPTION
            'V20260723.1 postcondition failed: payload data_type=% udt_name=% (expected jsonb/jsonb)',
            payload_type, payload_udt;
    END IF;

    -- result_payload must be JSONB (nullable)
    SELECT data_type, udt_name INTO result_type, result_udt
      FROM information_schema.columns
     WHERE table_schema = 'public'
       AND table_name = 'crm_integration_requests'
       AND column_name = 'result_payload';
    IF result_type IS DISTINCT FROM 'jsonb' OR result_udt IS DISTINCT FROM 'jsonb' THEN
        RAISE EXCEPTION
            'V20260723.1 postcondition failed: result_payload data_type=% udt_name=% (expected jsonb/jsonb)',
            result_type, result_udt;
    END IF;

    -- All 3 indexes must exist
    SELECT COUNT(*) INTO expected_indexes
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND tablename = 'crm_integration_requests'
       AND indexname IN (
           'crm_integration_tenant_status_idx',
           'crm_integration_correlation_idx',
           'crm_integration_tenant_entity_idx'
       );
    IF expected_indexes <> 3 THEN
        RAISE EXCEPTION
            'V20260723.1 postcondition failed: expected 3 indexes, found %',
            expected_indexes;
    END IF;

    -- 2 CRM-009 capabilities must be seeded
    SELECT COUNT(*) INTO cap_count
      FROM access_capabilities
     WHERE code IN ('CRM.WORKFLOW.EXECUTE', 'CRM.AI.READ', 'CRM.AI.CONFIRM')
       AND status = 'ACTIVE';
    IF cap_count <> 3 THEN
        RAISE EXCEPTION
            'V20260723.1 postcondition failed: expected 3 CRM-009 capabilities, found %',
            cap_count;
    END IF;
END
$postcondition$;

-- CRM.AI.CONFIRM capability (for human confirmation of AI recommendations)
INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
VALUES ('a0000009-0000-0000-0000-000000000903', 'CRM.AI.CONFIRM',
        'Confirm CRM AI Recommendations', 'Accept or reject AI-generated recommendations with human confirmation',
        'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
