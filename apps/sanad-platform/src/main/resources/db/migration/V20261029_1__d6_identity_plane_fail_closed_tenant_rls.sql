-- D6 closure (SANAD-G1): identity-plane fail-closed tenant RLS — forward-only.
--
-- Mandate: SANAD-SEC-G1-D6-CLOSURE (D6 — tables carrying tenant_id without RLS).
-- Base:    main@71e4371ffdbd91f7bf26f4c445ccc72ce4f362be (PR #1457 / D1 merged).
--
-- Live-catalog inventory at base (PostgreSQL 17.4, clean Flyway replay of all
-- 241 migrations as the least-privilege `sanad` role) proved the following
-- identity-plane tables carry a NOT NULL tenant_id yet had NO RLS at all:
--   roles, role_capabilities, user_role_assignments,
--   organizations, organization_memberships
--
-- These tables are read and written by authenticated, tenant-scoped flows:
--   * login bootstrap (AuthService applies the tenant GUC explicitly,
--     TenantRlsTransactionContext.applyForCurrentTransaction)
--   * capability evaluation and RBAC administration (@RequireCapability paths
--     resolve tenant from the validated JWT, never from client input)
--   * role template provisioning (RegistrationProvisioner / credential
--     bootstrap apply the tenant GUC for the target tenant)
-- so a fail-closed tenant policy is compatible with the sanctioned access
-- paths. Any unsanctioned path (no tenant context) now returns zero rows and
-- is denied writes, instead of silently crossing tenants.
--
-- Policy pattern mirrors the certified users-table wave (V20261011_3 +
-- V20261019_1): ENABLE + FORCE + permissive fail-closed policies granted to
-- the least-privilege runtime role `sanad` AND to `postgres`, the production
-- runtime/Flyway identity. The NULLIF(current_setting(...), '') form avoids
-- the empty-GUC cast failure observed on older policies.
--
-- Validation-only elsewhere: no data is read, modified or dropped. Fails
-- closed (RAISE EXCEPTION) so Flyway aborts when preconditions are unmet.

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_roles WHERE rolname = 'sanad'
      AND NOT rolsuper AND NOT rolbypassrls
  ) THEN
    RAISE EXCEPTION 'G1_D6_RUNTIME_ROLE_UNAVAILABLE: provision restricted sanad role before identity-plane RLS migration';
  END IF;
  IF to_regclass('public.roles') IS NULL
    OR to_regclass('public.role_capabilities') IS NULL
    OR to_regclass('public.user_role_assignments') IS NULL
    OR to_regclass('public.organizations') IS NULL
    OR to_regclass('public.organization_memberships') IS NULL THEN
    RAISE EXCEPTION 'G1_D6_IDENTITY_TABLES_MISSING: aborting identity-plane RLS migration';
  END IF;
END $$;

-- ============================================================================
-- roles
-- ============================================================================
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='roles'
      AND column_name='tenant_id' AND data_type='uuid' AND is_nullable='NO'
  ) THEN
    RAISE EXCEPTION 'G1_D6_PRECONDITION: roles.tenant_id UUID NOT NULL missing';
  END IF;
END $$;
ALTER TABLE public.roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.roles FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS roles_tenant_isolation ON public.roles;
CREATE POLICY roles_tenant_isolation ON public.roles
  AS PERMISSIVE FOR ALL TO sanad
  USING (tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''))
  WITH CHECK (tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''));
DROP POLICY IF EXISTS roles_tenant_isolation_runtime ON public.roles;
-- The postgres runtime policy is conditional: container-based environments
-- (e.g. the CRM contract/architecture CI jobs boot postgres:16-alpine with
-- POSTGRES_USER=sanad_contract / sanad_arch) have NO postgres role at all.
-- Production (Supabase) does, per V20261019_1.
DO $d6runtime$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'postgres') THEN
    EXECUTE 'CREATE POLICY roles_tenant_isolation_runtime ON public.roles '
         || 'AS PERMISSIVE FOR ALL TO postgres '
         || 'USING (tenant_id::text = NULLIF(current_setting(''app.tenant_id'', true), '''')) '
         || 'WITH CHECK (tenant_id::text = NULLIF(current_setting(''app.tenant_id'', true), ''''))';
  END IF;
END $d6runtime$;

-- ============================================================================
-- role_capabilities
-- ============================================================================
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='role_capabilities'
      AND column_name='tenant_id' AND data_type='uuid' AND is_nullable='NO'
  ) THEN
    RAISE EXCEPTION 'G1_D6_PRECONDITION: role_capabilities.tenant_id UUID NOT NULL missing';
  END IF;
END $$;
ALTER TABLE public.role_capabilities ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.role_capabilities FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS role_capabilities_tenant_isolation ON public.role_capabilities;
CREATE POLICY role_capabilities_tenant_isolation ON public.role_capabilities
  AS PERMISSIVE FOR ALL TO sanad
  USING (tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''))
  WITH CHECK (tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''));
