-- ============================================================
-- SANAD Platform - Flyway migration V28 (PostgreSQL only)
-- ------------------------------------------------------------
-- Stage 05A.1 §8 — RLS and grants for audit_chain_heads.
--
-- V27 created the audit_chain_heads table (H2+PG compatible).
-- V28 adds PostgreSQL-specific RLS policies and role grants.
-- ============================================================

-- RLS on audit_chain_heads
ALTER TABLE audit_chain_heads ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_chain_heads FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_audit_chain_heads ON audit_chain_heads
    USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant_id', true)::uuid);

-- Grant runtime role SELECT + UPDATE on chain heads (needed to lock and update)
-- The sanad_runtime_app role may not exist in all environments (e.g. backend-tests
-- uses a different role). Use DO blocks to grant only if the role exists.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sanad_runtime_app') THEN
        GRANT SELECT, UPDATE ON audit_chain_heads TO sanad_runtime_app;
        GRANT INSERT ON audit_chain_heads TO sanad_runtime_app;
    END IF;
END
$$;

-- Grant fixture CI role access for test cleanup (CI-only role)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sanad_fixture_ci') THEN
        GRANT ALL ON audit_chain_heads TO sanad_fixture_ci;
    END IF;
END
$$;
