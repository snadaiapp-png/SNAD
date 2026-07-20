-- ============================================================================
-- SANAD CRM-008 — V20260720_5 — Create CRM Assignments + Ownership History
-- ============================================================================
-- Forward-only. Idempotent. Tenant-scoped.
-- Partial unique index enforces "one ACTIVE assignment per record".
-- Ownership history is append-only (UPDATE/DELETE revoked at DB role level —
-- applied via separate role migration; here we only create the table).
-- REVIEW ONLY — DO NOT EXECUTE in this design phase.
-- ============================================================================

BEGIN;

-- ----------------------------------------------------------------------------
-- 1. Assignments (current and historical)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_assignments (
    id                  UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    record_type         VARCHAR(20) NOT NULL,
    record_id           UUID NOT NULL,
    owner_type          VARCHAR(10) NOT NULL,
    owner_user_id       UUID,
    owner_team_id       UUID,
    owner_queue_id      UUID,
    assigned_by_rule_id UUID,
    assigned_by_user_id UUID NOT NULL,
    reason              VARCHAR(100) NOT NULL,
    correlation_id      UUID NOT NULL,
    workflow_result     JSONB,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    effective_from      TIMESTAMPTZ NOT NULL DEFAULT now(),
    effective_to        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_assignments_tenants FOREIGN KEY (tenant_id)
        REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT fk_assignments_rules FOREIGN KEY (tenant_id, assigned_by_rule_id)
        REFERENCES crm_assignment_rules(tenant_id, id) ON DELETE SET NULL,
    CONSTRAINT ck_assignments_status CHECK (status IN ('ACTIVE','SUPERSEDED','ENDED')),
    CONSTRAINT ck_assignments_record_type CHECK (record_type IN (
        'ACCOUNT','CONTACT','LEAD','OPPORTUNITY','ACTIVITY','TASK'
    )),
    CONSTRAINT ck_assignments_owner_type CHECK (owner_type IN ('USER','TEAM','QUEUE')),
    CONSTRAINT ck_assignments_owner_set CHECK (
        (owner_type = 'USER' AND owner_user_id IS NOT NULL AND owner_team_id IS NULL AND owner_queue_id IS NULL)
        OR (owner_type = 'TEAM' AND owner_team_id IS NOT NULL AND owner_user_id IS NULL AND owner_queue_id IS NULL)
        OR (owner_type = 'QUEUE' AND owner_queue_id IS NOT NULL AND owner_user_id IS NULL AND owner_team_id IS NULL)
    ),
    -- Inline composite UNIQUE so cross-references can FK to (tenant_id, id) if needed
    CONSTRAINT pk_assignments PRIMARY KEY (id),
    CONSTRAINT uk_assignments_tenant_id UNIQUE (tenant_id, id)
);

-- Partial unique index: exactly one ACTIVE assignment per (tenant, record_type, record_id)
CREATE UNIQUE INDEX IF NOT EXISTS uk_assignments_active_per_record
    ON crm_assignments (tenant_id, record_type, record_id)
    WHERE status = 'ACTIVE';

-- Tenant-leading indexes for queries
CREATE INDEX IF NOT EXISTS idx_assignments_tenant_record
    ON crm_assignments (tenant_id, record_type, record_id, status, effective_from DESC);

CREATE INDEX IF NOT EXISTS idx_assignments_tenant_owner_user
    ON crm_assignments (tenant_id, owner_user_id, status, effective_from DESC)
    WHERE owner_type = 'USER';

CREATE INDEX IF NOT EXISTS idx_assignments_tenant_owner_team
    ON crm_assignments (tenant_id, owner_team_id, status, effective_from DESC)
    WHERE owner_type = 'TEAM';

CREATE INDEX IF NOT EXISTS idx_assignments_tenant_owner_queue
    ON crm_assignments (tenant_id, owner_queue_id, status, effective_from DESC)
    WHERE owner_type = 'QUEUE';

CREATE INDEX IF NOT EXISTS idx_assignments_tenant_correlation
    ON crm_assignments (tenant_id, correlation_id);

-- ----------------------------------------------------------------------------
-- 2. Ownership History (append-only ledger)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_ownership_history (
    id                  UUID NOT NULL DEFAULT gen_random_uuid(),
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
    effective_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    recorded_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
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
    -- Inline PK + composite UNIQUE (tenant_id, id) for cross-references
    CONSTRAINT pk_ownership_history PRIMARY KEY (id),
    CONSTRAINT uk_ownership_history_tenant_id UNIQUE (tenant_id, id)
);

-- Append-only enforcement at app layer; DB role grants only INSERT + SELECT.
-- (Role migration handled in a separate admin script, not in Flyway.)
CREATE INDEX IF NOT EXISTS idx_ownership_history_tenant_record_time
    ON crm_ownership_history (tenant_id, record_type, record_id, effective_at DESC);

CREATE INDEX IF NOT EXISTS idx_ownership_history_tenant_correlation
    ON crm_ownership_history (tenant_id, correlation_id);

CREATE INDEX IF NOT EXISTS idx_ownership_history_tenant_actor_time
    ON crm_ownership_history (tenant_id, actor_user_id, effective_at DESC);

COMMIT;

-- Validation:
-- SELECT count(*) FROM information_schema.tables WHERE table_name IN
--   ('crm_assignments','crm_ownership_history');
-- Expected: 2
--
-- Verify partial unique index:
-- SELECT indexname FROM pg_indexes WHERE tablename='crm_assignments' AND indexname='uk_assignments_active_per_record';
-- Expected: returns one row
