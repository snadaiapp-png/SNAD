-- Isolated forensic test only. No committed data changes.
-- Explicitly restrict this run to the known disposable V3 database and cluster.
\set ON_ERROR_STOP on
BEGIN;
DO $guard$
BEGIN
 IF current_database()<>'g1_restore_cert_20261104_v3'
 OR inet_server_port()<>55474
 OR (SELECT system_identifier::text FROM pg_control_system())<>'7703842253085562712'
 THEN RAISE EXCEPTION 'BLOCKED: test connection identity mismatch'; END IF;
END $guard$;
CREATE TEMP TABLE g1_before ON COMMIT DROP AS
SELECT count(*)::integer AS missing
FROM public.users u WHERE NOT EXISTS (
 SELECT 1 FROM public.login_tenant_index i
 WHERE i.tenant_id=u.tenant_id AND i.kind='email' AND i.identifier=u.email
);
DO $test$
DECLARE before_count integer; after_count integer; index_count integer;
BEGIN
 SELECT missing INTO before_count FROM g1_before;
 INSERT INTO public.login_tenant_index(kind,identifier,tenant_id)
 SELECT 'email',u.email,u.tenant_id FROM public.users u
 WHERE NOT EXISTS (SELECT 1 FROM public.login_tenant_index i
 WHERE i.kind='email' AND i.identifier=u.email AND i.tenant_id=u.tenant_id)
 ON CONFLICT(kind,identifier,tenant_id) DO NOTHING;
 INSERT INTO public.login_tenant_index(kind,identifier,tenant_id)
 SELECT 'username',u.username,u.tenant_id FROM public.users u
 WHERE u.username IS NOT NULL AND u.username<>u.email
 AND NOT EXISTS (SELECT 1 FROM public.login_tenant_index i
 WHERE i.kind='username' AND i.identifier=u.username AND i.tenant_id=u.tenant_id)
 ON CONFLICT(kind,identifier,tenant_id) DO NOTHING;
 SELECT count(*) INTO after_count FROM public.users u
 WHERE NOT EXISTS (SELECT 1 FROM public.login_tenant_index i
 WHERE i.tenant_id=u.tenant_id AND i.kind='email' AND i.identifier=u.email);
 IF after_count<>0 THEN RAISE EXCEPTION 'BACKFILL_RECONCILIATION=FAIL missing=%',after_count; END IF;
 SELECT count(*) INTO index_count FROM public.login_tenant_index;
 RAISE NOTICE 'BACKFILL_RECONCILIATION=PASS prior_missing=% index_rows_after=%',before_count,index_count;
END $test$;
-- Repeating the operation must not create additional rows.
DO $idempotency$
DECLARE prior integer; post integer;
BEGIN
 SELECT count(*) INTO prior FROM public.login_tenant_index;
 INSERT INTO public.login_tenant_index(kind,identifier,tenant_id)
 SELECT 'email',u.email,u.tenant_id FROM public.users u
 ON CONFLICT(kind,identifier,tenant_id) DO NOTHING;
 SELECT count(*) INTO post FROM public.login_tenant_index;
 IF prior<>post THEN RAISE EXCEPTION 'BACKFILL_IDEMPOTENCY=FAIL before=% after=%',prior,post; END IF;
 RAISE NOTICE 'BACKFILL_IDEMPOTENCY=PASS rows=%',post;
END $idempotency$;
ROLLBACK;
