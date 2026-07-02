-- ============================================================
-- SANAD Platform - Flyway migration V17 (PostgreSQL only)
-- ------------------------------------------------------------
-- Stage 04 §15-16 — PostgreSQL Row-Level Security for tenant-owned tables.
--
-- This migration is in db/migration-pg-only/ and is only loaded by the
-- prod profile (PostgreSQL). The local profile (H2) does not load this
-- directory — H2 does not support RLS.
--
-- Tables protected (all TENANT_OWNED per docs/tenant-isolation/04-tenant-domain-inventory.json):
--   - organizations, organization_memberships, users, roles,
--     role_capabilities, user_role_assignments, refresh_tokens,
--     password_reset_tokens
--
-- Tables NOT protected (per docs/tenant-isolation/04-global-table-exceptions.json):
--   - tenants (root table — no tenant_id column)
--   - access_capabilities (GLOBAL_REFERENCE — catalog of platform capabilities)
--
-- RLS policy:
--   USING  (tenant_id = current_setting('app.current_tenant_id', true)::uuid)
--   WITH CHECK (tenant_id = current_setting('app.current_tenant_id', true)::uuid)
--
-- The application sets `SET LOCAL app.current_tenant_id = '<uuid>'` inside
-- each transaction (via TenantRlsBinder). If the setting is missing,
-- `current_setting(..., true)` returns NULL and the policy evaluates to
-- FALSE for every row — the database fails CLOSED.
-- ============================================================

-- organizations
ALTER TABLE organizations ENABLE ROW LEVEL SECURITY;
ALTER TABLE organizations FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_organizations ON organizations;
CREATE POLICY tenant_isolation_organizations ON organizations
    USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant_id', true)::uuid);

-- organization_memberships
ALTER TABLE organization_memberships ENABLE ROW LEVEL SECURITY;
ALTER TABLE organization_memberships FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_org_memberships ON organization_memberships;
CREATE POLICY tenant_isolation_org_memberships ON organization_memberships
    USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant_id', true)::uuid);

-- users
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE users FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_users ON users;
CREATE POLICY tenant_isolation_users ON users
    USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant_id', true)::uuid);

-- roles
ALTER TABLE roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE roles FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_roles ON roles;
CREATE POLICY tenant_isolation_roles ON roles
    USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant_id', true)::uuid);

-- role_capabilities
ALTER TABLE role_capabilities ENABLE ROW LEVEL SECURITY;
ALTER TABLE role_capabilities FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_role_capabilities ON role_capabilities;
CREATE POLICY tenant_isolation_role_capabilities ON role_capabilities
    USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant_id', true)::uuid);

-- user_role_assignments
ALTER TABLE user_role_assignments ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_role_assignments FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_user_role_assignments ON user_role_assignments;
CREATE POLICY tenant_isolation_user_role_assignments ON user_role_assignments
    USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant_id', true)::uuid);

-- refresh_tokens
ALTER TABLE refresh_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE refresh_tokens FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_refresh_tokens ON refresh_tokens;
CREATE POLICY tenant_isolation_refresh_tokens ON refresh_tokens
    USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant_id', true)::uuid);

-- password_reset_tokens
ALTER TABLE password_reset_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE password_reset_tokens FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_password_reset_tokens ON password_reset_tokens;
CREATE POLICY tenant_isolation_password_reset_tokens ON password_reset_tokens
    USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant_id', true)::uuid);
