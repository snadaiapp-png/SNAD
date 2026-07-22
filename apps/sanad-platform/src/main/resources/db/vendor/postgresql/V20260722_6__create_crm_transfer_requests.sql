-- ============================================================
-- SNAD Platform — CRM-008B Foundation — V20260722.6
-- Create CRM Transfer Requests + Steps
-- ------------------------------------------------------------
-- Forward-only. Fail-closed. Tenant-scoped. Composite FKs.
-- JSONB for record_ids (batch transfers). Single-approver-per-step
-- unique index.
-- ============================================================

-- ============================================================
-- PRECONDITIONS
-- ============================================================
DO $precondition$
DECLARE
    target_table_count INTEGER;
    conflicting_index  INTEGER;
    failed_history     INTEGER;
    assignments_present INTEGER;
BEGIN
    -- crm_assignments must exist (with new columns from V20260722.5)
    SELECT COUNT(*) INTO assignments_present
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_name = 'crm_assignments';
    IF assignments_present <> 1 THEN
        RAISE EXCEPTION 'V20260722.6 precondition failed: crm_assignments must exist';
    END IF;

    -- Verify V20260722.5 actually applied (new columns exist)
    PERFORM 1
      FROM information_schema.columns
     WHERE table_schema = 'public'
       AND table_name = 'crm_assignments'
       AND column_name = 'record_type';
    IF NOT FOUND THEN
        RAISE EXCEPTION
            'V20260722.6 precondition failed: crm_assignments.record_type missing — V20260722.5 not applied';
    END IF;

    SELECT COUNT(*)
      INTO target_table_count
      FROM information_schema.tables
     WHERE table_schema = 'public'
       AND table_name IN ('crm_transfer_requests','crm_transfer_steps');
    IF target_table_count <> 0 THEN
        RAISE EXCEPTION
            'V20260722.6 precondition failed: % of 2 target tables already exist (expected 0)',
            target_table_count;
    END IF;

    SELECT COUNT(*)
      INTO conflicting_index
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND indexname IN (
           'uk_transfer_requests_tenant_id',
           'idx_transfer_requests_tenant_state',
           'idx_transfer_requests_tenant_requester',
           'idx_transfer_requests_tenant_proposed',
           'uk_transfer_steps_request_step',
           'idx_transfer_steps_approver'
       );
    IF conflicting_index <> 0 THEN
        RAISE EXCEPTION
            'V20260722.6 precondition failed: % conflicting indexes already exist',
            conflicting_index;
    END IF;

    SELECT COUNT(*)
      INTO failed_history
      FROM flyway_schema_history
     WHERE success = FALSE;
    IF failed_history > 0 THEN
        RAISE EXCEPTION
            'V20260722.6 refuses to apply over % failed Flyway history rows',
            failed_history;
    END IF;
END
$precondition$;

-- ============================================================
-- DDL
-- ============================================================
CREATE TABLE crm_transfer_requests (
    id                          UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    record_type                 VARCHAR(20) NOT NULL,
    record_ids                  JSONB NOT NULL DEFAULT '[]'::jsonb,
    requester_user_id           UUID NOT NULL,
    current_owner_user_id       UUID,
    proposed_owner_user_id      UUID,
    proposed_owner_team_id      UUID,
    transfer_type               VARCHAR(20) NOT NULL DEFAULT 'PERMANENT',
    temporary_end_date          TIMESTAMP WITH TIME ZONE,
    reason                      VARCHAR(500) NOT NULL,
    policy                      VARCHAR(20) NOT NULL DEFAULT 'SINGLE_APPROVER',
    state                       VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    current_approval_step       INTEGER,
    workflow_run_id             UUID,
    executed_at                 TIMESTAMP WITH TIME ZONE,
    executed_by_user_id         UUID,
    failure_reason              VARCHAR(500),
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_transfer_requests PRIMARY KEY (id),
    CONSTRAINT uk_transfer_requests_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_transfer_requests_tenants FOREIGN KEY (tenant_id)
        REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT ck_transfer_requests_record_type CHECK (record_type IN (
        'ACCOUNT','LEAD','OPPORTUNITY'
    )),
    CONSTRAINT ck_transfer_requests_type CHECK (transfer_type IN ('PERMANENT','TEMPORARY')),
    CONSTRAINT ck_transfer_requests_policy CHECK (policy IN (
        'SINGLE_APPROVER','MULTI_APPROVER','NO_APPROVAL_REQUIRED'
    )),
    CONSTRAINT ck_transfer_requests_state CHECK (state IN (
        'DRAFT','SUBMITTED','UNDER_REVIEW','APPROVED','REJECTED',
        'CANCELLED','COMPLETED','FAILED'
    )),
    CONSTRAINT ck_transfer_requests_temp_dates CHECK (
        (transfer_type = 'PERMANENT' AND temporary_end_date IS NULL)
        OR (transfer_type = 'TEMPORARY' AND temporary_end_date IS NOT NULL)
    ),
    CONSTRAINT ck_transfer_requests_sod CHECK (
        policy = 'NO_APPROVAL_REQUIRED'
        OR proposed_owner_user_id IS NULL
        OR requester_user_id <> proposed_owner_user_id
    ),
    CONSTRAINT ck_transfer_requests_record_ids CHECK (jsonb_typeof(record_ids) = 'array'),
    CONSTRAINT ck_transfer_requests_reason_not_empty CHECK (LENGTH(TRIM(reason)) > 0)
);

