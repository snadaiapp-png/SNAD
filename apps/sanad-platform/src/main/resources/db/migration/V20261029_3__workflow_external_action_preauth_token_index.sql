-- ============================================================
-- SNAD-SEC-RLS-FORENSIC-CLOSURE-2026-10-29 — External-portal pre-auth token resolution
-- Dedicated token-pointer index (repo pattern: V20261011_4 login_tenant_index)
-- ============================================================
-- EVIDENCE (branch security/rls-forensic-remediation, run set at SHA 1a656ab2,
-- Playwright E2E & Visual Regression run 3614, jobs "CRM Authenticated E2E" /
-- "Subscription E2E" + Workflow Y2 G4 Release Gate run 2409):
--   * WorkflowR2ExternalPortalTest.validTokenViewMarksViewedAndRespondRecordsEvidenceIdempotently
--       PortalDeniedException: INVALID_TOKEN (token was just issued to the fixture)
--   * WorkflowR2ExternalPortalTest.otpLifecycleIssueVerifyExpiryAndAttemptBounds
--       expected OTP_REQUIRED but got INVALID_TOKEN
--   * WorkflowR2ExternalPortalTest.tokenAndFingerprintRateLimitsFailClosed /
--       expiredAndRevokedActionsAreDenied
--       ExternalActionStateException: EXTERNAL_ACTION_NOT_FOUND
--
-- ROOT CAUSE (proven):
--   WorkflowExternalActionService.findByToken resolves the external action by
--   token_hash ALONE — the caller is an unauthenticated external participant
--   and NO tenant context can exist before the row is found (the tenant id is
--   a result of the lookup, not an input). V20261029_1 FORCE-bound
--   workflow_external_actions to its strict tenant policy, so the lookup
--   evaluates USING (tenant_id = current_setting('app.tenant_id', true)::uuid)
--   with a missing GUC => NULL comparison => zero rows => every legitimate
--   portal view/respond/OTP flow is denied with INVALID_TOKEN. This is a
--   production defect of the portal surface, not a test artifact.
--
-- DESIGN (mirrors the sanctioned pre-auth pattern of V20261011_4):
--   1. A DEDICATED pointer table (workflow_external_action_token_index) that is
--      NOT subject to RLS: it stores ONLY (token_hash -> action_id, tenant_id).
--      No action payload, no participant data, no response evidence. The token
--      hash is a 32-byte SecureRandom capability — unguessable by design.
--   2. A trigger keeps the index in sync with workflow_external_actions.
--   3. findByToken reads the pointer (index: no RLS), then applies the trusted
--      tenant scope (TenantRlsTransactionContext) and re-reads the AUTHORITATIVE
--      row from workflow_external_actions UNDER RLS. The index never becomes a
--      data path — authorization state (status/expiry/revocation) is always
--      resolved from the RLS-protected table.
--   4. No BYPASSRLS, no role changes, no policy weakening, no permissive branch
--      on the protected table.
--
-- SECURITY NOTES:
--   * The sync trigger is SECURITY DEFINER owned by the migration runner (the
--     table owner) exactly like sync_login_tenant_index(); it reads NEW/OLD
--     (never SELECTs the protected table) and writes ONLY to the index.
--   * Backfill loops the tenant directory (non-RLS) and seeds per tenant inside
--     a transaction-local app.tenant_id scope, as V20261029_1's future-
--     migration contract requires.
--   * EXECUTE/SELECT surfaces are restricted to sanad; PUBLIC is revoked.
-- ============================================================

-- G0: runtime role contract (same precondition as V20261029_1/V20261011_4).
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sanad'
                 AND NOT rolsuper AND NOT rolbypassrls) THEN
    RAISE EXCEPTION 'T3_RLS_RUNTIME_ROLE_UNAVAILABLE: provision restricted sanad role first';
  END IF;
END $$;

-- The pre-auth pointer table. NO RLS — cross-tenant discovery is the design
-- (mirrors login_tenant_index). Contents: hash + ids only.
CREATE TABLE IF NOT EXISTS public.workflow_external_action_token_index (
    token_hash  text        NOT NULL PRIMARY KEY,
    action_id   uuid        NOT NULL,
    tenant_id   uuid        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_wf_ext_action_token_idx_tenant
    ON public.workflow_external_action_token_index (tenant_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON public.workflow_external_action_token_index TO sanad;
REVOKE ALL ON public.workflow_external_action_token_index FROM PUBLIC;

-- Sync trigger: SECURITY DEFINER (fires on a FORCE-RLS table), search_path
-- pinned, writes only to the pointer table, never SELECTs the protected table.
CREATE OR REPLACE FUNCTION public.sync_workflow_external_action_token_index()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $fn$
BEGIN
  IF (TG_OP = 'DELETE') THEN
    IF OLD.token_hash IS NOT NULL THEN
      DELETE FROM public.workflow_external_action_token_index
       WHERE token_hash = OLD.token_hash;
    END IF;
    RETURN OLD;
  END IF;

  IF NEW.token_hash IS NOT NULL THEN
    INSERT INTO public.workflow_external_action_token_index
        (token_hash, action_id, tenant_id)
    VALUES (NEW.token_hash, NEW.id, NEW.tenant_id)
    ON CONFLICT (token_hash) DO UPDATE
        SET action_id = EXCLUDED.action_id,
            tenant_id = EXCLUDED.tenant_id;
  ELSIF TG_OP = 'UPDATE' AND OLD.token_hash IS NOT NULL
        AND OLD.token_hash <> NEW.token_hash THEN
    DELETE FROM public.workflow_external_action_token_index
     WHERE token_hash = OLD.token_hash;
  END IF;
  RETURN NEW;
END;
$fn$;

REVOKE ALL ON FUNCTION public.sync_workflow_external_action_token_index() FROM PUBLIC;

DROP TRIGGER IF EXISTS wf_ext_actions_sync_token_index ON public.workflow_external_actions;
CREATE TRIGGER wf_ext_actions_sync_token_index
  AFTER INSERT OR UPDATE OR DELETE ON public.workflow_external_actions
  FOR EACH ROW EXECUTE FUNCTION public.sync_workflow_external_action_token_index();

-- Backfill: per-tenant scoped loop (V20261029_1 future-migration contract).
-- The migration runner IS the owner bound by FORCE RLS, so rows are only
-- visible inside an explicit tenant scope.
DO $snad$
DECLARE
    t record;
    backfilled bigint := 0;
BEGIN
  FOR t IN SELECT id FROM tenants ORDER BY id LOOP
    PERFORM set_config('app.tenant_id', t.id::text, true);
    WITH ins AS (
      INSERT INTO public.workflow_external_action_token_index
          (token_hash, action_id, tenant_id)
      SELECT a.token_hash, a.id, a.tenant_id
        FROM public.workflow_external_actions a
       WHERE a.tenant_id = t.id
         AND a.token_hash IS NOT NULL
      ON CONFLICT (token_hash) DO NOTHING
      RETURNING 1
    )
    SELECT count(*) INTO backfilled FROM ins;
  END LOOP;
  PERFORM set_config('app.tenant_id', '', true);
  RAISE NOTICE 'T3 token index backfill completed (% rows)', backfilled;
END
$snad$;
