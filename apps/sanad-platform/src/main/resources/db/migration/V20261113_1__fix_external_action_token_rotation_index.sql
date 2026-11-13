-- C4-TOKEN-01: Replace the index sync function without modifying applied Flyway migrations.
-- Defect in V20261029_3: UPDATE old_nonnull -> new_nonnull upserted NEW but
-- skipped deletion of OLD because the cleanup was under ELSIF.
-- This migration changes function body only: preserves the existing owner,
-- SECURITY DEFINER mode, trigger registration, and explicit EXECUTE grants.
-- No production execution is authorized by this change.
CREATE OR REPLACE FUNCTION public.sync_workflow_external_action_token_index()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $fn$
BEGIN
  IF TG_OP = 'DELETE' THEN
    IF OLD.token_hash IS NOT NULL THEN
      DELETE FROM public.workflow_external_action_token_index
       WHERE token_hash = OLD.token_hash
         AND action_id = OLD.id
         AND tenant_id = OLD.tenant_id;
    END IF;
    RETURN OLD;
  END IF;

  -- On UPDATE, remove an obsolete index row BEFORE inserting the replacement.
  -- This also covers nonnull -> NULL; match OLD ownership to avoid removing an
  -- index row that has since been reassigned to a different action.
  IF TG_OP = 'UPDATE' AND OLD.token_hash IS NOT NULL
     AND (OLD.token_hash IS DISTINCT FROM NEW.token_hash
       OR OLD.id IS DISTINCT FROM NEW.id
       OR OLD.tenant_id IS DISTINCT FROM NEW.tenant_id) THEN
    DELETE FROM public.workflow_external_action_token_index
     WHERE token_hash = OLD.token_hash
       AND action_id = OLD.id
       AND tenant_id = OLD.tenant_id;
  END IF;

  IF NEW.token_hash IS NOT NULL THEN
    INSERT INTO public.workflow_external_action_token_index
      (token_hash, action_id, tenant_id)
    VALUES (NEW.token_hash, NEW.id, NEW.tenant_id)
    ON CONFLICT (token_hash) DO UPDATE
      SET action_id = EXCLUDED.action_id,
          tenant_id = EXCLUDED.tenant_id;
  END IF;

  RETURN NEW;
END;
$fn$;
