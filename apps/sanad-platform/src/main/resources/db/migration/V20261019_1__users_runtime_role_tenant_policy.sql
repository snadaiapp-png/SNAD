-- G1 closure: runtime-role tenant policy for public.users.
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
-- This migration grants the runtime/Flyway identity the SAME fail-closed
-- tenant predicate used for `sanad`. It introduces no bypass path: the
-- runtime role remains subject to app.tenant_id for every row access, and
-- the least-privilege contract for `sanad` is unchanged. Full removal of the
-- owner-role runtime (least-privilege switch) remains a follow-up program
-- and is tracked in the G1 closure report's residual-risk register.
--
-- Role resolution (fail-closed, environment-independent):
--   * The policy target is resolved at migration time from the executing
--     identity (`current_user`). In every certified environment the backend
--     runtime uses the same role Flyway executes as:
--     - Production (Supabase tkbrvupemreqabwzdpyq): Flyway connects as
--       `postgres` -> policy `TO postgres`, identical to the original
--       production intent of this migration.
--     - Containerized CI (crm-api-contract-validation POSTGRES_USER=
--       sanad_contract, crm-modular-architecture-validation sanad_arch,
--       crm-authenticated-acceptance sanad_test): the bootstrap superuser is
--       NOT named `postgres` and that role does not exist cluster-wide, so
--       the policy targets the actual bootstrap identity instead of aborting
--       the whole Flyway batch (this guard previously failed the backend
--       boot with G1_RUNTIME_ROLE_UNAVAILABLE in "CRM API Contract
--       Validation" run 39239329366 job 118016039108).
--   * When the `postgres` role exists and differs from `current_user`
--     (host-native CI runners), BOTH roles are covered by the same
--     fail-closed predicate, so the production contract stays explicit even
--     if the migration identity ever drifts.
--   * The `postgres` role is no longer REQUIRED to exist for this migration
--     to apply: a NOTICE records the resolved targets for audit. Aborting on
--     its absence added no production safety — the policy predicate itself
--     is default-deny unless `app.tenant_id` is set, for every target role.
--
-- Runs in the same Flyway batch as V20261011_3 (version order 20261011.3 <
-- 20261019.1), so the runtime policy exists before the backend serves
-- traffic — no default-deny window in production.

DO $policy$
DECLARE
  target_roles text;
BEGIN
  IF to_regclass('public.users') IS NULL THEN
    RAISE EXCEPTION 'public.users missing: aborting runtime-role policy migration';
  END IF;

  SELECT string_agg(format('%I', role_name), ', ' ORDER BY role_name)
    INTO target_roles
  FROM (
    SELECT current_user AS role_name
    UNION
    SELECT 'postgres'::name AS role_name
      WHERE EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'postgres')
  ) resolved;

  EXECUTE format('DROP POLICY IF EXISTS users_tenant_isolation_runtime ON public.users');
  EXECUTE format(
    'CREATE POLICY users_tenant_isolation_runtime ON public.users AS PERMISSIVE FOR ALL TO %s USING (tenant_id::text = NULLIF(current_setting(''app.tenant_id'', true), '''')) WITH CHECK (tenant_id::text = NULLIF(current_setting(''app.tenant_id'', true), ''''))',
    target_roles);

  RAISE NOTICE 'G1_RUNTIME_ROLE_POLICY_APPLIED table=public.users targets=(%)', target_roles;
END
$policy$;
