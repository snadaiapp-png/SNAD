-- ============================================================
-- V20260904_1: Workflow Y2 production reconciliation (forward-only)
--
-- INCIDENT (2026-09-04):
--   Workflow Y2 application code went live on production (image built from
--   63e1aabf) while the complete Y2 migration wave V20260902_1..7 was still
--   PENDING in the production flyway_schema_history (production schema
--   version stayed at 20260901.1). Root cause: the canonical migration
--   channel (flyway-prod-migrate.yml, workflow_dispatch) was never invoked
--   after the Y2 merge, runtime Flyway was not active on the service, and
--   the release gate (verify-flyway.sh) only asserted July-era sentinels.
--
-- THIS MIGRATION:
--   * Is FORWARD-ONLY: no historical migration is modified. It sits above
--     both the production head (20260901.1) and the repository head
--     (20260902.7), so Flyway applies it strictly in order with
--     FLYWAY_OUT_OF_ORDER=false.
--   * Is IDEMPOTENT by construction (IF NOT EXISTS / guarded DO blocks /
--     WHERE NOT EXISTS seeds): when the V20260902 wave already applied, it
--     executes as a pure verifier; on a drifted or partially-migrated
--     environment it completes every missing Y2 element.
--   * Completes ALL Workflow Y2 elements:
--       - workflow_definitions Y2 publication metadata (+ family backfill)
--       - workflow_step_transitions (explicit graph routing)
--       - Y2 step types on workflow_steps
--       - workflow_work_items + candidates (central work pools)
--       - Y2 approval policy snapshots on workflow_approval_requests
--       - workflow_instances runtime context (+ idempotent starts)
--       - workflow_branch_tokens (controlled parallelism)
--       - business calendars + holidays + delegations
--       - execution attempts + first-class incidents
--       - workflow_event_outbox / workflow_event_inbox / notification intents
--       - tenant constraints, indexes and RLS isolation on every Y2 table
--       - the 13 fine-grained Y2 capabilities (ACTIVE)
--       - ADMIN bindings for every tenant (platform invariant)
--   * FAILS CLOSED: a final verifier aborts the migration (and the whole
--     transaction) if any Y2 sentinel is still missing after completion.
-- ============================================================

-- ------------------------------------------------------------
-- 1. Y2 identity bridge: Employee <-> User linkage (V20260902_1)
-- ------------------------------------------------------------
CREATE UNIQUE INDEX IF NOT EXISTS uq_hr_employees_tenant_user
    ON hr_employees (tenant_id, user_id)
    WHERE user_id IS NOT NULL;

-- ------------------------------------------------------------
-- 2. workflow_definitions: Y2 publication metadata (V20260902_2)
-- ------------------------------------------------------------
ALTER TABLE workflow_definitions ADD COLUMN IF NOT EXISTS definition_family_id UUID;
ALTER TABLE workflow_definitions ADD COLUMN IF NOT EXISTS engine_generation VARCHAR(10) NOT NULL DEFAULT 'LEGACY';
ALTER TABLE workflow_definitions ADD COLUMN IF NOT EXISTS publication_state VARCHAR(20) NOT NULL DEFAULT 'DRAFT';
ALTER TABLE workflow_definitions ADD COLUMN IF NOT EXISTS published_by UUID;
ALTER TABLE workflow_definitions ADD COLUMN IF NOT EXISTS published_at TIMESTAMPTZ;
ALTER TABLE workflow_definitions ADD COLUMN IF NOT EXISTS validated_at TIMESTAMPTZ;
ALTER TABLE workflow_definitions ADD COLUMN IF NOT EXISTS definition_checksum VARCHAR(128);
ALTER TABLE workflow_definitions ADD COLUMN IF NOT EXISTS schema_version INTEGER NOT NULL DEFAULT 1;

