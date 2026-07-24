-- ============================================================
-- SNAD Platform — CRM-009 — V20260723.1
-- Create CRM Integration Requests + Outbox + Decisions Tables
-- ------------------------------------------------------------
-- Forward-only. Fail-closed. Tenant-scoped. PostgreSQL 16 native.
-- ============================================================

-- ============================================================
-- PRECONDITIONS
-- ============================================================
DO $precondition$
DECLARE
    target_table_count     INTEGER;
    outbox_table_count     INTEGER;
    decisions_table_count  INTEGER;
    conflicting_index       INTEGER;
    conflicting_capability  INTEGER;
    failed_history          INTEGER;
BEGIN
    SELECT COUNT(*) INTO target_table_count
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_name = 'crm_integration_requests';
    IF target_table_count <> 0 THEN
        RAISE EXCEPTION 'V20260723.1 precondition failed: crm_integration_requests already exists';
    END IF;

    SELECT COUNT(*) INTO outbox_table_count
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_name = 'crm_integration_outbox';
    IF outbox_table_count <> 0 THEN
        RAISE EXCEPTION 'V20260723.1 precondition failed: crm_integration_outbox already exists';
    END IF;

    SELECT COUNT(*) INTO decisions_table_count
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_name = 'crm_integration_decisions';
    IF decisions_table_count <> 0 THEN
        RAISE EXCEPTION 'V20260723.1 precondition failed: crm_integration_decisions already exists';
    END IF;

    SELECT COUNT(*) INTO conflicting_index
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND indexname IN (
           'crm_integration_tenant_status_idx',
           'crm_integration_correlation_idx',
           'crm_integration_tenant_entity_idx',
           'crm_integration_outbox_claimable_idx',
           'crm_integration_outbox_retry_idx',
           'crm_integration_outbox_tenant_status_idx',
           'crm_integration_outbox_expired_claim_idx',
           'crm_integration_decisions_tenant_request_idx'
       );
    IF conflicting_index <> 0 THEN
        RAISE EXCEPTION 'V20260723.1 precondition failed: % conflicting indexes already exist', conflicting_index;
    END IF;

    -- Strict capability tuple check: code, exact id, exact name, exact description, ACTIVE
    SELECT COUNT(*) INTO conflicting_capability
      FROM access_capabilities
     WHERE code IN ('CRM.WORKFLOW.EXECUTE', 'CRM.AI.READ', 'CRM.AI.CONFIRM')
       AND NOT (
            (code = 'CRM.WORKFLOW.EXECUTE'
                AND id   = 'a0000009-0000-0000-0000-000000000901'
                AND name = 'Execute CRM Workflows'
                AND description = 'Dispatch and inspect governed CRM workflow requests'
                AND status = 'ACTIVE')
            OR (code = 'CRM.AI.READ'
                AND id   = 'a0000009-0000-0000-0000-000000000902'
                AND name = 'Read CRM AI Insights'
                AND description = 'Request governed advisory CRM AI outputs'
                AND status = 'ACTIVE')
            OR (code = 'CRM.AI.CONFIRM'
                AND id   = 'a0000009-0000-0000-0000-000000000903'
                AND name = 'Confirm CRM AI Recommendations'
                AND description = 'Accept or reject AI-generated recommendations with human confirmation'
                AND status = 'ACTIVE')
       );
    IF conflicting_capability > 0 THEN
        RAISE EXCEPTION 'V20260723.1 precondition failed: % capabilities with conflicting data already exist', conflicting_capability;
    END IF;

    SELECT COUNT(*) INTO failed_history
      FROM flyway_schema_history
     WHERE success = FALSE;
    IF failed_history > 0 THEN
        RAISE EXCEPTION 'V20260723.1 refuses to apply over % failed Flyway history rows', failed_history;
    END IF;
END
$precondition$;

