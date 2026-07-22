-- ============================================================
-- SNAD Platform — CRM-008B Foundation — V20260722.5
-- Upgrade CRM Assignments + Create Ownership History
-- ------------------------------------------------------------
-- ALTER migration: predecessor schema (crm_assignments from
-- V20260717.6) MUST exist with subject_type, subject_id,
-- assigned_user_id columns. All target columns MUST be absent.
--
-- G1 BACKFILL (deterministic, fail-closed):
--   subject_type     → record_type
--   subject_id       → record_id
--   assigned_user_id → owner_user_id
--   owner_type       → 'USER' (G1 only had user assignments)
-- Any row that cannot be backfilled FAILS the entire migration.
-- ============================================================

-- ============================================================
-- PRECONDITIONS — verify exact predecessor schema
-- ============================================================
DO $precondition$
DECLARE
    assignments_exists         INTEGER;
    rules_exists               INTEGER;
    target_columns_count       INTEGER;
    expected_columns_count     INTEGER;
    failed_history             INTEGER;
    subject_type_type          TEXT;
    subject_id_type            TEXT;
    assigned_user_id_type      TEXT;
    conflicting_index          INTEGER;
    ownership_history_exists   INTEGER;
BEGIN
    -- crm_assignments must exist (from V20260717.6)
    SELECT COUNT(*) INTO assignments_exists
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_name = 'crm_assignments';
    IF assignments_exists <> 1 THEN
        RAISE EXCEPTION
            'V20260722.5 precondition failed: crm_assignments must exist (V20260717.6 not applied)';
    END IF;

    -- crm_assignment_rules must exist (from V20260722.4)
    SELECT COUNT(*) INTO rules_exists
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_name = 'crm_assignment_rules';
    IF rules_exists <> 1 THEN
        RAISE EXCEPTION
            'V20260722.5 precondition failed: crm_assignment_rules must exist (V20260722.4 not applied)';
    END IF;

    -- Predecessor columns must have exact types
    SELECT data_type INTO subject_type_type
      FROM information_schema.columns
     WHERE table_schema = 'public' AND table_name = 'crm_assignments' AND column_name = 'subject_type';
    IF subject_type_type IS NULL OR subject_type_type <> 'character varying' THEN
        RAISE EXCEPTION
            'V20260722.5 precondition failed: crm_assignments.subject_type has wrong type=% (expected character varying)',
            subject_type_type;
    END IF;

    SELECT udt_name INTO subject_id_type
      FROM information_schema.columns
     WHERE table_schema = 'public' AND table_name = 'crm_assignments' AND column_name = 'subject_id';
    IF subject_id_type IS NULL OR subject_id_type <> 'uuid' THEN
        RAISE EXCEPTION
            'V20260722.5 precondition failed: crm_assignments.subject_id has wrong udt=% (expected uuid)',
            subject_id_type;
    END IF;

    SELECT udt_name INTO assigned_user_id_type
      FROM information_schema.columns
     WHERE table_schema = 'public' AND table_name = 'crm_assignments' AND column_name = 'assigned_user_id';
    IF assigned_user_id_type IS NULL OR assigned_user_id_type <> 'uuid' THEN
        RAISE EXCEPTION
            'V20260722.5 precondition failed: crm_assignments.assigned_user_id has wrong udt=% (expected uuid)',
            assigned_user_id_type;
    END IF;

    -- All target columns for this migration MUST be absent (no partial state)
    SELECT COUNT(*) INTO target_columns_count
      FROM information_schema.columns
     WHERE table_schema = 'public'
       AND table_name = 'crm_assignments'
       AND column_name IN (
           'owner_type','owner_user_id','owner_team_id','owner_queue_id',
           'record_type','record_id','assigned_by_rule_id','assigned_by_user_id',
           'reason','correlation_id','workflow_result','effective_from','effective_to'
       );

    IF target_columns_count <> 0 THEN
        RAISE EXCEPTION
            'V20260722.5 precondition failed: % of 13 target columns already exist (expected 0) — partial state',
            target_columns_count;
    END IF;

    -- crm_ownership_history must be absent
    SELECT COUNT(*) INTO ownership_history_exists
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_name = 'crm_ownership_history';
    IF ownership_history_exists <> 0 THEN
        RAISE EXCEPTION
            'V20260722.5 precondition failed: crm_ownership_history already exists';
    END IF;

    -- No conflicting index names
    SELECT COUNT(*) INTO conflicting_index
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND tablename = 'crm_assignments'
       AND indexname IN (
           'idx_owr_assignments_record','idx_owr_assignments_owner_user',
           'idx_owr_assignments_owner_team','idx_owr_assignments_owner_queue',
           'idx_owr_assignments_correlation','uk_assignments_active_per_record',
           'uk_ownership_history_tenant_id',
           'idx_ownership_history_tenant_record_time',
           'idx_ownership_history_tenant_correlation',
           'idx_ownership_history_tenant_actor_time'
       );
    IF conflicting_index <> 0 THEN
        RAISE EXCEPTION
            'V20260722.5 precondition failed: % conflicting indexes already exist',
            conflicting_index;
    END IF;

    SELECT COUNT(*) INTO failed_history
      FROM flyway_schema_history
     WHERE success = FALSE;
    IF failed_history > 0 THEN
        RAISE EXCEPTION
            'V20260722.5 refuses to apply over % failed Flyway history rows',
            failed_history;
    END IF;
