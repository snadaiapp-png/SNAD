-- D5 (UAT-07/UAT-17/UAT-18, 2026-11-16): trusted owner-tenant directory for
-- subscription-addressed control-plane operations.
--
-- tenant_subscriptions (and every subscription ledger table) is fail-closed
-- FORCE RLS (V20261029_1): each HTTP session binds app.tenant_id to the
-- ACTING principal's tenant, so control-plane operations addressed by
-- subscription id alone (executive cancel/resume/renew/detail, operating-unit
-- bind, billing profiles) could not even LOOK UP the owning tenant of a
-- subscription owned by a different tenant — they 404ed for the platform
-- operator holding EXECUTIVE_MANAGE, and the same RLS blindness silently
-- weakened cross-tenant availability checks.
--
-- SECURITY DEFINER helpers do NOT bypass FORCE RLS (it applies to the table
-- owner too), and the migrator role has no CREATEROLE/BYPASSRLS. The minimal
-- correct primitive is therefore a PLATFORM-REFERENCE mapping table (non-RLS,
-- same classification as the tenants directory) kept in lockstep by a row
-- trigger: subscription_id -> tenant_id, nothing else. It exposes no tenant
-- data — only the ownership fact required to attach the correct RLS scope.
-- Authorization is still decided upstream (control-plane guard +
-- @RequireCapability); the mapping is used solely to re-scope the transaction.
--
-- ---------------------------------------------------------------------------
-- REMEDIATION 2026-11-16 (reviewer conditional approval for remediation only —
-- SANAD scope-audit findings S1/S3/S6; the PR is UNMERGED and no governed
-- environment has recorded version 20261116.2, so the version is corrected in
-- place and environments re-provision from scratch):
--
--   S6  fn_sync_subscription_owner was created with PostgreSQL's implicit
--       GRANT EXECUTE TO PUBLIC and without a pinned search_path — both violate
--       the default-deny contract (V20261020_1) asserted by
--       SupabaseDataApiDefaultDenyPostgresTest.applicationFunctionsAreNotPublicExecutable
--       and .applicationFunctionsUsePinnedSearchPath (CI Maven-suite failures on
--       the PR head). Fixed by pinning SET search_path and revoking PUBLIC.
--
--   S3  The mapping lifecycle had no DELETE half: deleting a subscription left
--       an orphan ownership row behind, breaking subscription→tenant binding
--       integrity. A DELETE trigger now removes the mapping row in lockstep.
--
--   S1  The legacy-data backfill executed a plain INSERT..SELECT against
--       tenant_subscriptions WITHOUT app.tenant_id. Under the fail-closed
--       FORCE RLS policy (tenant_id = NULLIF(current_setting('app.tenant_id',
--       true),'')) that read returns ZERO ROWS SILENTLY — a no-op backfill on
--       any database with pre-existing subscriptions (CI fresh DBs hide the
--       defect because they hold 0 rows). Per the V20261029_1 migration
--       contract ("any LATER migration that seeds these tables must set
--       app.tenant_id (SET LOCAL) inside its own transaction"), the backfill
--       now iterates the tenants directory and sets app.tenant_id per tenant
--       (set_config(..., is_local => true), same house path as V20260918_4 /
--       V20260923_3), then FAILS LOUDLY when the mapped-row counter does not
--       match the enumerated-row counter.
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS platform_subscription_owners (
    subscription_id uuid PRIMARY KEY,
    tenant_id       uuid NOT NULL,
    updated_at      timestamptz NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE platform_subscription_owners IS
    'PLATFORM-REFERENCE (non-RLS): ownership fact per subscription, maintained
     by trigger, used by trusted control-plane services to attach the correct
     FORCE-RLS scope. Authorization lives upstream.';

-- SYNC (INSERT / UPDATE OF tenant_id) — invoker-rights trigger function with a
-- pinned search_path (default-deny contract, S6).
CREATE OR REPLACE FUNCTION fn_sync_subscription_owner()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, public, pg_temp
AS $$
BEGIN
    INSERT INTO platform_subscription_owners (subscription_id, tenant_id, updated_at)
    VALUES (NEW.id, NEW.tenant_id, NOW())
    ON CONFLICT (subscription_id)
    DO UPDATE SET tenant_id = EXCLUDED.tenant_id, updated_at = NOW();
    RETURN NEW;
END $$;

-- Default-deny EXECUTE contract (S6): PostgreSQL grants EXECUTE to PUBLIC at
-- function creation; V20261020_1 revoked it only for functions existing at
-- that time, so each new function must revoke it itself. The runtime role
-- keeps EXECUTE (same ACL shape as sync_login_tenant_index in V20261102_1).
REVOKE EXECUTE ON FUNCTION public.fn_sync_subscription_owner() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.fn_sync_subscription_owner() TO sanad;

DROP TRIGGER IF EXISTS trg_sync_subscription_owner ON tenant_subscriptions;
CREATE TRIGGER trg_sync_subscription_owner
AFTER INSERT OR UPDATE OF tenant_id ON tenant_subscriptions
FOR EACH ROW EXECUTE FUNCTION fn_sync_subscription_owner();

-- CLEANUP (DELETE) — completes the ownership lifecycle (S3): deleting a
-- subscription must not leave an orphan ownership row behind.
CREATE OR REPLACE FUNCTION fn_delete_subscription_owner()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, public, pg_temp
AS $$
BEGIN
    DELETE FROM platform_subscription_owners WHERE subscription_id = OLD.id;
    RETURN OLD;
END $$;

REVOKE EXECUTE ON FUNCTION public.fn_delete_subscription_owner() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.fn_delete_subscription_owner() TO sanad;

DROP TRIGGER IF EXISTS trg_delete_subscription_owner ON tenant_subscriptions;
CREATE TRIGGER trg_delete_subscription_owner
AFTER DELETE ON tenant_subscriptions
FOR EACH ROW EXECUTE FUNCTION fn_delete_subscription_owner();

-- ---------------------------------------------------------------------------
-- Legacy-data backfill (S1) — FORCE RLS contract:
-- tenant_subscriptions is FORCE RLS fail-closed; a read without a matching
-- app.tenant_id returns zero rows EVEN FOR THE TABLE OWNER. Enumerate the
-- tenants directory (platform-reference, non-RLS), bind each tenant's GUC
-- locally (is_local => true, scoped exactly like the application path and the
-- V20260918_4 / V20260923_3 seed paths — no BYPASSRLS, no superuser), upsert
-- that tenant's ownership rows, and count both sides. The migration FAILS
-- when the counters disagree, so a silent no-op can never pass again.
-- ---------------------------------------------------------------------------
DO $backfill$
DECLARE
    v_tenant      uuid;
    v_side        integer;
    v_expected    integer := 0;
    v_mapped      integer := 0;
BEGIN
    FOR v_tenant IN SELECT id FROM tenants LOOP
        PERFORM set_config('app.tenant_id', v_tenant::text, true);

        SELECT COUNT(*) INTO v_side
          FROM tenant_subscriptions WHERE tenant_id = v_tenant;
        v_expected := v_expected + v_side;

        INSERT INTO platform_subscription_owners (subscription_id, tenant_id, updated_at)
        SELECT id, tenant_id, NOW() FROM tenant_subscriptions WHERE tenant_id = v_tenant
        ON CONFLICT (subscription_id)
        DO UPDATE SET tenant_id = EXCLUDED.tenant_id, updated_at = NOW();
        GET DIAGNOSTICS v_side = ROW_COUNT;
        v_mapped := v_mapped + v_side;
    END LOOP;

    PERFORM set_config('app.tenant_id', '', true);

    IF v_mapped <> v_expected THEN
        RAISE EXCEPTION 'SUBSCRIPTION_OWNER_BACKFILL_MISMATCH: enumerated=% mapped=%',
            v_expected, v_mapped;
    END IF;
    RAISE NOTICE 'SUBSCRIPTION_OWNER_BACKFILL_OK: enumerated=% mapped=%', v_expected, v_mapped;
END
$backfill$;