-- ============================================================
-- DDL — crm_integration_requests
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
    requested_locale        VARCHAR(20) NOT NULL,
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
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_crm_integration_requests PRIMARY KEY (id),
    CONSTRAINT crm_integration_tenant_id_uq UNIQUE (tenant_id, id),
    CONSTRAINT crm_integration_expiry_ck CHECK (expires_at > requested_at),
    CONSTRAINT crm_integration_status_ck CHECK (
        status IN ('PENDING','DISPATCHED','ACCEPTED','RUNNING','COMPLETED',
                   'RECOMMENDATION_AVAILABLE','CONFIRMED','EXECUTING','EXECUTED',
                   'EXECUTION_REJECTED','REJECTED','POLICY_DENIED','UNSAFE_OUTPUT',
                   'TIMED_OUT','UNAVAILABLE','CANCELLED','EXPIRED')
    ),
    CONSTRAINT crm_integration_type_ck CHECK (
        integration_type IN ('WORKFLOW','AI')
    ),
    CONSTRAINT crm_integration_classification_ck CHECK (
        data_classification IN ('PUBLIC','INTERNAL','CONFIDENTIAL','RESTRICTED')
    ),
    -- Terminal states (completed_at must be NOT NULL)
    -- CONFIRMED and EXECUTING are intermediate — completed_at stays NULL
    CONSTRAINT crm_integration_terminal_ck CHECK (
        (status NOT IN ('COMPLETED','EXECUTED','EXECUTION_REJECTED','REJECTED',
                        'POLICY_DENIED','UNSAFE_OUTPUT','TIMED_OUT',
                        'UNAVAILABLE','CANCELLED','EXPIRED'))
        OR (completed_at IS NOT NULL)
    ),
    CONSTRAINT crm_integration_non_terminal_ck CHECK (
        (status IN ('COMPLETED','EXECUTED','EXECUTION_REJECTED','REJECTED',
                    'POLICY_DENIED','UNSAFE_OUTPUT','TIMED_OUT',
                    'UNAVAILABLE','CANCELLED','EXPIRED'))
        OR (completed_at IS NULL)
    ),
    CONSTRAINT crm_integration_payload_ck CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT crm_integration_result_payload_ck CHECK (
        result_payload IS NULL OR jsonb_typeof(result_payload) = 'object'
    ),
    CONSTRAINT crm_integration_tenant_idempotency_uq
        UNIQUE (tenant_id, integration_type, idempotency_key)
);

CREATE INDEX crm_integration_tenant_status_idx
    ON crm_integration_requests (tenant_id, status, created_at DESC);

CREATE INDEX crm_integration_correlation_idx
    ON crm_integration_requests (tenant_id, correlation_id);

CREATE INDEX crm_integration_tenant_entity_idx
    ON crm_integration_requests (tenant_id, source_entity_type, source_entity_id, created_at DESC);