END
$precondition$;

-- ============================================================
-- DDL — ALTER crm_assignments (add new columns)
-- ============================================================
ALTER TABLE crm_assignments ADD COLUMN owner_type VARCHAR(10);
ALTER TABLE crm_assignments ADD COLUMN owner_user_id UUID;
ALTER TABLE crm_assignments ADD COLUMN owner_team_id UUID;
ALTER TABLE crm_assignments ADD COLUMN owner_queue_id UUID;
ALTER TABLE crm_assignments ADD COLUMN record_type VARCHAR(20);
ALTER TABLE crm_assignments ADD COLUMN record_id UUID;
ALTER TABLE crm_assignments ADD COLUMN assigned_by_rule_id UUID;
ALTER TABLE crm_assignments ADD COLUMN assigned_by_user_id UUID;
ALTER TABLE crm_assignments ADD COLUMN reason VARCHAR(100);
ALTER TABLE crm_assignments ADD COLUMN correlation_id UUID;
ALTER TABLE crm_assignments ADD COLUMN workflow_result JSONB;
ALTER TABLE crm_assignments ADD COLUMN effective_from TIMESTAMP WITH TIME ZONE;
ALTER TABLE crm_assignments ADD COLUMN effective_to TIMESTAMP WITH TIME ZONE;

-- CHECK constraints for the new columns
ALTER TABLE crm_assignments ADD CONSTRAINT ck_assignments_owner_type
    CHECK (owner_type IS NULL OR owner_type IN ('USER','TEAM','QUEUE'));

ALTER TABLE crm_assignments ADD CONSTRAINT ck_assignments_record_type_new
    CHECK (record_type IS NULL OR record_type IN (
        'ACCOUNT','CONTACT','LEAD','OPPORTUNITY','ACTIVITY','TASK'
    ));

ALTER TABLE crm_assignments ADD CONSTRAINT ck_assignments_workflow_result
    CHECK (workflow_result IS NULL OR jsonb_typeof(workflow_result) = 'object');

ALTER TABLE crm_assignments ADD CONSTRAINT ck_assignments_effective_dates
    CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_from <= effective_to);

-- FK to crm_assignment_rules (nullable — not all assignments come from rules)
ALTER TABLE crm_assignments ADD CONSTRAINT fk_assignments_rules
    FOREIGN KEY (tenant_id, assigned_by_rule_id)
    REFERENCES crm_assignment_rules(tenant_id, id) ON DELETE SET NULL;

-- Indexes for the new ownership model queries
CREATE INDEX idx_owr_assignments_record
    ON crm_assignments (tenant_id, record_type, record_id, status, effective_from DESC);

CREATE INDEX idx_owr_assignments_owner_user
    ON crm_assignments (tenant_id, owner_user_id, status);

CREATE INDEX idx_owr_assignments_owner_team
    ON crm_assignments (tenant_id, owner_team_id, status);

CREATE INDEX idx_owr_assignments_owner_queue
    ON crm_assignments (tenant_id, owner_queue_id, status);

CREATE INDEX idx_owr_assignments_correlation
    ON crm_assignments (tenant_id, correlation_id);

-- Single active assignment per (tenant, record_type, record_id)
CREATE UNIQUE INDEX uk_assignments_active_per_record
    ON crm_assignments (tenant_id, record_type, record_id)
    WHERE status = 'ACTIVE';

