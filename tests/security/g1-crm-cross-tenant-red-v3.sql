-- Regression proof of the unfixed CRM cross-tenant exposure.
-- Expected on vulnerable V3: psql exit nonzero with G1_CRM_CROSS_TENANT_ISOLATION_FAIL.
-- Expected after remediation: green SQL and ROLLBACK. Never run in production.
\set ON_ERROR_STOP on
BEGIN;
DO $guard$
BEGIN
 IF current_database()<>'g1_restore_cert_20261104_v3'
 OR inet_server_port()<>55474
 OR (SELECT system_identifier::text FROM pg_control_system())<>'7703842253085562712'
 THEN RAISE EXCEPTION 'BLOCKED: unexpected target'; END IF;
END $guard$;
INSERT INTO public.crm_customer_segments(tenant_id,segment_code,segment_name,segment_type)
VALUES ('11111111-1111-1111-1111-111111111171','g1-red-segment-a','G1 red A','MANUAL'),
       ('22222222-2222-2222-2222-222222222272','g1-red-segment-b','G1 red B','MANUAL');
SELECT set_config('app.tenant_id','11111111-1111-1111-1111-111111111171',true);
SET LOCAL ROLE sanad;
DO $check$
DECLARE foreign_visible integer;
BEGIN
 IF current_user<>'sanad' THEN RAISE EXCEPTION 'BLOCKED: runtime role not active'; END IF;
 SELECT count(*) INTO foreign_visible FROM public.crm_customer_segments
 WHERE tenant_id='22222222-2222-2222-2222-222222222272'
 AND segment_code='g1-red-segment-b';
 RAISE NOTICE 'G1_FOREIGN_TENANT_ROWS_VISIBLE=%',foreign_visible;
 IF foreign_visible<>0 THEN
   RAISE EXCEPTION 'G1_CRM_CROSS_TENANT_ISOLATION_FAIL foreign_visible=%',foreign_visible;
 END IF;
 RAISE NOTICE 'G1_CRM_CROSS_TENANT_ISOLATION=PASS';
END $check$;
ROLLBACK;
