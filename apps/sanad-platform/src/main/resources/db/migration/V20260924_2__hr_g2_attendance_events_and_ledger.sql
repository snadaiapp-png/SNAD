-- ============================================================
-- V20260924_2 — HRM-G2: attendance events, leave ledger, policies, idempotency
-- ============================================================
-- Append-only Flyway migration. RLS applied inline (same pattern as V20260924_1).
-- ============================================================

-- 1. hr_attendance_events — append-only event log
CREATE TABLE hr_attendance_events (
    id                    UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id             UUID           NOT NULL REFERENCES tenants(id),
    employment_id         UUID           NOT NULL,
    event_type            VARCHAR(20)    NOT NULL,
    event_timestamp       TIMESTAMPTZ    NOT NULL,
    timezone              VARCHAR(64)    NOT NULL DEFAULT 'Asia/Riyadh',
    source                VARCHAR(20)    NOT NULL DEFAULT 'MANUAL',
    actor_id              UUID,
    reason                TEXT,
    idempotency_key       VARCHAR(128),
    attendance_record_id  UUID,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_hr_attendance_events PRIMARY KEY (id),
    CONSTRAINT uq_hr_attendance_events_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_hr_attendance_events_type CHECK (
        event_type IN ('CLOCK_IN','CLOCK_OUT','BREAK_START','BREAK_END','MANUAL_CORRECTION')
    ),
    CONSTRAINT ck_hr_attendance_events_source CHECK (
        source IN ('MANUAL','BIOMETRIC','SYSTEM','IMPORT')
    )
);
CREATE UNIQUE INDEX uq_hr_attendance_events_idem
    ON hr_attendance_events (tenant_id, employment_id, event_type, event_timestamp, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

ALTER TABLE hr_attendance_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_attendance_events FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON hr_attendance_events
    USING (tenant_id = current_setting('app.tenant_id')::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id')::uuid);

-- 2. hr_leave_policies — configurable entitlement rules (separate from leave types)
CREATE TABLE hr_leave_policies (
    id                    UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id             UUID           NOT NULL REFERENCES tenants(id),
    leave_type_id         UUID           NOT NULL,
    code                  VARCHAR(64)    NOT NULL,
    name_ar               VARCHAR(128)   NOT NULL,
    name_en               VARCHAR(128)   NOT NULL,
    entitled_days_per_year DECIMAL(8,2),
    max_carryover_days    DECIMAL(8,2)   NOT NULL DEFAULT 0,
    accrual_frequency     VARCHAR(20)    NOT NULL DEFAULT 'ANNUAL',
    requires_attachment   BOOLEAN        NOT NULL DEFAULT false,
    requires_approval     BOOLEAN        NOT NULL DEFAULT true,
    approval_levels       INTEGER        NOT NULL DEFAULT 2,
    effective_from        DATE           NOT NULL,
    effective_to          DATE,
    state                 VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    version               INTEGER        NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_hr_leave_policies PRIMARY KEY (id),
    CONSTRAINT uq_hr_leave_policies_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT uq_hr_leave_policies_code UNIQUE (tenant_id, leave_type_id, code),
    CONSTRAINT fk_hr_leave_policies_type FOREIGN KEY (leave_type_id, tenant_id)
        REFERENCES hr_leave_types (id, tenant_id),
    CONSTRAINT ck_hr_leave_policies_accrual CHECK (accrual_frequency IN ('ANNUAL','MONTHLY','QUARTERLY')),
    CONSTRAINT ck_hr_leave_policies_state CHECK (state IN ('ACTIVE','SUSPENDED','ARCHIVED'))
);
ALTER TABLE hr_leave_policies ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_leave_policies FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON hr_leave_policies
    USING (tenant_id = current_setting('app.tenant_id')::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id')::uuid);

-- 3. hr_leave_ledger_entries — append-only ledger for leave balance
CREATE TABLE hr_leave_ledger_entries (
    id                    UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id             UUID           NOT NULL REFERENCES tenants(id),
    employment_id         UUID           NOT NULL,
    leave_type_id         UUID           NOT NULL,
    entry_type            VARCHAR(20)    NOT NULL,
    year                  INTEGER        NOT NULL,
    days                  DECIMAL(8,2)   NOT NULL,
    reference_type        VARCHAR(40),
    reference_id          UUID,
    reason                TEXT,
    actor_id             UUID,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_hr_leave_ledger_entries PRIMARY KEY (id),
    CONSTRAINT uq_hr_leave_ledger_entries_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_hr_leave_ledger_entries_type FOREIGN KEY (leave_type_id, tenant_id)
        REFERENCES hr_leave_types (id, tenant_id),
    CONSTRAINT ck_hr_leave_ledger_entries_type CHECK (
        entry_type IN ('OPENING','ACCRUAL','ADJUSTMENT','RESERVATION','RELEASE','CONSUMPTION','CARRYOVER','EXPIRY')
    ),
    CONSTRAINT ck_hr_leave_ledger_entries_days CHECK (days != 0)
);
CREATE INDEX idx_hr_leave_ledger_emp ON hr_leave_ledger_entries (tenant_id, employment_id, leave_type_id, year);
ALTER TABLE hr_leave_ledger_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_leave_ledger_entries FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON hr_leave_ledger_entries
    USING (tenant_id = current_setting('app.tenant_id')::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id')::uuid);

-- 4. hr_g2_idempotency_records — persistent idempotency for G2 mutations
CREATE TABLE hr_g2_idempotency_records (
    id                    UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id             UUID           NOT NULL REFERENCES tenants(id),
    idempotency_key       VARCHAR(128)   NOT NULL,
    request_fingerprint   VARCHAR(256)   NOT NULL,
    operation             VARCHAR(80)    NOT NULL,
    response_status       INTEGER        NOT NULL,
    response_body         JSONB,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_hr_g2_idempotency_records PRIMARY KEY (id),
    CONSTRAINT uq_hr_g2_idempotency_key UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT ck_hr_g2_idempotency_fingerprint CHECK (request_fingerprint != '')
);
ALTER TABLE hr_g2_idempotency_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_g2_idempotency_records FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON hr_g2_idempotency_records
    USING (tenant_id = current_setting('app.tenant_id')::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id')::uuid);

-- 5. hr_timesheet_entries — per-day entries within a timesheet period
CREATE TABLE hr_timesheet_entries (
    id                    UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id             UUID           NOT NULL REFERENCES tenants(id),
    timesheet_id          UUID           NOT NULL,
    entry_date            DATE           NOT NULL,
    clock_in              TIMESTAMPTZ,
    clock_out             TIMESTAMPTZ,
    break_minutes         INTEGER        NOT NULL DEFAULT 0,
    worked_minutes        INTEGER,
    source                VARCHAR(20)    NOT NULL DEFAULT 'MANUAL',
    notes                 TEXT,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_hr_timesheet_entries PRIMARY KEY (id),
    CONSTRAINT uq_hr_timesheet_entries_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_hr_timesheet_entries_ts FOREIGN KEY (timesheet_id, tenant_id)
        REFERENCES hr_timesheets (id, tenant_id),
    CONSTRAINT ck_hr_timesheet_entries_source CHECK (source IN ('MANUAL','SYSTEM','IMPORT'))
);
CREATE INDEX idx_hr_timesheet_entries_ts ON hr_timesheet_entries (tenant_id, timesheet_id, entry_date);
ALTER TABLE hr_timesheet_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_timesheet_entries FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON hr_timesheet_entries
    USING (tenant_id = current_setting('app.tenant_id')::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id')::uuid);