-- ============================================================
-- DDL — CREATE crm_ownership_history (new table)
-- ============================================================
CREATE TABLE crm_ownership_history (
    id                   UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id            UUID NOT NULL,
    record_type          VARCHAR(20) NOT NULL,
    record_id            UUID NOT NULL,
    from_owner_type      VARCHAR(10),
    from_owner_user_id   UUID,
    from_owner_team_id   UUID,
    from_owner_queue_id  UUID,
    to_owner_type        VARCHAR(10) NOT NULL,
    to_owner_user_id     UUID,
    to_owner_team_id     UUID,
    to_owner_queue_id    UUID,
    change_type          VARCHAR(30) NOT NULL,
    trigger_source       VARCHAR(30) NOT NULL,
    trigger_reference_id UUID,
    actor_user_id        UUID NOT NULL,
    reason               VARCHAR(500) NOT NULL,
    correlation_id       UUID NOT NULL DEFAULT gen_random_uuid(),
    effective_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    recorded_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ownership_history PRIMARY KEY (id),
    CONSTRAINT uk_ownership_history_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_ownership_history_tenants FOREIGN KEY (tenant_id)
        REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT ck_ownership_history_record_type CHECK (record_type IN (
        'ACCOUNT','CONTACT','LEAD','OPPORTUNITY','ACTIVITY','TASK'
    )),
    CONSTRAINT ck_ownership_history_change_type CHECK (change_type IN (
        'INITIAL','REASSIGN','TRANSFER','QUEUE_CLAIM','QUEUE_RELEASE',
        'TEMPORARY','RESTORE','BULK'
    )),
    CONSTRAINT ck_ownership_history_trigger_source CHECK (trigger_source IN (
        'MANUAL','RULE','TRANSFER_REQUEST','WORKFLOW','ABSENCE_POLICY'
    )),
    CONSTRAINT ck_ownership_history_to_owner_type CHECK (to_owner_type IN ('USER','TEAM','QUEUE')),
    CONSTRAINT ck_ownership_history_from_owner_type CHECK (
        from_owner_type IS NULL OR from_owner_type IN ('USER','TEAM','QUEUE')
    ),
    CONSTRAINT ck_ownership_history_reason_not_empty CHECK (LENGTH(TRIM(reason)) > 0)
);

CREATE INDEX idx_ownership_history_tenant_record_time
    ON crm_ownership_history (tenant_id, record_type, record_id, effective_at DESC);

CREATE INDEX idx_ownership_history_tenant_correlation
    ON crm_ownership_history (tenant_id, correlation_id);

CREATE INDEX idx_ownership_history_tenant_actor_time
    ON crm_ownership_history (tenant_id, actor_user_id, effective_at DESC);

-- ============================================================
-- G1 BACKFILL — deterministic, fail-closed
-- ============================================================
-- Map legacy G1 columns to the new CRM-008B ownership model.
-- Any row that cannot be backfilled FAILS the entire migration
-- (the postcondition assertion will raise).
DO $backfill$
DECLARE
    total_rows          INTEGER;
    backfilled_rows     INTEGER;
    unmappable_rows     INTEGER;
BEGIN
    SELECT COUNT(*) INTO total_rows FROM crm_assignments;

    -- Deterministic backfill: G1 subject_type values map 1:1 to record_type
    -- (ACCOUNT, CONTACT, LEAD, OPPORTUNITY, ACTIVITY, TASK).
    UPDATE crm_assignments
       SET record_type     = subject_type,
           record_id       = subject_id,
           owner_user_id   = assigned_user_id,
           owner_type      = 'USER',
           effective_from  = starts_at,
           effective_to    = ends_at
     WHERE subject_type IS NOT NULL
       AND subject_id IS NOT NULL
       AND assigned_user_id IS NOT NULL;

    GET DIAGNOSTICS backfilled_rows = ROW_COUNT;

    -- Any row that could not be backfilled is a data integrity violation.
    SELECT COUNT(*) INTO unmappable_rows
      FROM crm_assignments
     WHERE record_type IS NULL
        OR record_id IS NULL
        OR owner_user_id IS NULL
        OR owner_type IS NULL;

    IF unmappable_rows > 0 THEN
        RAISE EXCEPTION
            'V20260722.5 G1 backfill failed: % of % rows could not be mapped (NULL subject_type/subject_id/assigned_user_id)',
            unmappable_rows, total_rows;
    END IF;

    IF backfilled_rows <> total_rows THEN
        RAISE EXCEPTION
            'V20260722.5 G1 backfill failed: backfilled % rows but total is %',
            backfilled_rows, total_rows;
    END IF;
END
$backfill$;

