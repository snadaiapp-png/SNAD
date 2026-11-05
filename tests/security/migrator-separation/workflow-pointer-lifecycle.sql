-- Real restricted-runtime workflow lifecycle on the isolated S7 database.
-- Uses S4 seeded tenant A and user A; never persists fixtures.
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL ROLE sanad;
SELECT set_config('app.tenant_id','22222222-2222-2222-2222-222222222222',true);
INSERT INTO public.workflow_definitions
(id,tenant_id,definition_family_id,code,name,created_by,created_at,updated_at)
VALUES ('e1010101-0101-0101-0101-010101010101','22222222-2222-2222-2222-222222222222',
'e1010101-0101-0101-0101-010101010101',
'g1-wf-pointer-lifecycle','G1 pointer verification','44444444-4444-4444-4444-444444444444',now(),now());
INSERT INTO public.workflow_instances
(id,tenant_id,workflow_definition_id,workflow_version,business_entity_type,business_entity_id,
 started_by,started_at,created_at,updated_at)
VALUES ('e2020202-0202-0202-0202-020202020202','22222222-2222-2222-2222-222222222222',
'e1010101-0101-0101-0101-010101010101',1,'G1_TEST',
'e3030303-0303-0303-0303-030303030303',
'44444444-4444-4444-4444-444444444444',now(),now(),now());
INSERT INTO public.workflow_external_participants
(id,tenant_id,participant_type,source_module,source_entity_type,source_entity_id)
VALUES ('e4040404-0404-0404-0404-040404040404','22222222-2222-2222-2222-222222222222',
'EXTERNAL_PERSON','G1','G1_TEST','e5050505-0505-0505-0505-050505050505');
INSERT INTO public.workflow_external_actions
(id,tenant_id,workflow_instance_id,participant_id,action_type,token_hash,idempotency_key)
VALUES ('e6060606-0606-0606-0606-060606060606','22222222-2222-2222-2222-222222222222',
'e2020202-0202-0202-0202-020202020202','e4040404-0404-0404-0404-040404040404',
'ACKNOWLEDGE',md5('g1 workflow pointer fixture'),md5('g1 workflow pointer fixture'));
DO $g1$
DECLARE hits integer;
BEGIN
 SELECT count(*) INTO hits FROM public.workflow_external_action_token_index
 WHERE token_hash=md5('g1 workflow pointer fixture')
 AND action_id='e6060606-0606-0606-0606-060606060606'
 AND tenant_id='22222222-2222-2222-2222-222222222222';
 IF hits<>1 THEN RAISE EXCEPTION 'G1_WORKFLOW_POINTER_INSERT_SYNC_FAILED count=%',hits; END IF;
 RAISE NOTICE 'G1_WORKFLOW_POINTER_INSERT_SYNC=PASS';
END $g1$;
UPDATE public.workflow_external_actions SET status='VIEWED'
WHERE id='e6060606-0606-0606-0606-060606060606';
DO $g1$
DECLARE hits integer;
BEGIN
 SELECT count(*) INTO hits FROM public.workflow_external_action_token_index
 WHERE token_hash=md5('g1 workflow pointer fixture')
 AND action_id='e6060606-0606-0606-0606-060606060606';
 IF hits<>1 THEN RAISE EXCEPTION 'G1_WORKFLOW_POINTER_UPDATE_SYNC_FAILED count=%',hits; END IF;
 RAISE NOTICE 'G1_WORKFLOW_POINTER_UPDATE_SYNC=PASS';
END $g1$;
DELETE FROM public.workflow_external_actions WHERE id='e6060606-0606-0606-0606-060606060606';
DO $g1$
DECLARE hits integer;
BEGIN
 SELECT count(*) INTO hits FROM public.workflow_external_action_token_index
 WHERE token_hash=md5('g1 workflow pointer fixture');
 IF hits<>0 THEN RAISE EXCEPTION 'G1_WORKFLOW_POINTER_DELETE_SYNC_FAILED count=%',hits; END IF;
 RAISE NOTICE 'G1_WORKFLOW_POINTER_DELETE_SYNC=PASS';
END $g1$;
RESET ROLE;
ROLLBACK;
