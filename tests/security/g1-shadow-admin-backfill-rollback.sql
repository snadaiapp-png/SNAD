\set ON_ERROR_STOP on
BEGIN;
DO $guard$
BEGIN
 IF current_database()<>'g1_sep_shadow_20261104'
 OR inet_server_port()<>55474
 OR (SELECT system_identifier::text FROM pg_control_system())<>'7703842253085562712'
 THEN RAISE EXCEPTION 'BLOCKED unexpected database'; END IF;
END $guard$;
SET LOCAL sanad.g1_backfill_approved='yes';
SELECT count(*) AS before_missing_email FROM public.users u
WHERE NOT EXISTS (SELECT 1 FROM public.login_tenant_index i
 WHERE i.kind='email' AND i.identifier=u.email AND i.tenant_id=u.tenant_id);
\ir ../../scripts/ops/g1-login-index-admin-backfill.sql
DO $test$
DECLARE before_rows integer;after_rows integer;
BEGIN
 SELECT count(*) INTO before_rows FROM public.login_tenant_index;
 IF before_rows<1 THEN RAISE EXCEPTION 'G1_BACKFILL_TEST=FAIL no rows inserted'; END IF;
 RAISE NOTICE 'G1_BACKFILL_INITIAL=PASS rows=%',before_rows;
END $test$;
\ir ../../scripts/ops/g1-login-index-admin-backfill.sql
DO $test2$
DECLARE expected_rows integer;bad_links integer;
BEGIN
 SELECT count(*) INTO expected_rows FROM public.login_tenant_index;
 SELECT count(*) INTO bad_links FROM public.login_tenant_index i
 WHERE NOT EXISTS (SELECT 1 FROM public.users u WHERE u.tenant_id=i.tenant_id
 AND ((i.kind='email' AND i.identifier=u.email) OR
      (i.kind='username' AND i.identifier=u.username AND u.username<>u.email)));
 IF expected_rows<>1 OR bad_links<>0 THEN
  RAISE EXCEPTION 'G1_BACKFILL_REPLAY=FAIL count=% stale=%',expected_rows,bad_links;
 END IF;
 RAISE NOTICE 'G1_BACKFILL_REPLAY=PASS rows=% stale=%',expected_rows,bad_links;
END $test2$;
ROLLBACK;
