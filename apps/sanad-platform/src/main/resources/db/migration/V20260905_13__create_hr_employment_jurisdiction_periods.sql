-- ============================================================
-- HRM-G0 / Master Task 4 / WS3 Task 2
-- Canonical effective-dated employment labor jurisdiction
-- ============================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE hr_employment_jurisdiction_periods (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL REFERENCES tenants(id),
    employment_id       UUID         NOT NULL REFERENCES hr_employees(id),
    labor_jurisdiction  CHAR(2)      NOT NULL REFERENCES platform_countries(country_code),
    effective_from      DATE         NOT NULL,
    effective_to        DATE,
    approval_status     VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    approval_reference  VARCHAR(500),
    approved_by         UUID,
    approved_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_hr_employment_jurisdiction_periods PRIMARY KEY (id),
    CONSTRAINT ck_hr_employment_jurisdiction_dates
        CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_hr_employment_jurisdiction_approval_status
        CHECK (approval_status IN ('PENDING','APPROVED','REJECTED','REVOKED')),
    CONSTRAINT ck_hr_employment_jurisdiction_approved_reference
        CHECK (approval_status <> 'APPROVED' OR (approval_reference IS NOT NULL AND BTRIM(approval_reference) <> ''))
);

ALTER TABLE hr_employment_jurisdiction_periods
    ADD CONSTRAINT ex_hr_employment_jurisdiction_no_approved_overlap
    EXCLUDE USING gist (
        employment_id WITH =,
        daterange(effective_from, COALESCE(effective_to + 1, 'infinity'::date), '[)') WITH &&
    ) WHERE (approval_status = 'APPROVED');

CREATE INDEX idx_hr_employment_jurisdiction_lookup
    ON hr_employment_jurisdiction_periods
       (tenant_id, employment_id, approval_status, effective_from);

ALTER TABLE hr_employment_jurisdiction_periods ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_employment_jurisdiction_periods FORCE ROW LEVEL SECURITY;
CREATE POLICY hr_employment_jurisdiction_tenant_isolation
    ON hr_employment_jurisdiction_periods
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));
