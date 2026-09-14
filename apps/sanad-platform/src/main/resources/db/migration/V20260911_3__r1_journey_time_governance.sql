-- ============================================================
-- R1-H/I/J — Transaction Journey ledger + Time Governance schema
-- ============================================================
-- R1 GATES R1.19-R1.27. Forward-only. Tenant-scoped; RLS ENABLE + fail-closed
-- tenant_isolation policy (workflow engine family convention).

-- ------------------------------------------------------------
-- workflow_journey — append-only authoritative evidence ledger.
-- Corrections are appended as superseding evidence; rows are NEVER updated
-- or deleted (DB-enforced below, mirroring hr_audit_ledger precedent).
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS workflow_journey (
    id                  BIGSERIAL       PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    event_type          VARCHAR(50)     NOT NULL,
    definition_id       UUID,
    definition_family_id UUID,
    definition_version  INTEGER,
    workflow_instance_id UUID,
    step_instance_id    UUID,
    work_item_id        UUID,
    external_action_id  UUID,
    approval_id         UUID,
    source_module       VARCHAR(50),
    source_entity_type  VARCHAR(100),
    source_entity_id    UUID,
    actor_type          VARCHAR(20)     NOT NULL DEFAULT 'SYSTEM',
    actor_user_id       UUID,
    actor_employee_id   UUID,
    external_participant_id UUID,
    from_state          VARCHAR(60),
    to_state            VARCHAR(60),
    occurred_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    reason              TEXT,
    correlation_id      UUID,
    causation_id        UUID,
    event_key           VARCHAR(200),
    metadata            JSONB,
    CONSTRAINT ck_wf_journey_actor CHECK (actor_type IN ('USER','EMPLOYEE','SYSTEM','EXTERNAL')),
    CONSTRAINT uk_wf_journey_event_key UNIQUE (tenant_id, event_key)
);
-- deterministic read reconstruction order + tenant access paths
CREATE INDEX IF NOT EXISTS idx_wf_journey_replay
    ON workflow_journey(tenant_id, workflow_instance_id, id);
CREATE INDEX IF NOT EXISTS idx_wf_journey_type
    ON workflow_journey(tenant_id, event_type, occurred_at);

ALTER TABLE workflow_journey ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS workflow_journey_tenant_isolation ON workflow_journey;
CREATE POLICY workflow_journey_tenant_isolation ON workflow_journey
    USING (tenant_id = current_setting('app.tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::UUID);

-- append-only enforcement (application authority is restricted too)
CREATE OR REPLACE FUNCTION wf_journey_forbid_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'WORKFLOW_JOURNEY_IMMUTABLE: journey rows are append-only evidence';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_wf_journey_no_update ON workflow_journey;
CREATE TRIGGER trg_wf_journey_no_update
    BEFORE UPDATE ON workflow_journey
    FOR EACH ROW EXECUTE FUNCTION wf_journey_forbid_mutation();

DROP TRIGGER IF EXISTS trg_wf_journey_no_delete ON workflow_journey;
CREATE TRIGGER trg_wf_journey_no_delete
    BEFORE DELETE ON workflow_journey
    FOR EACH ROW EXECUTE FUNCTION wf_journey_forbid_mutation();

-- ------------------------------------------------------------
-- workflow_timers — governed time (measurement + execution deadlines)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS workflow_timers (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    scope               VARCHAR(30)     NOT NULL,
    scope_id            UUID            NOT NULL,
    purpose             VARCHAR(30)     NOT NULL,
    state               VARCHAR(20)     NOT NULL DEFAULT 'RUNNING',
    policy              VARCHAR(30)     NOT NULL DEFAULT 'MONITOR_ONLY',
    response_mode       VARCHAR(20)     NOT NULL DEFAULT 'STRICT',
    sla_mode            VARCHAR(20)     NOT NULL DEFAULT 'WALL_CLOCK',
    sla_calendar_id     UUID,
    due_at              TIMESTAMP WITH TIME ZONE,
    warned_at           TIMESTAMP WITH TIME ZONE,
    breached_at         TIMESTAMP WITH TIME ZONE,
    paused_at           TIMESTAMP WITH TIME ZONE,
    paused_seconds      BIGINT          NOT NULL DEFAULT 0,
    completed_at        TIMESTAMP WITH TIME ZONE,
    workflow_instance_id UUID,
    workflow_step_instance_id UUID,
    work_item_id        UUID,
    external_action_id  UUID,
    definition_version_id UUID,
    correlation_id      UUID,
    timeout_action_taken BOOLEAN        NOT NULL DEFAULT false,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_workflow_timers PRIMARY KEY (id),
    CONSTRAINT uk_workflow_timers_tenant UNIQUE (tenant_id, id),
    CONSTRAINT ck_wf_timer_scope CHECK (scope IN ('PROCESS','STEP','WORK_ITEM','EXTERNAL_ACTION')),
    CONSTRAINT ck_wf_timer_purpose CHECK (purpose IN ('MEASUREMENT','EXECUTION_DEADLINE')),
    CONSTRAINT ck_wf_timer_state CHECK (state IN ('RUNNING','PAUSED','BREACHED','COMPLETED','TIMED_OUT','CANCELLED')),
    CONSTRAINT ck_wf_timer_policy CHECK (policy IN ('MONITOR_ONLY','WARN_ONLY','ESCALATE','REASSIGN','AUTO_EXPIRE','AUTO_REJECT','AUTO_CANCEL','ROUTE_TO_TIMEOUT')),
    CONSTRAINT ck_wf_timer_response CHECK (response_mode IN ('STRICT','GRACE','OPEN_LATE')),
    CONSTRAINT ck_wf_timer_sla_mode CHECK (sla_mode IN ('WALL_CLOCK','CALENDAR_TIME','BUSINESS_TIME'))
);
CREATE INDEX IF NOT EXISTS idx_wf_timers_due
    ON workflow_timers(tenant_id, state, due_at)
    WHERE purpose = 'EXECUTION_DEADLINE';

ALTER TABLE workflow_timers ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS workflow_timers_tenant_isolation ON workflow_timers;
CREATE POLICY workflow_timers_tenant_isolation ON workflow_timers
    USING (tenant_id = current_setting('app.tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::UUID);

-- ------------------------------------------------------------
-- workflow_responsibility_segments — R1.27 ownership attribution
-- (foundation for R2 analytics; NO employee scoring in R1)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS workflow_responsibility_segments (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    work_item_id        UUID            NOT NULL,
    workflow_instance_id UUID,
    segment_type        VARCHAR(30)     NOT NULL,
    owner_employee_id   UUID,
    started_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at            TIMESTAMP WITH TIME ZONE,
    reason              VARCHAR(200),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_wf_resp_segments PRIMARY KEY (id),
    CONSTRAINT uk_wf_resp_segments_tenant UNIQUE (tenant_id, id),
    CONSTRAINT ck_wf_resp_segment_type CHECK (segment_type IN ('ASSIGNED','CLAIMED','EXTERNAL_WAIT','SYSTEM_WAIT','QUEUE'))
);
CREATE INDEX IF NOT EXISTS idx_wf_resp_segments_item
    ON workflow_responsibility_segments(tenant_id, work_item_id, started_at);

ALTER TABLE workflow_responsibility_segments ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS workflow_responsibility_segments_tenant_isolation ON workflow_responsibility_segments;
CREATE POLICY workflow_responsibility_segments_tenant_isolation ON workflow_responsibility_segments
    USING (tenant_id = current_setting('app.tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::UUID);
