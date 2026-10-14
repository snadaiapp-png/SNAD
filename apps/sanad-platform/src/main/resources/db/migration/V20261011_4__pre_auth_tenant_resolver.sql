-- G1 pre-authentication tenant discovery — DEDICATED LOOKUP TABLE design.
--
-- DESIGN (G1-R04 security redesign — eliminates BYPASSRLS + CREATEROLE):
-- Instead of a SECURITY DEFINER function that reads public.users directly
-- (which requires BYPASSRLS or a broad permissive policy), this migration
-- creates a SEPARATE lookup table (login_tenant_index) that is NOT subject
-- to RLS. A trigger on public.users keeps the index in sync.
--
-- The pre-auth function reads from login_tenant_index (no RLS to bypass).
-- No BYPASSRLS, no CREATEROLE, no column GRANT on public.users, no broad
-- permissive policy, no role membership issues.
--
-- Security advantages:
--   1. No CREATEROLE needed — the migration doesn't create any roles.
--   2. No BYPASSRLS — no role has BYPASSRLS for this function.
--   3. No column GRANT on public.users — the function doesn't read public.users.
--   4. No broad permissive policy — login_tenant_index has NO RLS.
--   5. No role membership/inheritance issues — no GRANT between roles.
--   6. login_tenant_index stores only (kind, identifier, tenant_id) —
--      no password_hash, no email (the identifier IS the email/username,
--      but the function returns only tenant_id).
--   7. The function is SECURITY INVOKER — uses the caller's (sanad)
--      privileges. sanad has SELECT on login_tenant_index (no RLS).
--   8. The sync trigger is SECURITY DEFINER owned by the migration runner
--      (table owner). It uses NEW (the trigger row) — doesn't need to
--      SELECT from public.users.
--
-- Backfill:
--   Seed migrations that insert users BEFORE V20261011_4 runs will NOT
--   have their data in login_tenant_index (the trigger doesn't exist yet).
--   For CI, this is acceptable — test fixtures insert users AFTER Flyway
--   completes (the trigger fires). For production, a separate backfill
--   step (run as superuser) is needed before enabling the pre-auth flow.

-- Precondition: sanad role must exist (for GRANT SELECT on the lookup table).
-- This matches V20261011_3's check (no rolcanlogin requirement).
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_roles WHERE rolname = 'sanad'
      AND NOT rolsuper AND NOT rolbypassrls
  ) THEN
    RAISE EXCEPTION 'G1_PREAUTH_LOOKUP_PRECONDITION: provision restricted sanad role before pre-auth resolver';
  END IF;
END $$;

-- Create the lookup table. NO RLS — this is a pre-auth discovery table
-- that needs to be readable across all tenants (the function returns only
-- tenant_id UUIDs, not user records).
CREATE TABLE IF NOT EXISTS public.login_tenant_index (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    kind text NOT NULL CHECK (kind IN ('email', 'username')),
    identifier text NOT NULL,
    tenant_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (kind, identifier, tenant_id)
);

-- Index for fast lookup by (kind, identifier).
CREATE INDEX IF NOT EXISTS idx_login_tenant_index_lookup
  ON public.login_tenant_index (kind, identifier);

-- Grant SELECT to sanad (the application runtime role).
-- sanad can read ALL rows from login_tenant_index (no RLS on this table).
-- This is intentional — pre-auth discovery needs to find tenant IDs across
-- all tenants. The function returns only tenant_id (not identifier).
GRANT SELECT ON public.login_tenant_index TO sanad;

-- Sync trigger function: keeps login_tenant_index in sync with public.users.
-- SECURITY DEFINER because the trigger fires on public.users (which has
-- FORCE RLS). The trigger function gets NEW/OLD from the trigger event —
-- it does NOT need to SELECT from public.users. It only INSERTs/DELETEs
-- into login_tenant_index (which has no RLS).
CREATE OR REPLACE FUNCTION public.sync_login_tenant_index() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
BEGIN
  IF (TG_OP = 'DELETE') THEN
    DELETE FROM public.login_tenant_index
    WHERE tenant_id = OLD.tenant_id
      AND identifier IN (OLD.email, OLD.username);
    RETURN OLD;
  END IF;

  -- INSERT or UPDATE: remove old entries, insert new ones.
  DELETE FROM public.login_tenant_index
  WHERE tenant_id = NEW.tenant_id
    AND identifier IN (OLD.email, OLD.username, NEW.email, NEW.username);

  INSERT INTO public.login_tenant_index (kind, identifier, tenant_id)
  VALUES ('email', NEW.email, NEW.tenant_id)
  ON CONFLICT (kind, identifier, tenant_id) DO NOTHING;

  IF NEW.username IS NOT NULL AND NEW.username != NEW.email THEN
    INSERT INTO public.login_tenant_index (kind, identifier, tenant_id)
    VALUES ('username', NEW.username, NEW.tenant_id)
    ON CONFLICT (kind, identifier, tenant_id) DO NOTHING;
  END IF;

  RETURN NEW;
END;
$$;

-- The trigger function needs INSERT/DELETE on login_tenant_index.
-- The function owner (migration runner = table owner) has these by default.
-- Also grant to sanad in case the trigger fires in a sanad-context.
GRANT INSERT, DELETE ON public.login_tenant_index TO sanad;

-- Attach trigger to public.users. Fires AFTER INSERT/UPDATE/DELETE.
DROP TRIGGER IF EXISTS users_sync_login_tenant_index ON public.users;
CREATE TRIGGER users_sync_login_tenant_index
  AFTER INSERT OR UPDATE OR DELETE ON public.users
  FOR EACH ROW EXECUTE FUNCTION public.sync_login_tenant_index();

-- The pre-auth function. SECURITY INVOKER — uses the caller's privileges.
-- The caller (sanad) has SELECT on login_tenant_index (no RLS on this table).
-- Returns only tenant_id UUIDs — no password_hash, no email, no username.
CREATE OR REPLACE FUNCTION public.g1_resolve_login_tenants(
    p_kind text, p_identifier text
) RETURNS TABLE(tenant_id uuid)
LANGUAGE sql STABLE SECURITY INVOKER
SET search_path = pg_catalog, public
AS $$
    SELECT DISTINCT tenant_id
      FROM public.login_tenant_index
     WHERE kind = p_kind AND identifier = p_identifier
     ORDER BY tenant_id
     LIMIT 101
$$;

-- Lock down EXECUTE privileges. Only sanad can call the function.
REVOKE ALL ON FUNCTION public.g1_resolve_login_tenants(text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.g1_resolve_login_tenants(text, text) TO sanad;
