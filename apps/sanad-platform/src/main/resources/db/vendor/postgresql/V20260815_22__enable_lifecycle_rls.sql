-- ============================================================
-- V20260815_22: Enable RLS on lifecycle base tables (PG-only)
--
-- Defense-in-depth gap identified by the v20260815.7 forensic audit:
-- the lifecycle base tables (tenants, organizations, users, roles,
-- access_capabilities, role_capabilities, user_role_assignments,
-- saas_plans, saas_plan_entitlements, tenant_subscriptions,
-- billing_invoices, subscription_change_events, modules,
-- module_capabilities, plan_module_entitlements, platform_audit_logs,
-- system_services) rely solely on application-layer WHERE tenant_id
-- filtering. This migration enables PostgreSQL Row-Level Security
-- as a second line of defense.
--
-- Design choice: ENABLE (not FORCE). The application connects as a
-- non-superuser role that respects RLS. Flyway migrations run as the
-- table owner and bypass RLS (intentional — needed for backfills and
-- seed operations). The TenantRlsConnectionHandler sets
-- `SET LOCAL app.tenant_id` per request so the policy is enforced for
-- every application query.
--
-- platform_audit_logs uses a relaxed policy because some audit rows
-- are control-plane events (target_tenant_id IS NULL); these are
-- visible to every authenticated tenant by design.
--
-- H2 compatibility: H2 has no RLS support. A no-op mirror migration
-- exists at src/test/resources/db/vendor/h2/V20260815_22__enable_lifecycle_rls.sql
-- to maintain Flyway version parity. H2 tests rely on application-layer
-- tenant filtering (snad.rls.enabled=false in application-local.yml).
-- ============================================================

DO $$
DECLARE
    tbl TEXT;
    policy_sql TEXT;
BEGIN
    -- Tables that have a single tenant_id column.
    FOREACH tbl IN ARRAY ARRAY[
        'tenants',
        'organizations',
        'organization_memberships',
        'users',
        'roles',
        'access_capabilities',
        'role_capabilities',
        'user_role_assignments',
        'saas_plans',
        'saas_plan_entitlements',
        'tenant_subscriptions',
        'billing_invoices',
        'subscription_change_events',
        'modules',
        'module_capabilities',
        'plan_module_entitlements',
        'system_services'
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
