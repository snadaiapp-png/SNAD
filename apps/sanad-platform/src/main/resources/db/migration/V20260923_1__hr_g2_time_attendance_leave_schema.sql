-- ============================================================
-- V20260923_1 — HRM-G2: time & attendance, timesheets, leave schema
-- ============================================================
-- Append-only Flyway migration (no edits to prior migrations; no down-script).
--
-- Conventions follow the G0/G1 baseline (V20260918_2 pattern):
--   * id UUID NOT NULL DEFAULT gen_random_uuid()
--   * tenant_id UUID NOT NULL REFERENCES tenants(id)
--   * created_at / updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
--   * version INTEGER NOT NULL DEFAULT 0 for optimistic locking
--   * UNIQUE (id, tenant_id) for tenant congruence via composite FKs
--   * state columns VARCHAR + CHECK constraints
--
-- RLS (ENABLE + FORCE + tenant_isolation policy) is applied in
-- V20260923_2 so this file stays schema-only.
-- ============================================================

-- ------------------------------------------------------------
-- 1. hr_leave_types — catalog of leave types per tenant
-- ------------------------------------------------------------
CREATE TABLE hr_leave_types (
    id                    UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id             UUID           NOT NULL REFERENCES tenants(id),
    code                  VARCHAR(32)    NOT NULL,
    name_ar               VARCHAR(128)   NOT NULL,
    name_en               VARCHAR(128)   NOT NULL,
    is_paid               BOOLEAN        NOT NULL DEFAULT true,
    requires_attachment   BOOLEAN        NOT NULL DEFAULT false,
    default_days_per_year INTEGER,
    state                 VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    version               INTEGER        NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_hr_leave_types PRIMARY KEY (id),
    CONSTRAINT uq_hr_leave_types_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT uq_hr_leave_types_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_hr_leave_types_state CHECK (state IN ('ACTIVE', 'SUSPENDED', 'ARCHIVED'))
);

-- ------------------------------------------------------------
-- 2. hr_leave_balances — per-employee per-year leave balance
-- ------------------------------------------------------------
CREATE TABLE hr_leave_balances (
    id                    UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id             UUID           NOT NULL REFERENCES tenants(id),
    employment_id         UUID           NOT NULL,
    leave_type_id         UUID           NOT NULL,
    year                  INTEGER        NOT NULL,
    entitled_days         DECIMAL(8,2)   NOT NULL DEFAULT 0,
    used_days             DECIMAL(8,2)   NOT NULL DEFAULT 0,
    pending_days          DECIMAL(8,2)   NOT NULL DEFAULT 0,
    carried_over_days     DECIMAL(8,2)   NOT NULL DEFAULT 0,
    version               INTEGER        NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_hr_leave_balances PRIMARY KEY (id),
    CONSTRAINT uq_hr_leave_balances_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT uq_hr_leave_balances_emp_type_year UNIQUE (tenant_id, employment_id, leave_type_id, year),
    CONSTRAINT fk_hr_leave_balances_leave_type FOREIGN KEY (leave_type_id, tenant_id)
        REFERENCES hr_leave_types (id, tenant_id),
    CONSTRAINT ck_hr_leave_balances_used CHECK (used_days >= 0),
    CONSTRAINT ck_hr_leave_balances_entitled CHECK (entitled_days >= 0)
);

