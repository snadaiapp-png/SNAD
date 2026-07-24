-- ============================================================
-- SNAD Platform — CRM-009 — V20260724.1
-- Create Command Execution Ledger + Event-Type-Filtered Claim Index
-- ------------------------------------------------------------
-- Forward-only. Fail-closed. Tenant-scoped. PostgreSQL 16 native.
--
-- Purpose:
--   1. Persist a durable ledger row per confirmed-command execution
--      so crash recovery can query the CRM command result by
--      idempotency key (decisionId) and resume finalization without
--      re-executing the side effect.
--   2. Add a partial index on (event_type, dispatch_status,
--      next_attempt_at, created_at) so each worker can claim only
--      the event types it owns — AI worker never claims command
--      events, command worker never claims AI events.
-- ============================================================

-- ============================================================
-- PRECONDITIONS
-- ============================================================
DO $precondition$
DECLARE
    ledger_count          INTEGER;
    conflicting_index      INTEGER;
BEGIN
    SELECT COUNT(*) INTO ledger_count
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_name = 'crm_integration_command_executions';
    IF ledger_count <> 0 THEN
        RAISE EXCEPTION 'V20260724.1 precondition failed: crm_integration_command_executions already exists';
    END IF;

    SELECT COUNT(*) INTO conflicting_index
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND indexname = 'crm_integration_outbox_event_claim_idx';
    IF conflicting_index <> 0 THEN
        RAISE EXCEPTION 'V20260724.1 precondition failed: crm_integration_outbox_event_claim_idx already exists';
    END IF;
END
$precondition$;

-- ============================================================
-- DDL — crm_integration_command_executions
-- ============================================================
-- One row per attempted execution of a confirmed recommendation.
-- UNIQUE(tenant_id, decision_id) ensures at most one ledger row per
-- decision — the worker creates the ledger before invoking the CRM
-- command and updates it after the command returns. On crash
-- recovery, the worker reads the ledger to determine whether the
-- command was already executed.
CREATE TABLE crm_integration_command_executions (
    id                      UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    decision_id             UUID NOT NULL,
    integration_request_id  UUID NOT NULL,
    action_code             VARCHAR(80) NOT NULL,
    execution_status        VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    idempotency_key         VARCHAR(200) NOT NULL,
    attempt_count           INTEGER NOT NULL DEFAULT 0,
    command_reference       VARCHAR(500),
    result_payload          JSONB,
    error_code              VARCHAR(120),
    claim_token             UUID,
    started_at              TIMESTAMPTZ,
    completed_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_crm_integration_command_executions PRIMARY KEY (id),
    CONSTRAINT fk_command_executions_request FOREIGN KEY (tenant_id, integration_request_id)
        REFERENCES crm_integration_requests (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT crm_command_executions_status_ck CHECK (
        execution_status IN ('PENDING','EXECUTING','EXECUTED','EXECUTION_REJECTED','UNKNOWN_OUTCOME')
    ),
    -- Terminal execution states require completed_at NOT NULL
    CONSTRAINT crm_command_executions_terminal_ck CHECK (
        (execution_status NOT IN ('EXECUTED','EXECUTION_REJECTED','UNKNOWN_OUTCOME'))
        OR (completed_at IS NOT NULL)
    ),
    CONSTRAINT crm_command_executions_non_terminal_ck CHECK (
        (execution_status IN ('EXECUTED','EXECUTION_REJECTED','UNKNOWN_OUTCOME'))
        OR (completed_at IS NULL)
    ),
    -- At most one ledger row per decision
    CONSTRAINT crm_command_executions_decision_uq
        UNIQUE (tenant_id, decision_id),
    -- Idempotency: at most one row per idempotency key (which is decisionId-derived)
    CONSTRAINT crm_command_executions_idempotency_uq
        UNIQUE (tenant_id, idempotency_key)
);

CREATE INDEX crm_integration_command_executions_tenant_status_idx
    ON crm_integration_command_executions (tenant_id, execution_status, created_at DESC);

CREATE INDEX crm_integration_command_executions_request_idx
    ON crm_integration_command_executions (tenant_id, integration_request_id, created_at DESC);

-- ============================================================
-- Index — Event-Type-Filtered Claim
-- ============================================================
-- Allows each worker to claim only the event types it owns:
--   AI worker  → AI_REQUEST_DISPATCH
--   Cmd worker → CONFIRMED_COMMAND_EXECUTION
--   Wf worker  → WORKFLOW_DISPATCH (future)
-- A worker never claims an event it cannot handle, so an event is
-- never left in CLAIMED by the wrong worker.
CREATE INDEX crm_integration_outbox_event_claim_idx
    ON crm_integration_outbox (
        event_type,
        dispatch_status,
        next_attempt_at,
        created_at
    )
    WHERE dispatch_status IN ('PENDING', 'RETRY_WAIT', 'CLAIMED');

-- ============================================================
-- POSTCONDITIONS
-- ============================================================
DO $postcondition$
DECLARE
    table_exists             INTEGER;
    decision_uq_count        INTEGER;
    idempotency_uq_count     INTEGER;
    index_count              INTEGER;
    status_ck_count          INTEGER;
    terminal_ck_count        INTEGER;
BEGIN
    SELECT COUNT(*) INTO table_exists
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_name = 'crm_integration_command_executions';
    IF table_exists <> 1 THEN
        RAISE EXCEPTION 'V20260724.1 postcondition failed: crm_integration_command_executions not created';
    END IF;

    SELECT COUNT(*) INTO decision_uq_count
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND tablename = 'crm_integration_command_executions'
       AND indexname = 'crm_command_executions_decision_uq';
    IF decision_uq_count <> 1 THEN
        RAISE EXCEPTION 'V20260724.1 postcondition failed: decision unique index missing';
    END IF;

    SELECT COUNT(*) INTO idempotency_uq_count
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND tablename = 'crm_integration_command_executions'
       AND indexname = 'crm_command_executions_idempotency_uq';
    IF idempotency_uq_count <> 1 THEN
        RAISE EXCEPTION 'V20260724.1 postcondition failed: idempotency unique index missing';
    END IF;

    SELECT COUNT(*) INTO index_count
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND indexname = 'crm_integration_outbox_event_claim_idx';
    IF index_count <> 1 THEN
        RAISE EXCEPTION 'V20260724.1 postcondition failed: event claim index missing';
    END IF;

    SELECT COUNT(*) INTO status_ck_count
      FROM information_schema.check_constraints
     WHERE constraint_schema = 'public'
       AND constraint_name = 'crm_command_executions_status_ck';
    IF status_ck_count <> 1 THEN
        RAISE EXCEPTION 'V20260724.1 postcondition failed: status check constraint missing';
    END IF;

    SELECT COUNT(*) INTO terminal_ck_count
      FROM information_schema.check_constraints
     WHERE constraint_schema = 'public'
       AND constraint_name = 'crm_command_executions_terminal_ck';
    IF terminal_ck_count <> 1 THEN
        RAISE EXCEPTION 'V20260724.1 postcondition failed: terminal check constraint missing';
    END IF;
END
$postcondition$;