CREATE INDEX idx_transfer_requests_tenant_state
    ON crm_transfer_requests (tenant_id, state, updated_at DESC);

CREATE INDEX idx_transfer_requests_tenant_requester
    ON crm_transfer_requests (tenant_id, requester_user_id, state);

CREATE INDEX idx_transfer_requests_tenant_proposed
    ON crm_transfer_requests (tenant_id, proposed_owner_user_id, state);

CREATE TABLE crm_transfer_steps (
    id                  UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    transfer_request_id UUID NOT NULL,
    step_number         INTEGER NOT NULL,
    approver_user_id    UUID NOT NULL,
    decision            VARCHAR(20),
    decided_at          TIMESTAMP WITH TIME ZONE,
    comment             VARCHAR(1000),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_transfer_steps PRIMARY KEY (id),
    CONSTRAINT fk_transfer_steps_tenants FOREIGN KEY (tenant_id)
        REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT fk_transfer_steps_requests FOREIGN KEY (tenant_id, transfer_request_id)
        REFERENCES crm_transfer_requests(tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_transfer_steps_decision CHECK (decision IS NULL OR decision IN (
        'APPROVED','REJECTED','EXPIRED','CANCELLED'
    )),
    CONSTRAINT ck_transfer_steps_step_number CHECK (step_number >= 1)
);

CREATE UNIQUE INDEX uk_transfer_steps_request_step
    ON crm_transfer_steps (tenant_id, transfer_request_id, step_number);

CREATE INDEX idx_transfer_steps_approver
    ON crm_transfer_steps (tenant_id, approver_user_id, decided_at DESC);

-- ============================================================
-- POSTCONDITIONS
-- ============================================================
DO $postcondition$
DECLARE
    requests_table_exists       INTEGER;
    steps_table_exists          INTEGER;
    record_ids_data_type        TEXT;
    record_ids_udt_name         TEXT;
    record_ids_nullable         TEXT;
    expected_indexes            INTEGER;
BEGIN
    SELECT COUNT(*) INTO requests_table_exists
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_name = 'crm_transfer_requests';
    IF requests_table_exists <> 1 THEN
        RAISE EXCEPTION 'V20260722.6 postcondition failed: crm_transfer_requests not created';
    END IF;

    SELECT COUNT(*) INTO steps_table_exists
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_name = 'crm_transfer_steps';
    IF steps_table_exists <> 1 THEN
        RAISE EXCEPTION 'V20260722.6 postcondition failed: crm_transfer_steps not created';
    END IF;

    -- record_ids must be JSONB NOT NULL.
    -- PostgreSQL catalog records JSONB columns as data_type='jsonb' AND udt_name='jsonb'.
    SELECT data_type, udt_name, is_nullable
      INTO record_ids_data_type, record_ids_udt_name, record_ids_nullable
      FROM information_schema.columns
     WHERE table_schema = 'public'
       AND table_name = 'crm_transfer_requests'
       AND column_name = 'record_ids';

    IF record_ids_data_type IS DISTINCT FROM 'jsonb'
       OR record_ids_udt_name IS DISTINCT FROM 'jsonb' THEN
        RAISE EXCEPTION
            'V20260722.6 postcondition failed: crm_transfer_requests.record_ids data_type=% udt_name=% (expected jsonb/jsonb)',
            record_ids_data_type,
            record_ids_udt_name;
    END IF;

    IF record_ids_nullable IS DISTINCT FROM 'NO' THEN
        RAISE EXCEPTION
            'V20260722.6 postcondition failed: crm_transfer_requests.record_ids is_nullable=% (expected NO)',
            record_ids_nullable;
    END IF;

    SELECT COUNT(*) INTO expected_indexes
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND tablename IN ('crm_transfer_requests','crm_transfer_steps')
       AND indexname IN (
           'uk_transfer_requests_tenant_id',
           'idx_transfer_requests_tenant_state',
           'idx_transfer_requests_tenant_requester',
           'idx_transfer_requests_tenant_proposed',
           'uk_transfer_steps_request_step',
           'idx_transfer_steps_approver'
       );
    IF expected_indexes <> 6 THEN
        RAISE EXCEPTION
            'V20260722.6 postcondition failed: expected 6 indexes, found %',
            expected_indexes;
    END IF;
END
$postcondition$;
