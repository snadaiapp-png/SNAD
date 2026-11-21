-- WS6: structural global hostname uniqueness for tenant application domains.
--
-- Defect discovered by automated UAT-14 (2026-11-16): tenant_domains is
-- fail-closed FORCE RLS, and the application-level availability check
-- (HostRoutingService.requireHostnameAvailable) therefore only ever sees the
-- claims of the currently bound tenant. Two different tenants could claim the
-- same hostname: website_domains and commerce_store_domains already enforce a
-- GLOBAL unique index on hostname, but tenant_domains only had the per-tenant
-- unique (tenant_id, hostname).
--
-- This migration closes the structural gap for the APPLICATION surface. The
-- advisory-lock + multi-surface application check stays in place for the
-- friendly 409 path and the cross-surface claims; this index is the backstop
-- that makes the invariant hold under any RLS scope, any race, and any actor.
--
-- Fail loudly (before creating the index) if existing rows would violate the
-- invariant — operators must reconcile duplicates explicitly; the migration
-- must never silently pick a winner.

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM tenant_domains
    GROUP BY lower(hostname)
    HAVING COUNT(*) > 1
  ) THEN
    RAISE EXCEPTION 'TENANT_DOMAINS_GLOBAL_HOSTNAME_DUPES_PRESENT: reconcile duplicate hostnames before upgrading';
  END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_tenant_domains_hostname_global
    ON tenant_domains (lower(hostname));
