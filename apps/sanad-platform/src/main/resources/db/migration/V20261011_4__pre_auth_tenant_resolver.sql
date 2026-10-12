-- G1 pre-authentication tenant discovery function.
--
-- Reviewed SECURITY DEFINER boundary: lets the sanad runtime role enumerate
-- tenant IDs for a given email/username WITHOUT receiving BYPASSRLS itself.
--
-- Security invariants (Phase 4.2 of P0-01 cross-PR remediation):
--   1. Owner is a NOLOGIN role with BYPASSRLS — narrowly scoped to OWN this
--      function only. It cannot log in, cannot create databases, cannot
--      create roles, is not a superuser. Its only privilege is BYPASSRLS,
--      which the SECURITY DEFINER function needs to read public.users
--      across all tenants (necessary because V20261011_3 sets FORCE RLS).
--   2. Fixed search_path = pg_catalog prevents search_path injection. The
--      function body fully qualifies public.users, so no unqualified name
--      resolution is possible.
--   3. REVOKE ALL FROM PUBLIC; GRANT EXECUTE only to sanad. The function
--      is callable only by the application runtime role (or roles that
--      inherit sanad via GRANT).
--   4. Returns ONLY tenant_id UUIDs. No password hashes, no emails, no
--      usernames, no User entity fields. The caller cannot use this
--      function to harvest user records.
--   5. LIMIT 101 bounds enumeration. The 101st row (if any) signals
--      ambiguity to the caller without disclosing the actual count.
--   6. STABLE — read-only, no side effects, no writes.
--   7. No dynamic SQL in the function body. The only EXECUTE is for the
--      idempotent CREATE ROLE of the function owner (necessary because
--      CREATE ROLE cannot appear directly in a PL/pgSQL DO block).
--
-- Depends on: V20261011_3 (creates FORCE RLS on public.users + the
-- users_tenant_isolation policy). Without V20261011_3, the function would
-- still execute (no RLS to bypass), but the security model would be
-- incomplete. V20261011_3's fail-fast guard already aborts Flyway if the
-- sanad runtime role is not provisioned, so by the time V20261011_4 runs,
-- sanad exists with the correct (non-superuser, non-bypassRLS) flags.

-- Fail closed if the sanad runtime role is not yet provisioned. This is a
-- defensive precondition check; V20261011_3 already enforces it.
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_roles WHERE rolname = 'sanad'
      AND NOT rolsuper AND NOT rolbypassrls AND NOT rolcanlogin
  ) THEN
    RAISE EXCEPTION 'G1_PREAUTH_OWNER_PRECONDITION: provision restricted sanad role before pre-auth resolver';
  END IF;
END $$;

-- Provision the NOLOGIN BYPASSRLS function owner. This role exists ONLY
-- to own this function. It is provisioned here (not in workflow
-- provisioning scripts) because:
--   - Its existence is a migration-time concern, not a runtime concern.
--   - Keeping the role creation in the migration ensures it is always
--     present when the function is created, regardless of which workflow
--     runs Flyway.
--   - Workflows do not need to know about this role; only the migration
--     and the function's SECURITY DEFINER boundary do.
-- CREATE ROLE cannot appear directly in a PL/pgSQL DO block, so we use
-- EXECUTE for the dynamic SQL. This is the only dynamic SQL in this
-- migration and is necessary for idempotent role creation.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sanad_preauth_owner') THEN
    EXECUTE 'CREATE ROLE sanad_preauth_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE BYPASSRLS';
  END IF;
END $$;
ALTER ROLE sanad_preauth_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE BYPASSRLS;

-- Sanity: confirm the function owner is correctly scoped. Fail closed if
-- the ALTER did not take effect (e.g., due to a role conflict).
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_roles WHERE rolname = 'sanad_preauth_owner'
      AND NOT rolsuper
      AND NOT rolcreatedb
      AND NOT rolcreaterole
      AND NOT rolcanlogin
      AND rolbypassrls
  ) THEN
    RAISE EXCEPTION 'G1_PREAUTH_OWNER_CONTRACT_VIOLATION: sanad_preauth_owner must be NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE BYPASSRLS';
  END IF;
END $$;

-- Grant SELECT on public.users to the function owner. The function reads
-- only tenant_id, but the GRANT is broader (SELECT *) because column-level
-- privileges would prevent the SECURITY DEFINER function from accessing
-- the row at all. The function's SQL text is the actual security boundary
-- (it returns only tenant_id in its RETURNS clause).
GRANT SELECT ON public.users TO sanad_preauth_owner;

-- The function itself. SECURITY DEFINER means the function executes as
-- sanad_preauth_owner (the owner), not as the caller. The caller (sanad)
-- therefore gains the ability to read tenant_id across all tenants, but
-- ONLY through this function — direct SELECT from public.users still
-- enforces the users_tenant_isolation policy (because sanad does NOT have
-- BYPASSRLS, only sanad_preauth_owner does).
CREATE OR REPLACE FUNCTION public.g1_resolve_login_tenants(
    p_kind text, p_identifier text
) RETURNS TABLE(tenant_id uuid)
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = pg_catalog
AS $$
    SELECT DISTINCT u.tenant_id
      FROM public.users AS u
     WHERE (p_kind = 'email' AND u.email = p_identifier)
        OR (p_kind = 'username' AND u.username = p_identifier)
     ORDER BY u.tenant_id
     LIMIT 101
$$;

-- Re-assert the function owner. CREATE OR REPLACE preserves the existing
-- owner if the function already exists; this ALTER is a no-op on first
-- run and a safety belt on subsequent re-runs.
ALTER FUNCTION public.g1_resolve_login_tenants(text, text) OWNER TO sanad_preauth_owner;

-- Lock down EXECUTE privileges. Default PostgreSQL grants EXECUTE on
-- functions to PUBLIC; this migration revokes that default and grants
-- EXECUTE only to sanad. No other role can call this function.
REVOKE ALL ON FUNCTION public.g1_resolve_login_tenants(text, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.g1_resolve_login_tenants(text, text) FROM sanad;
GRANT EXECUTE ON FUNCTION public.g1_resolve_login_tenants(text, text) TO sanad;
