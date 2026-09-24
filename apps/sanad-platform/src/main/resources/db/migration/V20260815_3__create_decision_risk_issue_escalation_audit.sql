-- ============================================================
-- V20260815_3: Senior Management — Decision + Risk + Issue + Escalation + Audit Engine
--
-- Creates the complete Senior Management Operating Layer Phase B+C+D+F:
--   - executive_decisions       (Decision lifecycle: DRAFT→APPROVED→EXECUTING→COMPLETED)
--   - decision_actions           (Tasks/actions resulting from a decision)
--   - decision_participants      (People involved in a decision)
--   - risks                      (Risk management with probability×impact scoring)
--   - risk_treatments            (Mitigation/contingency plans per risk)
--   - issues                     (Executive issue tracking)
--   - escalations                (Cross-domain escalation engine)
--   - management_audit_trail     (Immutable audit log for all management operations)
--
-- Design principles (same as V20260815_1):
--   * Tenant-scoped (RLS-enabled, tenant_id NOT NULL on every row)
--   * State machines via CHECK constraints
--   * TIMESTAMPTZ timestamps
--   * UUID primary keys
--   * Idempotent (IF NOT EXISTS / WHERE NOT EXISTS)
--   * Optimistic locking via version field
--   * No flyway_schema_history manipulation
-- ============================================================

