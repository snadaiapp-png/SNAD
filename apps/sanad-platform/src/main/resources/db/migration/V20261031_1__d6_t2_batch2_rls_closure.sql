-- ============================================================================
-- V20261031_1 — D6 Batch 2: T2 deferred-contract RLS closure (Issue #1462)
--               + owner-bypass FORCE bind for five legacy-shaped CRM tables.
--
-- Mandate:  SANAD P0 RLS FULL REMEDIATION (Issue #1462, checkboxes 2-3),
--           continuing SANAD-SEC-G1-D6-CLOSURE after D6 Batch 1
--           (refresh_tokens / password_reset_tokens, PR #1458).
-- Base:     main@5e854ef4426d7ff500675be69f15ff3358db917a
--
-- SCOPE (evidence-backed, tests/security/rls_tenant_owned_classification.json
-- class T2-DEFERRED-CONTRACT + PostgreSQL Direct suite FAIL entries):
--
--   1. provisioning_jobs, provisioning_job_steps, subscription_commands
--        -> ENABLE + FORCE RLS, fail-closed tenant policy (TO PUBLIC).
--      Access paths verified BEFORE enforcement (mandate §5.3/§5.8):
--        - ProvisioningJobRunner is request-scoped (LifecycleController
--          provision/retry endpoints) -> JWT-bound tenant GUC applies.
--        - TrialExpirationService (background) is adapted in the SAME
--          change set to the established dunning pattern
--          (BillingStateService#runDunningCycleOnce): enumerate tenants
--          from the non-RLS platform directory, apply tenant-local GUC
--          per transaction via TenantRlsTransactionContext.
--        - User-facing lifecycle commands run inside JWT-bound requests.
--
--   2. login_tenant_index (pre-auth discovery — NOT tenant-RLS by design)
--        -> ACL closure: explicit REVOKE ALL FROM PUBLIC, re-grant only the
--      required minimal surface (SELECT/INSERT/DELETE to sanad; the sync
--      trigger needs INSERT/DELETE, the resolver function needs SELECT),
--      REVOKE UPDATE (no update path exists), contract COMMENT.
--      Residual, documented: the identifier column stores the login
--      identifier in plaintext by design (possession of DB access already
--      implies user-row access); hashing to the auth_token_tenant_index
--      pattern is the recommended follow-up and is intentionally NOT a
--      policy change within this P0 batch.
--
--   3. Owner-bypass repair (PostgreSQL Direct suite FAIL evidence):
--      crm_next_best_actions (cross-tenant READ leak reproduced: with a
--      foreign tenant GUC the tenant-A row was returned — policy was the
--      legacy permissive-when-unset shape and the table was not FORCE
--      bound, so the owner role bypassed entirely), and the four sibling
--      tables shipped with the same legacy shape without FORCE:
--      crm_assignment_rules, crm_queue_memberships, crm_transfer_steps,
--      crm_sales_teams. All five get: FORCE ROW LEVEL SECURITY + the
--      fail-closed NULLIF policy language of V20261021_1/V20261029_1.
--
-- Guards fail closed (RAISE EXCEPTION aborts Flyway). Forward-only: no
-- historical migration is modified. No BYPASSRLS, no superuser, no new
-- role, no SECURITY DEFINER function is created by this migration.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Preconditions (fail closed)
-- ---------------------------------------------------------------------------
DO $guard$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_roles WHERE rolname = 'sanad'
      AND NOT rolsuper AND NOT rolbypassrls
  ) THEN
    RAISE EXCEPTION 'D6_T2_BATCH2_RUNTIME_ROLE_UNAVAILABLE: provision restricted sanad role before T2 batch 2 RLS';
  END IF;

  IF to_regclass('public.provisioning_jobs') IS NULL
     OR to_regclass('public.provisioning_job_steps') IS NULL
     OR to_regclass('public.subscription_commands') IS NULL
     OR to_regclass('public.login_tenant_index') IS NULL
     OR to_regclass('public.crm_next_best_actions') IS NULL
     OR to_regclass('public.crm_assignment_rules') IS NULL
     OR to_regclass('public.crm_queue_memberships') IS NULL
     OR to_regclass('public.crm_transfer_steps') IS NULL
     OR to_regclass('public.crm_sales_teams') IS NULL THEN
    RAISE EXCEPTION 'D6_T2_BATCH2_PRECONDITION: required table(s) missing';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema='public' AND table_name='provisioning_jobs' AND column_name='tenant_id')
     OR NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema='public' AND table_name='subscription_commands' AND column_name='tenant_id') THEN
    RAISE EXCEPTION 'D6_T2_BATCH2_PRECONDITION: tenant_id column missing on a T2 table';
  END IF;
  -- provisioning_job_steps is tenant-scoped THROUGH provisioning_jobs (job_id FK);
  -- it has no tenant_id column of its own (verified against information_schema).
  IF EXISTS (SELECT 1 FROM information_schema.columns
             WHERE table_schema='public' AND table_name='provisioning_job_steps' AND column_name='tenant_id') THEN
    RAISE EXCEPTION 'D6_T2_BATCH2_PRECONDITION: provisioning_job_steps unexpectedly gained a tenant_id column — re-derive the policy shape';
  END IF;
END
$guard$;

-- ---------------------------------------------------------------------------
-- 1. Fail-closed FORCE RLS: provisioning_jobs / provisioning_job_steps /
--    subscription_commands (policy language identical to V20261021_1)
-- ---------------------------------------------------------------------------
ALTER TABLE public.provisioning_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.provisioning_jobs FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON public.provisioning_jobs;
CREATE POLICY tenant_isolation ON public.provisioning_jobs
  AS PERMISSIVE FOR ALL TO PUBLIC
  USING (tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''))
  WITH CHECK (tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''));