-- ============================================================
-- POSTCONDITIONS — verify all columns, types, FKs, indexes, backfill
-- ============================================================
DO $postcondition$
DECLARE
    expected_columns            INTEGER;
    actual_columns              INTEGER;
    workflow_result_data_type   TEXT;
    workflow_result_udt_name    TEXT;
    workflow_result_nullable    TEXT;
    unmappable_rows             INTEGER;
    expected_indexes            INTEGER;
    history_table_count         INTEGER;
    history_columns             INTEGER;
BEGIN
    -- All 13 new columns must exist on crm_assignments
    SELECT COUNT(*) INTO actual_columns
      FROM information_schema.columns
     WHERE table_schema = 'public'
       AND table_name = 'crm_assignments'
       AND column_name IN (
           'owner_type','owner_user_id','owner_team_id','owner_queue_id',
           'record_type','record_id','assigned_by_rule_id','assigned_by_user_id',
           'reason','correlation_id','workflow_result','effective_from','effective_to'
       );
    IF actual_columns <> 13 THEN
        RAISE EXCEPTION
            'V20260722.5 postcondition failed: % of 13 new columns exist on crm_assignments',
            actual_columns;
    END IF;

    -- workflow_result must be JSONB (nullable).
    -- PostgreSQL catalog records JSONB columns as data_type='jsonb' AND udt_name='jsonb'.
    SELECT data_type, udt_name, is_nullable
      INTO workflow_result_data_type, workflow_result_udt_name, workflow_result_nullable
      FROM information_schema.columns
     WHERE table_schema = 'public'
       AND table_name = 'crm_assignments'
       AND column_name = 'workflow_result';

    IF workflow_result_data_type IS DISTINCT FROM 'jsonb'
       OR workflow_result_udt_name IS DISTINCT FROM 'jsonb' THEN
        RAISE EXCEPTION
            'V20260722.5 postcondition failed: crm_assignments.workflow_result data_type=% udt_name=% (expected jsonb/jsonb)',
            workflow_result_data_type,
            workflow_result_udt_name;
    END IF;

    -- workflow_result is intentionally nullable (NULL means "no workflow result yet").
    -- No is_nullable assertion here — only the type is the contract.

    -- G1 columns must still be present (preserved, not dropped)
    PERFORM 1
      FROM information_schema.columns
     WHERE table_schema = 'public'
       AND table_name = 'crm_assignments'
       AND column_name IN ('subject_type','subject_id','assigned_user_id','assignment_role','status','starts_at','ends_at');
    IF NOT FOUND THEN
        RAISE EXCEPTION
            'V20260722.5 postcondition failed: G1 columns missing from crm_assignments';
    END IF;

    -- G1 backfill: no NULLs in record_type, record_id, owner_user_id, owner_type
    SELECT COUNT(*) INTO unmappable_rows
      FROM crm_assignments
     WHERE record_type IS NULL
        OR record_id IS NULL
        OR owner_user_id IS NULL
        OR owner_type IS NULL;
    IF unmappable_rows > 0 THEN
        RAISE EXCEPTION
            'V20260722.5 postcondition failed: % rows have NULL record_type/record_id/owner_user_id/owner_type after backfill',
            unmappable_rows;
    END IF;

    -- All new indexes must exist (including partial unique)
    SELECT COUNT(*) INTO expected_indexes
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND tablename = 'crm_assignments'
       AND indexname IN (
           'idx_owr_assignments_record','idx_owr_assignments_owner_user',
           'idx_owr_assignments_owner_team','idx_owr_assignments_owner_queue',
           'idx_owr_assignments_correlation','uk_assignments_active_per_record'
       );
    IF expected_indexes <> 6 THEN
        RAISE EXCEPTION
            'V20260722.5 postcondition failed: expected 6 new indexes on crm_assignments, found %',
            expected_indexes;
    END IF;

    -- crm_ownership_history table and its 3 indexes must exist
    SELECT COUNT(*) INTO history_table_count
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_name = 'crm_ownership_history';
    IF history_table_count <> 1 THEN
        RAISE EXCEPTION 'V20260722.5 postcondition failed: crm_ownership_history not created';
    END IF;

    SELECT COUNT(*) INTO expected_indexes
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND tablename = 'crm_ownership_history'
       AND indexname IN (
           'uk_ownership_history_tenant_id',
           'idx_ownership_history_tenant_record_time',
           'idx_ownership_history_tenant_correlation',
           'idx_ownership_history_tenant_actor_time'
       );
    IF expected_indexes <> 4 THEN
        RAISE EXCEPTION
            'V20260722.5 postcondition failed: expected 4 indexes on crm_ownership_history, found %',
            expected_indexes;
    END IF;
END
$postcondition$;