-- ============================================================
-- STEP 1: executive_decisions — the core decision entity
-- ============================================================
CREATE TABLE IF NOT EXISTS executive_decisions (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    decision_number     VARCHAR(50)     NOT NULL,
    title               VARCHAR(300)    NOT NULL,
    description         TEXT,
    rationale           TEXT,
    category            VARCHAR(100),   -- STRATEGIC | OPERATIONAL | FINANCIAL | PERSONNEL | OTHER
    priority            VARCHAR(20)     NOT NULL DEFAULT 'NORMAL',
    status              VARCHAR(30)     NOT NULL DEFAULT 'DRAFT',
    impact              VARCHAR(20),    -- LOW | MEDIUM | HIGH | CRITICAL
    expected_outcome    TEXT,
    actual_outcome      TEXT,
    owner_user_id       UUID,
    created_by          UUID            NOT NULL,
    decided_by          UUID,
    decision_date       DATE,
    due_date            DATE,
    executed_at         TIMESTAMP WITH TIME ZONE,
    completed_at        TIMESTAMP WITH TIME ZONE,
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_executive_decisions PRIMARY KEY (id),
    CONSTRAINT uk_decisions_tenant_number UNIQUE (tenant_id, decision_number),
    CONSTRAINT ck_decision_status CHECK (status IN (
        'DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED',
        'EXECUTING', 'COMPLETED', 'CANCELLED'
    )),
    CONSTRAINT ck_decision_priority CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_decision_impact CHECK (impact IS NULL OR impact IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_decision_category CHECK (category IS NULL OR category IN (
        'STRATEGIC', 'OPERATIONAL', 'FINANCIAL', 'PERSONNEL', 'TECHNOLOGY', 'OTHER'
    )),
    CONSTRAINT fk_decision_owner FOREIGN KEY (tenant_id, owner_user_id)
        REFERENCES users(tenant_id, id),
    CONSTRAINT fk_decision_created_by FOREIGN KEY (tenant_id, created_by)
        REFERENCES users(tenant_id, id),
    CONSTRAINT fk_decision_decided_by FOREIGN KEY (tenant_id, decided_by)
        REFERENCES users(tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_decisions_tenant_status ON executive_decisions(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_decisions_tenant_priority ON executive_decisions(tenant_id, priority, due_date);
CREATE INDEX IF NOT EXISTS idx_decisions_owner ON executive_decisions(owner_user_id) WHERE owner_user_id IS NOT NULL;

-- ============================================================
-- STEP 2: decision_actions — tasks resulting from a decision
-- ============================================================
CREATE TABLE IF NOT EXISTS decision_actions (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    decision_id         UUID            NOT NULL,
    title               VARCHAR(300)    NOT NULL,
    description         TEXT,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    assignee_id         UUID,
    due_date            DATE,
    completed_at        TIMESTAMP WITH TIME ZONE,
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_decision_actions PRIMARY KEY (id),
    CONSTRAINT ck_action_status CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT fk_action_decision FOREIGN KEY (decision_id)
        REFERENCES executive_decisions(id) ON DELETE CASCADE,
    CONSTRAINT fk_action_assignee FOREIGN KEY (tenant_id, assignee_id)
        REFERENCES users(tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_actions_decision ON decision_actions(decision_id);
CREATE INDEX IF NOT EXISTS idx_actions_tenant_status ON decision_actions(tenant_id, status);

-- ============================================================
-- STEP 3: decision_participants — people involved in a decision
-- ============================================================
CREATE TABLE IF NOT EXISTS decision_participants (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    decision_id         UUID            NOT NULL,
    user_id             UUID            NOT NULL,
    role                VARCHAR(30)     NOT NULL,  -- REQUESTER | REVIEWER | APPROVER | ADVISOR | OBSERVER
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_decision_participants PRIMARY KEY (id),
    CONSTRAINT uk_participants_decision_user UNIQUE (decision_id, user_id),
    CONSTRAINT ck_participant_role CHECK (role IN (
        'REQUESTER', 'REVIEWER', 'APPROVER', 'ADVISOR', 'OBSERVER'
    )),
    CONSTRAINT fk_participant_decision FOREIGN KEY (decision_id)
        REFERENCES executive_decisions(id) ON DELETE CASCADE,
    CONSTRAINT fk_participant_user FOREIGN KEY (tenant_id, user_id)
        REFERENCES users(tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_participants_decision ON decision_participants(decision_id);
CREATE INDEX IF NOT EXISTS idx_participants_user ON decision_participants(user_id);

-- ============================================================
-- STEP 4: risks — risk management with probability × impact scoring
-- ============================================================
CREATE TABLE IF NOT EXISTS risks (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    code                VARCHAR(100)    NOT NULL,
    title               VARCHAR(300)    NOT NULL,
    description         TEXT,
    category            VARCHAR(100),   -- FINANCIAL | OPERATIONAL | STRATEGIC | COMPLIANCE | TECHNICAL | EXTERNAL
    status              VARCHAR(30)     NOT NULL DEFAULT 'IDENTIFIED',
    probability         INTEGER         NOT NULL DEFAULT 3 CHECK (probability BETWEEN 1 AND 5),
    impact              INTEGER         NOT NULL DEFAULT 3 CHECK (impact BETWEEN 1 AND 5),
    risk_score          INTEGER         NOT NULL DEFAULT 9,  -- probability * impact (computed)
    severity            VARCHAR(20)     NOT NULL DEFAULT 'MEDIUM',  -- LOW | MEDIUM | HIGH | CRITICAL (derived from score)
    owner_user_id       UUID,
    identified_by       UUID            NOT NULL,
    identified_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    due_date            DATE,
    mitigation          TEXT,
    contingency         TEXT,
    treatment_strategy  VARCHAR(30),    -- AVOID | MITIGATE | TRANSFER | ACCEPT
    residual_risk       TEXT,
    closed_at           TIMESTAMP WITH TIME ZONE,
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_risks PRIMARY KEY (id),
    CONSTRAINT uk_risks_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_risk_status CHECK (status IN (
        'IDENTIFIED', 'ASSESSED', 'MITIGATING', 'MONITORED', 'ACCEPTED', 'CLOSED'
    )),
    CONSTRAINT ck_risk_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_risk_treatment CHECK (treatment_strategy IS NULL OR treatment_strategy IN (
        'AVOID', 'MITIGATE', 'TRANSFER', 'ACCEPT'
    )),
    CONSTRAINT fk_risk_owner FOREIGN KEY (tenant_id, owner_user_id)
        REFERENCES users(tenant_id, id),
    CONSTRAINT fk_risk_identified_by FOREIGN KEY (tenant_id, identified_by)
        REFERENCES users(tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_risks_tenant_status ON risks(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_risks_tenant_severity ON risks(tenant_id, severity);
CREATE INDEX IF NOT EXISTS idx_risks_owner ON risks(owner_user_id) WHERE owner_user_id IS NOT NULL;

-- ============================================================
-- STEP 5: issues — executive issue tracking
-- ============================================================
CREATE TABLE IF NOT EXISTS issues (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    code                VARCHAR(100)    NOT NULL,
    title               VARCHAR(300)    NOT NULL,
    description         TEXT,
    severity            VARCHAR(20)     NOT NULL DEFAULT 'MEDIUM',
    priority            VARCHAR(20)     NOT NULL DEFAULT 'NORMAL',
    status              VARCHAR(30)     NOT NULL DEFAULT 'OPEN',
    source              VARCHAR(100),   -- INTERNAL | CUSTOMER | AUDIT | MONITORING | OTHER
    impact              TEXT,
    root_cause          TEXT,
    resolution          TEXT,
    owner_user_id       UUID,
    reported_by         UUID            NOT NULL,
    reported_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    due_date            DATE,
    resolved_at         TIMESTAMP WITH TIME ZONE,
    closed_at           TIMESTAMP WITH TIME ZONE,
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_issues PRIMARY KEY (id),
    CONSTRAINT uk_issues_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_issue_status CHECK (status IN (
        'OPEN', 'TRIAGED', 'IN_PROGRESS', 'BLOCKED', 'RESOLVED', 'CLOSED', 'REOPENED'
    )),
    CONSTRAINT ck_issue_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_issue_priority CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'CRITICAL')),
    CONSTRAINT fk_issue_owner FOREIGN KEY (tenant_id, owner_user_id)
        REFERENCES users(tenant_id, id),
    CONSTRAINT fk_issue_reported_by FOREIGN KEY (tenant_id, reported_by)
        REFERENCES users(tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_issues_tenant_status ON issues(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_issues_tenant_severity ON issues(tenant_id, severity);
CREATE INDEX IF NOT EXISTS idx_issues_owner ON issues(owner_user_id) WHERE owner_user_id IS NOT NULL;

-- ============================================================
-- STEP 6: escalations — cross-domain escalation engine
-- ============================================================
CREATE TABLE IF NOT EXISTS escalations (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    code                VARCHAR(100)    NOT NULL,
    source_entity_type  VARCHAR(50)     NOT NULL,  -- RISK | ISSUE | DECISION | KPI | OBJECTIVE
    source_entity_id    UUID            NOT NULL,
    reason              TEXT            NOT NULL,
    severity            VARCHAR(20)     NOT NULL DEFAULT 'HIGH',
    status              VARCHAR(30)     NOT NULL DEFAULT 'ACTIVE',
    escalation_level    INTEGER         NOT NULL DEFAULT 1 CHECK (escalation_level BETWEEN 1 AND 5),
    assigned_to         UUID,
    sla_deadline        TIMESTAMP WITH TIME ZONE,
    resolved_at         TIMESTAMP WITH TIME ZONE,
    resolution          TEXT,
    created_by          UUID            NOT NULL,
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_escalations PRIMARY KEY (id),
    CONSTRAINT uk_escalations_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_escalation_status CHECK (status IN ('ACTIVE', 'ACKNOWLEDGED', 'RESOLVED', 'CANCELLED')),
    CONSTRAINT ck_escalation_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_escalation_source CHECK (source_entity_type IN (
        'RISK', 'ISSUE', 'DECISION', 'KPI', 'OBJECTIVE'
    )),
    CONSTRAINT fk_escalation_assigned_to FOREIGN KEY (tenant_id, assigned_to)
        REFERENCES users(tenant_id, id),
    CONSTRAINT fk_escalation_created_by FOREIGN KEY (tenant_id, created_by)
        REFERENCES users(tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_escalations_tenant_status ON escalations(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_escalations_source ON escalations(source_entity_type, source_entity_id);
CREATE INDEX IF NOT EXISTS idx_escalations_assigned ON escalations(assigned_to) WHERE assigned_to IS NOT NULL;

-- ============================================================
-- STEP 7: management_audit_trail — immutable audit log
-- ============================================================
-- This table is append-only: records are NEVER updated or deleted.
-- Every management operation (decision state change, risk update, etc.)
-- creates an audit record here.
CREATE TABLE IF NOT EXISTS management_audit_trail (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    actor_user_id      UUID            NOT NULL,
    entity_type        VARCHAR(50)     NOT NULL,  -- DECISION | RISK | ISSUE | ESCALATION | OBJECTIVE | KPI | INITIATIVE
    entity_id          UUID            NOT NULL,
    action             VARCHAR(50)     NOT NULL,  -- CREATE | UPDATE | STATE_CHANGE | APPROVE | REJECT | DELETE | ASSIGN
    from_state          VARCHAR(50),
    to_state            VARCHAR(50),
    changes             JSONB,          -- before/after diff as JSON
    correlation_id     UUID,            -- request correlation ID
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_management_audit_trail PRIMARY KEY (id),
    CONSTRAINT ck_audit_entity CHECK (entity_type IN (
        'DECISION', 'RISK', 'ISSUE', 'ESCALATION', 'OBJECTIVE', 'KPI', 'INITIATIVE'
    )),
    CONSTRAINT ck_audit_action CHECK (action IN (
        'CREATE', 'UPDATE', 'STATE_CHANGE', 'APPROVE', 'REJECT', 'DELETE', 'ASSIGN', 'MEASURE'
    )),
    CONSTRAINT fk_audit_actor FOREIGN KEY (tenant_id, actor_user_id)
        REFERENCES users(tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_audit_tenant_entity ON management_audit_trail(tenant_id, entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_tenant_actor ON management_audit_trail(tenant_id, actor_user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_audit_created_at ON management_audit_trail(created_at);

-- ============================================================
-- STEP 8: Enable RLS on all new tables
-- ============================================================
DO $$
DECLARE
    tbl TEXT;
BEGIN
    FOREACH tbl IN ARRAY ARRAY[
        'executive_decisions',
        'decision_actions',
        'decision_participants',
        'risks',
        'issues',
        'escalations',
        'management_audit_trail'
    ] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tbl);
        EXECUTE format($f$
            DROP POLICY IF EXISTS tenant_isolation ON %I;
            CREATE POLICY tenant_isolation ON %I
                USING (tenant_id = current_setting('app.tenant_id', true)::uuid)
        $f$, tbl, tbl);
    END LOOP;
END $$;
