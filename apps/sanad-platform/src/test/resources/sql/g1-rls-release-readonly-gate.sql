-- Read-only G1 release gate. Run using a restricted connection on intended target.
-- A query returning rows signals that security closure is incomplete.
SELECT current_database() AS database_name,current_user AS active_role;
SELECT c.relname AS tenant_table, c.relrowsecurity AS rls_enabled,
       c.relforcerowsecurity AS force_rls,
       pg_get_userbyid(c.relowner) AS owner
FROM pg_class c
JOIN pg_namespace n ON n.oid=c.relnamespace
JOIN pg_attribute a ON a.attrelid=c.oid AND a.attname='tenant_id'
WHERE n.nspname='public' AND c.relkind IN('r','p')
  AND c.relrowsecurity AND NOT c.relforcerowsecurity
ORDER BY c.relname;
SELECT c.relname AS policy_table,p.polname,
       pg_get_expr(p.polqual,p.polrelid) AS using_clause,
       pg_get_expr(p.polwithcheck,p.polrelid) AS check_clause
FROM pg_policy p JOIN pg_class c ON c.oid=p.polrelid
WHERE c.relnamespace='public'::regnamespace
  AND c.relrowsecurity AND
  (pg_get_expr(p.polqual,p.polrelid) LIKE '%current_setting(''app.tenant_id''%IS NULL) OR%'
   OR pg_get_expr(p.polwithcheck,p.polrelid) LIKE '%current_setting(''app.tenant_id''%IS NULL) OR%')
ORDER BY c.relname,p.polname;
SELECT c.relname AS owner_privilege_issue
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='public' AND c.relkind IN('r','p')
AND (pg_get_userbyid(c.relowner)='sanad' OR has_table_privilege('sanad',c.oid,'TRUNCATE'))
ORDER BY c.relname;

-- Fail the process with SQLSTATE P0001 while findings remain. Querying
-- findings alone is not an accepted security gate.
DO $g1_release_gate$
DECLARE
    v_nonforced integer;
    v_failopen integer;
    v_owner_or_truncate integer;
BEGIN
    SELECT count(*) INTO v_nonforced
    FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
    JOIN pg_attribute a ON a.attrelid=c.oid AND a.attname='tenant_id'
    WHERE n.nspname='public' AND c.relkind IN ('r','p')
      AND c.relrowsecurity AND NOT c.relforcerowsecurity;

    SELECT count(*) INTO v_failopen
    FROM pg_policy p JOIN pg_class c ON c.oid=p.polrelid
    WHERE c.relnamespace='public'::regnamespace
      AND c.relrowsecurity
      AND (pg_get_expr(p.polqual,p.polrelid) LIKE '%current_setting(''app.tenant_id''%IS NULL) OR%'
        OR pg_get_expr(p.polwithcheck,p.polrelid) LIKE '%current_setting(''app.tenant_id''%IS NULL) OR%');

    SELECT count(*) INTO v_owner_or_truncate
    FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
    WHERE n.nspname='public' AND c.relkind IN ('r','p')
      AND (pg_get_userbyid(c.relowner)='sanad' OR has_table_privilege('sanad',c.oid,'TRUNCATE'));

    IF v_nonforced>0 OR v_failopen>0 OR v_owner_or_truncate>0 THEN
      RAISE EXCEPTION
      'G1_RELEASE_GATE_FAIL nonforced=% failopen_policies=% runtime_owner_or_truncate=%',
      v_nonforced,v_failopen,v_owner_or_truncate;
    END IF;
    RAISE NOTICE 'G1_RELEASE_GATE_CATALOG_CHECK=PASS (behavioral tests still required)';
END $g1_release_gate$;

-- Fail closed for legacy CRM even if a policy is changed to USING (true):
-- the generic IS NULL/OR heuristic above cannot detect arbitrary permissive
-- policy regressions. Compare the deparsed expressions against the exact
-- PostgreSQL 17 canonical form of the reviewed V20261104_2 policy.
DO $g1_crm_policy_integrity$
DECLARE
  expected_expr text := $canonical$((tenant_id)::text = NULLIF(current_setting('app.tenant_id'::text, true), ''::text))$canonical$;
  table_name text;
  bad_count integer;
  checked_count integer := 0;
BEGIN
  FOREACH table_name IN ARRAY ARRAY[
    'crm_assignment_rule_counters','crm_assignment_rule_versions',
    'crm_customer_score_history','crm_customer_scores','crm_customer_segments',
    'crm_integration_command_artifacts','crm_integration_command_executions',
    'crm_integration_decisions','crm_integration_outbox','crm_integration_requests',
    'crm_ownership_history','crm_queues','crm_scoring_models',
    'crm_segment_memberships','crm_team_memberships','crm_territories',
    'crm_territory_assignments','crm_territory_closure','crm_transfer_requests'
  ] LOOP
    SELECT count(*) INTO bad_count
    FROM pg_class c
    WHERE c.oid=to_regclass(format('public.%I',table_name))
      AND c.relrowsecurity AND c.relforcerowsecurity
      AND (SELECT count(*) FROM pg_policy p WHERE p.polrelid=c.oid)=1
      AND EXISTS (
        SELECT 1 FROM pg_policy p WHERE p.polrelid=c.oid
        AND p.polname='tenant_isolation' AND p.polcmd='*'
        AND pg_get_expr(p.polqual,p.polrelid)=expected_expr
        AND pg_get_expr(p.polwithcheck,p.polrelid)=expected_expr
      );
    IF bad_count<>1 THEN
      RAISE EXCEPTION 'G1_CRM_POLICY_INTEGRITY_FAIL table=%',table_name;
    END IF;
    checked_count:=checked_count+1;
  END LOOP;
  RAISE NOTICE 'G1_CRM_POLICY_INTEGRITY=PASS tables=%',checked_count;
END $g1_crm_policy_integrity$;
