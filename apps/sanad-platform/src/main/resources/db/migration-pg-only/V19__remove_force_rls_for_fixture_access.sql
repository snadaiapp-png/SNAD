-- ============================================================
-- SANAD Platform - Flyway migration V19 (PostgreSQL only)
-- ------------------------------------------------------------
-- Stage 04A.3.1 — Remove FORCE RLS so table owner (migration_owner)
-- can insert test fixtures without RLS restrictions.
--
-- KEEP: ENABLE ROW LEVEL SECURITY (runtime role is still subject to RLS)
-- REMOVE: FORCE ROW LEVEL SECURITY (table owner no longer forced)
--
-- This is safe because:
-- - The runtime role (sanad_runtime_app) is NOT the table owner
-- - The runtime role does NOT have BYPASSRLS
-- - RLS still applies to all non-owner roles
-- - The table owner (migration_owner) is only used for migrations
--   and test fixture creation — never for application runtime queries
-- ============================================================

ALTER TABLE organizations NO FORCE ROW LEVEL SECURITY;
ALTER TABLE organization_memberships NO FORCE ROW LEVEL SECURITY;
ALTER TABLE users NO FORCE ROW LEVEL SECURITY;
ALTER TABLE roles NO FORCE ROW LEVEL SECURITY;
ALTER TABLE role_capabilities NO FORCE ROW LEVEL SECURITY;
ALTER TABLE user_role_assignments NO FORCE ROW LEVEL SECURITY;
ALTER TABLE refresh_tokens NO FORCE ROW LEVEL SECURITY;
ALTER TABLE password_reset_tokens NO FORCE ROW LEVEL SECURITY;
