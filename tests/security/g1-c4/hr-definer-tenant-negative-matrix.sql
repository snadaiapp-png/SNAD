-- C4 isolated only: executable negative tenant authorization matrix for ALL
-- 10 directly invokable HR SECURITY DEFINER signatures.
-- The CI wrapper runs this in disposable separation_cert PG17, not production.
-- Every test is inside an explicit transaction and always rolls back.
--
-- Structure (two independent transactions):
--   1. POSITIVE control — runs as the provisioning operator (postgres) in its
--      OWN transaction: reads the operator-provisioned per-run key from
--      platform_tenant_ctx_keys (documented separated-topology operator flow;
--      V20261124_2 auto-provisions NOTHING on the separation topology) and
--      binds the positive tenant through the full HMAC protocol. psql variable
--      interpolation is NOT performed inside dollar-quoted bodies, so the key
--      is read from the table — never inlined as a :var.
--   2. NEGATIVE matrix — SET LOCAL ROLE sanad; 20 expected-denial cases. The
--      positive bind above cannot leak here: its transaction already rolled
--      back (bindings are transaction-local).
BEGIN;
DO $c4_positive$
DECLARE
  tenant_a constant uuid := 'aaaaaaaa-0000-0000-0000-0000000000a4';
  v_key bytea;
  v_mac text;
BEGIN
  SELECT secret INTO v_key
    FROM public.platform_tenant_ctx_keys
   WHERE key_id = 'c4-matrix-operator' AND status = 'ACTIVE'
   ORDER BY created_at DESC
   LIMIT 1;
  IF v_key IS NULL THEN
    RAISE EXCEPTION 'C4_OPERATOR_KEY_MISSING: CI wrapper must provision the per-run operator key first';
  END IF;
  v_mac := encode(hmac(
      convert_to(tenant_a::text || ':' || txid_current()::text || ':' || pg_backend_pid()::text, 'UTF8'),
      v_key, 'sha256'), 'hex');
  IF NOT public.app_bind_tenant_ctx(tenant_a, v_mac)
     OR public.app_tenant_ctx_text() IS DISTINCT FROM tenant_a::text THEN
    RAISE EXCEPTION 'C4_POSITIVE_SIGNED_BIND_REFUSED';
  END IF;
  PERFORM public.hr_assert_migration_tenant_scope(tenant_a);
  RAISE NOTICE 'C4_POSITIVE_SCOPE_CONTROL=PASS';
END
$c4_positive$;
ROLLBACK;

BEGIN;
SET LOCAL ROLE sanad;
DO $c4_negative$
DECLARE
  tenant_a constant uuid := 'aaaaaaaa-0000-0000-0000-0000000000a4';
  tenant_b constant uuid := 'bbbbbbbb-0000-0000-0000-0000000000b4';
  v_mac text;
  sig text;
  statement text;
  scenario text;
  got_state text;
  count_checked integer := 0;
  funcs constant text[] := ARRAY[
    'hr_assert_migration_tenant_scope(uuid)',
    'hr_default_migration_as_of_date(uuid)',
    'hr_precheck_tenant(uuid)',
    'hr_precheck_tenant(uuid,date)',
    'hr_reconcile_tenant_report(uuid)',
    'hr_reconcile_tenant_report(uuid,date)',
    'hr_reconcile_tenant(uuid)',
    'hr_reconcile_tenant(uuid,date)',
    'hr_backfill_tenant(uuid)',
    'hr_backfill_tenant(uuid,date)'
  ];
BEGIN
  IF current_user <> 'sanad' THEN
    RAISE EXCEPTION 'C4_NEGATIVE_WRONG_RUNTIME_ROLE=%',current_user USING ERRCODE='42501';
  END IF;
  FOREACH sig IN ARRAY funcs LOOP
    IF to_regprocedure('public.'||sig) IS NULL THEN
      RAISE EXCEPTION 'C4_NEGATIVE_FUNCTION_MISSING=%',sig USING ERRCODE='42501';
    END IF;
    IF NOT has_function_privilege(current_user,'public.'||sig,'EXECUTE') THEN
      RAISE EXCEPTION 'C4_NEGATIVE_NO_EXECUTE_GRANT=%',sig USING ERRCODE='42501';
    END IF;
    -- Precheck all signatures; a successful call with missing/mismatched
    -- GUC is an actual security failure, not a skipped test.
    statement := 'SELECT '||CASE WHEN sig LIKE 'hr_reconcile_tenant_report%' THEN '* FROM ' ELSE '' END
       ||'public.'||split_part(sig,'(',1)||'($1::uuid'
       ||CASE WHEN sig LIKE '%,date)' THEN ',$2::date' ELSE '' END||')';
    FOREACH scenario IN ARRAY ARRAY['missing_or_empty_guc','foreign_tenant']::text[] LOOP
      IF scenario='missing_or_empty_guc' THEN
        PERFORM set_config('app.tenant_id','',true);
      ELSE
        PERFORM set_config('app.tenant_id',tenant_b::text,true);
      END IF;
      got_state := NULL;
      BEGIN
        EXECUTE statement USING tenant_a, date '2026-11-13';
      EXCEPTION WHEN OTHERS THEN
        GET STACKED DIAGNOSTICS got_state = RETURNED_SQLSTATE;
      END;
      IF got_state IS DISTINCT FROM '42501' THEN
        RAISE EXCEPTION 'C4_HR_NEGATIVE_FAIL fn=% case=% sqlstate=% expected=42501',
          sig,scenario,coalesce(got_state,'NO_ERROR') USING ERRCODE='42501';
      END IF;
      count_checked := count_checked + 1;
      RAISE NOTICE 'C4_HR_NEGATIVE_PASS function=% scenario=% sqlstate=%',
        sig,scenario,got_state;
    END LOOP;
  END LOOP;
  IF count_checked <> 20 THEN
    RAISE EXCEPTION 'C4_HR_NEGATIVE_WRONG_COUNT=%',count_checked USING ERRCODE='42501';
  END IF;
  RAISE NOTICE 'C4_HR_NEGATIVE_MATRIX=PASS 20/20 expected_denials';
END
$c4_negative$;
ROLLBACK;
