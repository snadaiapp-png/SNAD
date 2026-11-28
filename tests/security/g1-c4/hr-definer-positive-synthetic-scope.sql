-- C4 strictly isolated POSITIVE control for two read-only HR routines.
-- NOT an authorization test of real business/user paths or the other HR functions.
-- Must run after Flyway on disposable separated PostgreSQL; no production access.
\set ON_ERROR_STOP on
BEGIN;
DO $preflight$
BEGIN
  IF current_database() <> 'separation_cert' THEN
    RAISE EXCEPTION 'C4_POSITIVE_NON_ISOLATED_DB=%',current_database();
  END IF;
  IF current_user <> 'postgres' THEN
    RAISE EXCEPTION 'C4_POSITIVE_EXPECTED_POSTGRES_OPERATOR=%',current_user;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='sanad' AND NOT rolsuper AND NOT rolbypassrls) THEN
    RAISE EXCEPTION 'C4_POSITIVE_RUNTIME_ROLE_UNSAFE_OR_MISSING';
  END IF;
END $preflight$;
-- Session-local hand-off table: the setup DO binds a synthetic tenant, the
-- restricted-role DO reads it back after SET LOCAL ROLE sanad.
CREATE TEMP TABLE IF NOT EXISTS c4_positive_tenant(t uuid);
DELETE FROM c4_positive_tenant;
GRANT SELECT ON c4_positive_tenant TO sanad;
-- G1 signed-context contract (V20261124_1/_2): the rewritten HR routines read
-- the HMAC-validated public.app_tenant_ctx_text() — a raw session GUC no
-- longer authorizes anything. Bind the synthetic tenant through the full
-- protocol BEFORE switching to the restricted runtime role: this DO block
-- still runs as the provisioning operator and reads the per-run operator key
-- the CI wrapper provisioned (psql does not interpolate :vars inside
-- dollar-quoted bodies, so the key is read from the table, never inlined).
DO $positive_setup$
DECLARE
  t uuid;
  v_key bytea;
  v_mac text;
BEGIN
  t := gen_random_uuid();
  SELECT secret INTO v_key
    FROM public.platform_tenant_ctx_keys
   WHERE key_id = 'c4-matrix-operator' AND status = 'ACTIVE'
   ORDER BY created_at DESC
   LIMIT 1;
  IF v_key IS NULL THEN
    RAISE EXCEPTION 'C4_OPERATOR_KEY_MISSING: CI wrapper must provision the per-run operator key first';
  END IF;
  v_mac := encode(hmac(
      convert_to(t::text || ':' || txid_current()::text || ':' || pg_backend_pid()::text, 'UTF8'),
      v_key, 'sha256'), 'hex');
  IF NOT public.app_bind_tenant_ctx(t, v_mac)
     OR public.app_tenant_ctx_text() IS DISTINCT FROM t::text THEN
    RAISE EXCEPTION 'C4_POSITIVE_SIGNED_BIND_REFUSED';
  END IF;
  DELETE FROM c4_positive_tenant;
  INSERT INTO c4_positive_tenant VALUES (t);
END $positive_setup$;
SET LOCAL ROLE sanad;
DO $positive$
DECLARE
  t uuid;
  as_of date;
BEGIN
  SELECT tt.t INTO t FROM c4_positive_tenant tt;
  IF current_user <> 'sanad' THEN
    RAISE EXCEPTION 'C4_POSITIVE_WRONG_ROLE=%',current_user;
  END IF;
  -- Synthetic UUID avoids modifying business data; confirm no existing tenant data.
  IF EXISTS (SELECT 1 FROM public.hr_employees WHERE tenant_id=t)
    OR EXISTS (SELECT 1 FROM public.organization_legal_entities WHERE tenant_id=t) THEN
    RAISE EXCEPTION 'C4_POSITIVE_FIXTURE_NOT_EMPTY';
  END IF;
  PERFORM public.hr_assert_migration_tenant_scope(t);
  SELECT public.hr_default_migration_as_of_date(t) INTO as_of;
  IF as_of IS DISTINCT FROM DATE '1970-01-01' THEN
    RAISE EXCEPTION 'C4_POSITIVE_UNEXPECTED_AS_OF_DATE=%',as_of;
  END IF;
  RAISE NOTICE 'C4_HR_POSITIVE_SYNTHETIC=PASS 2/2 role=sanad (scope check and default date only)';
END $positive$;
ROLLBACK;