ALTER TABLE public.provisioning_job_steps ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.provisioning_job_steps FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON public.provisioning_job_steps;
-- Tenant ownership flows through the parent job. The subquery is evaluated
-- under the SAME session GUC, so provisioning_jobs' own FORCE RLS applies
-- inside it: with tenant A's context only A's jobs are visible, therefore
-- only A's steps pass. WITH CHECK protects cross-tenant step insertion.
CREATE POLICY tenant_isolation ON public.provisioning_job_steps
  AS PERMISSIVE FOR ALL TO PUBLIC
  USING (EXISTS (
      SELECT 1 FROM public.provisioning_jobs j
      WHERE j.id = provisioning_job_steps.job_id
        AND j.tenant_id::text = NULLIF(current_setting('app.tenant_id', true), '')
  ))
  WITH CHECK (EXISTS (
      SELECT 1 FROM public.provisioning_jobs j
      WHERE j.id = provisioning_job_steps.job_id
        AND j.tenant_id::text = NULLIF(current_setting('app.tenant_id', true), '')
  ));

ALTER TABLE public.subscription_commands ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.subscription_commands FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON public.subscription_commands;
CREATE POLICY tenant_isolation ON public.subscription_commands
  AS PERMISSIVE FOR ALL TO PUBLIC
  USING (tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''))
  WITH CHECK (tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''));

-- ---------------------------------------------------------------------------
-- 2. login_tenant_index — ACL closure (pre-auth discovery contract)
-- ---------------------------------------------------------------------------
REVOKE ALL ON public.login_tenant_index FROM PUBLIC;
REVOKE UPDATE ON public.login_tenant_index FROM sanad;
GRANT SELECT ON public.login_tenant_index TO sanad;
GRANT INSERT, DELETE ON public.login_tenant_index TO sanad;

COMMENT ON TABLE public.login_tenant_index IS
  'PRE-AUTH DISCOVERY (no RLS by documented design — V20261011_4 contract): maps login identifier -> tenant_id UUIDs only. ACL (V20261031_1): SELECT/INSERT/DELETE restricted to the sanad runtime role, UPDATE revoked (sync trigger only inserts/deletes), PUBLIC revoked. Residual risk (documented, out of P0 scope): identifier stored in plaintext; hashing to the auth_token_tenant_index pattern is the recommended follow-up.';