-- Backfill: each existing definition row becomes the first member of its
-- own version family (idempotent: only NULL rows are touched).
UPDATE workflow_definitions SET definition_family_id = id WHERE definition_family_id IS NULL;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'public'
                 AND table_name = 'workflow_definitions'
                 AND column_name = 'definition_family_id')
       AND EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = 'public'
                     AND table_name = 'workflow_definitions'
                     AND column_name = 'definition_family_id'
                     AND is_nullable = 'YES') THEN
        ALTER TABLE workflow_definitions ALTER COLUMN definition_family_id SET NOT NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_wf_def_family_version
    ON workflow_definitions(tenant_id, definition_family_id, version DESC);

ALTER TABLE workflow_definitions DROP CONSTRAINT IF EXISTS ck_wf_def_engine_generation;
ALTER TABLE workflow_definitions ADD CONSTRAINT ck_wf_def_engine_generation
    CHECK (engine_generation IN ('LEGACY', 'Y2'));

ALTER TABLE workflow_definitions DROP CONSTRAINT IF EXISTS ck_wf_def_publication_state;
ALTER TABLE workflow_definitions ADD CONSTRAINT ck_wf_def_publication_state
    CHECK (publication_state IN ('DRAFT', 'PUBLISHED', 'RETIRED'));

-- ------------------------------------------------------------
-- 3. workflow_step_transitions: explicit graph routing (V20260902_2)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS workflow_step_transitions (
    id                      UUID            NOT NULL,
    tenant_id               UUID            NOT NULL REFERENCES tenants(id),
    workflow_definition_id  UUID            NOT NULL REFERENCES workflow_definitions(id) ON DELETE CASCADE,
    from_step_id            UUID            NOT NULL REFERENCES workflow_steps(id) ON DELETE CASCADE,
    to_step_id              UUID            NOT NULL REFERENCES workflow_steps(id),
    transition_key          VARCHAR(100)    NOT NULL,
    outcome                 VARCHAR(50),
    condition_ast           JSONB,
    priority                INTEGER         NOT NULL DEFAULT 0,
    metadata                JSONB           NOT NULL DEFAULT '{}'::jsonb,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_workflow_step_transitions PRIMARY KEY (id),
    CONSTRAINT uk_wf_transitions_def_key UNIQUE (workflow_definition_id, transition_key)
);

CREATE INDEX IF NOT EXISTS idx_wf_transitions_def_from
    ON workflow_step_transitions(workflow_definition_id, from_step_id, priority DESC);

-- Y2 step types join the legacy five (idempotent drop + re-add).
ALTER TABLE workflow_steps DROP CONSTRAINT IF EXISTS ck_wf_step_type;
ALTER TABLE workflow_steps ADD CONSTRAINT ck_wf_step_type
    CHECK (step_type IN (
        'ACTION', 'APPROVAL', 'CONDITION', 'NOTIFICATION', 'END',
        'START', 'HUMAN_TASK', 'SYSTEM_ACTION',
        'PARALLEL_FORK', 'PARALLEL_JOIN', 'CALL_WORKFLOW'
    ));

ALTER TABLE workflow_step_transitions ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON workflow_step_transitions;
CREATE POLICY tenant_isolation ON workflow_step_transitions
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

-- ------------------------------------------------------------
-- 4. Central work items + work pools (V20260902_3)
-- ------------------------------------------------------------
-- Tenant-safe FK target for work-item assignees/candidates.
CREATE UNIQUE INDEX IF NOT EXISTS uq_hr_employees_tenant_id
    ON hr_employees (tenant_id, id);

