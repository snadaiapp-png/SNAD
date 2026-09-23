-- ============================================================
-- V20260924_1 — HRM-G2: work scheduling schema
-- ============================================================
-- Append-only Flyway migration. RLS applied in V20260924_2.
-- ============================================================

-- 1. hr_work_schedules — schedule template
CREATE TABLE hr_work_schedules (
    id                    UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id             UUID           NOT NULL REFERENCES tenants(id),
    code                  VARCHAR(64)    NOT NULL,
    name_ar               VARCHAR(128)   NOT NULL,
    name_en               VARCHAR(128)   NOT NULL,
    timezone              VARCHAR(64)    NOT NULL DEFAULT 'Asia/Riyadh',
    working_days          VARCHAR(20)[]   NOT NULL DEFAULT '{MON,TUE,WED,THU,SUN}',
    shift_start           TIME           NOT NULL,
    shift_end             TIME           NOT NULL,
    break_start           TIME,
    break_end             TIME,
    break_minutes         INTEGER        NOT NULL DEFAULT 0,
    expected_minutes      INTEGER        NOT NULL,
    is_overnight          BOOLEAN        NOT NULL DEFAULT false,
    state                 VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    version               INTEGER        NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_hr_work_schedules PRIMARY KEY (id),
    CONSTRAINT uq_hr_work_schedules_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT uq_hr_work_schedules_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_hr_work_schedules_state CHECK (state IN ('ACTIVE','SUSPENDED','ARCHIVED')),
    CONSTRAINT ck_hr_work_schedules_shift CHECK (shift_end > shift_start OR is_overnight = true)
);

-- 2. hr_schedule_versions — immutable published version
CREATE TABLE hr_schedule_versions (
    id                    UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id             UUID           NOT NULL REFERENCES tenants(id),
    schedule_id           UUID           NOT NULL,
    version_number        INTEGER        NOT NULL,
    definition            JSONB          NOT NULL,
    published_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    published_by          UUID,
    CONSTRAINT pk_hr_schedule_versions PRIMARY KEY (id),
    CONSTRAINT uq_hr_schedule_versions_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT uq_hr_schedule_versions_sched_ver UNIQUE (tenant_id, schedule_id, version_number),
    CONSTRAINT fk_hr_schedule_versions_schedule FOREIGN KEY (schedule_id, tenant_id)
        REFERENCES hr_work_schedules (id, tenant_id)
);

-- 3. hr_schedule_assignments — effective-dated employee→schedule binding
CREATE TABLE hr_schedule_assignments (
    id                    UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id             UUID           NOT NULL REFERENCES tenants(id),
    employment_id         UUID           NOT NULL,
    schedule_id           UUID           NOT NULL,
    schedule_version_id   UUID,
    effective_from        DATE           NOT NULL,
    effective_to          DATE,
    is_primary            BOOLEAN        NOT NULL DEFAULT true,
    state                 VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    version               INTEGER        NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_hr_schedule_assignments PRIMARY KEY (id),
    CONSTRAINT uq_hr_schedule_assignments_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_hr_schedule_assignments_schedule FOREIGN KEY (schedule_id, tenant_id)
        REFERENCES hr_work_schedules (id, tenant_id),
    CONSTRAINT ck_hr_schedule_assignments_dates CHECK (
        effective_to IS NULL OR effective_to >= effective_from
    ),
    CONSTRAINT ck_hr_schedule_assignments_state CHECK (state IN ('ACTIVE','ENDED'))
);

-- Index for effective-date lookup
CREATE INDEX idx_hr_schedule_assignments_emp ON hr_schedule_assignments (tenant_id, employment_id, effective_from, effective_to);

-- Prevent overlapping active primary assignments for same employment
CREATE UNIQUE INDEX uq_hr_schedule_assignments_primary_overlap
    ON hr_schedule_assignments (tenant_id, employment_id, effective_from)
    WHERE is_primary = true AND state = 'ACTIVE';

-- ============================================================
-- RLS for scheduling tables
-- ============================================================
ALTER TABLE hr_work_schedules ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_work_schedules FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON hr_work_schedules
    USING (tenant_id = current_setting('app.tenant_id')::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id')::uuid);

ALTER TABLE hr_schedule_versions ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_schedule_versions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON hr_schedule_versions
    USING (tenant_id = current_setting('app.tenant_id')::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id')::uuid);

ALTER TABLE hr_schedule_assignments ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_schedule_assignments FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON hr_schedule_assignments
    USING (tenant_id = current_setting('app.tenant_id')::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id')::uuid);