-- ---------------------------------------------------------------------------
-- 3. Owner-bypass repair: five legacy-shaped CRM tables
--    (ENABLE already set by their creation migrations; FORCE was missing,
--     and the legacy permissive-when-unset policy is replaced fail-closed)
-- ---------------------------------------------------------------------------
ALTER TABLE public.crm_next_best_actions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crm_next_best_actions FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON public.crm_next_best_actions;
CREATE POLICY tenant_isolation ON public.crm_next_best_actions
  AS PERMISSIVE FOR ALL TO PUBLIC
  USING (tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''))
  WITH CHECK (tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''));

ALTER TABLE public.crm_assignment_rules ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crm_assignment_rules FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON public.crm_assignment_rules;
CREATE POLICY tenant_isolation ON public.crm_assignment_rules
  AS PERMISSIVE FOR ALL TO PUBLIC
  USING (tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''))
  WITH CHECK (tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''));

ALTER TABLE public.crm_queue_memberships ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crm_queue_memberships FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON public.crm_queue_memberships;
CREATE POLICY tenant_isolation ON public.crm_queue_memberships
  AS PERMISSIVE FOR ALL TO PUBLIC
  USING (tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''))
  WITH CHECK (tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''));

ALTER TABLE public.crm_transfer_steps ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crm_transfer_steps FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON public.crm_transfer_steps;
CREATE POLICY tenant_isolation ON public.crm_transfer_steps
  AS PERMISSIVE FOR ALL TO PUBLIC
  USING (tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''))
  WITH CHECK (tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''));

ALTER TABLE public.crm_sales_teams ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crm_sales_teams FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON public.crm_sales_teams;
CREATE POLICY tenant_isolation ON public.crm_sales_teams
  AS PERMISSIVE FOR ALL TO PUBLIC
  USING (tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''))
  WITH CHECK (tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''));

-- ---------------------------------------------------------------------------
-- Catalog self-verification (aborts the migration if anything above drifted)
-- ---------------------------------------------------------------------------
DO $verify$
DECLARE
  v_bad INT;
BEGIN
  SELECT count(*) INTO v_bad
  FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
  WHERE n.nspname = 'public'
    AND c.relname IN ('provisioning_jobs', 'provisioning_job_steps',
                      'subscription_commands', 'crm_next_best_actions',
                      'crm_assignment_rules', 'crm_queue_memberships',
                      'crm_transfer_steps', 'crm_sales_teams')
    AND (c.relrowsecurity = false OR c.relforcerowsecurity = false);
  IF v_bad > 0 THEN
    RAISE EXCEPTION 'D6_T2_BATCH2_VERIFY: % table(s) missing ENABLE+FORCE after migration', v_bad;
  END IF;

  SELECT count(*) INTO v_bad
  FROM pg_policy p
  JOIN pg_class c ON c.oid = p.polrelid
  JOIN pg_namespace n ON n.oid = c.relnamespace
  WHERE n.nspname = 'public'
    AND c.relname IN ('provisioning_jobs', 'subscription_commands',
                      'crm_next_best_actions', 'crm_assignment_rules',
                      'crm_queue_memberships', 'crm_transfer_steps',
                      'crm_sales_teams')
    AND pg_get_expr(p.polqual, p.polrelid) NOT LIKE '%NULLIF(current_setting%';
  IF v_bad > 0 THEN
    RAISE EXCEPTION 'D6_T2_BATCH2_VERIFY: % policy(ies) not fail-closed NULLIF shape', v_bad;
  END IF;

  SELECT count(*) INTO v_bad
  FROM pg_policy p
  JOIN pg_class c ON c.oid = p.polrelid
  JOIN pg_namespace n ON n.oid = c.relnamespace
  WHERE n.nspname = 'public' AND c.relname = 'provisioning_job_steps'
    AND pg_get_expr(p.polqual, p.polrelid) NOT LIKE '%provisioning_jobs%';
  IF v_bad > 0 THEN
    RAISE EXCEPTION 'D6_T2_BATCH2_VERIFY: provisioning_job_steps policy does not reference the parent jobs table';
  END IF;
END
$verify$;