CREATE TABLE IF NOT EXISTS workflow_work_items (
    id                          UUID            NOT NULL,
    tenant_id                   UUID            NOT NULL REFERENCES tenants(id),
    workflow_instance_id        UUID            NOT NULL REFERENCES workflow_instances(id) ON DELETE CASCADE,
    workflow_step_instance_id   UUID            NOT NULL REFERENCES workflow_step_instances(id) ON DELETE CASCADE,
    type                        VARCHAR(20)     NOT NULL,
    status                      VARCHAR(30)     NOT NULL DEFAULT 'AVAILABLE',
    assignee_employee_id        UUID,
    claimed_by_employee_id      UUID,
    assignment_mode             VARCHAR(20)     NOT NULL,
    source_module               VARCHAR(50)     NOT NULL,
    source_entity_type          VARCHAR(100)    NOT NULL,
    source_entity_id            UUID            NOT NULL,
    title                       VARCHAR(300)    NOT NULL,
    description                 TEXT,
    priority                    INTEGER         NOT NULL DEFAULT 0,
    due_at                      TIMESTAMPTZ,
    sla_due_at                  TIMESTAMPTZ,
    claimed_at                  TIMESTAMPTZ,
    completed_at                TIMESTAMPTZ,
    version                     BIGINT          NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ     NOT NULL,
    updated_at                  TIMESTAMPTZ     NOT NULL,
    CONSTRAINT pk_workflow_work_items PRIMARY KEY (id),
    CONSTRAINT ck_work_item_type CHECK (type IN ('HUMAN_TASK', 'APPROVAL')),
    CONSTRAINT ck_work_item_status CHECK (status IN (
        'AVAILABLE', 'CLAIMED', 'IN_PROGRESS', 'ASSIGNEE_UNAVAILABLE',
        'COMPLETED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_work_item_assignment_mode CHECK (assignment_mode IN ('DIRECT', 'WORK_POOL')),
    CONSTRAINT fk_work_item_assignee FOREIGN KEY (tenant_id, assignee_employee_id)
        REFERENCES hr_employees(tenant_id, id),
    CONSTRAINT fk_work_item_claimant FOREIGN KEY (tenant_id, claimed_by_employee_id)
        REFERENCES hr_employees(tenant_id, id)
);

CREATE TABLE IF NOT EXISTS workflow_work_item_candidates (
    tenant_id           UUID            NOT NULL REFERENCES tenants(id),
    work_item_id        UUID            NOT NULL REFERENCES workflow_work_items(id) ON DELETE CASCADE,
    employee_id         UUID            NOT NULL,
    resolution_source   VARCHAR(50)     NOT NULL,
    resolved_at         TIMESTAMPTZ     NOT NULL,
    snapshot_metadata   JSONB           NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT pk_workflow_work_item_candidates PRIMARY KEY (work_item_id, employee_id),
    CONSTRAINT fk_work_item_candidate_employee FOREIGN KEY (tenant_id, employee_id)
        REFERENCES hr_employees(tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_work_items_tenant_assignee
    ON workflow_work_items(tenant_id, assignee_employee_id, status);
CREATE INDEX IF NOT EXISTS idx_work_items_tenant_status
    ON workflow_work_items(tenant_id, status, priority DESC);
CREATE INDEX IF NOT EXISTS idx_work_item_candidates_employee
    ON workflow_work_item_candidates(tenant_id, employee_id);

ALTER TABLE workflow_work_items ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON workflow_work_items;
CREATE POLICY tenant_isolation ON workflow_work_items
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE workflow_work_item_candidates ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON workflow_work_item_candidates;
CREATE POLICY tenant_isolation ON workflow_work_item_candidates
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

-- ------------------------------------------------------------
-- 5. Y2 approval policy snapshots (V20260902_3)
-- ------------------------------------------------------------
ALTER TABLE workflow_approval_requests ADD COLUMN IF NOT EXISTS requested_from_employee_id UUID;
ALTER TABLE workflow_approval_requests ADD COLUMN IF NOT EXISTS approval_policy VARCHAR(20) NOT NULL DEFAULT 'ANY_ONE';
ALTER TABLE workflow_approval_requests ADD COLUMN IF NOT EXISTS self_approval_policy VARCHAR(20) NOT NULL DEFAULT 'DENY';
ALTER TABLE workflow_approval_requests ADD COLUMN IF NOT EXISTS policy_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE workflow_approval_requests DROP CONSTRAINT IF EXISTS ck_wf_approval_policy;
ALTER TABLE workflow_approval_requests ADD CONSTRAINT ck_wf_approval_policy
    CHECK (approval_policy IN ('ANY_ONE', 'ALL'));

ALTER TABLE workflow_approval_requests DROP CONSTRAINT IF EXISTS ck_wf_self_approval_policy;
ALTER TABLE workflow_approval_requests ADD CONSTRAINT ck_wf_self_approval_policy
    CHECK (self_approval_policy IN ('DENY', 'ALLOW'));

-- ------------------------------------------------------------
-- 6. Runtime context + engine generation (V20260902_4)
-- ------------------------------------------------------------
ALTER TABLE workflow_instances ADD COLUMN IF NOT EXISTS engine_generation VARCHAR(10) NOT NULL DEFAULT 'LEGACY';
ALTER TABLE workflow_instances ADD COLUMN IF NOT EXISTS definition_family_id UUID;
ALTER TABLE workflow_instances ADD COLUMN IF NOT EXISTS definition_version_id UUID;
ALTER TABLE workflow_instances ADD COLUMN IF NOT EXISTS parent_instance_id UUID;
ALTER TABLE workflow_instances ADD COLUMN IF NOT EXISTS trigger_type VARCHAR(30);
ALTER TABLE workflow_instances ADD COLUMN IF NOT EXISTS trigger_id UUID;
ALTER TABLE workflow_instances ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(200);
ALTER TABLE workflow_instances ADD COLUMN IF NOT EXISTS causation_id UUID;
ALTER TABLE workflow_instances ADD COLUMN IF NOT EXISTS context_json JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE workflow_instances ADD COLUMN IF NOT EXISTS context_schema_version INTEGER NOT NULL DEFAULT 1;

-- Externally retryable starts stay idempotent per tenant/trigger/definition.
CREATE UNIQUE INDEX IF NOT EXISTS uq_wf_instances_idempotency
    ON workflow_instances (tenant_id, trigger_type, workflow_definition_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

ALTER TABLE workflow_instances DROP CONSTRAINT IF EXISTS ck_wf_inst_engine_generation;
ALTER TABLE workflow_instances ADD CONSTRAINT ck_wf_inst_engine_generation
    CHECK (engine_generation IN ('LEGACY', 'Y2'));

-- ------------------------------------------------------------
-- 7. Branch tokens (V20260902_4)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS workflow_branch_tokens (
    id                      UUID            NOT NULL,
    tenant_id               UUID            NOT NULL REFERENCES tenants(id),
    workflow_instance_id    UUID            NOT NULL REFERENCES workflow_instances(id) ON DELETE CASCADE,
    fork_step_instance_id   UUID            NOT NULL REFERENCES workflow_step_instances(id) ON DELETE CASCADE,
    branch_key              VARCHAR(100)    NOT NULL,
    status                  VARCHAR(20)     NOT NULL DEFAULT 'RUNNING',
    join_step_id            UUID,
    version                 BIGINT          NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ     NOT NULL,
    updated_at              TIMESTAMPTZ     NOT NULL,
    CONSTRAINT pk_workflow_branch_tokens PRIMARY KEY (id),
    CONSTRAINT uk_wf_branch_instance_key UNIQUE (workflow_instance_id, fork_step_instance_id, branch_key),
    CONSTRAINT ck_wf_branch_status CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_wf_branch_join
    ON workflow_branch_tokens(tenant_id, join_step_id, status);

ALTER TABLE workflow_branch_tokens ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON workflow_branch_tokens;
CREATE POLICY tenant_isolation ON workflow_branch_tokens
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

-- ------------------------------------------------------------
-- 8. Business calendars, holidays, delegations (V20260902_5)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS workflow_business_calendars (
    id              UUID            NOT NULL,
    tenant_id       UUID            NOT NULL REFERENCES tenants(id),
    name            VARCHAR(200)    NOT NULL,
    timezone        VARCHAR(64)     NOT NULL,
    working_days    JSONB           NOT NULL DEFAULT '[1,2,3,4,5]'::jsonb,
    working_windows JSONB           NOT NULL DEFAULT '[{"start":"09:00","end":"17:00"}]'::jsonb,
    effective_from  DATE,
    effective_to    DATE,
    created_at      TIMESTAMPTZ     NOT NULL,
    updated_at      TIMESTAMPTZ     NOT NULL,
    CONSTRAINT pk_workflow_business_calendars PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS workflow_calendar_holidays (
    id            UUID        NOT NULL,
    tenant_id     UUID        NOT NULL REFERENCES tenants(id),
    calendar_id   UUID        NOT NULL REFERENCES workflow_business_calendars(id) ON DELETE CASCADE,
    holiday_date  DATE        NOT NULL,
    name          VARCHAR(200),
    created_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_workflow_calendar_holidays PRIMARY KEY (id),
    CONSTRAINT uk_wf_calendar_holiday UNIQUE (calendar_id, holiday_date)
);

CREATE TABLE IF NOT EXISTS workflow_delegations (
    id                     UUID        NOT NULL,
    tenant_id              UUID        NOT NULL REFERENCES tenants(id),
    delegator_employee_id  UUID        NOT NULL,
    delegate_employee_id   UUID        NOT NULL,
    workflow_family_id     UUID,
    module                 VARCHAR(50),
    task_category          VARCHAR(50),
    valid_from             TIMESTAMPTZ NOT NULL,
    valid_until            TIMESTAMPTZ NOT NULL,
    status                 VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by             UUID,
    created_at             TIMESTAMPTZ NOT NULL,
    updated_at             TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_workflow_delegations PRIMARY KEY (id),
    CONSTRAINT ck_wf_delegation_status CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT fk_wf_delegation_delegator FOREIGN KEY (tenant_id, delegator_employee_id)
        REFERENCES hr_employees(tenant_id, id),
    CONSTRAINT fk_wf_delegation_delegate FOREIGN KEY (tenant_id, delegate_employee_id)
        REFERENCES hr_employees(tenant_id, id),
    CONSTRAINT ck_wf_delegation_window CHECK (valid_until > valid_from)
);

CREATE INDEX IF NOT EXISTS idx_wf_delegations_delegator
    ON workflow_delegations(tenant_id, delegator_employee_id, status, valid_from, valid_until);

-- ------------------------------------------------------------
-- 9. Execution attempts + first-class incidents (V20260902_5)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS workflow_execution_attempts (
    id                  UUID        NOT NULL,
    tenant_id           UUID        NOT NULL REFERENCES tenants(id),
    workflow_instance_id UUID       NOT NULL REFERENCES workflow_instances(id) ON DELETE CASCADE,
    step_instance_id    UUID        NOT NULL REFERENCES workflow_step_instances(id) ON DELETE CASCADE,
    attempt_number      INTEGER     NOT NULL,
    idempotency_key     VARCHAR(200),
    outcome             VARCHAR(30) NOT NULL,
    failure_category    VARCHAR(50),
    external_reference  VARCHAR(200),
    diagnostics         JSONB       NOT NULL DEFAULT '{}'::jsonb,
    started_at          TIMESTAMPTZ NOT NULL,
    finished_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_workflow_execution_attempts PRIMARY KEY (id),
    CONSTRAINT uk_wf_attempt_number UNIQUE (step_instance_id, attempt_number),
    CONSTRAINT ck_wf_attempt_outcome CHECK (outcome IN (
        'IN_PROGRESS', 'SUCCEEDED', 'FAILED_TRANSIENT', 'FAILED_PERMANENT', 'TIMED_OUT', 'SKIPPED'))
);

CREATE TABLE IF NOT EXISTS workflow_incidents (
    id                  UUID        NOT NULL,
    tenant_id           UUID        NOT NULL REFERENCES tenants(id),
    workflow_instance_id UUID       REFERENCES workflow_instances(id) ON DELETE CASCADE,
    step_instance_id    UUID        REFERENCES workflow_step_instances(id) ON DELETE CASCADE,
    source              VARCHAR(50) NOT NULL,
    severity            VARCHAR(20) NOT NULL DEFAULT 'HIGH',
    failure_category    VARCHAR(50),
    status              VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    owner               UUID,
    resolution          TEXT,
    retry_step_instance_id UUID,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_workflow_incidents PRIMARY KEY (id),
    CONSTRAINT ck_wf_incident_status CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED', 'CLOSED')),
    CONSTRAINT ck_wf_incident_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE INDEX IF NOT EXISTS idx_wf_incidents_tenant_status
    ON workflow_incidents(tenant_id, status, created_at);

ALTER TABLE workflow_business_calendars ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON workflow_business_calendars;
CREATE POLICY tenant_isolation ON workflow_business_calendars
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE workflow_calendar_holidays ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON workflow_calendar_holidays;
CREATE POLICY tenant_isolation ON workflow_calendar_holidays
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE workflow_delegations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON workflow_delegations;
CREATE POLICY tenant_isolation ON workflow_delegations
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE workflow_execution_attempts ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON workflow_execution_attempts;
CREATE POLICY tenant_isolation ON workflow_execution_attempts
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE workflow_incidents ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON workflow_incidents;
CREATE POLICY tenant_isolation ON workflow_incidents
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

-- ------------------------------------------------------------
-- 10. Reliable events: inbox / outbox / notification intents (V20260902_6)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS workflow_event_inbox (
    id                      UUID        NOT NULL,
    tenant_id               UUID        NOT NULL REFERENCES tenants(id),
    event_id                UUID        NOT NULL,
    trigger_key             VARCHAR(200) NOT NULL,
    workflow_definition_id  UUID        NOT NULL REFERENCES workflow_definitions(id),
    workflow_instance_id    UUID        REFERENCES workflow_instances(id),
    received_at             TIMESTAMPTZ NOT NULL,
    processed_at            TIMESTAMPTZ,
    status                  VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    error_code              VARCHAR(100),
    CONSTRAINT pk_workflow_event_inbox PRIMARY KEY (id),
    CONSTRAINT uk_wf_inbox_event UNIQUE (tenant_id, event_id, trigger_key, workflow_definition_id),
    CONSTRAINT ck_wf_inbox_status CHECK (status IN ('RECEIVED', 'PROCESSED', 'FAILED'))
);

CREATE TABLE IF NOT EXISTS workflow_event_outbox (
    id              UUID        NOT NULL,
    tenant_id       UUID        NOT NULL REFERENCES tenants(id),
    event_type      VARCHAR(160) NOT NULL,
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    UUID        NOT NULL,
    payload_json    TEXT        NOT NULL,
    correlation_id  VARCHAR(160),
    causation_id    VARCHAR(160),
    schema_version  INTEGER     NOT NULL DEFAULT 1,
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count   INTEGER     NOT NULL DEFAULT 0,
    last_error      TEXT,
    available_at    TIMESTAMPTZ NOT NULL,
    claimed_at      TIMESTAMPTZ,
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_workflow_event_outbox PRIMARY KEY (id),
    CONSTRAINT ck_wf_outbox_status CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_wf_outbox_dispatch
    ON workflow_event_outbox(status, available_at);

CREATE TABLE IF NOT EXISTS workflow_notification_intents (
    id              UUID        NOT NULL,
    tenant_id       UUID        NOT NULL REFERENCES tenants(id),
    event_type      VARCHAR(60) NOT NULL,
    workflow_instance_id UUID   REFERENCES workflow_instances(id) ON DELETE CASCADE,
    work_item_id    UUID        REFERENCES workflow_work_items(id) ON DELETE CASCADE,
    recipient_user_id UUID,
    channel         VARCHAR(20) NOT NULL DEFAULT 'IN_APP',
    deduplication_key VARCHAR(200),
    delivery_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count   INTEGER     NOT NULL DEFAULT 0,
    last_error      TEXT,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_workflow_notification_intents PRIMARY KEY (id),
    CONSTRAINT ck_wf_notification_channel CHECK (channel IN ('IN_APP', 'EMAIL', 'WEBHOOK')),
    CONSTRAINT ck_wf_notification_delivery CHECK (delivery_status IN (
        'PENDING', 'SENT', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_wf_notifications_recipient
    ON workflow_notification_intents(tenant_id, recipient_user_id, delivery_status);

ALTER TABLE workflow_event_inbox ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON workflow_event_inbox;
CREATE POLICY tenant_isolation ON workflow_event_inbox
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE workflow_event_outbox ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON workflow_event_outbox;
CREATE POLICY tenant_isolation ON workflow_event_outbox
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE workflow_notification_intents ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON workflow_notification_intents;
CREATE POLICY tenant_isolation ON workflow_notification_intents
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

-- ------------------------------------------------------------
-- 11. Break-glass override audit action (V20260902_7)
-- ------------------------------------------------------------
ALTER TABLE workflow_transition_audit DROP CONSTRAINT IF EXISTS ck_wf_audit_action;
ALTER TABLE workflow_transition_audit ADD CONSTRAINT ck_wf_audit_action
    CHECK (action IN (
        'CREATE', 'UPDATE', 'ACTIVATE', 'DEACTIVATE', 'START', 'PAUSE', 'RESUME',
        'CANCEL', 'ADVANCE', 'APPROVE', 'REJECT', 'EXPIRE', 'FAIL', 'COMPLETE',
        'ARCHIVE', 'ASSIGN', 'OVERRIDE'
    ));

-- ------------------------------------------------------------
-- 12. The 13 fine-grained Y2 capabilities + ADMIN bindings per tenant
-- ------------------------------------------------------------
INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), code, name, desc_text, 'ACTIVE', NOW(), NOW()
FROM (VALUES
    ('WORKFLOW.DESIGN', 'Workflow Design', 'Create and edit draft workflow definitions'),
    ('WORKFLOW.VALIDATE', 'Workflow Validate', 'Validate and simulate workflow drafts'),
    ('WORKFLOW.PUBLISH', 'Workflow Publish', 'Publish immutable workflow versions'),
    ('WORKFLOW.START', 'Workflow Start', 'Start workflow instances'),
    ('WORKFLOW.TASK_EXECUTE', 'Workflow Task Execute', 'Claim and complete workflow tasks'),
    ('WORKFLOW.REASSIGN', 'Workflow Reassign', 'Reassign workflow work items'),
    ('WORKFLOW.DELEGATE', 'Workflow Delegate', 'Manage workflow delegation'),
    ('WORKFLOW.CANCEL', 'Workflow Cancel', 'Cancel workflow instances'),
    ('WORKFLOW.INCIDENT_MANAGE', 'Workflow Incident Manage', 'Acknowledge and resolve workflow incidents'),
    ('WORKFLOW.MONITOR', 'Workflow Monitor', 'View operational workflow monitoring'),
    ('WORKFLOW.AUDIT_VIEW', 'Workflow Audit View', 'Read workflow business audit'),
    ('WORKFLOW.BREAK_GLASS', 'Workflow Break Glass', 'Execute audited emergency workflow overrides'),
    ('WORKFLOW.SELF_APPROVAL_OVERRIDE', 'Workflow Self Approval Override', 'Permit explicitly configured exceptional self approval')
) AS v(code, name, desc_text)
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities ac WHERE ac.code = v.code);

INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), t.id, r.id, ac.id, NOW()
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'ADMIN'
JOIN access_capabilities ac ON ac.status = 'ACTIVE'
   AND ac.code IN (
       'WORKFLOW.DESIGN', 'WORKFLOW.VALIDATE', 'WORKFLOW.PUBLISH',
       'WORKFLOW.START', 'WORKFLOW.TASK_EXECUTE', 'WORKFLOW.REASSIGN',
       'WORKFLOW.DELEGATE', 'WORKFLOW.CANCEL', 'WORKFLOW.INCIDENT_MANAGE',
       'WORKFLOW.MONITOR', 'WORKFLOW.AUDIT_VIEW', 'WORKFLOW.BREAK_GLASS',
       'WORKFLOW.SELF_APPROVAL_OVERRIDE'
   )
WHERE NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);

-- ------------------------------------------------------------
-- 13. FAIL-CLOSED VERIFIER: abort the transaction if any Y2 sentinel is
--     still missing. This turns any future partial state into a loud
--     migration failure instead of a silent production gap.
-- ------------------------------------------------------------
DO $$
DECLARE
    missing_tables  TEXT;
    missing_columns INTEGER;
    missing_caps    INTEGER;
    missing_admin   INTEGER;
BEGIN
    -- 12 Y2 tables must exist.
    SELECT string_agg(t.name, ', ') INTO missing_tables
    FROM (VALUES
        ('workflow_step_transitions'), ('workflow_work_items'),
        ('workflow_work_item_candidates'), ('workflow_branch_tokens'),
        ('workflow_business_calendars'), ('workflow_calendar_holidays'),
        ('workflow_delegations'), ('workflow_execution_attempts'),
        ('workflow_incidents'), ('workflow_event_inbox'),
        ('workflow_event_outbox'), ('workflow_notification_intents')
    ) AS t(name)
    WHERE to_regclass('public.' || t.name) IS NULL;
    IF missing_tables IS NOT NULL THEN
        RAISE EXCEPTION 'WORKFLOW Y2 RECONCILIATION FAILED: missing tables: %', missing_tables;
    END IF;

    -- Y2 definition graph metadata must exist and be fully backfilled.
    SELECT COUNT(*) INTO missing_columns
    FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'workflow_definitions'
      AND column_name = 'definition_family_id';
    IF missing_columns <> 1 THEN
        RAISE EXCEPTION 'WORKFLOW Y2 RECONCILIATION FAILED: workflow_definitions.definition_family_id missing';
    END IF;
    IF EXISTS (SELECT 1 FROM workflow_definitions WHERE definition_family_id IS NULL) THEN
        RAISE EXCEPTION 'WORKFLOW Y2 RECONCILIATION FAILED: workflow_definitions.definition_family_id has unbackfilled NULLs';
    END IF;

    -- The Y2 capability catalog must be complete and active.
    SELECT COUNT(*) INTO missing_caps
    FROM (VALUES
        ('WORKFLOW.DESIGN'), ('WORKFLOW.VALIDATE'), ('WORKFLOW.PUBLISH'),
        ('WORKFLOW.START'), ('WORKFLOW.TASK_EXECUTE'), ('WORKFLOW.REASSIGN'),
        ('WORKFLOW.DELEGATE'), ('WORKFLOW.CANCEL'), ('WORKFLOW.INCIDENT_MANAGE'),
        ('WORKFLOW.MONITOR'), ('WORKFLOW.AUDIT_VIEW'), ('WORKFLOW.BREAK_GLASS'),
        ('WORKFLOW.SELF_APPROVAL_OVERRIDE')
    ) AS c(code)
    WHERE NOT EXISTS (
        SELECT 1 FROM access_capabilities ac
        WHERE ac.code = c.code AND ac.status = 'ACTIVE');
    IF missing_caps > 0 THEN
        RAISE EXCEPTION 'WORKFLOW Y2 RECONCILIATION FAILED: % Y2 capabilities missing/inactive', missing_caps;
    END IF;

    -- Every tenant ADMIN must hold every Y2 capability.
    SELECT COUNT(*) INTO missing_admin
    FROM tenants t
    JOIN roles r ON r.tenant_id = t.id AND r.code = 'ADMIN'
    JOIN access_capabilities ac ON ac.status = 'ACTIVE'
       AND ac.code IN ('WORKFLOW.DESIGN', 'WORKFLOW.VALIDATE', 'WORKFLOW.PUBLISH',
                       'WORKFLOW.START', 'WORKFLOW.TASK_EXECUTE', 'WORKFLOW.REASSIGN',
                       'WORKFLOW.DELEGATE', 'WORKFLOW.CANCEL', 'WORKFLOW.INCIDENT_MANAGE',
                       'WORKFLOW.MONITOR', 'WORKFLOW.AUDIT_VIEW', 'WORKFLOW.BREAK_GLASS',
                       'WORKFLOW.SELF_APPROVAL_OVERRIDE')
    WHERE NOT EXISTS (
        SELECT 1 FROM role_capabilities rc
        WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id);
    IF missing_admin > 0 THEN
        RAISE EXCEPTION 'WORKFLOW Y2 RECONCILIATION FAILED: % ADMIN capability bindings missing', missing_admin;
    END IF;

    RAISE NOTICE 'WORKFLOW Y2 RECONCILIATION VERIFIED: all tables, columns, capabilities and ADMIN bindings present';
END $$;
