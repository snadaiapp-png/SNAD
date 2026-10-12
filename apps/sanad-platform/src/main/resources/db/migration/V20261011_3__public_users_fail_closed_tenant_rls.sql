-- G1 security: public.users was found with relrowsecurity=false and no policies.
-- This migration intentionally requires a tenant context for runtime DML.
-- DEPLOYMENT GATE: verify authentication/login, account bootstrap, password reset
-- and user administration on the exact candidate backend BEFORE promoting to prod.
-- Admin/migration roles MUST stay separate from the least-privilege sanad role.
-- No broad permissive policy or owner bypass exception is introduced.
DO $$
BEGIN
  IF to_regclass('public.users') IS NULL THEN
    RAISE EXCEPTION 'public.users missing: aborting RLS migration';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='users'
      AND column_name='tenant_id' AND data_type='uuid'
  ) THEN
    RAISE EXCEPTION 'public.users tenant_id UUID missing: aborting RLS migration';
  END IF;
END $$;

ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.users FORCE ROW LEVEL SECURITY;

-- Fail closed if the restricted runtime role was not provisioned. A policy
-- granted to the migration owner would be a misleading green CI result and
-- must never substitute for testing as the actual application role.
DO $policy$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sanad'
                 AND NOT rolsuper AND NOT rolbypassrls) THEN
    RAISE EXCEPTION 'G1_RLS_RUNTIME_ROLE_UNAVAILABLE: provision restricted sanad role before users RLS migration';
  END IF;

  DROP POLICY IF EXISTS users_tenant_isolation ON public.users;
  CREATE POLICY users_tenant_isolation ON public.users
    AS PERMISSIVE FOR ALL TO sanad
    USING (tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''));
END
$policy$;

-- Intentional fail-closed: missing tenant context returns no rows and denies writes.
-- No special exemption for unauthenticated authentication/bootstrap flows.
