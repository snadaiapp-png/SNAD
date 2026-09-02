-- ============================================================
-- V20260815_26: Enable RLS on governance_configurations (PG-only)
--
-- Tenant-owned table created by V20260815_24. This migration adds the
-- permissive-when-unset `tenant_isolation` policy so the table is
-- defense-in-depth protected in production. The H2 test profile relies
-- on application-layer WHERE tenant_id filtering (RLS is PG-only).
--
-- H2 no-op mirror exists at src/test/resources/db/vendor/h2/V20260815_26__enable_governance_cfg_rls.sql.
-- ============================================================

ALTER TABLE governance_configurations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON governance_configurations;
CREATE POLICY tenant_isolation ON governance_configurations
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
