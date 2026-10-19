-- G1 closure: production runtime-role tenant policy for public.users.
--
-- Evidence basis (2026-10-19, main@8e339af4):
--   * g1-postgres-live-security-cert #24 catalog: public.users owner=postgres,
--     rls_enabled=false; crm_accounts/hr_employees already RLS-protected.
--   * Production Flyway head rank 237 = 20261010.99 (applied 2026-10-11), and
--     rank 236 = 20261010.1 "crm accounts fail closed rls" (ALTER/owner DDL)
--     succeeded, proving the production runtime/Flyway identity is the table
--     owner role `postgres` (not the least-privilege `sanad` role).
--   * V20261011_3 enables+forces RLS on public.users with a policy scoped
--     `TO sanad` only. Once FORCE applies, the owner role would be
--     default-deny on public.users, breaking authentication on the deployed
--     backend (login and password reset resolve users post-tenant-scope).
--
-- This migration grants the production runtime role the SAME fail-closed
-- tenant predicate used for `sanad`. It introduces no bypass path: the
-- runtime role remains subject to app.tenant_id for every row access, and
-- the least-privilege contract for `sanad` is unchanged. Full removal of the
-- owner-role runtime (least-privilege switch) remains a follow-up program
-- and is tracked in the G1 closure report's residual-risk register.
--
-- Runs in the same Flyway batch as V20261011_3 (version order 20261011.3 <
-- 20261019.1), so the runtime policy exists before the backend serves
-- traffic — no default-deny window in production.

DO $policy$
BEGIN
  IF to_regclass('public.users') IS NULL THEN
    RAISE EXCEPTION 'public.users missing: aborting runtime-role policy migration';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'postgres') THEN
    RAISE EXCEPTION 'G1_RUNTIME_ROLE_UNAVAILABLE: postgres role missing';
  END IF;

  DROP POLICY IF EXISTS users_tenant_isolation_runtime ON public.users;
  CREATE POLICY users_tenant_isolation_runtime ON public.users
    AS PERMISSIVE FOR ALL TO postgres
    USING (tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''));
END
$policy$;
