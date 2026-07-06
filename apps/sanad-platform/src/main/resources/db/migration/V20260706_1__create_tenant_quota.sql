-- ============================================================
-- SANAD Stage 08 Sprint 1 — ST8-S1-002 Tenant Quota Model
-- ------------------------------------------------------------
-- Per-tenant quota table for API rate limiting, AI token budgets,
-- storage, webhooks, and background jobs.
--
-- Security: tenant-scoped. Application enforces no cross-tenant reads.
-- Audit: quota changes are recorded via application audit log.
-- ============================================================

CREATE TABLE IF NOT EXISTS tenant_quota (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    dimension       VARCHAR(32)  NOT NULL,
    limit_value     BIGINT       NOT NULL,
    used_value      BIGINT       NOT NULL DEFAULT 0,
    reset_at        TIMESTAMP WITH TIME ZONE  NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tenant_quota_tenant_dim UNIQUE (tenant_id, dimension),
    CONSTRAINT chk_tenant_quota_dimension
        CHECK (dimension IN ('API_RPM', 'API_RPD', 'AI_TOKENS_DAY', 'AI_TOKENS_MONTH',
                             'STORAGE_GB', 'WEBHOOKS_DAY', 'JOBS_DAY')),
    CONSTRAINT chk_tenant_quota_limit_nonneg CHECK (limit_value >= 0),
    CONSTRAINT chk_tenant_quota_used_nonneg  CHECK (used_value >= 0)
);

CREATE INDEX IF NOT EXISTS idx_tenant_quota_tenant_dim ON tenant_quota (tenant_id, dimension);
CREATE INDEX IF NOT EXISTS idx_tenant_quota_reset_at  ON tenant_quota (reset_at);

COMMENT ON TABLE  tenant_quota IS 'Stage 08 Sprint 1 ST8-S1-002: per-tenant resource quotas';
COMMENT ON COLUMN tenant_quota.dimension IS 'Quota dimension: API_RPM, API_RPD, AI_TOKENS_DAY, AI_TOKENS_MONTH, STORAGE_GB, WEBHOOKS_DAY, JOBS_DAY';
COMMENT ON COLUMN tenant_quota.limit_value IS 'Maximum allowed value for this dimension';
COMMENT ON COLUMN tenant_quota.used_value  IS 'Current consumed value since last reset';
COMMENT ON COLUMN tenant_quota.reset_at    IS 'When the quota resets to zero (UTC)';
