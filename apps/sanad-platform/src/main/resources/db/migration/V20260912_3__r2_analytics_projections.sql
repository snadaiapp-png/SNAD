-- ============================================================
-- R2-F — Journey-Derived Analytics Projections (GATE R2.14/R2.15)
-- ============================================================
-- Deterministic, rebuildable read models derived ONLY from
-- workflow_journey + workflow_responsibility_segments + workflow_timers
-- (Journey remains the authoritative evidence). Any dashboard number must
-- be traceable to Journey/source evidence. Non-computable => NULL.

-- ------------------------------------------------------------
-- 1. workflow_analytics_process_facts — per-instance projection
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS workflow_analytics_process_facts (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL REFERENCES tenants(id),
    workflow_instance_id UUID           NOT NULL,
    definition_id       UUID,
    definition_family_id UUID,
    definition_version  INTEGER,
    source_module       VARCHAR(50),
    source_entity_type  VARCHAR(100),
    source_entity_id    UUID,
    status              VARCHAR(30),
    started_at          TIMESTAMP WITH TIME ZONE,
    completed_at        TIMESTAMP WITH TIME ZONE,
    process_duration_seconds BIGINT,
    sla_breach_count    INTEGER         NOT NULL DEFAULT 0,
    sla_compliant       BOOLEAN,
    late_completed      BOOLEAN,
    timeout_count       INTEGER         NOT NULL DEFAULT 0,
    reassignment_count  INTEGER         NOT NULL DEFAULT 0,
    first_response_seconds BIGINT,
    external_response_seconds BIGINT,
    notification_delivery_latency_ms BIGINT,
    customer_wait_seconds BIGINT,
    system_wait_seconds BIGINT,
    queue_wait_seconds  BIGINT,
    employee_responsibility_seconds BIGINT,
    calendar_id         UUID,
    sla_mode            VARCHAR(20),
    projection_revision INTEGER         NOT NULL DEFAULT 1,
    derived_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    derived_from_event_id BIGINT,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_workflow_analytics_process_facts PRIMARY KEY (id),
    CONSTRAINT uk_wf_apf_tenant_instance UNIQUE (tenant_id, workflow_instance_id),
    CONSTRAINT ck_wf_apf_sla_mode CHECK (sla_mode IS NULL OR sla_mode IN
        ('WALL_CLOCK', 'CALENDAR_TIME', 'BUSINESS_TIME'))
);
CREATE INDEX IF NOT EXISTS idx_wf_apf_dims
    ON workflow_analytics_process_facts(tenant_id, definition_family_id, started_at);
CREATE INDEX IF NOT EXISTS idx_wf_apf_staleness
    ON workflow_analytics_process_facts(tenant_id, derived_at);

ALTER TABLE workflow_analytics_process_facts ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS workflow_apf_tenant_isolation ON workflow_analytics_process_facts;
CREATE POLICY workflow_apf_tenant_isolation ON workflow_analytics_process_facts
    USING (tenant_id = current_setting('app.tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::UUID);

-- ------------------------------------------------------------
-- 2. workflow_analytics_step_facts — per-step projection
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS workflow_analytics_step_facts (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL REFERENCES tenants(id),
    workflow_instance_id UUID           NOT NULL,
    workflow_step_instance_id UUID     NOT NULL,
    step_key            VARCHAR(200),
    step_type           VARCHAR(40),
    work_item_id        UUID,
    owner_employee_id   UUID,
    team_id             UUID,
    status              VARCHAR(30),
    entered_at          TIMESTAMP WITH TIME ZONE,
    completed_at        TIMESTAMP WITH TIME ZONE,
    step_duration_seconds BIGINT,
    queue_wait_seconds  BIGINT,
    employee_responsibility_seconds BIGINT,
    customer_wait_seconds BIGINT,
    system_wait_seconds BIGINT,
    sla_breached        BOOLEAN,
    late_completed      BOOLEAN,
    timed_out           BOOLEAN,
    external_step       BOOLEAN         NOT NULL DEFAULT FALSE,
    projection_revision INTEGER         NOT NULL DEFAULT 1,
    derived_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    derived_from_event_id BIGINT,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_workflow_analytics_step_facts PRIMARY KEY (id),
    CONSTRAINT uk_wf_asf_tenant_step UNIQUE (tenant_id, workflow_step_instance_id)
);
CREATE INDEX IF NOT EXISTS idx_wf_asf_dims
    ON workflow_analytics_step_facts(tenant_id, workflow_instance_id, step_key);
CREATE INDEX IF NOT EXISTS idx_wf_asf_employee
    ON workflow_analytics_step_facts(tenant_id, owner_employee_id, entered_at);

ALTER TABLE workflow_analytics_step_facts ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS workflow_asf_tenant_isolation ON workflow_analytics_step_facts;
CREATE POLICY workflow_asf_tenant_isolation ON workflow_analytics_step_facts
    USING (tenant_id = current_setting('app.tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::UUID);