-- ============================================================
-- DDL — crm_integration_outbox (transactional outbox)
-- ============================================================
CREATE TABLE crm_integration_outbox (
    id                      UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    integration_request_id  UUID NOT NULL,
    integration_type        VARCHAR(80) NOT NULL,
    event_type              VARCHAR(40) NOT NULL DEFAULT 'AI_REQUEST_DISPATCH',
    dispatch_status         VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    attempt_count           INTEGER NOT NULL DEFAULT 0,
    max_attempts            INTEGER NOT NULL DEFAULT 5,
    next_attempt_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claimed_at              TIMESTAMPTZ,
    claimed_by              VARCHAR(200),
    claim_token             UUID,
    claim_expires_at        TIMESTAMPTZ,
    last_error_code         VARCHAR(120),
    idempotency_key         VARCHAR(200) NOT NULL,
    payload                 JSONB NOT NULL DEFAULT '{}'::jsonb,
    completed_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_crm_integration_outbox PRIMARY KEY (id),
    CONSTRAINT fk_crm_integration_outbox_request FOREIGN KEY (tenant_id, integration_request_id)
        REFERENCES crm_integration_requests (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT crm_integration_outbox_status_ck CHECK (
        dispatch_status IN ('PENDING','CLAIMED','RETRY_WAIT','COMPLETED','DEAD_LETTER','CANCELLED')
    ),
    CONSTRAINT crm_integration_outbox_event_type_ck CHECK (
        event_type IN ('AI_REQUEST_DISPATCH','WORKFLOW_DISPATCH','CONFIRMED_COMMAND_EXECUTION')
    ),
    CONSTRAINT crm_integration_outbox_attempts_ck CHECK (
        attempt_count >= 0 AND attempt_count <= max_attempts
    ),
    CONSTRAINT crm_integration_outbox_claim_ck CHECK (
        (dispatch_status = 'CLAIMED' AND claimed_at IS NOT NULL AND claim_expires_at IS NOT NULL AND claim_token IS NOT NULL AND claimed_by IS NOT NULL)
        OR (dispatch_status <> 'CLAIMED' AND claimed_at IS NULL AND claim_expires_at IS NULL AND claim_token IS NULL AND claimed_by IS NULL)
    ),
    CONSTRAINT crm_integration_outbox_terminal_ck CHECK (
        (dispatch_status NOT IN ('COMPLETED','DEAD_LETTER','CANCELLED'))
        OR (completed_at IS NOT NULL)
    ),
    CONSTRAINT crm_integration_outbox_non_terminal_ck CHECK (
        (dispatch_status IN ('COMPLETED','DEAD_LETTER','CANCELLED'))
        OR (completed_at IS NULL)
    ),
    CONSTRAINT crm_integration_outbox_uq UNIQUE (tenant_id, integration_request_id, event_type)
);

-- Claimable events: PENDING or RETRY_WAIT with next_attempt_at <= now
CREATE INDEX crm_integration_outbox_claimable_idx
    ON crm_integration_outbox (tenant_id, dispatch_status, next_attempt_at)
    WHERE dispatch_status IN ('PENDING', 'RETRY_WAIT');

-- Retry index: find events that need retry
CREATE INDEX crm_integration_outbox_retry_idx
    ON crm_integration_outbox (next_attempt_at, dispatch_status)
    WHERE dispatch_status IN ('PENDING', 'RETRY_WAIT');

-- Tenant status index
CREATE INDEX crm_integration_outbox_tenant_status_idx
    ON crm_integration_outbox (tenant_id, dispatch_status, created_at DESC);

-- Expired claims recovery index — partial index on CLAIMED events whose claim has expired
CREATE INDEX crm_integration_outbox_expired_claim_idx
    ON crm_integration_outbox (claim_expires_at)
    WHERE dispatch_status = 'CLAIMED';

-- ============================================================
-- DDL — crm_integration_decisions (human confirmation idempotency)
-- ============================================================
CREATE TABLE crm_integration_decisions (
    id                      UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    integration_request_id  UUID NOT NULL,
    actor_id                UUID NOT NULL,
    decision                VARCHAR(20) NOT NULL,
    idempotency_key         VARCHAR(200) NOT NULL,
    request_fingerprint     VARCHAR(500) NOT NULL,
    expected_entity_version BIGINT NOT NULL CHECK (expected_entity_version >= 0),
    correlation_id          VARCHAR(160) NOT NULL,
    decision_status         VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    command_reference       VARCHAR(500),
    error_code              VARCHAR(120),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at            TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_crm_integration_decisions PRIMARY KEY (id),
    CONSTRAINT fk_crm_integration_decisions_request FOREIGN KEY (tenant_id, integration_request_id)
        REFERENCES crm_integration_requests (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT crm_integration_decision_ck CHECK (
        decision IN ('CONFIRM', 'REJECT')
    ),
    CONSTRAINT crm_integration_decision_status_ck CHECK (
        decision_status IN ('PENDING','CONFIRMED','REJECTED','EXECUTING','EXECUTED','EXECUTION_REJECTED','CONFLICT')
    ),
    -- Terminal decision states require completed_at NOT NULL:
    --   REJECTED, EXECUTED, EXECUTION_REJECTED, CONFLICT
    -- Non-terminal decision states require completed_at NULL:
    --   PENDING, CONFIRMED, EXECUTING
    CONSTRAINT crm_integration_decision_terminal_ck CHECK (
        (decision_status NOT IN ('REJECTED','EXECUTED','EXECUTION_REJECTED','CONFLICT'))
        OR (completed_at IS NOT NULL)
    ),
    CONSTRAINT crm_integration_decision_non_terminal_ck CHECK (
        (decision_status IN ('REJECTED','EXECUTED','EXECUTION_REJECTED','CONFLICT'))
        OR (completed_at IS NULL)
    ),
    CONSTRAINT crm_integration_decision_uq
        UNIQUE (tenant_id, integration_request_id, idempotency_key)
);

CREATE INDEX crm_integration_decisions_tenant_request_idx
    ON crm_integration_decisions (tenant_id, integration_request_id, created_at DESC);

CREATE INDEX crm_integration_decisions_tenant_status_idx
    ON crm_integration_decisions (tenant_id, decision_status, created_at DESC);

-- ============================================================
-- DML — seed 3 CRM-009 capabilities (deterministic, fail-closed)
-- ============================================================
INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
VALUES ('a0000009-0000-0000-0000-000000000901', 'CRM.WORKFLOW.EXECUTE',
        'Execute CRM Workflows', 'Dispatch and inspect governed CRM workflow requests',
        'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
VALUES ('a0000009-0000-0000-0000-000000000902', 'CRM.AI.READ',
        'Read CRM AI Insights', 'Request governed advisory CRM AI outputs',
        'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
VALUES ('a0000009-0000-0000-0000-000000000903', 'CRM.AI.CONFIRM',
        'Confirm CRM AI Recommendations', 'Accept or reject AI-generated recommendations with human confirmation',
        'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ============================================================
-- POSTCONDITIONS
-- ============================================================
DO $postcondition$
DECLARE
    table_exists             INTEGER;
    outbox_exists            INTEGER;
    decisions_exists         INTEGER;
    payload_type             TEXT;
    payload_udt              TEXT;
    result_type              TEXT;
    result_udt               TEXT;
    version_type             TEXT;
    version_nullable         TEXT;
    version_default          TEXT;
    claim_token_type         TEXT;
    claim_token_nullable     TEXT;
    requested_locale_type    TEXT;
    requested_locale_nullable TEXT;
    expected_indexes         INTEGER;
    cap_count                INTEGER;
    cap_workflow             INTEGER;
    cap_ai_read              INTEGER;
    cap_ai_confirm           INTEGER;
BEGIN
    SELECT COUNT(*) INTO table_exists
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_name = 'crm_integration_requests';
    IF table_exists <> 1 THEN
        RAISE EXCEPTION 'V20260723.1 postcondition failed: crm_integration_requests not created';
    END IF;

    SELECT COUNT(*) INTO outbox_exists
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_name = 'crm_integration_outbox';
    IF outbox_exists <> 1 THEN
        RAISE EXCEPTION 'V20260723.1 postcondition failed: crm_integration_outbox not created';
    END IF;

    SELECT COUNT(*) INTO decisions_exists
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_name = 'crm_integration_decisions';
    IF decisions_exists <> 1 THEN
        RAISE EXCEPTION 'V20260723.1 postcondition failed: crm_integration_decisions not created';
    END IF;

    -- payload must be JSONB NOT NULL
    SELECT data_type, udt_name INTO payload_type, payload_udt
      FROM information_schema.columns
     WHERE table_schema = 'public'
       AND table_name = 'crm_integration_requests'
       AND column_name = 'payload';
    IF payload_type IS DISTINCT FROM 'jsonb' OR payload_udt IS DISTINCT FROM 'jsonb' THEN
        RAISE EXCEPTION 'V20260723.1 postcondition failed: payload data_type=% udt_name=% (expected jsonb/jsonb)', payload_type, payload_udt;
    END IF;

    -- result_payload must be JSONB (nullable)
    SELECT data_type, udt_name INTO result_type, result_udt
      FROM information_schema.columns
     WHERE table_schema = 'public'
       AND table_name = 'crm_integration_requests'
       AND column_name = 'result_payload';
    IF result_type IS DISTINCT FROM 'jsonb' OR result_udt IS DISTINCT FROM 'jsonb' THEN
        RAISE EXCEPTION 'V20260723.1 postcondition failed: result_payload data_type=% udt_name=% (expected jsonb/jsonb)', result_type, result_udt;
    END IF;

    -- version column must be BIGINT NOT NULL DEFAULT 0
    SELECT data_type, is_nullable, column_default INTO version_type, version_nullable, version_default
      FROM information_schema.columns
     WHERE table_schema = 'public'
       AND table_name = 'crm_integration_requests'
       AND column_name = 'version';
    IF version_type IS DISTINCT FROM 'bigint' OR version_nullable IS DISTINCT FROM 'NO' THEN
        RAISE EXCEPTION 'V20260723.1 postcondition failed: version data_type=% is_nullable=% (expected bigint/NO)', version_type, version_nullable;
    END IF;
    IF version_default IS DISTINCT FROM '0' THEN
        RAISE EXCEPTION 'V20260723.1 postcondition failed: version column_default=% (expected 0)', version_default;
    END IF;

    -- claim_token must be UUID and nullable
    SELECT data_type, is_nullable INTO claim_token_type, claim_token_nullable
      FROM information_schema.columns
     WHERE table_schema = 'public'
       AND table_name = 'crm_integration_outbox'
       AND column_name = 'claim_token';
    IF claim_token_type IS DISTINCT FROM 'uuid' OR claim_token_nullable IS DISTINCT FROM 'YES' THEN
        RAISE EXCEPTION 'V20260723.1 postcondition failed: claim_token data_type=% is_nullable=% (expected uuid/YES)', claim_token_type, claim_token_nullable;
    END IF;

    -- requested_locale must be VARCHAR NOT NULL
    SELECT data_type, is_nullable INTO requested_locale_type, requested_locale_nullable
      FROM information_schema.columns
     WHERE table_schema = 'public'
       AND table_name = 'crm_integration_requests'
       AND column_name = 'requested_locale';
    IF requested_locale_type IS DISTINCT FROM 'character varying' OR requested_locale_nullable IS DISTINCT FROM 'NO' THEN
        RAISE EXCEPTION 'V20260723.1 postcondition failed: requested_locale data_type=% is_nullable=% (expected character varying/NO)', requested_locale_type, requested_locale_nullable;
    END IF;

    -- All 8 indexes must exist (3 request + 4 outbox + 1 decisions)
    SELECT COUNT(*) INTO expected_indexes
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND tablename IN ('crm_integration_requests','crm_integration_outbox','crm_integration_decisions')
       AND indexname IN (
           'crm_integration_tenant_status_idx',
           'crm_integration_correlation_idx',
           'crm_integration_tenant_entity_idx',
           'crm_integration_outbox_claimable_idx',
           'crm_integration_outbox_retry_idx',
           'crm_integration_outbox_tenant_status_idx',
           'crm_integration_outbox_expired_claim_idx',
           'crm_integration_decisions_tenant_request_idx',
           'crm_integration_decisions_tenant_status_idx'
       );
    IF expected_indexes <> 9 THEN
        RAISE EXCEPTION 'V20260723.1 postcondition failed: expected 9 indexes, found %', expected_indexes;
    END IF;

    -- 3 CRM-009 capabilities must be seeded with EXACT tuple match
    SELECT COUNT(*) INTO cap_count
      FROM access_capabilities
     WHERE code IN ('CRM.WORKFLOW.EXECUTE', 'CRM.AI.READ', 'CRM.AI.CONFIRM')
       AND status = 'ACTIVE'
       AND id IN ('a0000009-0000-0000-0000-000000000901',
                  'a0000009-0000-0000-0000-000000000902',
                  'a0000009-0000-0000-0000-000000000903');
    IF cap_count <> 3 THEN
        RAISE EXCEPTION 'V20260723.1 postcondition failed: expected 3 CRM-009 capabilities, found %', cap_count;
    END IF;

    SELECT COUNT(*) INTO cap_workflow
      FROM access_capabilities
     WHERE id = 'a0000009-0000-0000-0000-000000000901'
       AND code = 'CRM.WORKFLOW.EXECUTE'
       AND name = 'Execute CRM Workflows'
       AND description = 'Dispatch and inspect governed CRM workflow requests'
       AND status = 'ACTIVE';
    IF cap_workflow <> 1 THEN
        RAISE EXCEPTION 'V20260723.1 postcondition failed: CRM.WORKFLOW.EXECUTE tuple mismatch (found %)', cap_workflow;
    END IF;

    SELECT COUNT(*) INTO cap_ai_read
      FROM access_capabilities
     WHERE id = 'a0000009-0000-0000-0000-000000000902'
       AND code = 'CRM.AI.READ'
       AND name = 'Read CRM AI Insights'
       AND description = 'Request governed advisory CRM AI outputs'
       AND status = 'ACTIVE';
    IF cap_ai_read <> 1 THEN
        RAISE EXCEPTION 'V20260723.1 postcondition failed: CRM.AI.READ tuple mismatch (found %)', cap_ai_read;
    END IF;

    SELECT COUNT(*) INTO cap_ai_confirm
      FROM access_capabilities
     WHERE id = 'a0000009-0000-0000-0000-000000000903'
       AND code = 'CRM.AI.CONFIRM'
       AND name = 'Confirm CRM AI Recommendations'
       AND description = 'Accept or reject AI-generated recommendations with human confirmation'
       AND status = 'ACTIVE';
    IF cap_ai_confirm <> 1 THEN
        RAISE EXCEPTION 'V20260723.1 postcondition failed: CRM.AI.CONFIRM tuple mismatch (found %)', cap_ai_confirm;
    END IF;
END
$postcondition$;
