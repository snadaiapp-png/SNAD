-- C4 isolated only: executable negative tenant authorization matrix for ALL
-- 10 directly invokable HR SECURITY DEFINER signatures.
-- The CI wrapper runs this in disposable separation_cert PG17, not production.
-- Every test is inside an explicit transaction and always rolls back.
BEGIN;
SET LOCAL ROLE sanad;
DO $c4_negative$
DECLARE
  tenant_a constant uuid := 'aaaaaaaa-0000-0000-0000-0000000000a4';
  tenant_b constant uuid := 'bbbbbbbb-0000-0000-0000-0000000000b4';
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
  -- The minimal positive control validates the scope checker itself; it
  -- performs no HR mutations and uses a synthetic tenant with no data.
  PERFORM set_config('app.tenant_id',tenant_a::text,true);
  PERFORM public.hr_assert_migration_tenant_scope(tenant_a);
  RAISE NOTICE 'C4_HR_NEGATIVE_MATRIX=PASS 20/20 expected_denials; positive_scope=PASS';
END
$c4_negative$;
ROLLBACK;
