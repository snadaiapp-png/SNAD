-- G1: explicit fail-closed tenant isolation for the 19 historical CRM tables.
-- Forward-only. Requires independent review and PostgreSQL A/B evidence.
DO $g1$
DECLARE
  t text;
  names text[] := ARRAY[
    'crm_assignment_rule_counters','crm_assignment_rule_versions',
    'crm_customer_score_history','crm_customer_scores','crm_customer_segments',
    'crm_integration_command_artifacts','crm_integration_command_executions',
    'crm_integration_decisions','crm_integration_outbox','crm_integration_requests',
    'crm_ownership_history','crm_queues','crm_scoring_models',
    'crm_segment_memberships','crm_team_memberships','crm_territories',
    'crm_territory_assignments','crm_territory_closure','crm_transfer_requests'
  ];
  policy_count int;
BEGIN
  FOREACH t IN ARRAY names LOOP
    IF NOT EXISTS (
      SELECT 1 FROM pg_class
      WHERE oid=format('public.%I',t)::regclass AND relrowsecurity
    ) THEN
      RAISE EXCEPTION 'G1 expected RLS-enabled table: %',t;
    END IF;
    SELECT count(*) INTO policy_count
      FROM pg_policy WHERE polrelid=format('public.%I',t)::regclass;
    IF policy_count <> 1 THEN
      RAISE EXCEPTION 'G1 expected exactly one policy for %, found %',t,policy_count;
    END IF;
    SELECT count(*) INTO policy_count
      FROM pg_policy WHERE polrelid=format('public.%I',t)::regclass
      AND polname='tenant_isolation' AND polcmd='*';
    IF policy_count <> 1 THEN
      RAISE EXCEPTION 'G1 expected tenant_isolation FOR ALL on %',t;
    END IF;
    EXECUTE format(
      'ALTER POLICY tenant_isolation ON public.%I USING (tenant_id::text = NULLIF(current_setting(''app.tenant_id'',true),'''')) WITH CHECK (tenant_id::text = NULLIF(current_setting(''app.tenant_id'',true),''''))',t
    );
    EXECUTE format('ALTER TABLE public.%I FORCE ROW LEVEL SECURITY',t);
  END LOOP;
END
$g1$;
