-- V20261117_2 — D6/D5 follow-up: restricted runtime ACL for the
--               platform_subscription_owners directory table.
--
-- Mandate:  SNAD Session 9 Phase 03 — D5 Red Team adjudication (live probes,
--           isolated PostgreSQL 17.12 @ 127.0.0.1:5433, fresh chain to head
--           20261117.1, 2026-11-18).
-- Base:     Session 9 integration line: 4cee76ad7 (main, PR #1538) merged with
--           PR #1537 (f7d2ea40) and the re-certified WS7 branch (7a0bcae0).
--
-- FINDING (PostgreSQL Direct probes, evidence transcript
-- download/session9-preservation/d5-redteam-probes/PROBES-TRANSCRIPT-20261118.txt):
--   V20261116_2 grants runtime EXECUTE on its trigger FUNCTIONS only and
--   follows the same ACL shape note as V20261102_1's function grants, but
--   NO table privilege for the restricted runtime role `sanad` on
--   public.platform_subscription_owners (information_schema.role_table_grants
--   probe: 0 rows for grantee='sanad'; grants exist only for sanad_migrator).
--   The chain itself mandates that runtime role:
--     - V20261011_3 precondition requires a restricted `sanad` (non-super,
--       no BYPASSRLS) before any tenant RLS;
--     - V20261103_2 grants the runtime DML matrix on tenant_subscriptions
--       (SELECT, INSERT, UPDATE) — so runtime subscription INSERTs run.
--   Live probe results as `sanad` (separated topology, fresh DB):
--     PROBE_A  SELECT tenant_id FROM platform_subscription_owners
--              -> ERROR 42501 permission denied (control-plane lookups in
--              SaasAdministrationService / SubscriptionOperatingUnitService
--              fail at runtime);
--     PROBE_B  INSERT INTO tenant_subscriptions (runtime-legal per V20261103_2)
--              -> the invoker-rights sync trigger fn_sync_subscription_owner
--              fails inside itself with `permission denied for table
--              platform_subscription_owners` — EVERY runtime subscription
--              INSERT breaks (commerce lifecycle unavailable).
--   The same defect class was already adjudicated for the token tables by
--   V20261102_1; this migration closes it for the D5 directory with the
--   identical contract.
--
-- EFFECT BEFORE THIS MIGRATION:
--   As `sanad`: subscription INSERT fails inside the sync trigger; the
--   trusted owner-tenant directory is unreadable. Fail-closed (no privilege
--   escalation), but the D5 executive/detail and control-plane paths and
--   basic subscription creation are functionally broken for the
--   security-preferred runtime identity.
--
-- FIX CONTRACT (smallest safe change — no weakening):
--   - GRANT SELECT, INSERT, UPDATE, DELETE on platform_subscription_owners
--     to `sanad` (the exact privilege set the V20261102_1 contract uses for
--     runtime-owned mirror tables; INSERT/UPDATE/DELETE are required because
--     the V20261116_2 trigger functions are SECURITY INVOKER by design —
--     they execute with the calling role's privileges).
--   - GRANT USAGE ON SCHEMA public to `sanad` (same V20261102_1 line; needed
--     whenever schema USAGE is not granted to PUBLIC).
--   - NO new REVOKE (default-deny from V20261020_1 stays untouched),
--     NO policy change, NO RLS change, NO new role, NO SECURITY DEFINER,
--     NO BYPASSRLS. The table stays a non-RLS PLATFORM-REFERENCE mapping
--     (ownership fact only — no tenant data), classified identically to the
--     tenants directory by V20261116_2's header contract.
--   - The C3 rollback-injection slot for the g5 cert re-derives to
--     V20261118_1 = chain_tail + 1 (runtime-generated in the disposable
--     chain copy; NEVER committed to the repository).
-- Forward-only. Idempotent. Never rewrites published history.
-- ============================================================================

-- Preconditions (fail closed so Flyway aborts the transaction)
DO $$
BEGIN
  IF to_regclass('public.platform_subscription_owners') IS NULL THEN
    RAISE EXCEPTION 'SUB_OWNER_RUNTIME_GRANTS_PRECONDITION: platform_subscription_owners missing';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_roles WHERE rolname = 'sanad'
      AND NOT rolsuper AND NOT rolbypassrls
  ) THEN
    RAISE EXCEPTION 'SUB_OWNER_RUNTIME_GRANTS_PRECONDITION: restricted runtime role sanad required';
  END IF;
END $$;
-- Restricted runtime ACL (mirrors the V20261102_1 contract for runtime mirrors)
GRANT USAGE ON SCHEMA public TO sanad;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.platform_subscription_owners TO sanad;
-- Exit guard: the full DML surface must be present or the migration fails
DO $$
BEGIN
  IF NOT has_table_privilege('sanad', 'public.platform_subscription_owners', 'SELECT')
     OR NOT has_table_privilege('sanad', 'public.platform_subscription_owners', 'INSERT')
     OR NOT has_table_privilege('sanad', 'public.platform_subscription_owners', 'UPDATE')
     OR NOT has_table_privilege('sanad', 'public.platform_subscription_owners', 'DELETE') THEN
    RAISE EXCEPTION 'SUB_OWNER_RUNTIME_GRANTS_VERIFY: runtime ACL incomplete';
  END IF;
  -- The trigger functions must keep their pinned search_path and default-deny
  -- EXECUTE contract (V20261116_2 / S6) — no weakening allowed by this grants
  -- migration.
  IF EXISTS (
    SELECT 1 FROM pg_proc
    WHERE oid = to_regprocedure('public.fn_sync_subscription_owner()')
      AND proconfig IS DISTINCT FROM ARRAY['search_path=pg_catalog, public, pg_temp']
  ) OR EXISTS (
    SELECT 1 FROM pg_proc
    WHERE oid = to_regprocedure('public.fn_delete_subscription_owner()')
      AND proconfig IS DISTINCT FROM ARRAY['search_path=pg_catalog, public, pg_temp']
  ) THEN
    RAISE EXCEPTION 'SUB_OWNER_RUNTIME_GRANTS_VERIFY: trigger function search_path contract altered';
  END IF;
END $$;
