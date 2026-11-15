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

CREATE TABLE IF NOT EXISTS platform_subscription_owners (
    subscription_id uuid PRIMARY KEY,
    tenant_id       uuid NOT NULL,
    updated_at      timestamptz NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE platform_subscription_owners IS
    'PLATFORM-REFERENCE (non-RLS): ownership fact per subscription, maintained
     by trigger, used by trusted control-plane services to attach the correct
     FORCE-RLS scope. Authorization lives upstream.';

CREATE OR REPLACE FUNCTION fn_sync_subscription_owner()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO platform_subscription_owners (subscription_id, tenant_id, updated_at)
    VALUES (NEW.id, NEW.tenant_id, NOW())
    ON CONFLICT (subscription_id)
    DO UPDATE SET tenant_id = EXCLUDED.tenant_id, updated_at = NOW();
    RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_sync_subscription_owner ON tenant_subscriptions;
CREATE TRIGGER trg_sync_subscription_owner
AFTER INSERT OR UPDATE OF tenant_id ON tenant_subscriptions
FOR EACH ROW EXECUTE FUNCTION fn_sync_subscription_owner();

-- Backfill existing subscriptions.
INSERT INTO platform_subscription_owners (subscription_id, tenant_id, updated_at)
SELECT id, tenant_id, NOW() FROM tenant_subscriptions
ON CONFLICT (subscription_id)
DO UPDATE SET tenant_id = EXCLUDED.tenant_id, updated_at = NOW();
