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
GRANT SELECT, UPDATE ON audit_chain_heads TO sanad_runtime_app;
GRANT INSERT ON audit_chain_heads TO sanad_runtime_app;

-- Grant fixture CI role access for test cleanup
GRANT ALL ON audit_chain_heads TO sanad_fixture_ci;
