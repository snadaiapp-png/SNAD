-- ============================================================
-- HRM-G0 WS2 Task 3 GREEN — Effective-Dated Structure
-- ------------------------------------------------------------
-- Creates 5 new tables:
--   hr_org_units          — stable Org Unit identity
--   hr_org_unit_versions  — effective-dated Org Unit versions
--   hr_jobs               — stable Job identity
--   hr_job_versions       — effective-dated Job versions
--   hr_position_versions  — effective-dated Position versions
--
-- Evolves hr_positions non-destructively (adds organization_id,
-- version, stable_code — all nullable for expand-safe transition).
--
-- Design contracts:
--   1. Stable identity + version model: stable row never updated;
--      versioned attributes stored in *_versions tables.
--   2. Temporal EXCLUDE: versions of same stable identity MUST NOT
--      overlap (daterange [..., '[)') &&).
--   3. Adjacent periods: V1 effective_to = D, V2 effective_from = D
--      is ALLOWED (half-open [) boundary).
--   4. Tenant-safe composite FKs where schema permits.
--   5. Org Unit types: BUSINESS_UNIT, DIVISION, DEPARTMENT, TEAM.
--   6. Date validity: effective_to >= effective_from (CHECK).
--   7. All 5 new tables: ENABLE + FORCE RLS, fail-closed policy.
--
-- Legacy tables NOT modified destructively:
--   hr_departments — preserved as-is (Task 5 may harden RLS)
--   hr_positions — expanded (ADD COLUMN), legacy columns preserved
-- ============================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ------------------------------------------------------------
-- 1. hr_org_units — stable Org Unit identity
-- ------------------------------------------------------------
CREATE TABLE hr_org_units (
    id              UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id        UUID           NOT NULL,
    organization_id UUID           NOT NULL,
    stable_code     VARCHAR(50)    NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_hr_org_units PRIMARY KEY (id),
    CONSTRAINT fk_hr_org_units_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_hr_org_units_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT uq_hr_org_unit_code
        UNIQUE (tenant_id, organization_id, stable_code)
);

