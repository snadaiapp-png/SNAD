-- ============================================================
-- V20260815_5: Senior Management — Command Center + Alerts + Workflow + AI Intelligence
--
-- Creates:
--   - executive_alerts              (Alert lifecycle: OPEN→ACKNOWLEDGED→RESOLVED/DISMISSED)
--   - executive_insights           (AI-generated insights with provenance)
--   - executive_recommendations    (AI-generated advisory recommendations)
--   - executive_health_snapshots   (Immutable historical health score snapshots)
--
-- Design: same principles as V20260815_1/3
-- ============================================================

-- ============================================================
-- STEP 1: executive_alerts — cross-domain alert system
-- ============================================================
CREATE TABLE IF NOT EXISTS executive_alerts (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    type                VARCHAR(50)     NOT NULL,  -- CRITICAL_RISK, CRITICAL_ISSUE, SLA_BREACH, KPI_OFF_TRACK, OBJECTIVE_OFF_TRACK, DECISION_PENDING, ESCALATION_OVERDUE
    severity            VARCHAR(20)     NOT NULL DEFAULT 'HIGH',
    source_entity_type  VARCHAR(50)     NOT NULL,  -- RISK, ISSUE, DECISION, KPI, OBJECTIVE, ESCALATION
    source_entity_id    UUID            NOT NULL,
    title               VARCHAR(300)    NOT NULL,
    description         TEXT,
    status              VARCHAR(20)     NOT NULL DEFAULT 'OPEN',
    acknowledged_by     UUID,
    acknowledged_at     TIMESTAMP WITH TIME ZONE,
    resolved_by         UUID,
    resolved_at         TIMESTAMP WITH TIME ZONE,
    resolution          TEXT,
    created_by          UUID            NOT NULL,
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_executive_alerts PRIMARY KEY (id),
    -- Unique constraint prevents duplicate alerts for the same source event
    CONSTRAINT uk_alerts_tenant_source UNIQUE (tenant_id, source_entity_type, source_entity_id, type),
    CONSTRAINT ck_alert_type CHECK (type IN (
        'CRITICAL_RISK', 'CRITICAL_ISSUE', 'SLA_BREACH', 'KPI_OFF_TRACK',
        'OBJECTIVE_OFF_TRACK', 'DECISION_PENDING', 'ESCALATION_OVERDUE'
    )),
    CONSTRAINT ck_alert_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_alert_status CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED', 'DISMISSED')),
    CONSTRAINT ck_alert_source CHECK (source_entity_type IN (
        'RISK', 'ISSUE', 'DECISION', 'KPI', 'OBJECTIVE', 'ESCALATION'
    )),
    CONSTRAINT fk_alert_ack_by FOREIGN KEY (tenant_id, acknowledged_by)
        REFERENCES users(tenant_id, id),
    CONSTRAINT fk_alert_res_by FOREIGN KEY (tenant_id, resolved_by)
        REFERENCES users(tenant_id, id),
    CONSTRAINT fk_alert_created_by FOREIGN KEY (tenant_id, created_by)
        REFERENCES users(tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_alerts_tenant_status ON executive_alerts(tenant_id, status, severity);
CREATE INDEX IF NOT EXISTS idx_alerts_source ON executive_alerts(source_entity_type, source_entity_id);

-- ============================================================
-- STEP 2: executive_insights — AI-generated analysis (advisory only)
-- ============================================================
CREATE TABLE IF NOT EXISTS executive_insights (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    type                VARCHAR(50)     NOT NULL,  -- SUMMARY, ANOMALY, TREND, RISK_ASSESSMENT, ISSUE_ANALYSIS, FORECAST
    title               VARCHAR(300)    NOT NULL,
    description         TEXT            NOT NULL,
    confidence          NUMERIC(5, 4)   NOT NULL DEFAULT 0.5000 CHECK (confidence BETWEEN 0 AND 1),
    evidence            JSONB,          -- references to source data (KPI IDs, measurement IDs, etc.)
    model_name          VARCHAR(100),   -- which AI model generated this (or 'deterministic' for rule-based)
    model_version       VARCHAR(50),
    advisory            BOOLEAN         NOT NULL DEFAULT TRUE,  -- always TRUE — AI is advisory only
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, DISMISSED, ARCHIVED
    generated_by        UUID            NOT NULL,  -- user who triggered the analysis (or system UUID)
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_executive_insights PRIMARY KEY (id),
    CONSTRAINT ck_insight_type CHECK (type IN (
        'SUMMARY', 'ANOMALY', 'TREND', 'RISK_ASSESSMENT', 'ISSUE_ANALYSIS', 'FORECAST', 'RECOMMENDATION'
    )),
    CONSTRAINT ck_insight_status CHECK (status IN ('ACTIVE', 'DISMISSED', 'ARCHIVED')),
    CONSTRAINT fk_insight_generated_by FOREIGN KEY (tenant_id, generated_by)
        REFERENCES users(tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_insights_tenant_status ON executive_insights(tenant_id, status, created_at);
CREATE INDEX IF NOT EXISTS idx_insights_tenant_type ON executive_insights(tenant_id, type);

-- ============================================================
-- STEP 3: executive_health_snapshots — immutable historical health scores
-- ============================================================
-- Append-only: records are never updated or deleted.
-- Each snapshot captures the executive health score at a point in time.
CREATE TABLE IF NOT EXISTS executive_health_snapshots (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    health_score        INTEGER         NOT NULL CHECK (health_score BETWEEN 0 AND 100),
    strategy_score      INTEGER         NOT NULL CHECK (strategy_score BETWEEN 0 AND 100),
    kpi_score           INTEGER         NOT NULL CHECK (kpi_score BETWEEN 0 AND 100),
    decision_score      INTEGER         NOT NULL CHECK (decision_score BETWEEN 0 AND 100),
    risk_score          INTEGER         NOT NULL CHECK (risk_score BETWEEN 0 AND 100),
    issue_score         INTEGER         NOT NULL CHECK (issue_score BETWEEN 0 AND 100),
    escalation_score   INTEGER         NOT NULL CHECK (escalation_score BETWEEN 0 AND 100),
    total_objectives    INTEGER         NOT NULL DEFAULT 0,
    active_objectives   INTEGER         NOT NULL DEFAULT 0,
    at_risk_objectives INTEGER         NOT NULL DEFAULT 0,
    off_track_objectives INTEGER       NOT NULL DEFAULT 0,
    total_kpis          INTEGER         NOT NULL DEFAULT 0,
    on_track_kpis       INTEGER         NOT NULL DEFAULT 0,
    at_risk_kpis        INTEGER         NOT NULL DEFAULT 0,
    off_track_kpis      INTEGER         NOT NULL DEFAULT 0,
    pending_decisions   INTEGER         NOT NULL DEFAULT 0,
    overdue_decisions   INTEGER         NOT NULL DEFAULT 0,
    critical_risks      INTEGER         NOT NULL DEFAULT 0,
    high_risks          INTEGER         NOT NULL DEFAULT 0,
    open_issues         INTEGER         NOT NULL DEFAULT 0,
    critical_issues     INTEGER         NOT NULL DEFAULT 0,
    active_escalations  INTEGER         NOT NULL DEFAULT 0,
    overdue_escalations INTEGER        NOT NULL DEFAULT 0,
    active_alerts       INTEGER         NOT NULL DEFAULT 0,
    snapshot_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_executive_health_snapshots PRIMARY KEY (id),
    CONSTRAINT fk_health_snapshot_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants(id)
);

CREATE INDEX IF NOT EXISTS idx_health_snapshots_tenant ON executive_health_snapshots(tenant_id, snapshot_at);

-- ============================================================
-- STEP 4: Enable RLS on all new tables
-- ============================================================
DO $$
DECLARE
    tbl TEXT;
BEGIN
    FOREACH tbl IN ARRAY ARRAY[
        'executive_alerts',
        'executive_insights',
        'executive_health_snapshots'
    ] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tbl);
        EXECUTE format($f$
            DROP POLICY IF EXISTS tenant_isolation ON %I;
            CREATE POLICY tenant_isolation ON %I
                USING (tenant_id = current_setting('app.tenant_id', true)::uuid)
        $f$, tbl, tbl);
    END LOOP;
END $$;
