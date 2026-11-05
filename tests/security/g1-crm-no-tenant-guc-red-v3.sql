-- Fail-closed missing GUC baseline on disposable V3 (NOT production).
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
VALUES ('33333333-3333-3333-3333-333333333373','g1-guc-red-segment','G1 no GUC red','MANUAL');
SET LOCAL ROLE sanad;
DO $check$
DECLARE foreign_visible integer;
BEGIN
 IF current_user<>'sanad' THEN RAISE EXCEPTION 'BLOCKED wrong role'; END IF;
 IF current_setting('app.tenant_id',true) IS NOT NULL THEN
   RAISE EXCEPTION 'BLOCKED: app.tenant_id is already set in this session';
 END IF;
 SELECT count(*) INTO foreign_visible FROM public.crm_customer_segments
 WHERE tenant_id='33333333-3333-3333-3333-333333333373'
 AND segment_code='g1-guc-red-segment';
 RAISE NOTICE 'G1_NO_GUC_ROWS_VISIBLE=%',foreign_visible;
 IF foreign_visible<>0 THEN RAISE EXCEPTION 'G1_CRM_MISSING_GUC_FAIL_OPEN rows=%',foreign_visible; END IF;
 RAISE NOTICE 'G1_CRM_MISSING_GUC=PASS';
END $check$;
ROLLBACK;
