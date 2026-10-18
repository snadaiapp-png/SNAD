-- UNAPPROVED SECURITY DESIGN — NOT A FLYWAY MIGRATION.
-- Do not promote until independently reviewed migration-owner privileges,
-- enumeration risks, RLS compatibility and tenant A/B direct tests are green.
-- SECURITY DEFINER MUST NOT be owned by the ordinary sanad runtime role.

-- G1 pre-authentication tenant identity resolver. STAGED ONLY: do not merge
-- until migration-owner privileges, identifier enumeration, and PostgreSQL
-- Direct A/B tests have independent security sign-off.
--
-- The privileged function returns tenant UUIDs ONLY, not user records or
-- password hashes. The runtime role MUST NOT receive BYPASSRLS.
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

REVOKE ALL ON FUNCTION public.g1_resolve_login_tenants(text,text) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.g1_resolve_login_tenants(text,text) FROM sanad;
GRANT EXECUTE ON FUNCTION public.g1_resolve_login_tenants(text,text) TO sanad;
-- The function must be owned by a controlled no-login migration owner with
-- tightly reviewed privileges on users; never grant BYPASSRLS to sanad.
