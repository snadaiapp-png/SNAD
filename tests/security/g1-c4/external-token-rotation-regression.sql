-- C4-TOKEN-01 — isolated PostgreSQL regression, never run against production.
-- CI wrapper verifies disposal of the whole database. Everything below rolls back.
-- Creates only a temporary source table; uses the REAL SECURITY DEFINER trigger
-- and REAL token pointer index in ephemeral, migrated separation_cert database.
BEGIN;

DO $precondition$
BEGIN
  IF current_database() <> 'separation_cert' THEN
    RAISE EXCEPTION 'C4_TOKEN_TEST_NON_ISOLATED_DB=%', current_database();
  END IF;
  IF to_regprocedure('public.sync_workflow_external_action_token_index()') IS NULL
     OR to_regclass('public.workflow_external_action_token_index') IS NULL THEN
    RAISE EXCEPTION 'C4_TOKEN_TEST_MISSING_MIGRATION';
  END IF;
END;
$precondition$;

CREATE TEMP TABLE c4_token_rotation_source (
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL,
 token_hash text
) ON COMMIT DROP;

CREATE TRIGGER c4_token_rotation_sync
AFTER INSERT OR UPDATE OR DELETE ON c4_token_rotation_source
FOR EACH ROW EXECUTE FUNCTION public.sync_workflow_external_action_token_index();

DO $cases$
DECLARE
  action_id constant uuid := 'aaaaaaaa-1111-2222-3333-444444444444';
  tenant_a constant uuid := 'bbbbbbbb-1111-2222-3333-444444444444';
  tenant_b constant uuid := 'cccccccc-1111-2222-3333-444444444444';
  token_a constant text := 'c4-rotation-before-aaaaaaaa-1111';
  token_b constant text := 'c4-rotation-after-bbbbbbbb-2222';
  token_c constant text := 'c4-rotation-tenant-change-cccc3333';
  n integer;
  passed integer := 0;
BEGIN
  -- Fixture cannot collide with existing seeded rows.
  IF EXISTS (SELECT 1 FROM public.workflow_external_action_token_index
             WHERE token_hash IN (token_a,token_b,token_c)) THEN
    RAISE EXCEPTION 'C4_TOKEN_FIXTURE_COLLISION';
  END IF;
  INSERT INTO pg_temp.c4_token_rotation_source(id,tenant_id,token_hash)
  VALUES (action_id,tenant_a,token_a);
  SELECT count(*) INTO n FROM public.workflow_external_action_token_index
    WHERE token_hash=token_a AND action_id = 'aaaaaaaa-1111-2222-3333-444444444444'::uuid AND tenant_id=tenant_a;
  IF n <> 1 THEN RAISE EXCEPTION 'C4_TOKEN_INITIAL_POINTER_MISSING'; END IF;
  passed := passed+1;

  UPDATE pg_temp.c4_token_rotation_source SET token_hash=token_b WHERE id=action_id;
  IF EXISTS (SELECT 1 FROM public.workflow_external_action_token_index WHERE token_hash=token_a) THEN
    RAISE EXCEPTION 'C4_TOKEN_OLD_HASH_STALE_AFTER_ROTATION';
  END IF;
  passed := passed+1;
  IF NOT EXISTS (SELECT 1 FROM public.workflow_external_action_token_index idx
                 WHERE idx.token_hash=token_b AND idx.action_id=action_id AND idx.tenant_id=tenant_a) THEN
    RAISE EXCEPTION 'C4_TOKEN_NEW_HASH_MISSING_AFTER_ROTATION';
  END IF;
  passed := passed+1;

  UPDATE pg_temp.c4_token_rotation_source SET tenant_id=tenant_b WHERE id=action_id;
  IF NOT EXISTS (SELECT 1 FROM public.workflow_external_action_token_index idx
                 WHERE idx.token_hash=token_b AND idx.action_id=action_id AND idx.tenant_id=tenant_b) THEN
    RAISE EXCEPTION 'C4_TOKEN_TENANT_POINTER_NOT_UPDATED';
  END IF;
  passed := passed+1;

  UPDATE pg_temp.c4_token_rotation_source SET token_hash=NULL WHERE id=action_id;
  IF EXISTS (SELECT 1 FROM public.workflow_external_action_token_index WHERE token_hash=token_b) THEN
    RAISE EXCEPTION 'C4_TOKEN_POINTER_STALE_AFTER_CLEAR';
  END IF;
  passed := passed+1;

  UPDATE pg_temp.c4_token_rotation_source SET token_hash=token_c WHERE id=action_id;
  IF NOT EXISTS (SELECT 1 FROM public.workflow_external_action_token_index idx
                 WHERE idx.token_hash=token_c AND idx.action_id=action_id AND idx.tenant_id=tenant_b) THEN
    RAISE EXCEPTION 'C4_TOKEN_POINTER_MISSING_AFTER_REISSUE';
  END IF;
  passed := passed+1;

  DELETE FROM pg_temp.c4_token_rotation_source WHERE id=action_id;
  IF EXISTS (SELECT 1 FROM public.workflow_external_action_token_index WHERE token_hash=token_c) THEN
    RAISE EXCEPTION 'C4_TOKEN_POINTER_STALE_AFTER_DELETE';
  END IF;
  passed := passed+1;
  IF passed <> 7 THEN
    RAISE EXCEPTION 'C4_TOKEN_TEST_COUNT_MISMATCH=%',passed;
  END IF;
  RAISE NOTICE 'C4_TOKEN_ROTATION_REGRESSION=PASS 7/7 isolated';
END;
$cases$;

ROLLBACK;
