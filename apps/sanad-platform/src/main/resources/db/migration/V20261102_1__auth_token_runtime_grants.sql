-- ============================================================================
-- V20261102_1 — D6 batch 1 follow-up: restricted runtime ACL for the
--               FORCE-RLS'd auth token tables.
--
-- Mandate:  SANAD P0 G1 PHASE 4 (Issue #1462) — independent rehearsal finding.
-- Base:     PR #1458 head 1dbb1db9d50ac8464a94b9c9b98d475d6e4fdc05.
--
-- FINDING (PostgreSQL Direct rehearsal, isolated PostgreSQL 16.4, 2026-11-02):
--   The 248-migration chain contains NO table privilege for the restricted
--   runtime role `sanad` on public.refresh_tokens / public.password_reset_tokens
--   (information_schema.role_table_grants probe: 0 rows for grantee='sanad').
--   The chain itself mandates that runtime role:
--     - V20261011_3 precondition requires a restricted `sanad` (non-super,
--       no BYPASSRLS) before any tenant RLS;
--     - V20261028_2 precondition repeats the gate before token-table RLS;
--     - V20261101_1 grants the runtime role ACLs on the pre-auth index ONLY.
--   The CI contract harness (tests/security/pr1458_auth_index_acl.sql lines
--   11-12) must inject the token-table grants out-of-band to pass — masking
--   the gap for every deployment that connects as the restricted role.
--
-- EFFECT BEFORE THIS MIGRATION:
--   As `sanad`: every refresh-token / password-reset DML fails with
--   `permission denied for table refresh_tokens` (reproduced in the Phase 4
--   rehearsal). Fail-closed, but authentication availability is broken for
--   the security-preferred runtime identity.
--
-- FIX CONTRACT (smallest safe change — no weakening):
--   - GRANT SELECT, INSERT, UPDATE, DELETE on the two token tables to `sanad`
--     (the exact privilege set the CI contract harness grants).
--   - GRANT USAGE ON SCHEMA public to `sanad` (same harness line 11; needed
--     whenever schema USAGE is not granted to PUBLIC).
--   - NO new REVOKE (default-deny from V20261020_1 stays untouched),
--     NO policy change, NO RLS change, NO new role, NO SECURITY DEFINER,
--     NO BYPASSRLS. FORCE RLS on the token tables continues to bind `sanad`
--     with the fail-closed `app.tenant_id` policy (V20261028_2).
-- Forward-only. Idempotent. Never rewrites published history.
-- ============================================================================

-- Preconditions (fail closed so Flyway aborts the transaction)
DO $$
BEGIN
  IF to_regclass('public.refresh_tokens') IS NULL
     OR to_regclass('public.password_reset_tokens') IS NULL THEN
    RAISE EXCEPTION 'AUTH_TOKEN_RUNTIME_GRANTS_PRECONDITION: token tables missing';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_roles WHERE rolname = 'sanad'
      AND NOT rolsuper AND NOT rolbypassrls
  ) THEN
    RAISE EXCEPTION 'AUTH_TOKEN_RUNTIME_GRANTS_PRECONDITION: restricted runtime role sanad required';
  END IF;
END $$;

-- Restricted runtime ACL (mirrors tests/security/pr1458_auth_index_acl.sql L11-12)
GRANT USAGE ON SCHEMA public TO sanad;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.refresh_tokens TO sanad;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.password_reset_tokens TO sanad;

-- Exit guard: the full DML surface must be present or the migration fails
DO $$
DECLARE
    tbl text;
BEGIN
  IF NOT has_table_privilege('sanad', 'public.refresh_tokens', 'SELECT')
     OR NOT has_table_privilege('sanad', 'public.refresh_tokens', 'INSERT')
     OR NOT has_table_privilege('sanad', 'public.refresh_tokens', 'UPDATE')
     OR NOT has_table_privilege('sanad', 'public.refresh_tokens', 'DELETE')
     OR NOT has_table_privilege('sanad', 'public.password_reset_tokens', 'SELECT')
     OR NOT has_table_privilege('sanad', 'public.password_reset_tokens', 'INSERT')
     OR NOT has_table_privilege('sanad', 'public.password_reset_tokens', 'UPDATE')
     OR NOT has_table_privilege('sanad', 'public.password_reset_tokens', 'DELETE') THEN
    RAISE EXCEPTION 'AUTH_TOKEN_RUNTIME_GRANTS_VERIFY: runtime ACL incomplete';
  END IF;
  -- RLS must remain ENABLE + FORCE on both token tables (no weakening allowed)
  FOREACH tbl IN ARRAY ARRAY['refresh_tokens', 'password_reset_tokens'] LOOP
    IF NOT EXISTS (
      SELECT 1 FROM pg_class
      WHERE oid = to_regclass('public.' || tbl)
        AND relrowsecurity AND relforcerowsecurity
    ) THEN
      RAISE EXCEPTION 'AUTH_TOKEN_RUNTIME_GRANTS_VERIFY: FORCE RLS must remain on %', tbl;
    END IF;
  END LOOP;
END $$;
