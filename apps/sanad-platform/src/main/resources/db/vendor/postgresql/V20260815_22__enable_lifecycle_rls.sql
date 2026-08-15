-- ============================================================
-- V20260815_22: Enable RLS on tenant-owned lifecycle tables (PG-only)
--
-- Defense-in-depth gap identified by the v20260815.7 forensic audit:
-- the tenant-owned lifecycle base tables (organizations, users, roles,
-- role_capabilities, user_role_assignments, tenant_subscriptions,
-- billing_invoices, subscription_change_events) rely solely on
-- application-layer WHERE tenant_id filtering. This migration enables
-- PostgreSQL Row-Level Security as a second line of defense.
--
-- TABLES NOT INCLUDED (intentional — these are GLOBAL catalogs with
-- no tenant_id column; enabling RLS on them would fail and would be
-- semantically wrong since they are reference data shared across tenants):
--   * tenants              (the tenant root itself; uses id column)
--   * access_capabilities  (global capability catalog)
--   * saas_plans           (global plan catalog)
--   * saas_plan_entitlements (legacy feature catalog)
--   * modules              (global module catalog)
--   * module_capabilities  (global catalog of cap→module)
--   * plan_module_entitlements (links 2 global catalogs)
--   * system_services      (global service catalog)
--
-- TABLES INCLUDED (have tenant_id column):
--   * organizations, organization_memberships, users, roles,
--     role_capabilities, user_role_assignments, tenant_subscriptions,
--     billing_invoices, subscription_change_events
--
-- SPECIAL CASE: platform_audit_logs uses target_tenant_id (nullable for
-- control-plane events). Policy: visible when target IS NULL (control-
-- plane event) OR matches the active tenant context.
--
-- Design choice: ENABLE (not FORCE). The application connects as a
-- non-superuser role that respects RLS. Flyway migrations run as the
-- table owner and bypass RLS (intentional — needed for backfills and
-- seed operations). The TenantRlsConnectionHandler sets
-- `SET LOCAL app.tenant_id` per request so the policy is enforced for
-- every application query.
--
-- H2 compatibility: H2 has no RLS support. A no-op mirror migration
-- exists at src/test/resources/db/vendor/h2/V20260815_22__enable_lifecycle_rls.sql
-- to maintain Flyway version parity.
-- ============================================================

DO $$
DECLARE
    tbl TEXT;
BEGIN
    -- Tenant-owned tables with a single tenant_id column.
    FOREACH tbl IN ARRAY ARRAY[
        'organizations',
        'organization_memberships',
        'users',
        'roles',
        'role_capabilities',
        'user_role_assignments',
        'tenant_subscriptions',
        'billing_invoices',
        'subscription_change_events'
    ] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tbl);
        EXECUTE format($f$
            DROP POLICY IF EXISTS tenant_isolation ON %I;
            CREATE POLICY tenant_isolation ON %I
                USING (tenant_id = current_setting('app.tenant_id', true)::uuid)
        $f$, tbl, tbl);
    END LOOP;

    -- platform_audit_logs uses target_tenant_id (nullable for control-plane events).
    -- Policy: row is visible if (a) target_tenant_id is NULL (control-plane),
    -- OR (b) target_tenant_id matches the active tenant context.
    ALTER TABLE platform_audit_logs ENABLE ROW LEVEL SECURITY;
    DROP POLICY IF EXISTS tenant_isolation ON platform_audit_logs;
    CREATE POLICY tenant_isolation ON platform_audit_logs
        USING (target_tenant_id IS NULL
               OR target_tenant_id = current_setting('app.tenant_id', true)::uuid);
END $$;
