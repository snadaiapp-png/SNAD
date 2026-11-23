-- C4 strictly isolated POSITIVE control for two read-only HR routines.
-- NOT an authorization test of real business/user paths or the other HR functions.
-- Must run after Flyway on disposable separated PostgreSQL; no production access.
--
-- G1 P0-B v5 (V20261124_1/V20261124_2): hr_assert_migration_tenant_scope now
-- validates the caller context through the SIGNED TENANT-CONTEXT BIND
-- protocol (app_tenant_ctx_text), so this control reproduces the backend
-- protocol exactly: the operator identity (postgres, superuser — the ONLY
-- identity that can read the zero-grant keys table) mints the mac over its
-- own slot and binds, THEN switches to the runtime role for the calls under
-- test. Every statement below is inside ONE transaction, so txid/pid binding
-- holds. On an unbound session the scope assert now DENIES (fail closed) —
-- which is precisely the negative matrix's expectation.
\set ON_ERROR_STOP on
BEGIN;
SELECT gen_random_uuid() AS t \gset
-- Operator step: read the ACTIVE key (superuser-only by design) and bind the
-- synthetic tenant within this same transaction (V20261124_1 protocol).
WITH slot AS (SELECT * FROM public.app_tenant_ctx_slot()),
     k AS (SELECT secret FROM public.platform_tenant_ctx_keys WHERE status = 'ACTIVE' ORDER BY created_at DESC LIMIT 1)
SELECT public.app_bind_tenant_ctx(:'t'::uuid,
           encode(hmac((:'t'::text || ':' || slot.ctx_txid || ':' || slot.ctx_pid::text)::bytea, k.secret, 'sha256'), 'hex')) AS bound
FROM slot, k \gset
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
  -- The signed bind (operator step, same transaction) validated this tenant:
  -- the validator is the single authoritative source for the context value.
  t uuid := public.app_tenant_ctx_text()::uuid;
  as_of date;
  logged_tenant text;
  bound_tenant text;
BEGIN
  IF current_user <> 'sanad' THEN
    RAISE EXCEPTION 'C4_POSITIVE_WRONG_ROLE=%',current_user;
  END IF;
  -- The signed bind (operator step above) set the transaction context.
  logged_tenant := current_setting('app.tenant_id', true);
  bound_tenant := public.app_tenant_ctx_text();
  -- Synthetic UUID avoids modifying business data; confirm no existing tenant data.
  IF EXISTS (SELECT 1 FROM public.hr_employees WHERE tenant_id=t)
    OR EXISTS (SELECT 1 FROM public.organization_legal_entities WHERE tenant_id=t) THEN
    RAISE EXCEPTION 'C4_POSITIVE_FIXTURE_NOT_EMPTY';
  END IF;
  IF logged_tenant IS DISTINCT FROM t::text THEN
    RAISE EXCEPTION 'C4_POSITIVE_TENANT_CONTEXT_NOT_SET';
  END IF;
  IF bound_tenant IS DISTINCT FROM t::text THEN
    RAISE EXCEPTION 'C4_POSITIVE_SIGNED_CONTEXT_NOT_VALIDATED';
  END IF;
  PERFORM public.hr_assert_migration_tenant_scope(t);
  SELECT public.hr_default_migration_as_of_date(t) INTO as_of;
  IF as_of IS DISTINCT FROM DATE '1970-01-01' THEN
    RAISE EXCEPTION 'C4_POSITIVE_UNEXPECTED_AS_OF_DATE=%',as_of;
  END IF;
  RAISE NOTICE 'C4_HR_POSITIVE_SYNTHETIC=PASS 2/2 role=sanad (scope check and default date only; signed-bind protocol)';
END $positive$;
ROLLBACK;