-- ------------------------------------------------------------
-- 2. hr_org_unit_versions — effective-dated Org Unit versions
-- ------------------------------------------------------------
CREATE TABLE hr_org_unit_versions (
    id                UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id         UUID           NOT NULL,
    org_unit_id       UUID           NOT NULL,
    name              VARCHAR(200)   NOT NULL,
    code              VARCHAR(50)    NOT NULL,
    unit_type         VARCHAR(30)    NOT NULL,
    parent_org_unit_id UUID,
    effective_from    DATE           NOT NULL,
    effective_to      DATE,
    status            VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT pk_hr_org_unit_versions PRIMARY KEY (id),
    CONSTRAINT fk_hr_org_unit_versions_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_hr_org_unit_versions_org_unit
        FOREIGN KEY (org_unit_id) REFERENCES hr_org_units (id),
    CONSTRAINT fk_hr_org_unit_versions_parent
        FOREIGN KEY (parent_org_unit_id) REFERENCES hr_org_units (id),
    CONSTRAINT ck_hr_org_unit_type
        CHECK (unit_type IN ('BUSINESS_UNIT','DIVISION','DEPARTMENT','TEAM')),
    CONSTRAINT ck_hr_org_unit_dates
        CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

-- Partial EXCLUDE: open periods (effective_to IS NULL) cannot overlap.
-- Closed periods can coexist (immutability model).
ALTER TABLE hr_org_unit_versions
    ADD CONSTRAINT ex_hr_org_unit_versions_no_overlap
    EXCLUDE USING gist (
        org_unit_id WITH =,
        daterange(effective_from, COALESCE(effective_to + 1, 'infinity'::date), '[)') WITH &&
    ) WHERE (effective_to IS NULL);

CREATE INDEX idx_hr_org_unit_versions_org_unit
    ON hr_org_unit_versions (tenant_id, org_unit_id);

-- ------------------------------------------------------------
-- 3. hr_jobs — stable Job identity
-- ------------------------------------------------------------
CREATE TABLE hr_jobs (
    id              UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID           NOT NULL,
    organization_id UUID           NOT NULL,
    stable_code     VARCHAR(50)    NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_hr_jobs PRIMARY KEY (id),
    CONSTRAINT fk_hr_jobs_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_hr_jobs_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT uq_hr_jobs_code
        UNIQUE (tenant_id, organization_id, stable_code)
);

-- ------------------------------------------------------------
-- 4. hr_job_versions — effective-dated Job versions
-- ------------------------------------------------------------
CREATE TABLE hr_job_versions (
    id              UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID           NOT NULL,
    job_id          UUID           NOT NULL,
    title           VARCHAR(200)   NOT NULL,
    description     TEXT,
    grade           VARCHAR(20),
    effective_from  DATE           NOT NULL,
    effective_to    DATE,
    status          VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT pk_hr_job_versions PRIMARY KEY (id),
    CONSTRAINT fk_hr_job_versions_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_hr_job_versions_job
        FOREIGN KEY (job_id) REFERENCES hr_jobs (id),
    CONSTRAINT ck_hr_job_dates
        CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

ALTER TABLE hr_job_versions
    ADD CONSTRAINT ex_hr_job_versions_no_overlap
    EXCLUDE USING gist (
        job_id WITH =,
        daterange(effective_from, COALESCE(effective_to + 1, 'infinity'::date), '[)') WITH &&
    ) WHERE (effective_to IS NULL);

CREATE INDEX idx_hr_job_versions_job
    ON hr_job_versions (tenant_id, job_id);

-- ------------------------------------------------------------
-- 5. Evolve hr_positions (expand-safe, non-destructive)
-- ------------------------------------------------------------
ALTER TABLE hr_positions ADD COLUMN IF NOT EXISTS organization_id UUID;
ALTER TABLE hr_positions ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE hr_positions ADD COLUMN IF NOT EXISTS stable_code VARCHAR(50);

-- ------------------------------------------------------------
-- 6. hr_position_versions — effective-dated Position versions
-- ------------------------------------------------------------
CREATE TABLE hr_position_versions (
    id              UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID           NOT NULL,
    position_id     UUID           NOT NULL,
    organization_id UUID,
    job_id          UUID,
    org_unit_id     UUID,
    title           VARCHAR(200)   NOT NULL,
    effective_from  DATE           NOT NULL,
    effective_to    DATE,
    status          VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT pk_hr_position_versions PRIMARY KEY (id),
    CONSTRAINT fk_hr_position_versions_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_hr_position_versions_position
        FOREIGN KEY (position_id) REFERENCES hr_positions (id),
    CONSTRAINT fk_hr_position_versions_job
        FOREIGN KEY (job_id) REFERENCES hr_jobs (id),
    CONSTRAINT fk_hr_position_versions_org_unit
        FOREIGN KEY (org_unit_id) REFERENCES hr_org_units (id),
    CONSTRAINT ck_hr_position_dates
        CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

ALTER TABLE hr_position_versions
    ADD CONSTRAINT ex_hr_position_versions_no_overlap
    EXCLUDE USING gist (
        position_id WITH =,
        daterange(effective_from, COALESCE(effective_to + 1, 'infinity'::date), '[)') WITH &&
    ) WHERE (effective_to IS NULL);

CREATE INDEX idx_hr_position_versions_position
    ON hr_position_versions (tenant_id, position_id);

-- ------------------------------------------------------------
-- 7. RLS: ENABLE + FORCE + fail-closed on all 5 new tables
-- ------------------------------------------------------------
ALTER TABLE hr_org_units ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_org_units FORCE ROW LEVEL SECURITY;
CREATE POLICY hr_org_units_tenant_isolation ON hr_org_units
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

ALTER TABLE hr_org_unit_versions ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_org_unit_versions FORCE ROW LEVEL SECURITY;
CREATE POLICY hr_org_unit_versions_tenant_isolation ON hr_org_unit_versions
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

ALTER TABLE hr_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_jobs FORCE ROW LEVEL SECURITY;
CREATE POLICY hr_jobs_tenant_isolation ON hr_jobs
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

ALTER TABLE hr_job_versions ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_job_versions FORCE ROW LEVEL SECURITY;
CREATE POLICY hr_job_versions_tenant_isolation ON hr_job_versions
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

ALTER TABLE hr_position_versions ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_position_versions FORCE ROW LEVEL SECURITY;
CREATE POLICY hr_position_versions_tenant_isolation ON hr_position_versions
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));
