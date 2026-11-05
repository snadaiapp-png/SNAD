-- G1 F05 dry-run ONLY: executes approved admin-only reconciliation on the
-- dedicated local certification fixture, then ALWAYS rolls it back.
-- No independent production approval is implied.
\set ON_ERROR_STOP on
BEGIN;
DO $target_guard$
BEGIN
 IF current_database() <> 'separation_cert'
    OR inet_server_port() <> 55576
    OR (SELECT system_identifier::text FROM pg_control_system()) <> '7704438226812603328'
    OR NOT (SELECT rolsuper FROM pg_roles WHERE rolname=current_user)
 THEN RAISE EXCEPTION 'G1_F05_TRIAL_BLOCKED_WRONG_TARGET_OR_ROLE'; END IF;
END $target_guard$;
DO $baseline$
DECLARE missing_count bigint;
BEGIN
 SELECT count(*) INTO missing_count FROM public.users u
 WHERE NOT EXISTS (
  SELECT 1 FROM public.login_tenant_index i
  WHERE i.kind='email' AND i.identifier=u.email AND i.tenant_id=u.tenant_id
 );
 IF missing_count<>1 THEN
   RAISE EXCEPTION 'G1_F05_TRIAL_BASELINE_MISMATCH expected=1 actual=%',missing_count;
 END IF;
 RAISE NOTICE 'G1_F05_TRIAL_BASELINE=CONFIRMED missing=1';
END $baseline$;
SET LOCAL sanad.g1_backfill_approved='yes';
\ir ../../scripts/ops/g1-login-index-admin-backfill.sql
DO $accept$
DECLARE missing_count bigint; stale_count bigint;
BEGIN
 WITH expected AS (
   SELECT tenant_id,'email'::text AS kind,email::text AS identifier
     FROM public.users WHERE email IS NOT NULL
   UNION
   SELECT tenant_id,'username'::text,username::text
     FROM public.users WHERE username IS NOT NULL AND username<>email
 ), actual AS (
   SELECT tenant_id,kind,identifier FROM public.login_tenant_index
 )
 SELECT (SELECT count(*) FROM (SELECT * FROM expected EXCEPT SELECT * FROM actual) a),
        (SELECT count(*) FROM (SELECT * FROM actual EXCEPT SELECT * FROM expected) b)
 INTO missing_count,stale_count;
 IF missing_count<>0 OR stale_count<>0 THEN
  RAISE EXCEPTION 'G1_F05_TRIAL_RECONCILIATION_FAILED missing=% stale=%',missing_count,stale_count;
 END IF;
 RAISE NOTICE 'G1_F05_TRIAL_POST_BACKFILL=PASS missing=0 stale=0';
END $accept$;
-- Never commit this test data / metadata change.
ROLLBACK;
