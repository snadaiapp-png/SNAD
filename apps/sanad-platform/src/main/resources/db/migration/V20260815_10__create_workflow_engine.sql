-- ============================================================
-- V20260815_10: Workflow Engine — Definitions + Steps + Instances + Approvals + Audit
--
-- Creates the complete Workflow Engine schema:
--   - workflow_definitions       (workflow metadata, versioning, lifecycle)
--   - workflow_steps              (step definitions within a workflow)
--   - workflow_instances          (runtime execution of workflows)
--   - workflow_step_instances    (runtime state of individual steps)
--   - workflow_approval_requests (approval work items with segregation of duties)
--   - workflow_transition_audit   (immutable transition/provenance trail)
--
-- Design principles (same as V20260815_1/3/5):
--   * Tenant-scoped (RLS-enabled, tenant_id NOT NULL on every row)
--   * State machines via CHECK constraints
--   * TIMESTAMPTZ timestamps
--   * UUID primary keys
--   * Idempotent (IF NOT EXISTS / WHERE NOT EXISTS)
--   * Optimistic locking via version field
--   * No flyway_schema_history manipulation
-- ============================================================

-- ============================================================
-- STEP 1: workflow_definitions — workflow metadata + versioning
-- ============================================================
CREATE TABLE IF NOT EXISTS workflow_definitions (
    id              UUID            NOT NULL,
    tenant_id       UUID            NOT NULL,
    code            VARCHAR(100)    NOT NULL,
    name            VARCHAR(300)    NOT NULL,
    description     TEXT,
    module          VARCHAR(50)     NOT NULL DEFAULT 'GENERAL',
    version         INTEGER         NOT NULL DEFAULT 1,
    status          VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    trigger_type    VARCHAR(30)     NOT NULL DEFAULT 'MANUAL',
    created_by      UUID            NOT NULL,
    version_lock    BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_workflow_definitions PRIMARY KEY (id),
    CONSTRAINT uk_workflow_def_tenant_code_version UNIQUE (tenant_id, code, version),
    CONSTRAINT ck_wf_def_status CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_wf_def_trigger CHECK (trigger_type IN ('MANUAL', 'EVENT', 'SCHEDULED', 'API')),
    CONSTRAINT fk_wf_def_created_by FOREIGN KEY (tenant_id, created_by)
        REFERENCES users(tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_wf_def_tenant_status ON workflow_definitions(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_wf_def_tenant_module ON workflow_definitions(tenant_id, module);

-- ============================================================
-- STEP 2: workflow_steps — step definitions within a workflow
-- ============================================================
CREATE TABLE IF NOT EXISTS workflow_steps (
    id                      UUID            NOT NULL,
    tenant_id               UUID            NOT NULL,
    workflow_definition_id  UUID            NOT NULL,
    step_key                VARCHAR(100)    NOT NULL,
    name                    VARCHAR(300)    NOT NULL,
    step_type               VARCHAR(30)     NOT NULL,
    sequence_order          INTEGER         NOT NULL,
    configuration           JSONB,
    sla_hours               INTEGER,
    required_capability     VARCHAR(100),
    required_role           VARCHAR(50),
    version                 BIGINT          NOT NULL DEFAULT 0,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_workflow_steps PRIMARY KEY (id),
    CONSTRAINT uk_wf_steps_def_key UNIQUE (workflow_definition_id, step_key),
    CONSTRAINT ck_wf_step_type CHECK (step_type IN ('ACTION', 'APPROVAL', 'CONDITION', 'NOTIFICATION', 'END')),
    CONSTRAINT fk_wf_steps_def FOREIGN KEY (workflow_definition_id)
        REFERENCES workflow_definitions(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_wf_steps_def ON workflow_steps(workflow_definition_id);
CREATE INDEX IF NOT EXISTS idx_wf_steps_tenant ON workflow_steps(tenant_id);

-- ============================================================
-- STEP 3: workflow_instances — runtime execution
-- ============================================================
CREATE TABLE IF NOT EXISTS workflow_instances (
    id                      UUID            NOT NULL,
    tenant_id               UUID            NOT NULL,
    workflow_definition_id  UUID            NOT NULL,
    workflow_version        INTEGER         NOT NULL,
    business_entity_type   VARCHAR(100)    NOT NULL,
    business_entity_id      UUID            NOT NULL,
    status                  VARCHAR(20)     NOT NULL DEFAULT 'RUNNING',
    current_step_key        VARCHAR(100),
    started_by              UUID            NOT NULL,
    started_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at            TIMESTAMP WITH TIME ZONE,
    cancelled_at            TIMESTAMP WITH TIME ZONE,
    cancelled_by            UUID,
    cancel_reason           TEXT,
    correlation_id          UUID,
    version                 BIGINT          NOT NULL DEFAULT 0,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_workflow_instances PRIMARY KEY (id),
    CONSTRAINT ck_wf_inst_status CHECK (status IN ('RUNNING', 'PAUSED', 'COMPLETED', 'CANCELLED', 'FAILED')),
    CONSTRAINT fk_wf_inst_def FOREIGN KEY (workflow_definition_id)
        REFERENCES workflow_definitions(id),
    CONSTRAINT fk_wf_inst_started_by FOREIGN KEY (tenant_id, started_by)
        REFERENCES users(tenant_id, id),
    CONSTRAINT fk_wf_inst_cancelled_by FOREIGN KEY (tenant_id, cancelled_by)
        REFERENCES users(tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_wf_inst_tenant_status ON workflow_instances(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_wf_inst_entity ON workflow_instances(tenant_id, business_entity_type, business_entity_id);
CREATE INDEX IF NOT EXISTS idx_wf_inst_def ON workflow_instances(workflow_definition_id);

-- ============================================================
-- STEP 4: workflow_step_instances — runtime step state
-- ============================================================
CREATE TABLE IF NOT EXISTS workflow_step_instances (
    id                      UUID            NOT NULL,
    tenant_id               UUID            NOT NULL,
    workflow_instance_id    UUID            NOT NULL,
    workflow_step_id        UUID            NOT NULL,
    step_key                VARCHAR(100)    NOT NULL,
    status                  VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    assigned_user_id        UUID,
    assigned_role           VARCHAR(50),
    started_at              TIMESTAMP WITH TIME ZONE,
    completed_at            TIMESTAMP WITH TIME ZONE,
    due_at                  TIMESTAMP WITH TIME ZONE,
    attempt_count           INTEGER         NOT NULL DEFAULT 0,
    result                  TEXT,
    version                 BIGINT          NOT NULL DEFAULT 0,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_workflow_step_instances PRIMARY KEY (id),
    CONSTRAINT ck_wf_step_inst_status CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'SKIPPED', 'FAILED')),
    CONSTRAINT fk_wf_step_inst_instance FOREIGN KEY (workflow_instance_id)
        REFERENCES workflow_instances(id) ON DELETE CASCADE,
    CONSTRAINT fk_wf_step_inst_step FOREIGN KEY (workflow_step_id)
        REFERENCES workflow_steps(id),
    CONSTRAINT fk_wf_step_inst_assigned FOREIGN KEY (tenant_id, assigned_user_id)
        REFERENCES users(tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_wf_step_inst_instance ON workflow_step_instances(workflow_instance_id);
CREATE INDEX IF NOT EXISTS idx_wf_step_inst_tenant_status ON workflow_step_instances(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_wf_step_inst_due ON workflow_step_instances(tenant_id, due_at) WHERE due_at IS NOT NULL;

-- ============================================================
-- STEP 5: workflow_approval_requests — approval work items
-- ============================================================
CREATE TABLE IF NOT EXISTS workflow_approval_requests (
    id                          UUID            NOT NULL,
    tenant_id                   UUID            NOT NULL,
    workflow_instance_id        UUID            NOT NULL,
    workflow_step_instance_id   UUID            NOT NULL,
    requested_from_user_id      UUID            NOT NULL,
    requested_from_role         VARCHAR(50),
    status                      VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    requested_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    due_at                      TIMESTAMP WITH TIME ZONE,
    acted_by                    UUID,
    acted_at                    TIMESTAMP WITH TIME ZONE,
    decision                    VARCHAR(20),
    comments                    TEXT,
    version                     BIGINT          NOT NULL DEFAULT 0,
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_workflow_approval_requests PRIMARY KEY (id),
    CONSTRAINT ck_wf_approval_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_wf_approval_decision CHECK (decision IS NULL OR decision IN ('APPROVED', 'REJECTED')),
    CONSTRAINT fk_wf_approval_instance FOREIGN KEY (workflow_instance_id)
        REFERENCES workflow_instances(id) ON DELETE CASCADE,
    CONSTRAINT fk_wf_approval_step FOREIGN KEY (workflow_step_instance_id)
        REFERENCES workflow_step_instances(id) ON DELETE CASCADE,
    CONSTRAINT fk_wf_approval_user FOREIGN KEY (tenant_id, requested_from_user_id)
        REFERENCES users(tenant_id, id),
    CONSTRAINT fk_wf_approval_acted_by FOREIGN KEY (tenant_id, acted_by)
        REFERENCES users(tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_wf_approval_tenant_status ON workflow_approval_requests(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_wf_approval_instance ON workflow_approval_requests(workflow_instance_id);
CREATE INDEX IF NOT EXISTS idx_wf_approval_user ON workflow_approval_requests(requested_from_user_id);
CREATE INDEX IF NOT EXISTS idx_wf_approval_due ON workflow_approval_requests(tenant_id, due_at, status) WHERE due_at IS NOT NULL;

-- ============================================================
-- STEP 6: workflow_transition_audit — immutable transition trail
-- ============================================================
CREATE TABLE IF NOT EXISTS workflow_transition_audit (
    id                      UUID            NOT NULL,
    tenant_id               UUID            NOT NULL,
    workflow_instance_id   UUID            NOT NULL,
    workflow_step_instance_id UUID,
    actor_user_id           UUID,
    action                  VARCHAR(50)    NOT NULL,
    from_state              VARCHAR(50),
    to_state                VARCHAR(50),
    correlation_id          UUID,
    metadata                JSONB,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_workflow_transition_audit PRIMARY KEY (id),
    CONSTRAINT ck_wf_audit_action CHECK (action IN (
        'CREATE', 'UPDATE', 'ACTIVATE', 'DEACTIVATE', 'START', 'PAUSE', 'RESUME',
        'CANCEL', 'ADVANCE', 'APPROVE', 'REJECT', 'EXPIRE', 'FAIL', 'COMPLETE',
        'ARCHIVE', 'ASSIGN'
    )),
    CONSTRAINT fk_wf_audit_instance FOREIGN KEY (workflow_instance_id)
        REFERENCES workflow_instances(id) ON DELETE CASCADE,
    CONSTRAINT fk_wf_audit_actor FOREIGN KEY (tenant_id, actor_user_id)
        REFERENCES users(tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_wf_audit_tenant ON workflow_transition_audit(tenant_id, created_at);
CREATE INDEX IF NOT EXISTS idx_wf_audit_instance ON workflow_transition_audit(workflow_instance_id);

-- ============================================================
-- STEP 7: Enable RLS on all workflow tables
-- ============================================================
DO $$
DECLARE
    tbl TEXT;
BEGIN
    FOREACH tbl IN ARRAY ARRAY[
        'workflow_definitions',
        'workflow_steps',
        'workflow_instances',
        'workflow_step_instances',
        'workflow_approval_requests',
        'workflow_transition_audit'
    ] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tbl);
        EXECUTE format($f$
            DROP POLICY IF EXISTS tenant_isolation ON %I;
            CREATE POLICY tenant_isolation ON %I
                USING (tenant_id = current_setting('app.tenant_id', true)::uuid)
        $f$, tbl, tbl);
    END LOOP;
END $$;
