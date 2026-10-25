-- CANDIDATE ONLY — not a numbered migration and NOT applied to any database.
-- Apply only after review in a disposable PostgreSQL environment and after
-- regression tests pass. This file contains catalog-derived function hardening.
--
-- 1) Prevent client roles from invoking the SECURITY DEFINER trigger function
--    through PostgREST RPC. Preserve the existing explicit service_role grant.
REVOKE EXECUTE ON FUNCTION public.sync_login_tenant_index()
  FROM PUBLIC, anon, authenticated;

-- 2) Pin search_path for the 16 catalog-confirmed functions currently lacking it.
-- These functions are trigger/lifecycle helpers and use public relations by
-- unqualified name; public CREATE is currently not granted to anon/authenticated
-- or PUBLIC. Re-run ACL/schema privilege checks before applying.
ALTER FUNCTION public.enforce_final_partner_recovery_capability()
  SET search_path = pg_catalog, public, pg_temp;
ALTER FUNCTION public.enforce_last_partner_admin()
  SET search_path = pg_catalog, public, pg_temp;
ALTER FUNCTION public.enforce_partner_delegation_grant_lifecycle()
  SET search_path = pg_catalog, public, pg_temp;
ALTER FUNCTION public.enforce_partner_membership_capability_lifecycle()
  SET search_path = pg_catalog, public, pg_temp;
ALTER FUNCTION public.enforce_partner_membership_recovery_survival()
  SET search_path = pg_catalog, public, pg_temp;
ALTER FUNCTION public.enforce_partner_tenant_binding_lifecycle()
  SET search_path = pg_catalog, public, pg_temp;
ALTER FUNCTION public.hr_t7_offer_versions_append_only_guard()
  SET search_path = pg_catalog, public, pg_temp;
ALTER FUNCTION public.trg_partner_delegation_capabilities_audit()
  SET search_path = pg_catalog, public, pg_temp;
ALTER FUNCTION public.trg_partner_delegation_capabilities_updated_at()
  SET search_path = pg_catalog, public, pg_temp;
ALTER FUNCTION public.trg_partner_delegation_grants_audit()
  SET search_path = pg_catalog, public, pg_temp;
ALTER FUNCTION public.trg_partner_membership_capabilities_audit()
  SET search_path = pg_catalog, public, pg_temp;
ALTER FUNCTION public.trg_partner_memberships_audit()
  SET search_path = pg_catalog, public, pg_temp;
ALTER FUNCTION public.trg_partner_memberships_updated_at()
  SET search_path = pg_catalog, public, pg_temp;
ALTER FUNCTION public.trg_partner_tenant_bindings_audit()
  SET search_path = pg_catalog, public, pg_temp;
ALTER FUNCTION public.trg_partners_authorization_change_event()
  SET search_path = pg_catalog, public, pg_temp;
ALTER FUNCTION public.trg_partners_updated_at()
  SET search_path = pg_catalog, public, pg_temp;

-- Required post-apply checks:
--   * anon/authenticated cannot EXECUTE public.sync_login_tenant_index()
--   * the function remains SECURITY DEFINER and its trigger remains attached
--   * INSERT/UPDATE/DELETE on the trigger source keeps login_tenant_index correct
--   * the 16 functions have search_path in pg_proc.proconfig
--   * relevant partner lifecycle and HR append-only tests pass
--   * Supabase Security Advisor no longer reports these findings
