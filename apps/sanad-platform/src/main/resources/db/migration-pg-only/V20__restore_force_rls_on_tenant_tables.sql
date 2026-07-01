-- ============================================================
-- SANAD Platform - Flyway migration V20 (PostgreSQL only)
-- ------------------------------------------------------------
-- Stage 04A.3.2 §4 — Restore FORCE ROW LEVEL SECURITY.
--
-- V19 temporarily removed FORCE RLS to support an early test-fixture
-- approach that used the migration_owner for fixture inserts.
-- V20 restores the required production defense-in-depth policy.
--
-- Test fixtures must use a CI-only privileged fixture role
-- (sanad_fixture_ci with BYPASSRLS) instead of weakening the
-- production schema.
--
-- With FORCE RLS:
--   - sanad_runtime_app (runtime): subject to RLS (non-owner, no BYPASSRLS)
--   - sanad_migration_owner (table owner): subject to RLS (FORCE applies to owner)
--   - sanad_fixture_ci (CI-only): BYPASSRLS — exempt from RLS for fixture setup
-- ============================================================

ALTER TABLE organizations FORCE ROW LEVEL SECURITY;
ALTER TABLE organization_memberships FORCE ROW LEVEL SECURITY;
ALTER TABLE users FORCE ROW LEVEL SECURITY;
ALTER TABLE roles FORCE ROW LEVEL SECURITY;
ALTER TABLE role_capabilities FORCE ROW LEVEL SECURITY;
ALTER TABLE user_role_assignments FORCE ROW LEVEL SECURITY;
ALTER TABLE refresh_tokens FORCE ROW LEVEL SECURITY;
ALTER TABLE password_reset_tokens FORCE ROW LEVEL SECURITY;