-- ------------------------------------------------------------
-- 3. hr_attendance_records — daily clock-in/clock-out per employee
-- ------------------------------------------------------------
CREATE TABLE hr_attendance_records (
    id                    UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id             UUID           NOT NULL REFERENCES tenants(id),
    employment_id         UUID           NOT NULL,
    record_date           DATE           NOT NULL,
    clock_in              TIMESTAMPTZ,
    clock_out             TIMESTAMPTZ,
    break_minutes         INTEGER        NOT NULL DEFAULT 0,
    worked_minutes        INTEGER,
    source                VARCHAR(20)    NOT NULL DEFAULT 'MANUAL',
    state                 VARCHAR(20)    NOT NULL DEFAULT 'OPEN',
    notes                 TEXT,
    version               INTEGER        NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_hr_attendance_records PRIMARY KEY (id),
    CONSTRAINT uq_hr_attendance_records_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT uq_hr_attendance_records_emp_date UNIQUE (tenant_id, employment_id, record_date),
    CONSTRAINT ck_hr_attendance_records_source CHECK (source IN ('MANUAL', 'BIOMETRIC', 'SYSTEM', 'IMPORT')),
    CONSTRAINT ck_hr_attendance_records_state CHECK (state IN ('OPEN', 'COMPLETED', 'MISSED', 'CORRECTED')),
    CONSTRAINT ck_hr_attendance_records_clocks CHECK (
        (clock_in IS NULL AND clock_out IS NULL) OR
        (clock_in IS NOT NULL AND clock_out IS NULL) OR
        (clock_in IS NOT NULL AND clock_out IS NOT NULL AND clock_out > clock_in)
    )
);

-- ------------------------------------------------------------
-- 4. hr_leave_requests — leave application with approval workflow
-- ------------------------------------------------------------
CREATE TABLE hr_leave_requests (
    id                    UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id             UUID           NOT NULL REFERENCES tenants(id),
    employment_id         UUID           NOT NULL,
    leave_type_id         UUID           NOT NULL,
    start_date            DATE           NOT NULL,
    end_date              DATE           NOT NULL,
    days_count            DECIMAL(8,2)   NOT NULL,
    reason                TEXT,
    attachment_url        VARCHAR(512),
    state                 VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    submitted_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    approver_id           UUID,
    approved_at           TIMESTAMPTZ,
    approver_comment      TEXT,
    version               INTEGER        NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_hr_leave_requests PRIMARY KEY (id),
    CONSTRAINT uq_hr_leave_requests_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_hr_leave_requests_leave_type FOREIGN KEY (leave_type_id, tenant_id)
        REFERENCES hr_leave_types (id, tenant_id),
    CONSTRAINT ck_hr_leave_requests_state CHECK (state IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'COMPLETED')),
    CONSTRAINT ck_hr_leave_requests_dates CHECK (end_date >= start_date),
    CONSTRAINT ck_hr_leave_requests_days CHECK (days_count > 0)
);

-- ------------------------------------------------------------
-- 5. hr_timesheets — weekly/monthly timesheet aggregation
-- ------------------------------------------------------------
CREATE TABLE hr_timesheets (
    id                    UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id             UUID           NOT NULL REFERENCES tenants(id),
    employment_id         UUID           NOT NULL,
    period_start          DATE           NOT NULL,
    period_end            DATE           NOT NULL,
    total_worked_minutes  INTEGER        NOT NULL DEFAULT 0,
    total_break_minutes   INTEGER        NOT NULL DEFAULT 0,
    state                 VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    submitted_at          TIMESTAMPTZ,
    approver_id           UUID,
    approved_at           TIMESTAMPTZ,
    approver_comment      TEXT,
    version               INTEGER        NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_hr_timesheets PRIMARY KEY (id),
    CONSTRAINT uq_hr_timesheets_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT uq_hr_timesheets_emp_period UNIQUE (tenant_id, employment_id, period_start, period_end),
    CONSTRAINT ck_hr_timesheets_state CHECK (state IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_hr_timesheets_dates CHECK (period_end >= period_start)
);

-- ------------------------------------------------------------
-- Indexes for query performance
-- ------------------------------------------------------------
CREATE INDEX idx_hr_attendance_emp_date ON hr_attendance_records (tenant_id, employment_id, record_date);
CREATE INDEX idx_hr_leave_requests_emp ON hr_leave_requests (tenant_id, employment_id, state);
CREATE INDEX idx_hr_leave_balances_emp ON hr_leave_balances (tenant_id, employment_id, year);
CREATE INDEX idx_hr_timesheets_emp_period ON hr_timesheets (tenant_id, employment_id, period_start);
CREATE INDEX idx_hr_leave_types_tenant ON hr_leave_types (tenant_id, state);
