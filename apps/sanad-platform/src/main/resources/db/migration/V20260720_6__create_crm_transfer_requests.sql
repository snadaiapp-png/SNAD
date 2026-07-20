-- ============================================================================
-- SANAD CRM-008 — V20260720_6 — Create CRM Transfer Requests
-- ============================================================================
-- Forward-only. Idempotent. Tenant-scoped. State machine enforced at app layer.
-- REVIEW ONLY — DO NOT EXECUTE in this design phase.
-- ============================================================================

BEGIN;

-- ----------------------------------------------------------------------------
-- 1. Transfer Requests
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_transfer_requests (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    record_type                 VARCHAR(20) NOT NULL,
    -- record_ids is stored as JSONB array (composite type would require CREATE TYPE)
    record_ids                  JSONB NOT NULL,
    requester_user_id           UUID NOT NULL,
    current_owner_user_id       UUID,
    proposed_owner_user_id      UUID,
    proposed_owner_team_id      UUID,
    transfer_type               VARCHAR(20) NOT NULL DEFAULT 'PERMANENT',
    temporary_end_date          TIMESTAMPTZ,
    reason                      VARCHAR(500) NOT NULL,
    policy                      VARCHAR(20) NOT NULL DEFAULT 'SINGLE_APPROVER',
    state                       VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    current_approval_step       INTEGER,
    workflow_run_id             UUID,
    executed_at                 TIMESTAMPTZ,
    executed_by_user_id         UUID,
    failure_reason              VARCHAR(500),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
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
    -- Separation of Duties: when policy requires approval, requester ≠ proposed owner
    CONSTRAINT ck_transfer_requests_sod CHECK (
        policy = 'NO_APPROVAL_REQUIRED'
        OR proposed_owner_user_id IS NULL
        OR requester_user_id <> proposed_owner_user_id
    )
);

CREATE INDEX IF NOT EXISTS idx_transfer_requests_tenant_state
    ON crm_transfer_requests (tenant_id, state, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_transfer_requests_tenant_requester
    ON crm_transfer_requests (tenant_id, requester_user_id, state);

CREATE INDEX IF NOT EXISTS idx_transfer_requests_tenant_proposed
    ON crm_transfer_requests (tenant_id, proposed_owner_user_id, state);

CREATE INDEX IF NOT EXISTS idx_transfer_requests_tenant_workflow
    ON crm_transfer_requests (tenant_id, workflow_run_id)
    WHERE workflow_run_id IS NOT NULL;

-- ----------------------------------------------------------------------------
-- 2. Transfer Steps (one row per approval step)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_transfer_steps (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    transfer_request_id UUID NOT NULL,
    step_number         INTEGER NOT NULL,
    approver_user_id    UUID NOT NULL,
    decision            VARCHAR(20),
    decided_at          TIMESTAMPTZ,
    comment             VARCHAR(1000),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_transfer_steps_tenants FOREIGN KEY (tenant_id)
        REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT fk_transfer_steps_requests FOREIGN KEY (tenant_id, transfer_request_id)
        REFERENCES crm_transfer_requests(tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_transfer_steps_decision CHECK (decision IS NULL OR decision IN (
        'APPROVED','REJECTED','EXPIRED','CANCELLED'
    ))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_transfer_steps_request_step
    ON crm_transfer_steps (tenant_id, transfer_request_id, step_number);

CREATE INDEX IF NOT EXISTS idx_transfer_steps_approver
    ON crm_transfer_steps (tenant_id, approver_user_id, decided_at DESC);

COMMIT;

-- Validation:
-- SELECT count(*) FROM information_schema.tables WHERE table_name IN
--   ('crm_transfer_requests','crm_transfer_steps');
-- Expected: 2
