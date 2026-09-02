-- ============================================================
-- V20260902_5: Workflow Y2 business calendars, delegation, incidents,
--              execution attempts
--
-- Wave 2 — Task 12/13 (design decisions V3/G3/O3/AF3/P3):
--   * Tenant business calendars with versioned effective dates.
--   * Time-bounded, tenant-safe delegation records (B1-dominant).
--   * Durable execution attempts and first-class incidents.
-- ============================================================

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

-- Tenant isolation consistent with the other workflow tables.
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
