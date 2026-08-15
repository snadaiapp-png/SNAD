-- ============================================================
-- V20260815_18: Analytics Module — Data Sources, Dashboards, Reports
--
-- Creates the Analytics module schema:
--   - analytics_data_sources    (registered data sources for analytics queries)
--   - analytics_dashboards       (dashboard configurations with layout/widgets)
--   - analytics_reports          (report definitions with query and schedule)
--
-- Design principles (same as V20260815_1/3/5/10/14/16):
--   * Tenant-scoped (RLS-enabled, tenant_id NOT NULL on every row)
--   * State machines via CHECK constraints
--   * TIMESTAMPTZ timestamps
--   * UUID primary keys
--   * Idempotent (IF NOT EXISTS / WHERE NOT EXISTS)
--   * Optimistic locking via version field
--   * No flyway_schema_history manipulation
-- ============================================================

-- ============================================================
-- STEP 1: analytics_data_sources — registered data sources (MUST come first — reports reference it)
-- ============================================================
CREATE TABLE IF NOT EXISTS analytics_data_sources (
    id              UUID            NOT NULL,
    tenant_id       UUID            NOT NULL,
    code            VARCHAR(100)   NOT NULL,
    name            VARCHAR(300)   NOT NULL,
    description     TEXT,
    source_type     VARCHAR(30)    NOT NULL,
    module          VARCHAR(50),
    configuration   JSONB,
    status          VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    last_tested_at  TIMESTAMP WITH TIME ZONE,
    last_test_status VARCHAR(20),
    created_by      UUID            NOT NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_analytics_data_sources PRIMARY KEY (id),
    CONSTRAINT uk_analytics_ds_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT uk_analytics_ds_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_analytics_ds_status CHECK (status IN ('PENDING','ACTIVE','INACTIVE','ERROR')),
    CONSTRAINT ck_analytics_ds_type CHECK (source_type IN ('CRM','FINANCE','AI','WORKFLOW','MANAGEMENT','DATABASE','API','EXTERNAL')),
    CONSTRAINT ck_analytics_ds_test CHECK (last_test_status IS NULL OR last_test_status IN ('SUCCESS','FAILED')),
    CONSTRAINT fk_analytics_ds_created_by FOREIGN KEY (tenant_id, created_by)
        REFERENCES users(tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_analytics_ds_tenant_status ON analytics_data_sources(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_analytics_ds_tenant_type ON analytics_data_sources(tenant_id, source_type);

-- ============================================================
-- STEP 2: analytics_dashboards — dashboard configurations
-- ============================================================
CREATE TABLE IF NOT EXISTS analytics_dashboards (
    id              UUID            NOT NULL,
    tenant_id       UUID            NOT NULL,
    code            VARCHAR(100)   NOT NULL,
    name            VARCHAR(300)   NOT NULL,
    description     TEXT,
    dashboard_type  VARCHAR(30)    NOT NULL DEFAULT 'STANDARD',
    configuration   JSONB,
    status          VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    created_by      UUID            NOT NULL,
    version_lock    BIGINT         NOT NULL DEFAULT 0,
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_analytics_dashboards PRIMARY KEY (id),
    CONSTRAINT uk_analytics_dash_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT uk_analytics_dash_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_analytics_dash_status CHECK (status IN ('DRAFT','ACTIVE','INACTIVE','ARCHIVED')),
    CONSTRAINT ck_analytics_dash_type CHECK (dashboard_type IN ('STANDARD','EXECUTIVE','OPERATIONAL','CUSTOM')),
    CONSTRAINT fk_analytics_dash_created_by FOREIGN KEY (tenant_id, created_by)
        REFERENCES users(tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_analytics_dash_tenant_status ON analytics_dashboards(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_analytics_dash_tenant_type ON analytics_dashboards(tenant_id, dashboard_type);

-- ============================================================
-- STEP 3: analytics_reports — report definitions (references data_sources)
-- ============================================================
CREATE TABLE IF NOT EXISTS analytics_reports (
    id              UUID            NOT NULL,
    tenant_id       UUID            NOT NULL,
    code            VARCHAR(100)   NOT NULL,
    name            VARCHAR(300)   NOT NULL,
    description     TEXT,
    report_type     VARCHAR(30)    NOT NULL DEFAULT 'TABLE',
    data_source_id  UUID,
    query_text      TEXT,
    parameters      JSONB,
    schedule_cron   VARCHAR(100),
    output_format   VARCHAR(20)    NOT NULL DEFAULT 'JSON',
    status          VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    last_executed_at TIMESTAMP WITH TIME ZONE,
    last_execution_status VARCHAR(20),
    created_by      UUID            NOT NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_analytics_reports PRIMARY KEY (id),
    CONSTRAINT uk_analytics_reports_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT uk_analytics_reports_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_analytics_report_status CHECK (status IN ('DRAFT','ACTIVE','ARCHIVED','SCHEDULED')),
    CONSTRAINT ck_analytics_report_type CHECK (report_type IN ('TABLE','CHART','PIVOT','SUMMARY','CUSTOM')),
    CONSTRAINT ck_analytics_report_format CHECK (output_format IN ('JSON','CSV','PDF','EXCEL')),
    CONSTRAINT ck_analytics_exec_status CHECK (last_execution_status IS NULL OR last_execution_status IN ('SUCCESS','FAILED','RUNNING')),
    CONSTRAINT fk_analytics_report_source FOREIGN KEY (tenant_id, data_source_id)
        REFERENCES analytics_data_sources(tenant_id, id),
    CONSTRAINT fk_analytics_report_created_by FOREIGN KEY (tenant_id, created_by)
        REFERENCES users(tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_analytics_reports_tenant_status ON analytics_reports(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_analytics_reports_tenant_type ON analytics_reports(tenant_id, report_type);

-- ============================================================
-- STEP 4: Enable RLS on all analytics tables
-- ============================================================
DO $$
DECLARE
    tbl TEXT;
BEGIN
    FOREACH tbl IN ARRAY ARRAY[
        'analytics_data_sources',
        'analytics_dashboards',
        'analytics_reports'
    ] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tbl);
        EXECUTE format($f$
            DROP POLICY IF EXISTS tenant_isolation ON %I;
            CREATE POLICY tenant_isolation ON %I
                USING (tenant_id = current_setting('app.tenant_id', true)::uuid)
        $f$, tbl, tbl);
    END LOOP;
END $$;
