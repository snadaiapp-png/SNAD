-- SNAD-SEC-RLS-EXEC-2026-10-26 — RLS forensic remediation: function boundary hardening.
-- Forward-only Flyway migration. Do not edit after apply (checksum discipline).
--
-- Source of truth:
--   * docs/security/RLS_FORENSIC_REMEDIATION_2026-10-26.md  (live read-only catalog
--     snapshot of Supabase project tkbrvupemreqabwzdpyq, taken 2026-10-26)
--   * docs/security/candidate_function_hardening.sql        (candidate reviewed on
--     branch security/rls-forensic-remediation, commit a8d4a81e)
--
-- Findings addressed (from the 2026-10-26 production snapshot):
--   FN-01  16 public functions with unset/mutable search_path
--          (Supabase Security Advisor finding).
--   RPC-01/02  public.sync_login_tenant_index() is SECURITY DEFINER, its EXECUTE
--          was inherited from PUBLIC, so anon/authenticated could reach it via RPC.
--
-- Relationship to V20261020_1: that migration already (a) revokes EXECUTE from
-- PUBLIC/anon/authenticated and (b) pins search_path on all non-extension-owned
-- public functions *at its run time*. The 2026-10-26 production snapshot still
-- reported both findings, therefore production had not received V20261020_1.
-- This migration re-asserts the remediation for the exact audited set with
-- fail-closed verification guards, so the fix is explicit, independent of
-- catalog state at V20261020_1 run time, and audit-traceable per finding ID.
--
-- Naming note: V20261026_1..V20261026_3 were never persisted on this branch and
-- were never applied to any database (prior session workspace was lost before
-- commit). Numbering follows the remediation owner's instruction; no drift risk
-- exists because this is the first migration of the RLS forensic task in this
-- chain.
--
-- REVISION (2026-10-29, pre-merge CI closure, PR #1460): the G1 and G4 role
-- probes originally used a single SELECT whose WHERE clause combined
-- "EXISTS(pg_roles)" with has_schema_privilege()/has_function_privilege().
-- PostgreSQL does not guarantee the evaluation order of WHERE predicates, so
-- on clusters without the Supabase client roles (e.g. the CI PostgreSQL 16.15
-- provisioned by ci.yml) the planner could evaluate the privilege probe for
-- the absent role first and abort with SQLSTATE 42704 ("role \"anon\" does not
-- exist"). Both probes are rewritten as deterministic two-phase evaluation:
-- role existence is resolved first (FOR-loop query, no privilege function),
-- then the privilege is probed per existing role, statement by statement.
-- Semantics, failure conditions and RAISE messages are UNCHANGED — this is a
-- determinism repair, not a relaxation. Checksum note: this migration has
-- never been recorded in any Flyway-managed schema history (production
-- flyway_schema_history stops at 20261021.1; CI databases are ephemeral and
-- destroyed per run), so the in-place repair is forward-only-safe.
--
-- Guard rails ("REVOKE مزوّد بالحواجز"):
--   G0  every audited function must exist, else abort (fail closed);
--   G1  schema public must not grant CREATE to PUBLIC/anon/authenticated
--       (shadow-object precondition from the candidate review), else abort;
--   G2  client-role revocation is applied only for roles that exist;
--   G3  the explicit service_role EXECUTE grant is (re-)asserted, never removed;
--   G4  post-apply verification aborts the migration unless: 17/17 audited
--       functions have a pinned search_path; sync_login_tenant_index() remains
--       SECURITY DEFINER; its trigger users_sync_login_tenant_index remains
--       attached and enabled; anon/authenticated no longer hold EXECUTE.
-- The function is NOT switched to SECURITY INVOKER and its search_path
-- (pg_catalog, public) is intentionally left as shipped by V20261011_4.

-- -----------------------------------------------------------------------------
-- G0 — audited functions must exist (fail closed)
-- -----------------------------------------------------------------------------
DO $rls_forensic_g0$
DECLARE
  v_missing text;
BEGIN
  SELECT string_agg(name, ', ') INTO v_missing
  FROM (
    VALUES
      ('public.enforce_final_partner_recovery_capability()'),
      ('public.enforce_last_partner_admin()'),
      ('public.enforce_partner_delegation_grant_lifecycle()'),
      ('public.enforce_partner_membership_capability_lifecycle()'),
      ('public.enforce_partner_membership_recovery_survival()'),
      ('public.enforce_partner_tenant_binding_lifecycle()'),
      ('public.hr_t7_offer_versions_append_only_guard()'),
      ('public.trg_partner_delegation_capabilities_audit()'),
      ('public.trg_partner_delegation_capabilities_updated_at()'),
      ('public.trg_partner_delegation_grants_audit()'),
      ('public.trg_partner_membership_capabilities_audit()'),
      ('public.trg_partner_memberships_audit()'),
      ('public.trg_partner_memberships_updated_at()'),
      ('public.trg_partner_tenant_bindings_audit()'),
      ('public.trg_partners_authorization_change_event()'),
      ('public.trg_partners_updated_at()'),
      ('public.sync_login_tenant_index()')
  ) AS audited(name)
  WHERE to_regprocedure(name) IS NULL;

  IF v_missing IS NOT NULL THEN
    RAISE EXCEPTION 'RLS_FN_G0: audited functions absent from catalog: %', v_missing;
  END IF;
END
$rls_forensic_g0$;

-- -----------------------------------------------------------------------------
-- G1 — schema shadow-object precondition (candidate review requirement)
-- -----------------------------------------------------------------------------
DO $rls_forensic_g1$
DECLARE
  v_risky text;
  v_role  text;
BEGIN
  -- Deterministic two-phase probe: PostgreSQL does not guarantee WHERE
  -- predicate evaluation order, so a single-level filter could evaluate
  -- has_schema_privilege() for a role that does not exist (SQLSTATE 42704).
  -- Phase 1 resolves existing roles only (no privilege function in this
  -- query); Phase 2 probes each existing role statement by statement.
  FOR v_role IN
    SELECT candidate.role_name
    FROM (
      VALUES ('public'), ('anon'), ('authenticated')
    ) AS candidate(role_name)
    WHERE EXISTS (SELECT 1 FROM pg_roles WHERE rolname = candidate.role_name)
  LOOP
    IF has_schema_privilege(v_role, 'public', 'CREATE') THEN
      v_risky := concat_ws(', ', v_risky, v_role);
    END IF;
  END LOOP;

  IF v_risky IS NOT NULL THEN
    RAISE EXCEPTION
      'RLS_FN_G1: schema public grants CREATE to (%) — revoke schema CREATE before applying search_path pins', v_risky;
  END IF;
END
$rls_forensic_g1$;

-- -----------------------------------------------------------------------------
-- FN-01 — pin search_path for the 16 audited functions
-- (ordering matches V20261020_1: pg_catalog first, pg_temp last)
-- -----------------------------------------------------------------------------
ALTER FUNCTION public.enforce_final_partner_recovery_capability()
  SET search_path TO pg_catalog, public, pg_temp;
ALTER FUNCTION public.enforce_last_partner_admin()
  SET search_path TO pg_catalog, public, pg_temp;
ALTER FUNCTION public.enforce_partner_delegation_grant_lifecycle()
  SET search_path TO pg_catalog, public, pg_temp;
ALTER FUNCTION public.enforce_partner_membership_capability_lifecycle()
  SET search_path TO pg_catalog, public, pg_temp;
ALTER FUNCTION public.enforce_partner_membership_recovery_survival()
  SET search_path TO pg_catalog, public, pg_temp;
ALTER FUNCTION public.enforce_partner_tenant_binding_lifecycle()
  SET search_path TO pg_catalog, public, pg_temp;
ALTER FUNCTION public.hr_t7_offer_versions_append_only_guard()
  SET search_path TO pg_catalog, public, pg_temp;
ALTER FUNCTION public.trg_partner_delegation_capabilities_audit()
  SET search_path TO pg_catalog, public, pg_temp;
ALTER FUNCTION public.trg_partner_delegation_capabilities_updated_at()
  SET search_path TO pg_catalog, public, pg_temp;
ALTER FUNCTION public.trg_partner_delegation_grants_audit()
  SET search_path TO pg_catalog, public, pg_temp;
ALTER FUNCTION public.trg_partner_membership_capabilities_audit()
  SET search_path TO pg_catalog, public, pg_temp;
ALTER FUNCTION public.trg_partner_memberships_audit()
  SET search_path TO pg_catalog, public, pg_temp;
ALTER FUNCTION public.trg_partner_memberships_updated_at()
  SET search_path TO pg_catalog, public, pg_temp;
ALTER FUNCTION public.trg_partner_tenant_bindings_audit()
  SET search_path TO pg_catalog, public, pg_temp;
ALTER FUNCTION public.trg_partners_authorization_change_event()
  SET search_path TO pg_catalog, public, pg_temp;
ALTER FUNCTION public.trg_partners_updated_at()
  SET search_path TO pg_catalog, public, pg_temp;

-- -----------------------------------------------------------------------------
-- RPC-01/02 — close the RPC surface of the SECURITY DEFINER trigger function
-- -----------------------------------------------------------------------------
REVOKE EXECUTE ON FUNCTION public.sync_login_tenant_index() FROM PUBLIC;

DO $rls_forensic_rpc$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
    REVOKE EXECUTE ON FUNCTION public.sync_login_tenant_index() FROM anon;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
    REVOKE EXECUTE ON FUNCTION public.sync_login_tenant_index() FROM authenticated;
  END IF;
  -- G3 — preserve the operational service_role grant observed in production.
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
    GRANT EXECUTE ON FUNCTION public.sync_login_tenant_index() TO service_role;
  END IF;
END
$rls_forensic_rpc$;

-- -----------------------------------------------------------------------------
-- G4 — post-apply verification (fail closed)
-- -----------------------------------------------------------------------------
DO $rls_forensic_g4$
DECLARE
  v_unpinned int;
  v_still_exposed text;
  v_role text;
BEGIN
  -- 17/17 audited functions must carry a pinned search_path
  SELECT count(*) INTO v_unpinned
  FROM (
    VALUES
      ('public.enforce_final_partner_recovery_capability()'),
      ('public.enforce_last_partner_admin()'),
      ('public.enforce_partner_delegation_grant_lifecycle()'),
      ('public.enforce_partner_membership_capability_lifecycle()'),
      ('public.enforce_partner_membership_recovery_survival()'),
      ('public.enforce_partner_tenant_binding_lifecycle()'),
      ('public.hr_t7_offer_versions_append_only_guard()'),
      ('public.trg_partner_delegation_capabilities_audit()'),
      ('public.trg_partner_delegation_capabilities_updated_at()'),
      ('public.trg_partner_delegation_grants_audit()'),
      ('public.trg_partner_membership_capabilities_audit()'),
      ('public.trg_partner_memberships_audit()'),
      ('public.trg_partner_memberships_updated_at()'),
      ('public.trg_partner_tenant_bindings_audit()'),
      ('public.trg_partners_authorization_change_event()'),
      ('public.trg_partners_updated_at()'),
      ('public.sync_login_tenant_index()')
  ) AS audited(name)
  WHERE EXISTS (
    SELECT 1 FROM pg_proc p
    WHERE p.oid = to_regprocedure(name)
      AND (
        p.proconfig IS NULL
        OR NOT EXISTS (
          SELECT 1 FROM unnest(p.proconfig) AS cfg
          WHERE cfg LIKE 'search_path=%'
        )
      )
  );

  IF v_unpinned > 0 THEN
    RAISE EXCEPTION 'RLS_FN_G4: % audited functions still lack a pinned search_path', v_unpinned;
  END IF;

  -- sync_login_tenant_index must remain SECURITY DEFINER (trigger dependency)
  IF EXISTS (
    SELECT 1 FROM pg_proc p
    WHERE p.oid = to_regprocedure('public.sync_login_tenant_index()')
      AND NOT p.prosecdef
  ) THEN
    RAISE EXCEPTION 'RLS_FN_G4: sync_login_tenant_index must remain SECURITY DEFINER';
  END IF;

  -- its trigger must remain attached and enabled
  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger t
    WHERE t.tgrelid = to_regclass('public.users')
      AND t.tgname = 'users_sync_login_tenant_index'
      AND (t.tgtype & 1) <> 0          -- row-level trigger
      AND t.tgenabled IN ('O', 'A', 'R')  -- enabled for origin/always/replica
  ) THEN
    RAISE EXCEPTION 'RLS_FN_G4: trigger users_sync_login_tenant_index missing or disabled';
  END IF;

  -- client roles must no longer hold EXECUTE (checks explicit AND inherited/PUBLIC)
  -- Deterministic two-phase probe, same rationale as the G1 revision note:
  -- existence first, privilege probe second, so an absent client role cannot
  -- reach has_function_privilege() and abort with SQLSTATE 42704.
  v_still_exposed := NULL;
  FOR v_role IN
    SELECT candidate.role_name
    FROM (
      VALUES ('anon'), ('authenticated')
    ) AS candidate(role_name)
    WHERE EXISTS (SELECT 1 FROM pg_roles WHERE rolname = candidate.role_name)
  LOOP
    IF has_function_privilege(v_role, 'public.sync_login_tenant_index()', 'EXECUTE') THEN
      v_still_exposed := concat_ws(', ', v_still_exposed, v_role);
    END IF;
  END LOOP;

  IF v_still_exposed IS NOT NULL THEN
    RAISE EXCEPTION 'RLS_FN_G4: client roles retain EXECUTE on sync_login_tenant_index: %', v_still_exposed;
  END IF;
END
$rls_forensic_g4$;

-- Post-apply manual checks (mirrors docs/security/candidate_function_hardening.sql):
--   * INSERT/UPDATE (email or username change)/DELETE on public.users keeps
--     login_tenant_index correct  -> tests/security/rls_fn_regression_20261026.sql
--   * partner lifecycle and HR append-only trigger regression pass
--   * Supabase Security Advisor no longer reports these findings