DROP POLICY IF EXISTS role_capabilities_tenant_isolation_runtime ON public.role_capabilities;
-- The postgres runtime policy is conditional: container-based environments
-- (e.g. the CRM contract/architecture CI jobs boot postgres:16-alpine with
-- POSTGRES_USER=sanad_contract / sanad_arch) have NO postgres role at all.
-- Production (Supabase) does, per V20261019_1.
DO $d6runtime$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'postgres') THEN
    EXECUTE 'CREATE POLICY role_capabilities_tenant_isolation_runtime ON public.role_capabilities '
         || 'AS PERMISSIVE FOR ALL TO postgres '
         || 'USING (tenant_id::text = NULLIF(current_setting(''app.tenant_id'', true), '''')) '
         || 'WITH CHECK (tenant_id::text = NULLIF(current_setting(''app.tenant_id'', true), ''''))';
  END IF;
END $d6runtime$;

-- ============================================================================
-- user_role_assignments
-- ============================================================================
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='user_role_assignments'
      AND column_name='tenant_id' AND data_type='uuid' AND is_nullable='NO'
  ) THEN
    RAISE EXCEPTION 'G1_D6_PRECONDITION: user_role_assignments.tenant_id UUID NOT NULL missing';
  END IF;
END $$;
ALTER TABLE public.user_role_assignments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_role_assignments FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS user_role_assignments_tenant_isolation ON public.user_role_assignments;
CREATE POLICY user_role_assignments_tenant_isolation ON public.user_role_assignments
  AS PERMISSIVE FOR ALL TO sanad
  USING (tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''))
  WITH CHECK (tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''));
DROP POLICY IF EXISTS user_role_assignments_tenant_isolation_runtime ON public.user_role_assignments;
-- The postgres runtime policy is conditional: container-based environments
-- (e.g. the CRM contract/architecture CI jobs boot postgres:16-alpine with
-- POSTGRES_USER=sanad_contract / sanad_arch) have NO postgres role at all.
-- Production (Supabase) does, per V20261019_1.
DO $d6runtime$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'postgres') THEN
    EXECUTE 'CREATE POLICY user_role_assignments_tenant_isolation_runtime ON public.user_role_assignments '
         || 'AS PERMISSIVE FOR ALL TO postgres '
         || 'USING (tenant_id::text = NULLIF(current_setting(''app.tenant_id'', true), '''')) '
         || 'WITH CHECK (tenant_id::text = NULLIF(current_setting(''app.tenant_id'', true), ''''))';
  END IF;
END $d6runtime$;

-- ============================================================================
-- organizations
-- ============================================================================
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='organizations'
      AND column_name='tenant_id' AND data_type='uuid' AND is_nullable='NO'
  ) THEN
    RAISE EXCEPTION 'G1_D6_PRECONDITION: organizations.tenant_id UUID NOT NULL missing';
  END IF;
END $$;
ALTER TABLE public.organizations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.organizations FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS organizations_tenant_isolation ON public.organizations;
CREATE POLICY organizations_tenant_isolation ON public.organizations
  AS PERMISSIVE FOR ALL TO sanad
  USING (tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''))
  WITH CHECK (tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''));
DROP POLICY IF EXISTS organizations_tenant_isolation_runtime ON public.organizations;
-- The postgres runtime policy is conditional: container-based environments
-- (e.g. the CRM contract/architecture CI jobs boot postgres:16-alpine with
-- POSTGRES_USER=sanad_contract / sanad_arch) have NO postgres role at all.
-- Production (Supabase) does, per V20261019_1.
DO $d6runtime$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'postgres') THEN
    EXECUTE 'CREATE POLICY organizations_tenant_isolation_runtime ON public.organizations '
         || 'AS PERMISSIVE FOR ALL TO postgres '
         || 'USING (tenant_id::text = NULLIF(current_setting(''app.tenant_id'', true), '''')) '
         || 'WITH CHECK (tenant_id::text = NULLIF(current_setting(''app.tenant_id'', true), ''''))';
  END IF;
END $d6runtime$;

-- ============================================================================
-- organization_memberships
-- ============================================================================
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='organization_memberships'
      AND column_name='tenant_id' AND data_type='uuid' AND is_nullable='NO'
  ) THEN
    RAISE EXCEPTION 'G1_D6_PRECONDITION: organization_memberships.tenant_id UUID NOT NULL missing';
  END IF;
END $$;
ALTER TABLE public.organization_memberships ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.organization_memberships FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS organization_memberships_tenant_isolation ON public.organization_memberships;
CREATE POLICY organization_memberships_tenant_isolation ON public.organization_memberships
  AS PERMISSIVE FOR ALL TO sanad
  USING (tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''))
  WITH CHECK (tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''));
DROP POLICY IF EXISTS organization_memberships_tenant_isolation_runtime ON public.organization_memberships;
-- The postgres runtime policy is conditional: container-based environments
-- (e.g. the CRM contract/architecture CI jobs boot postgres:16-alpine with
-- POSTGRES_USER=sanad_contract / sanad_arch) have NO postgres role at all.
-- Production (Supabase) does, per V20261019_1.
DO $d6runtime$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'postgres') THEN
    EXECUTE 'CREATE POLICY organization_memberships_tenant_isolation_runtime ON public.organization_memberships '
         || 'AS PERMISSIVE FOR ALL TO postgres '
         || 'USING (tenant_id::text = NULLIF(current_setting(''app.tenant_id'', true), '''')) '
         || 'WITH CHECK (tenant_id::text = NULLIF(current_setting(''app.tenant_id'', true), ''''))';
  END IF;
END $d6runtime$;
