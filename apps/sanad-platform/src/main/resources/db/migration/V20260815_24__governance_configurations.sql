-- ============================================================
-- V20260815_24: Governance Configuration (GAP 26)
--
-- Tenant-scoped runtime-editable governance configuration store.
-- Replaces hard-coded SLA/alert/escalation thresholds with
-- tenant-overridable defaults. If no row exists for a key, the
-- service falls back to the Java-side default (safe behavior).
--
-- Design principles (mirrors prior V20260815_* migrations):
--   * Tenant-scoped: every row carries tenant_id.
--   * State-machine CHECK constraints.
--   * TIMESTAMPTZ for temporal fields.
--   * UUID primary keys.
--   * Idempotent (IF NOT EXISTS).
--   * No flyway_schema_history manipulation.
--
-- H2 compatibility: standard DDL, runs unchanged on PG and H2.
-- ============================================================

CREATE TABLE IF NOT EXISTS governance_configurations (
    id              UUID            NOT NULL,
    tenant_id       UUID            NOT NULL,
    config_key      VARCHAR(150)    NOT NULL,
    config_value    VARCHAR(2000)   NOT NULL,
    config_type     VARCHAR(20)     NOT NULL DEFAULT 'STRING',
    description     VARCHAR(500),
    enabled         BOOLEAN         NOT NULL DEFAULT TRUE,
    updated_by      UUID,
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_governance_configurations PRIMARY KEY (id),
    CONSTRAINT uk_governance_cfg_tenant_key UNIQUE (tenant_id, config_key),
    CONSTRAINT uk_governance_cfg_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_governance_cfg_type CHECK (config_type IN ('STRING','INTEGER','DECIMAL','BOOLEAN','DURATION_ISO','JSON')),
    CONSTRAINT ck_governance_cfg_value_not_blank CHECK (config_value IS NOT NULL AND length(trim(config_value)) > 0)
);

CREATE INDEX IF NOT EXISTS idx_governance_cfg_tenant_enabled
    ON governance_configurations(tenant_id, enabled);

CREATE INDEX IF NOT EXISTS idx_governance_cfg_tenant_key
    ON governance_configurations(tenant_id, config_key);
