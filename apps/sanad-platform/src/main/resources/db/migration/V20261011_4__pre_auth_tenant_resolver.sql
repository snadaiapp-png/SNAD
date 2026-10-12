-- G1 pre-authentication tenant discovery function.
--
-- CORRECTIVE VERSION (P0-02 redesign): eliminates BYPASSRLS from the
-- pre-auth design. The function owner now has a narrow column-level GRANT
-- (tenant_id, email, username only) + a permissive SELECT policy. No
-- BYPASSRLS privilege is granted anywhere.
--
-- Security invariants (Phase 4.2 of P0-01 cross-PR remediation):
--   1. Owner is a NOLOGIN role WITHOUT BYPASSRLS — narrowly scoped to
--      OWN this function only. It cannot log in, cannot create databases,
--      cannot create roles, is not a superuser, and does NOT bypass RLS.
--   2. Fixed search_path = pg_catalog prevents search_path injection.
--      Function body fully qualifies public.users.
--   3. REVOKE ALL FROM PUBLIC; GRANT EXECUTE only to sanad. The function
--      is callable only by the application runtime role (or roles that
--      inherit sanad via GRANT).
--   4. Returns ONLY tenant_id UUIDs. No password hashes, no emails, no
--      usernames, no User entity fields. The caller cannot use this
--      function to harvest user records.
--   5. LIMIT 101 bounds enumeration. The 101st row signals ambiguity to
--      the caller without disclosing the actual count.
--   6. STABLE — read-only, no side effects, no writes.
--   7. No dynamic SQL in the function body. The only EXECUTE is for the
--      idempotent CREATE ROLE of the function owner (necessary because
--      CREATE ROLE cannot appear directly in a PL/pgSQL DO block).
--
-- ACCESS PATH (how the function reads public.users under FORCE RLS):
--   a. Function is SECURITY DEFINER owned by sanad_preauth_owner.
--   b. sanad_preauth_owner has GRANT SELECT (tenant_id, email, username)
--      on public.users — column-level privilege restricts to 3 columns.
--   c. sanad_preauth_owner has a permissive SELECT policy
--      (users_preauth_owner_discovery) — allows all rows.
--   d. Combined: sanad_preauth_owner can read (tenant_id, email, username)
--      for all rows, but NOT password_hash or other sensitive columns.
--   e. The function's SQL text returns only tenant_id — the caller gets
--      only tenant IDs, never email/username/password_hash.
--
-- This is BETTER than BYPASSRLS (which would allow reading ALL columns
-- on ALL tables). The function owner cannot read password hashes, and
-- cannot bypass RLS on other tables.
--
-- Precondition: checks for the sanad role (WITHOUT the rolcanlogin check
-- that was in the original V20261011_4). The sanad role may be LOGIN (as
-- in ci.yml's provisioning) or NOLOGIN (as in g3-authenticated-acceptance
-- provisioning). Both are acceptable — the function only needs sanad to
-- exist as a non-superuser, non-bypassRLS role to receive the EXECUTE GRANT.

-- Fail closed if the sanad runtime role is not yet provisioned. This
-- matches V20261011_3's check (without the rolcanlogin requirement that
-- was incorrectly added in the original V20261011_4).
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_roles WHERE rolname = 'sanad'
      AND NOT rolsuper AND NOT rolbypassrls
  ) THEN
    RAISE EXCEPTION 'G1_PREAUTH_OWNER_PRECONDITION: provision restricted sanad role before pre-auth resolver';
  END IF;
END $$;

-- Provision the NOLOGIN function owner. This role exists ONLY to own
-- this function. It is NOLOGIN (cannot be logged into) and does NOT
-- have BYPASSRLS — the function reads public.users via a column-level
-- GRANT + permissive SELECT policy, not via RLS bypass.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sanad_preauth_owner') THEN
    EXECUTE 'CREATE ROLE sanad_preauth_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS';
  END IF;
END $$;
ALTER ROLE sanad_preauth_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;

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
      AND NOT rolbypassrls
  ) THEN
    RAISE EXCEPTION 'G1_PREAUTH_OWNER_CONTRACT_VIOLATION: sanad_preauth_owner must be NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS';
  END IF;
END $$;

-- Revoke any prior broad SELECT grant on public.users from
-- sanad_preauth_owner (in case a prior version of this migration granted
-- all columns). Then grant ONLY the 3 columns needed for pre-auth
-- discovery: tenant_id, email, username. password_hash and other
-- sensitive columns are NOT granted.
REVOKE SELECT ON public.users FROM sanad_preauth_owner;
GRANT SELECT (tenant_id, email, username) ON public.users TO sanad_preauth_owner;

-- Permissive SELECT policy for the function owner. This allows the
-- function (which executes as sanad_preauth_owner via SECURITY DEFINER)
-- to read all rows from public.users — necessary for cross-tenant
-- pre-auth discovery. The column-level GRANT above restricts WHICH
-- columns are readable; this policy restricts WHICH ROWS are visible
-- (all rows, in this case).
CREATE POLICY IF NOT EXISTS users_preauth_owner_discovery ON public.users
  AS PERMISSIVE FOR SELECT TO sanad_preauth_owner
  USING (true);

-- The function itself. SECURITY DEFINER means the function executes as
-- sanad_preauth_owner (the owner), not as the caller. The caller (sanad)
-- therefore gains the ability to read tenant_id across all tenants, but
-- ONLY through this function — direct SELECT from public.users by sanad
-- still enforces the users_tenant_isolation policy (because sanad does
-- NOT have the users_preauth_owner_discovery policy; only
-- sanad_preauth_owner does).
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
