-- ============================================================
-- V20261006_1 — HRM-G4 payroll snapshot foundation
-- ============================================================
-- G4-T2: country-neutral payroll persistence only.
-- No statutory Saudi rules, GOSI/WPS, bank execution, tax, or GL posting.
-- PostgreSQL Direct / FORCE RLS / tenant-congruent references.
-- ============================================================

CREATE TABLE hr_payroll_runs (
    id                    UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id             UUID           NOT NULL REFERENCES tenants(id),
    legal_entity_id       UUID           NOT NULL,
    period_start          DATE           NOT NULL,
    period_end            DATE           NOT NULL,
    currency_code         CHAR(3)        NOT NULL,
    status                VARCHAR(24)    NOT NULL DEFAULT 'DRAFT',
    source_cutoff_at      TIMESTAMPTZ    NOT NULL,
    version               BIGINT         NOT NULL DEFAULT 0,
    created_by            UUID,
    reviewed_by           UUID,
    reviewed_at           TIMESTAMPTZ,
    approved_by           UUID,
    approved_at           TIMESTAMPTZ,
    exported_at           TIMESTAMPTZ,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_hr_payroll_runs PRIMARY KEY (id),
    CONSTRAINT uq_hr_payroll_runs_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_hr_payroll_runs_legal_entity
        FOREIGN KEY (tenant_id, legal_entity_id)
        REFERENCES legal_entities (tenant_id, id),
    CONSTRAINT ck_hr_payroll_runs_period
        CHECK (period_end >= period_start),
    CONSTRAINT ck_hr_payroll_runs_currency
        CHECK (currency_code ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_hr_payroll_runs_status
        CHECK (status IN ('DRAFT','CALCULATED','REVIEWED','APPROVED','EXPORTED','CANCELLED')),
    CONSTRAINT ck_hr_payroll_runs_reviewed
        CHECK (
            (reviewed_at IS NULL AND reviewed_by IS NULL)
            OR (reviewed_at IS NOT NULL AND reviewed_by IS NOT NULL)
        ),
    CONSTRAINT ck_hr_payroll_runs_approved
        CHECK (
            (approved_at IS NULL AND approved_by IS NULL)
            OR (approved_at IS NOT NULL AND approved_by IS NOT NULL)
        )
);

ALTER TABLE hr_payroll_runs ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_payroll_runs FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON hr_payroll_runs;
CREATE POLICY tenant_isolation ON hr_payroll_runs
    FOR ALL
    USING (
        tenant_id::text = NULLIF(current_setting('app.tenant_id', true), '')
    )
    WITH CHECK (
        tenant_id::text = NULLIF(current_setting('app.tenant_id', true), '')
    );

CREATE INDEX idx_hr_payroll_runs_tenant_period
    ON hr_payroll_runs (tenant_id, legal_entity_id, period_start, period_end);
CREATE INDEX idx_hr_payroll_runs_tenant_status
    ON hr_payroll_runs (tenant_id, status, period_end DESC);

CREATE TABLE hr_payroll_items (
    id                       UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id                UUID           NOT NULL REFERENCES tenants(id),
    payroll_run_id            UUID           NOT NULL,
    employment_id             UUID           NOT NULL,
    compensation_package_id   UUID           NOT NULL,
    timesheet_id              UUID           NOT NULL,
    base_amount               NUMERIC(19,4)  NOT NULL,
    gross_amount              NUMERIC(19,4)  NOT NULL,
    deduction_total           NUMERIC(19,4)  NOT NULL DEFAULT 0,
    net_amount                NUMERIC(19,4)  NOT NULL,
    status                    VARCHAR(24)    NOT NULL DEFAULT 'CALCULATED',
    exception_code            VARCHAR(80),
    version                   BIGINT         NOT NULL DEFAULT 0,
    created_at                TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_hr_payroll_items PRIMARY KEY (id),
    CONSTRAINT uq_hr_payroll_items_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT uq_hr_payroll_items_run_employment
        UNIQUE (tenant_id, payroll_run_id, employment_id),

    CONSTRAINT fk_hr_payroll_items_run
        FOREIGN KEY (payroll_run_id, tenant_id)
        REFERENCES hr_payroll_runs (id, tenant_id),
    CONSTRAINT fk_hr_payroll_items_employment
        FOREIGN KEY (employment_id, tenant_id)
        REFERENCES hr_employees (id, tenant_id),
    CONSTRAINT fk_hr_payroll_items_compensation
        FOREIGN KEY (compensation_package_id, tenant_id)
        REFERENCES hr_compensation_packages (id, tenant_id),
    CONSTRAINT fk_hr_payroll_items_timesheet
        FOREIGN KEY (timesheet_id, tenant_id)
        REFERENCES hr_timesheets (id, tenant_id),

    CONSTRAINT ck_hr_payroll_items_amounts
        CHECK (
            base_amount >= 0
            AND gross_amount >= 0
            AND deduction_total >= 0
            AND net_amount >= 0
        ),
    CONSTRAINT ck_hr_payroll_items_net_equation
        CHECK (net_amount = gross_amount - deduction_total),
    CONSTRAINT ck_hr_payroll_items_status
        CHECK (status IN ('CALCULATED','EXCEPTION','REVIEWED','APPROVED','EXPORTED','CANCELLED'))
);

ALTER TABLE hr_payroll_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_payroll_items FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON hr_payroll_items;
CREATE POLICY tenant_isolation ON hr_payroll_items
    FOR ALL
    USING (
        tenant_id::text = NULLIF(current_setting('app.tenant_id', true), '')
    )
    WITH CHECK (
        tenant_id::text = NULLIF(current_setting('app.tenant_id', true), '')
    );

CREATE INDEX idx_hr_payroll_items_run
    ON hr_payroll_items (tenant_id, payroll_run_id);
CREATE INDEX idx_hr_payroll_items_employment
    ON hr_payroll_items (tenant_id, employment_id, payroll_run_id);
CREATE INDEX idx_hr_payroll_items_status
    ON hr_payroll_items (tenant_id, status);

COMMENT ON TABLE hr_payroll_runs IS
    'HRM-G4 country-neutral payroll run snapshot; statutory country rules remain independently gated.';
COMMENT ON TABLE hr_payroll_items IS
    'HRM-G4 tenant-bound payroll item snapshot referencing canonical HR compensation and approved G2 timesheet inputs.';
