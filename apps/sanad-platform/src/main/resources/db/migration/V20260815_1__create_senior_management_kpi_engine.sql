-- ============================================================
-- V20260815_1: Senior Management — Strategic Objectives + KPI Engine
--
-- Creates the foundational tables for the Senior Management Operating Layer:
--   - strategic_objectives  (OKR "Objectives")
--   - key_results            (OKR "Key Results" — measurable outcomes per objective)
--   - kpi_definitions       (reusable KPI catalog: formula, unit, direction, owner)
--   - kpi_targets            (per-period target + threshold per KPI per tenant)
--   - kpi_measurements       (historical actual values — append-only, immutable)
--   - strategic_initiatives  (programs of work that advance objectives)
--
-- Design principles:
--   * Tenant-scoped (RLS-enabled, tenant_id NOT NULL on every row)
--   * Status state machines via CHECK constraints (NOT enums — portable)
--   * All timestamps are TIMESTAMPTZ
--   * UUID primary keys
--   * Idempotent (IF NOT EXISTS / WHERE NOT EXISTS)
--   * Backward compatible (pure additions, no ALTER of existing tables)
--   * No flyway_schema_history manipulation (Flyway manages that itself)
-- ============================================================

-- ============================================================
-- STEP 1: strategic_objectives — the "O" in OKR
-- ============================================================
CREATE TABLE IF NOT EXISTS strategic_objectives (
    id              UUID            NOT NULL,
    tenant_id       UUID            NOT NULL,
    parent_id       UUID,           -- nullable: supports cascading objectives
    code            VARCHAR(100)    NOT NULL,
    title           VARCHAR(300)   NOT NULL,
    description     TEXT,
    status          VARCHAR(30)     NOT NULL DEFAULT 'DRAFT',
    priority        VARCHAR(20)     NOT NULL DEFAULT 'NORMAL',
    owner_user_id   UUID,
    period_start    DATE            NOT NULL,
    period_end      DATE            NOT NULL,
    progress_pct    INTEGER         NOT NULL DEFAULT 0 CHECK (progress_pct BETWEEN 0 AND 100),
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_strategic_objectives PRIMARY KEY (id),
    CONSTRAINT uk_strategic_objectives_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_obj_status CHECK (status IN (
        'DRAFT', 'ACTIVE', 'AT_RISK', 'OFF_TRACK', 'ACHIEVED', 'CLOSED', 'CANCELLED'
    )),
    CONSTRAINT ck_obj_priority CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_obj_dates CHECK (period_end >= period_start),
    CONSTRAINT fk_obj_parent FOREIGN KEY (parent_id)
        REFERENCES strategic_objectives(id),
    CONSTRAINT fk_obj_owner FOREIGN KEY (tenant_id, owner_user_id)
        REFERENCES users(tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_obj_tenant_status ON strategic_objectives(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_obj_tenant_period ON strategic_objectives(tenant_id, period_start, period_end);
CREATE INDEX IF NOT EXISTS idx_obj_owner ON strategic_objectives(owner_user_id) WHERE owner_user_id IS NOT NULL;

-- ============================================================
-- STEP 2: key_results — the "KR" in OKR (measurable outcomes)
-- ============================================================
CREATE TABLE IF NOT EXISTS key_results (
    id              UUID            NOT NULL,
    tenant_id       UUID            NOT NULL,
    objective_id    UUID            NOT NULL,
    title           VARCHAR(300)   NOT NULL,
    description     TEXT,
    metric_unit     VARCHAR(50)     NOT NULL DEFAULT 'COUNT',  -- COUNT | PERCENTAGE | CURRENCY | RATIO | DURATION
    baseline_value  NUMERIC(20, 4),
    target_value    NUMERIC(20, 4)  NOT NULL,
    current_value   NUMERIC(20, 4) NOT NULL DEFAULT 0,
    direction       VARCHAR(10)    NOT NULL DEFAULT 'UP',  -- UP (higher is better) | DOWN (lower is better)
    status          VARCHAR(30)     NOT NULL DEFAULT 'NOT_STARTED',
    weight_pct      INTEGER         NOT NULL DEFAULT 100 CHECK (weight_pct BETWEEN 0 AND 100),
    owner_user_id   UUID,
    due_date        DATE,
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_key_results PRIMARY KEY (id),
    CONSTRAINT uk_key_results_tenant_obj_title UNIQUE (tenant_id, objective_id, title),
    CONSTRAINT ck_kr_status CHECK (status IN (
        'NOT_STARTED', 'ON_TRACK', 'AT_RISK', 'OFF_TRACK', 'ACHIEVED', 'MISSED'
    )),
    CONSTRAINT ck_kr_unit CHECK (metric_unit IN (
        'COUNT', 'PERCENTAGE', 'CURRENCY', 'RATIO', 'DURATION'
    )),
    CONSTRAINT ck_kr_direction CHECK (direction IN ('UP', 'DOWN')),
    CONSTRAINT fk_kr_objective FOREIGN KEY (objective_id)
        REFERENCES strategic_objectives(id) ON DELETE CASCADE,
    CONSTRAINT fk_kr_owner FOREIGN KEY (tenant_id, owner_user_id)
        REFERENCES users(tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_kr_objective ON key_results(objective_id);
CREATE INDEX IF NOT EXISTS idx_kr_tenant_status ON key_results(tenant_id, status);

-- ============================================================
-- STEP 3: kpi_definitions — reusable KPI catalog (tenant-scoped)
-- ============================================================
CREATE TABLE IF NOT EXISTS kpi_definitions (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    code                VARCHAR(100)    NOT NULL,
    name                VARCHAR(200)    NOT NULL,
    description         TEXT,
    category            VARCHAR(100),   -- e.g., 'FINANCIAL', 'OPERATIONAL', 'CUSTOMER', 'GROWTH'
    metric_unit         VARCHAR(50)     NOT NULL DEFAULT 'COUNT',
    direction           VARCHAR(10)     NOT NULL DEFAULT 'UP',
    formula             TEXT,           -- human-readable formula description
    source_system       VARCHAR(100),   -- e.g., 'CRM', 'ERP', 'HRM' (for cross-module KPIs)
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    owner_user_id       UUID,
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_kpi_definitions PRIMARY KEY (id),
    CONSTRAINT uk_kpi_definitions_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_kpi_def_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'DEPRECATED')),
    CONSTRAINT ck_kpi_def_unit CHECK (metric_unit IN (
        'COUNT', 'PERCENTAGE', 'CURRENCY', 'RATIO', 'DURATION'
    )),
    CONSTRAINT ck_kpi_def_direction CHECK (direction IN ('UP', 'DOWN')),
    CONSTRAINT fk_kpi_def_owner FOREIGN KEY (tenant_id, owner_user_id)
        REFERENCES users(tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_kpi_def_tenant_status ON kpi_definitions(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_kpi_def_category ON kpi_definitions(tenant_id, category) WHERE category IS NOT NULL;

-- ============================================================
-- STEP 4: kpi_targets — per-period target + thresholds
-- ============================================================
CREATE TABLE IF NOT EXISTS kpi_targets (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    kpi_definition_id   UUID            NOT NULL,
    period_start        DATE            NOT NULL,
    period_end          DATE            NOT NULL,
    target_value        NUMERIC(20, 4)  NOT NULL,
    minimum_value       NUMERIC(20, 4),  -- below this = OFF_TRACK
    stretch_value       NUMERIC(20, 4),  -- above this = ACHIEVED (stretch)
    owner_user_id       UUID,
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_kpi_targets PRIMARY KEY (id),
    CONSTRAINT uk_kpi_targets_def_period UNIQUE (kpi_definition_id, period_start, period_end),
    CONSTRAINT ck_kpi_target_status CHECK (status IN ('ACTIVE', 'CLOSED', 'CANCELLED')),
    CONSTRAINT ck_kpi_target_dates CHECK (period_end >= period_start),
    CONSTRAINT fk_kpi_target_def FOREIGN KEY (kpi_definition_id)
        REFERENCES kpi_definitions(id) ON DELETE CASCADE,
    CONSTRAINT fk_kpi_target_owner FOREIGN KEY (tenant_id, owner_user_id)
        REFERENCES users(tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_kpi_target_def ON kpi_targets(kpi_definition_id);
CREATE INDEX IF NOT EXISTS idx_kpi_target_tenant_period ON kpi_targets(tenant_id, period_start, period_end);

-- ============================================================
-- STEP 5: kpi_measurements — historical actual values (append-only)
-- ============================================================
-- This table is append-only: records are NEVER updated or deleted.
-- Each measurement is an immutable historical snapshot. This supports:
--   - Trend analysis
--   - Audit trail
--   - Reproducible dashboards
--   - Evidence-based performance management
CREATE TABLE IF NOT EXISTS kpi_measurements (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    kpi_definition_id   UUID            NOT NULL,
    kpi_target_id       UUID,           -- nullable: measurement can exist without a target
    period              DATE            NOT NULL,  -- the period the measurement represents
    measured_value      NUMERIC(20, 4)  NOT NULL,
    previous_value      NUMERIC(20, 4),  -- for delta calculation (denormalized for perf)
    variance_pct        NUMERIC(10, 4),  -- (measured - target) / target * 100
    status              VARCHAR(30)     NOT NULL,
    evidence            TEXT,            -- human-readable source reference
    measured_by         UUID            NOT NULL,
    measured_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_kpi_measurements PRIMARY KEY (id),
    CONSTRAINT uk_kpi_measurements_def_period UNIQUE (kpi_definition_id, period),
    CONSTRAINT ck_kpi_meas_status CHECK (status IN (
        'ON_TRACK', 'AT_RISK', 'OFF_TRACK', 'ACHIEVED', 'NOT_STARTED', 'NO_DATA'
    )),
    CONSTRAINT fk_kpi_meas_def FOREIGN KEY (kpi_definition_id)
        REFERENCES kpi_definitions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_kpi_meas_target FOREIGN KEY (kpi_target_id)
        REFERENCES kpi_targets(id) ON DELETE SET NULL,
    CONSTRAINT fk_kpi_meas_user FOREIGN KEY (tenant_id, measured_by)
        REFERENCES users(tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_kpi_meas_def_period ON kpi_measurements(kpi_definition_id, period);
CREATE INDEX IF NOT EXISTS idx_kpi_meas_tenant_status ON kpi_measurements(tenant_id, status, period);

-- ============================================================
-- STEP 6: strategic_initiatives — programs of work that advance objectives
-- ============================================================
CREATE TABLE IF NOT EXISTS strategic_initiatives (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    objective_id        UUID            NOT NULL,
    code                VARCHAR(100)    NOT NULL,
    name                VARCHAR(300)   NOT NULL,
    description         TEXT,
    status              VARCHAR(30)     NOT NULL DEFAULT 'PLANNED',
    owner_user_id       UUID,
    start_date          DATE,
    target_end_date     DATE,
    actual_end_date    DATE,
    progress_pct        INTEGER         NOT NULL DEFAULT 0 CHECK (progress_pct BETWEEN 0 AND 100),
    budget_minor        BIGINT,         -- budget in minor currency units (e.g., halalas for SAR)
    spent_minor         BIGINT          NOT NULL DEFAULT 0,
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_strategic_initiatives PRIMARY KEY (id),
    CONSTRAINT uk_strategic_initiatives_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_init_status CHECK (status IN (
        'PLANNED', 'IN_PROGRESS', 'ON_HOLD', 'COMPLETED', 'CANCELLED', 'FAILED'
    )),
    CONSTRAINT fk_init_objective FOREIGN KEY (objective_id)
        REFERENCES strategic_objectives(id) ON DELETE CASCADE,
    CONSTRAINT fk_init_owner FOREIGN KEY (tenant_id, owner_user_id)
        REFERENCES users(tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_init_objective ON strategic_initiatives(objective_id);
CREATE INDEX IF NOT EXISTS idx_init_tenant_status ON strategic_initiatives(tenant_id, status);

-- ============================================================
-- STEP 7: Enable RLS on all new tables
-- ============================================================
DO $$
DECLARE
    tbl TEXT;
BEGIN
    FOREACH tbl IN ARRAY ARRAY[
        'strategic_objectives',
        'key_results',
        'kpi_definitions',
        'kpi_targets',
        'kpi_measurements',
        'strategic_initiatives'
    ] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tbl);
        EXECUTE format($f$
            DROP POLICY IF EXISTS tenant_isolation ON %I;
            CREATE POLICY tenant_isolation ON %I
                USING (tenant_id = current_setting('app.tenant_id', true)::uuid)
        $f$, tbl, tbl);
    END LOOP;
END $$;
