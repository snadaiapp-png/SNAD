\set ON_ERROR_STOP on
BEGIN;
DO $g$
BEGIN
 IF current_database()<>'separation_cert'
 OR inet_server_port()<>55576
 OR (SELECT system_identifier::text FROM pg_control_system())<>'7704438226812603328'
 THEN RAISE EXCEPTION 'BLOCKED wrong certification target'; END IF;
END $g$;
INSERT INTO public.tenants(id,name,subdomain,status,created_at,updated_at)
VALUES('a3333333-3333-3333-3333-333333333371','G1 callback acl test','g1acl261105','ACTIVE',now(),now());
REVOKE UPDATE ON public.service_callback_replay FROM sanad;
DO $before$
BEGIN
 IF has_table_privilege('sanad','public.service_callback_replay','UPDATE')
 OR NOT has_table_privilege('sanad','public.service_callback_replay','INSERT')
 OR NOT has_table_privilege('sanad','public.service_callback_replay','DELETE')
 OR NOT has_table_privilege('sanad','public.service_callback_replay','SELECT')
 THEN RAISE EXCEPTION 'G1_CALLBACK_ACL_CONTRACT_FAIL'; END IF;
 RAISE NOTICE 'G1_CALLBACK_ACL_CONTRACT=PASS';
END $before$;
SET LOCAL ROLE sanad;
INSERT INTO public.service_callback_replay
(tenant_id,service_name,jti,nonce,correlation_id,received_at,expires_at)
VALUES('a3333333-3333-3333-3333-333333333371','g1-cert','g1-test-jti-261105','g1-test-nonce-261105','g1-test-correlation',now(),now()+interval '1 hour');
DO $test$
DECLARE read_count integer; denied boolean:=false; deleted integer;
BEGIN
 SELECT count(*) INTO read_count FROM public.service_callback_replay WHERE jti='g1-test-jti-261105';
 IF read_count<>1 THEN RAISE EXCEPTION 'G1_CALLBACK_READ_FAIL count=%',read_count; END IF;
 BEGIN
 UPDATE public.service_callback_replay SET correlation_id='forbidden'
 WHERE jti='g1-test-jti-261105';
 EXCEPTION WHEN insufficient_privilege THEN denied:=true;
 END;
 IF NOT denied THEN RAISE EXCEPTION 'G1_CALLBACK_UPDATE_DENIAL_FAIL'; END IF;
 WITH deleted_rows AS (
 DELETE FROM public.service_callback_replay WHERE jti='g1-test-jti-261105' RETURNING 1)
 SELECT count(*) INTO deleted FROM deleted_rows;
 IF deleted<>1 THEN RAISE EXCEPTION 'G1_CALLBACK_DELETE_FAIL count=%',deleted; END IF;
 RAISE NOTICE 'G1_CALLBACK_RUNTIME=PASS select=1 insert=1 update=denied delete=1';
END $test$;
RESET ROLE;
ROLLBACK;
