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
SET LOCAL ROLE sanad;
DO $positive$
DECLARE
  t uuid := gen_random_uuid();
  as_of date;
  logged_tenant text;
BEGIN
  IF current_user <> 'sanad' THEN
    RAISE EXCEPTION 'C4_POSITIVE_WRONG_ROLE=%',current_user;
  END IF;
  -- Set context first so RLS does not hide unexpected fixture rows.
  PERFORM set_config('app.tenant_id', t::text, true);
  logged_tenant := current_setting('app.tenant_id',true);
  -- Synthetic UUID avoids modifying business data; confirm no existing tenant data.
  IF EXISTS (SELECT 1 FROM public.hr_employees WHERE tenant_id=t)
    OR EXISTS (SELECT 1 FROM public.organization_legal_entities WHERE tenant_id=t) THEN
    RAISE EXCEPTION 'C4_POSITIVE_FIXTURE_NOT_EMPTY';
  END IF;
  IF logged_tenant IS DISTINCT FROM t::text THEN
    RAISE EXCEPTION 'C4_POSITIVE_TENANT_CONTEXT_NOT_SET';
  END IF;
  PERFORM public.hr_assert_migration_tenant_scope(t);
  SELECT public.hr_default_migration_as_of_date(t) INTO as_of;
  IF as_of IS DISTINCT FROM DATE '1970-01-01' THEN
    RAISE EXCEPTION 'C4_POSITIVE_UNEXPECTED_AS_OF_DATE=%',as_of;
  END IF;
  RAISE NOTICE 'C4_HR_POSITIVE_SYNTHETIC=PASS 2/2 role=sanad (scope check and default date only)';
END $positive$;
ROLLBACK;
