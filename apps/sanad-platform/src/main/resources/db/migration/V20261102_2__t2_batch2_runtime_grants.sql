-- ============================================================================
-- V20261102_2 — D6 T2 batch 2 follow-up: restricted runtime ACL for the
--               FORCE-RLS'd provisioning / subscription-command tables.
--
-- Mandate:  SANAD P0 G1 UNIFIED FORENSIC REMEDIATION (Issue #1462, six-table
--           acceptance matrix) — executed in the isolated PostgreSQL 17.6
--           rehearsal at PR #1465 head 81b5585fdc3254523b69572ca0fd812a6063249e.
--
-- FINDING (six-table runtime matrix, role `sanad`, 2026-11-02):
--   V20261031_1 applied ENABLE + FORCE RLS and fail-closed policies to
--   provisioning_jobs / provisioning_job_steps / subscription_commands, but
--   the chain grants NO table privilege on them to the restricted runtime
--   role `sanad` (information_schema.role_table_grants probe: 0 rows).
--   Every runtime operation fails with `permission denied for table …`
--   (reproduced live, matrix sections 4/5/6/8). Fail-closed, but
--   provisioning and subscription-command operations are unavailable for
--   every deployment that connects as the security-preferred runtime
--   identity. Same masked-gap class as the auth-token tables fixed by
--   V20261102_1: the migration owner (migrator role) bypasses ACLs via
--   ownership, so CI fixtures running as the migrator never observed it.
--
-- FIX CONTRACT (smallest safe change — no weakening):
--   Evidence-derived least privilege (all call sites audited in
--   apps/sanad-platform/src/main/java at this head):
--     - provisioning_jobs      : SELECT, INSERT, UPDATE
--         INSERT  LifecycleController#provision/retry (request-scoped)
--         UPDATE  ProvisioningJobRunner status transitions (RUNNING/final)
--         SELECT  runner + read services
--       No DELETE call site exists -> DELETE NOT granted.
--     - provisioning_job_steps : SELECT, INSERT, UPDATE
--         SELECT  ProvisioningJobRunner (succeeded-step guard)
--         INSERT/UPDATE recordStep() upsert (ON CONFLICT DO UPDATE)
--       No DELETE call site exists -> DELETE NOT granted.
--     - subscription_commands  : SELECT, INSERT
--         INSERT  SubscriptionChangeService ledger write
--         SELECT  SubscriptionDetailService history read
--       Append-only command ledger: NO UPDATE, NO DELETE call sites exist
--       -> neither granted (default-deny preserved by V20261020_1).
--   - GRANT USAGE ON SCHEMA public to `sanad` (idempotent).
--   - NO REVOKE, NO policy change, NO RLS change, NO new role, NO
--     SECURITY DEFINER, NO BYPASSRLS. FORCE RLS on all three tables
--     continues to bind `sanad` with the fail-closed app.tenant_id
--     policy (V20261031_1); tenant isolation is unaffected.
-- Forward-only. Idempotent. Never rewrites published history.
-- ============================================================================

-- Preconditions (fail closed so Flyway aborts the transaction)
DO $$
BEGIN
  IF to_regclass('public.provisioning_jobs') IS NULL
     OR to_regclass('public.provisioning_job_steps') IS NULL
     OR to_regclass('public.subscription_commands') IS NULL THEN
    RAISE EXCEPTION 'T2_BATCH2_RUNTIME_GRANTS_PRECONDITION: required tables missing';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_roles WHERE rolname = 'sanad'
      AND NOT rolsuper AND NOT rolbypassrls
  ) THEN
    RAISE EXCEPTION 'T2_BATCH2_RUNTIME_GRANTS_PRECONDITION: restricted runtime role sanad required';
  END IF;
END $$;

-- Restricted runtime ACL (evidence-derived least privilege)
GRANT USAGE ON SCHEMA public TO sanad;
GRANT SELECT, INSERT, UPDATE ON TABLE public.provisioning_jobs TO sanad;
GRANT SELECT, INSERT, UPDATE ON TABLE public.provisioning_job_steps TO sanad;
GRANT SELECT, INSERT ON TABLE public.subscription_commands TO sanad;

-- Exit guard: the exact DML surface must be present — and the ledger must
-- remain append-only (no UPDATE/DELETE on subscription_commands) — or the
-- migration fails.
DO $$
DECLARE
    tbl text;
BEGIN
  FOREACH tbl IN ARRAY ARRAY['provisioning_jobs', 'provisioning_job_steps'] LOOP
    IF NOT has_table_privilege('sanad', 'public.' || tbl, 'SELECT')
       OR NOT has_table_privilege('sanad', 'public.' || tbl, 'INSERT')
       OR NOT has_table_privilege('sanad', 'public.' || tbl, 'UPDATE') THEN
      RAISE EXCEPTION 'T2_BATCH2_RUNTIME_GRANTS_VERIFY: runtime ACL incomplete on %', tbl;
    END IF;
  END LOOP;
  IF NOT has_table_privilege('sanad', 'public.subscription_commands', 'SELECT')
     OR NOT has_table_privilege('sanad', 'public.subscription_commands', 'INSERT') THEN
    RAISE EXCEPTION 'T2_BATCH2_RUNTIME_GRANTS_VERIFY: subscription_commands ledger ACL incomplete';
  END IF;
  -- Append-only contract: UPDATE/DELETE must stay denied on the ledger.
  -- Scope: the separate-owner deployment model (migrator != sanad, e.g.
  -- Supabase prod where postgres owns and sanad runs). In the owner-as-sanad
  -- fixture model (CI/dev databases where the restricted role is also the
  -- migrator and therefore table owner) the owner legitimately holds every
  -- table privilege, so the negative check is skipped there; FORCE RLS still
  -- binds the owner and the tenant_isolation policy remains the isolation
  -- boundary. The separate-owner contract is asserted by the dedicated
  -- owner-separation tests (Auth Token Index Owner Separation / PostgreSQL
  -- Direct suites).
  IF current_user <> 'sanad' THEN
    IF has_table_privilege('sanad', 'public.subscription_commands', 'UPDATE')
       OR has_table_privilege('sanad', 'public.subscription_commands', 'DELETE') THEN
      RAISE EXCEPTION 'T2_BATCH2_RUNTIME_GRANTS_VERIFY: subscription_commands must remain append-only for sanad';
    END IF;
  END IF;
  -- RLS must remain ENABLE + FORCE on all three tables (no weakening allowed)
  FOREACH tbl IN ARRAY ARRAY['provisioning_jobs', 'provisioning_job_steps', 'subscription_commands'] LOOP
    IF NOT EXISTS (
      SELECT 1 FROM pg_class
      WHERE oid = to_regclass('public.' || tbl)
        AND relrowsecurity AND relforcerowsecurity
    ) THEN
      RAISE EXCEPTION 'T2_BATCH2_RUNTIME_GRANTS_VERIFY: FORCE RLS must remain on %', tbl;
    END IF;
  END LOOP;
END $$;
