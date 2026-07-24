-- ============================================================
-- SNAD Platform — CRM-009 — V20260724.2
-- Create Command Artifacts Idempotency Table
-- ------------------------------------------------------------
-- Forward-only. Fail-closed. Tenant-scoped. PostgreSQL 16 native.
--
-- Purpose:
--   Enforce exactly-once artifact creation atomically at the DB level.
--   Each adapter inserts/reserves a row in this table inside the SAME
--   transaction as the CRM artifact creation (activity/task). If the
--   transaction commits, both the artifact and the idempotency row
--   are persisted. If it rolls back, neither is.
--
--   On crash recovery, the adapter queries this table by (tenant_id,
--   decision_id, action_code). If a row exists with a non-null
--   artifact_id, the original artifact is returned — no duplicate
--   is created.
--
--   This replaces the fragile "subject LIKE decisionId" pattern
--   which is not atomic and can produce duplicates under crash
--   recovery.
-- ============================================================

-- ============================================================
-- PRECONDITIONS
-- ============================================================
DO $precondition$
DECLARE
    table_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO table_count
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_name = 'crm_integration_command_artifacts';
    IF table_count <> 0 THEN
        RAISE EXCEPTION 'V20260724.2 precondition failed: crm_integration_command_artifacts already exists';
    END IF;
END
$precondition$;

-- ============================================================
-- DDL — crm_integration_command_artifacts
-- ============================================================
CREATE TABLE crm_integration_command_artifacts (
    id                      UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    decision_id             UUID NOT NULL,
    action_code             VARCHAR(80) NOT NULL,
    artifact_type           VARCHAR(80) NOT NULL,
    artifact_id             UUID NOT NULL,
    execution_status        VARCHAR(40) NOT NULL DEFAULT 'CREATED',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_crm_integration_command_artifacts PRIMARY KEY (id),
    CONSTRAINT crm_command_artifacts_decision_action_uq
        UNIQUE (tenant_id, decision_id, action_code),
    CONSTRAINT crm_command_artifacts_status_ck CHECK (
        execution_status IN ('CREATED', 'REVERSED')
    ),
    CONSTRAINT crm_command_artifacts_tenant_uq UNIQUE (tenant_id, id)
);

CREATE INDEX crm_integration_command_artifacts_tenant_decision_idx
    ON crm_integration_command_artifacts (tenant_id, decision_id);

CREATE INDEX crm_integration_command_artifacts_artifact_idx
    ON crm_integration_command_artifacts (tenant_id, artifact_type, artifact_id);

-- ============================================================
-- POSTCONDITIONS
-- ============================================================
DO $postcondition$
DECLARE
    table_exists      INTEGER;
    unique_count      INTEGER;
    index_count       INTEGER;
BEGIN
    SELECT COUNT(*) INTO table_exists
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_name = 'crm_integration_command_artifacts';
    IF table_exists <> 1 THEN
        RAISE EXCEPTION 'V20260724.2 postcondition failed: crm_integration_command_artifacts not created';
    END IF;

    SELECT COUNT(*) INTO unique_count
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND tablename = 'crm_integration_command_artifacts'
       AND indexname = 'crm_command_artifacts_decision_action_uq';
    IF unique_count <> 1 THEN
        RAISE EXCEPTION 'V20260724.2 postcondition failed: decision_action unique index missing';
    END IF;

    SELECT COUNT(*) INTO index_count
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND tablename = 'crm_integration_command_artifacts'
       AND indexname IN (
           'crm_integration_command_artifacts_tenant_decision_idx',
           'crm_integration_command_artifacts_artifact_idx'
       );
    IF index_count <> 2 THEN
        RAISE EXCEPTION 'V20260724.2 postcondition failed: expected 2 indexes, found %', index_count;
    END IF;
END
$postcondition$;
