-- ============================================================
-- HRM-G0 WS2 Task 4 GREEN — Effective-Dated Assignments
-- ------------------------------------------------------------
-- Creates hr_employee_assignments with:
--   - CHECK constraints (assignment_type, occupancy_mode, status, allocation, dates)
--   - Partial EXCLUDE for PRIMARY overlap (WHERE assignment_type='PRIMARY' AND status='ACTIVE')
--   - Partial EXCLUDE for Position occupancy (WHERE position_id IS NOT NULL AND occupancy_mode='OCCUPYING' AND status='ACTIVE')
--   - ENABLE + FORCE RLS with fail-closed policy
-- ============================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE hr_employee_assignments (
    id                      UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id               UUID           NOT NULL,
    employment_id           UUID           NOT NULL,
    organization_id         UUID           NOT NULL,
    org_unit_id             UUID,
    position_id             UUID,
    reports_to_assignment_id UUID,
    work_location_id        UUID,
    cost_center_id          UUID,
    assignment_type         VARCHAR(20)    NOT NULL,
    occupancy_mode          VARCHAR(20)    NOT NULL,
    allocation_percent     NUMERIC(5,2)   NOT NULL,
    effective_from          DATE           NOT NULL,
    effective_to            DATE,
    status                  VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    version                 BIGINT         NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_hr_employee_assignments PRIMARY KEY (id),
    CONSTRAINT fk_hr_assignments_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_hr_assignments_employment
        FOREIGN KEY (employment_id) REFERENCES hr_employees (id),
    CONSTRAINT fk_hr_assignments_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_hr_assignments_position
        FOREIGN KEY (position_id) REFERENCES hr_positions (id),
    CONSTRAINT fk_hr_assignments_reports_to
        FOREIGN KEY (reports_to_assignment_id) REFERENCES hr_employee_assignments (id),
    CONSTRAINT ck_hr_assignments_type
        CHECK (assignment_type IN ('PRIMARY','SECONDARY')),
    CONSTRAINT ck_hr_assignments_occupancy
        CHECK (occupancy_mode IN ('OCCUPYING','NON_OCCUPYING')),
    CONSTRAINT ck_hr_assignments_status
        CHECK (status IN ('ACTIVE','ENDED','VOIDED')),
    CONSTRAINT ck_hr_assignments_allocation
        CHECK (allocation_percent > 0 AND allocation_percent <= 100),
    CONSTRAINT ck_hr_assignments_dates
        CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

-- Partial EXCLUDE: no overlapping PRIMARY assignments for same employment.
ALTER TABLE hr_employee_assignments
    ADD CONSTRAINT ex_hr_assignments_primary_no_overlap
    EXCLUDE USING gist (
        employment_id WITH =,
        daterange(effective_from, COALESCE(effective_to + 1, 'infinity'::date), '[)') WITH &&
    ) WHERE (assignment_type = 'PRIMARY' AND status = 'ACTIVE');

-- Partial EXCLUDE: no overlapping OCCUPYING assignments for same position.
ALTER TABLE hr_employee_assignments
    ADD CONSTRAINT ex_hr_assignments_occupancy_no_overlap
    EXCLUDE USING gist (
        position_id WITH =,
        daterange(effective_from, COALESCE(effective_to + 1, 'infinity'::date), '[)') WITH &&
    ) WHERE (position_id IS NOT NULL AND occupancy_mode = 'OCCUPYING' AND status = 'ACTIVE');

CREATE INDEX idx_hr_assignments_tenant ON hr_employee_assignments (tenant_id);
CREATE INDEX idx_hr_assignments_employment ON hr_employee_assignments (tenant_id, employment_id);
CREATE INDEX idx_hr_assignments_position ON hr_employee_assignments (tenant_id, position_id);

ALTER TABLE hr_employee_assignments ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_employee_assignments FORCE ROW LEVEL SECURITY;
CREATE POLICY hr_employee_assignments_tenant_isolation ON hr_employee_assignments
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));
