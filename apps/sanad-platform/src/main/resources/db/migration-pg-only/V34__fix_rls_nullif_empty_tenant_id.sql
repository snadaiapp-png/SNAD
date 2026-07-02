-- ============================================================
-- SANAD Platform - Flyway migration V34 (PostgreSQL only)
-- ------------------------------------------------------------
-- Stage 05A.2.6 §3 — Fix RLS policy to handle empty string
-- from current_setting gracefully.
--
-- When app.current_tenant_id is not set, current_setting(..., true)
-- can return '' (empty string) instead of NULL on some PostgreSQL
-- configurations. Casting '' to uuid throws:
--   ERROR: invalid input syntax for type uuid: ""
--
-- This migration drops and recreates ALL tenant-isolation RLS
-- policies using NULLIF to convert empty string to NULL before
-- casting to uuid. NULL = NULL evaluates to FALSE in the policy,
-- so 0 rows are visible (fail-closed).
-- ============================================================

-- audit_events
DROP POLICY IF EXISTS tenant_isolation_audit_events ON audit_events;
CREATE POLICY tenant_isolation_audit_events ON audit_events
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

-- audit_chain_heads
DROP POLICY IF EXISTS tenant_isolation_audit_chain_heads ON audit_chain_heads;
CREATE POLICY tenant_isolation_audit_chain_heads ON audit_chain_heads
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

-- idempotency_records
DROP POLICY IF EXISTS tenant_isolation_idempotency_records ON idempotency_records;
CREATE POLICY tenant_isolation_idempotency_records ON idempotency_records
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

-- organizations
DROP POLICY IF EXISTS tenant_isolation_organizations ON organizations;
CREATE POLICY tenant_isolation_organizations ON organizations
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

-- organization_memberships
DROP POLICY IF EXISTS tenant_isolation_org_memberships ON organization_memberships;
CREATE POLICY tenant_isolation_org_memberships ON organization_memberships
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

-- users
DROP POLICY IF EXISTS tenant_isolation_users ON users;
CREATE POLICY tenant_isolation_users ON users
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

-- roles
DROP POLICY IF EXISTS tenant_isolation_roles ON roles;
CREATE POLICY tenant_isolation_roles ON roles
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

-- role_capabilities
DROP POLICY IF EXISTS tenant_isolation_role_capabilities ON role_capabilities;
CREATE POLICY tenant_isolation_role_capabilities ON role_capabilities
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

-- user_role_assignments
DROP POLICY IF EXISTS tenant_isolation_user_role_assignments ON user_role_assignments;
CREATE POLICY tenant_isolation_user_role_assignments ON user_role_assignments
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

-- refresh_tokens
DROP POLICY IF EXISTS tenant_isolation_refresh_tokens ON refresh_tokens;
CREATE POLICY tenant_isolation_refresh_tokens ON refresh_tokens
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

-- password_reset_tokens
DROP POLICY IF EXISTS tenant_isolation_password_reset_tokens ON password_reset_tokens;
CREATE POLICY tenant_isolation_password_reset_tokens ON password_reset_tokens
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
