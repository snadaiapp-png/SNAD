-- ============================================================
-- V20260830_3: Workflow Y2 central WorkItems and work pools
--
-- Wave 1 — Task 7 (design decisions C3/L3/N3):
--   * Central actionable WorkItems exist only for HUMAN_TASK and APPROVAL.
--   * DIRECT assignment names one Employee; WORK_POOL names candidates
--     with atomic claim semantics (optimistic version + status guard).
--   * Tenant-safe employee reference via additive composite unique index.
-- ============================================================

-- Tenant-safe FK target for work-item assignees/candidates: hr_employees
-- has an id-only PK, so add the additive composite unique index the FK needs.
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
