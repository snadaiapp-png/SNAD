\set ON_ERROR_STOP on
BEGIN;
DO $guard$
BEGIN
 IF current_database()<>'g1_sep_shadow_20261104'
 OR inet_server_port()<>55474
 OR (SELECT system_identifier::text FROM pg_control_system())<>'7703842253085562712'
 THEN RAISE EXCEPTION 'BLOCKED wrong database'; END IF;
END $guard$;
\ir ../../scripts/ops/g1-login-index-admin-backfill.sql
ROLLBACK;
