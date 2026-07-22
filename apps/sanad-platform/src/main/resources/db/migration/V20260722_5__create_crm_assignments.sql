-- ============================================================================
-- SANAD CRM-008B — V20260722_5 — Extend CRM Assignments + Create Ownership History
-- ============================================================================
-- ALTER migration: crm_assignments already exists from V20260717_6 (CRM-G1).
-- This migration ADDS columns for the new ownership model and creates
-- crm_ownership_history as a new table.
--
-- G1 crm_assignments uses: subject_type, subject_id, assigned_user_id
-- CRM-008B adds: owner_type, owner_user_id, owner_team_id, owner_queue_id,
--                record_type, record_id, assigned_by_rule_id, assigned_by_user_id,
--                reason, correlation_id, workflow_result, effective_from, effective_to
-- ============================================================================

BEGIN;

-- -----------------------------------------------------------------------
-- 1. ALTER existing crm_assignments table (from V20260717_6)
-- -----------------------------------------------------------------------
-- Add new columns for the CRM-008B ownership model alongside existing G1 columns.
-- The G1 columns (subject_type, subject_id, assigned_user_id) are preserved
-- for backward compatibility. A data backfill is NOT performed here — it
-- will be done by the application layer or a separate reconciliation migration.

ALTER TABLE crm_assignments ADD COLUMN IF NOT EXISTS owner_type VARCHAR(10);
ALTER TABLE crm_assignments ADD COLUMN IF NOT EXISTS owner_user_id UUID;
ALTER TABLE crm_assignments ADD COLUMN IF NOT EXISTS owner_team_id UUID;
ALTER TABLE crm_assignments ADD COLUMN IF NOT EXISTS owner_queue_id UUID;
ALTER TABLE crm_assignments ADD COLUMN IF NOT EXISTS record_type VARCHAR(20);
ALTER TABLE crm_assignments ADD COLUMN IF NOT EXISTS record_id UUID;
ALTER TABLE crm_assignments ADD COLUMN IF NOT EXISTS assigned_by_rule_id UUID;
ALTER TABLE crm_assignments ADD COLUMN IF NOT EXISTS assigned_by_user_id UUID;
ALTER TABLE crm_assignments ADD COLUMN IF NOT EXISTS reason VARCHAR(100);
ALTER TABLE crm_assignments ADD COLUMN IF NOT EXISTS correlation_id UUID;
ALTER TABLE crm_assignments ADD COLUMN IF NOT EXISTS workflow_result TEXT;
ALTER TABLE crm_assignments ADD COLUMN IF NOT EXISTS effective_from TIMESTAMP WITH TIME ZONE;
ALTER TABLE crm_assignments ADD COLUMN IF NOT EXISTS effective_to TIMESTAMP WITH TIME ZONE;

-- Add CHECK constraints for the new columns (as separate constraints to avoid H2 issues)
ALTER TABLE crm_assignments ADD CONSTRAINT ck_assignments_owner_type
    CHECK (owner_type IS NULL OR owner_type IN ('USER','TEAM','QUEUE'));
ALTER TABLE crm_assignments ADD CONSTRAINT ck_assignments_record_type_new
    CHECK (record_type IS NULL OR record_type IN (
        'ACCOUNT','CONTACT','LEAD','OPPORTUNITY','ACTIVITY','TASK'
    ));

-- Add FK to crm_assignment_rules (nullable — not all assignments come from rules)
ALTER TABLE crm_assignments ADD CONSTRAINT fk_assignments_rules
    FOREIGN KEY (tenant_id, assigned_by_rule_id)
    REFERENCES crm_assignment_rules(tenant_id, id) ON DELETE SET NULL;

-- Index for the new ownership model queries
CREATE INDEX idx_assignments_tenant_record_new
    ON crm_assignments (tenant_id, record_type, record_id, status, effective_from DESC);

CREATE INDEX idx_assignments_tenant_owner_user_new
    ON crm_assignments (tenant_id, owner_user_id, status);

CREATE INDEX idx_assignments_tenant_owner_team_new
    ON crm_assignments (tenant_id, owner_team_id, status);

CREATE INDEX idx_assignments_tenant_owner_queue_new
    ON crm_assignments (tenant_id, owner_queue_id, status);

CREATE INDEX idx_assignments_tenant_correlation_new
    ON crm_assignments (tenant_id, correlation_id);

-- -----------------------------------------------------------------------
-- 2. CREATE crm_ownership_history (new table — does not exist in G1)
-- -----------------------------------------------------------------------
CREATE TABLE crm_ownership_history (
    id                  UUID NOT NULL,
    tenant_id           UUID NOT NULL,
    record_type         VARCHAR(20) NOT NULL,
    record_id           UUID NOT NULL,
    from_owner_type     VARCHAR(10),
    from_owner_user_id  UUID,
    from_owner_team_id  UUID,
    from_owner_queue_id UUID,
    to_owner_type       VARCHAR(10) NOT NULL,
    to_owner_user_id    UUID,
    to_owner_team_id    UUID,
    to_owner_queue_id   UUID,
    change_type         VARCHAR(30) NOT NULL,
    trigger_source      VARCHAR(30) NOT NULL,
    trigger_reference_id UUID,
    actor_user_id       UUID NOT NULL,
    reason              VARCHAR(500) NOT NULL,
    correlation_id      UUID NOT NULL,
    effective_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    recorded_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
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
    CONSTRAINT ck_ownership_history_to_owner_type CHECK (to_owner_type IN ('USER','TEAM','QUEUE'))
);

CREATE INDEX idx_ownership_history_tenant_record_time
    ON crm_ownership_history (tenant_id, record_type, record_id, effective_at DESC);

CREATE INDEX idx_ownership_history_tenant_correlation
    ON crm_ownership_history (tenant_id, correlation_id);

CREATE INDEX idx_ownership_history_tenant_actor_time
    ON crm_ownership_history (tenant_id, actor_user_id, effective_at DESC);

COMMIT;
