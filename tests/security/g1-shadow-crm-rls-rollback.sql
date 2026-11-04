\set ON_ERROR_STOP on
BEGIN;
DO $guard$
BEGIN
 IF current_database()<>'g1_sep_shadow_20261104' OR inet_server_port()<>55474
 OR (SELECT system_identifier::text FROM pg_control_system())<>'7703842253085562712'
 THEN RAISE EXCEPTION 'BLOCKED: unexpected shadow PostgreSQL target'; END IF;
END $guard$;
INSERT INTO public.crm_customer_segments(tenant_id,segment_code,segment_name,segment_type)
VALUES
('11111111-1111-1111-1111-111111111171','g1-fix-tenant-a','G1 fix A','MANUAL'),
('22222222-2222-2222-2222-222222222272','g1-fix-tenant-b','G1 fix B','MANUAL');
-- Reconstruct the upgrade ordering on a fully reversible shadow fixture:
-- ownership bootstrap -> separated-mode ACL migration -> new CRM policy repair.
\ir ../../scripts/ops/migrator-separation/transfer-ownership-to-migrator.sql
SET LOCAL sanad.deployment_topology='separated';
\ir ../../apps/sanad-platform/src/main/resources/db/migration/V20261103_2__migrator_ownership_and_runtime_acl.sql
\ir ../../apps/sanad-platform/src/main/resources/db/migration/V20261104_2__crm_rls_fail_closed_force.sql
-- Replay exact forward migration to verify idempotent DDL on the same snapshot.
\ir ../../apps/sanad-platform/src/main/resources/db/migration/V20261104_2__crm_rls_fail_closed_force.sql
-- Each of the 19 tables must have exactly one tenant_isolation policy
-- with both USING and WITH CHECK identical to the verified reference policy.
DO $crm_policy_contract$
DECLARE ref_using text; ref_check text; mismatch_count integer;
BEGIN
 SELECT pg_get_expr(p.polqual,p.polrelid),pg_get_expr(p.polwithcheck,p.polrelid)
 INTO ref_using,ref_check FROM pg_policy p
 WHERE p.polrelid='public.crm_customer_segments'::regclass
 AND p.polname='tenant_isolation' AND p.polcmd='*';
 IF ref_using IS NULL OR ref_check IS NULL OR
    position('current_setting' in ref_using)=0 OR
    position('tenant_id' in ref_using)=0 OR
    position('NULLIF' in ref_using)=0 OR
    position(' OR ' in ref_using)>0 OR
    ref_using<>ref_check
 THEN RAISE EXCEPTION 'G1_CRM_POLICY_REFERENCE_INVALID'; END IF;
 WITH expected(name) AS (
  SELECT unnest(ARRAY[
   'crm_assignment_rule_counters','crm_assignment_rule_versions',
   'crm_customer_score_history','crm_customer_scores','crm_customer_segments',
   'crm_integration_command_artifacts','crm_integration_command_executions',
   'crm_integration_decisions','crm_integration_outbox','crm_integration_requests',
   'crm_ownership_history','crm_queues','crm_scoring_models','crm_segment_memberships',
   'crm_team_memberships','crm_territories','crm_territory_assignments',
   'crm_territory_closure','crm_transfer_requests'
  ])
 )
 SELECT count(*) INTO mismatch_count FROM expected e
 LEFT JOIN pg_class c ON c.oid=to_regclass(format('public.%I',e.name))
 WHERE c.oid IS NULL OR NOT c.relrowsecurity OR NOT c.relforcerowsecurity OR
       (SELECT count(*) FROM pg_policy p WHERE p.polrelid=c.oid)<>1 OR
       NOT EXISTS (SELECT 1 FROM pg_policy p WHERE p.polrelid=c.oid
       AND p.polname='tenant_isolation' AND p.polcmd='*'
       AND pg_get_expr(p.polqual,p.polrelid)=ref_using
       AND pg_get_expr(p.polwithcheck,p.polrelid)=ref_check);
 IF mismatch_count<>0 THEN RAISE EXCEPTION 'G1_CRM_POLICY_CONTRACT_FAIL count=%',mismatch_count; END IF;
 RAISE NOTICE 'G1_CRM_POLICY_CONTRACT=PASS tables=19 using_and_with_check_equal';
END $crm_policy_contract$;
-- The official catalog gate must not raise while the corrected ACL/RLS
-- state is visible in this transaction (not a production release decision).
\ir ../../apps/sanad-platform/src/test/resources/sql/g1-rls-release-readonly-gate.sql
DO $ownership$
DECLARE owner_name text; truncate_grants integer;
BEGIN
 SELECT pg_get_userbyid(relowner) INTO owner_name FROM pg_class
 WHERE oid='public.crm_customer_segments'::regclass;
 SELECT count(*) INTO truncate_grants FROM pg_class c
 WHERE c.relnamespace='public'::regnamespace AND c.relkind IN ('r','p')
 AND has_table_privilege('sanad',c.oid,'TRUNCATE');
 IF owner_name <> 'sanad_migrator' OR truncate_grants<>0 THEN
   RAISE EXCEPTION 'G1_OWNERSHIP_SEPARATION=FAIL owner=% runtime_truncate=%',owner_name,truncate_grants;
 END IF;
 RAISE NOTICE 'G1_OWNERSHIP_SEPARATION=PASS owner=% runtime_truncate=%',owner_name,truncate_grants;
END $ownership$;
-- Separate runtime check before assigning tenant context in this session.
SET LOCAL ROLE sanad;
DO $missing$
DECLARE visible integer; guc text;
BEGIN
 guc := current_setting('app.tenant_id',true);
 IF guc IS NOT NULL AND guc<>'' THEN
   RAISE EXCEPTION 'BLOCKED inherited tenant context: %',guc;
 END IF;
 SELECT count(*) INTO visible FROM public.crm_customer_segments
 WHERE segment_code IN ('g1-fix-tenant-a','g1-fix-tenant-b');
 IF visible<>0 THEN
   RAISE EXCEPTION 'G1_MISSING_GUC=FAIL visible=%',visible;
 END IF;
 RAISE NOTICE 'G1_MISSING_GUC=PASS visible=%',visible;
END $missing$;
-- All 19 CRM tables must actually enforce RLS for the non-owner runtime role;
-- checking only relforcerowsecurity in the catalog is not sufficient.
DO $every_crm$
DECLARE tbl text; visible bigint; verified int:=0;
BEGIN
 FOR tbl IN
   SELECT unnest(ARRAY[
    'crm_assignment_rule_counters','crm_assignment_rule_versions',
    'crm_customer_score_history','crm_customer_scores','crm_customer_segments',
    'crm_integration_command_artifacts','crm_integration_command_executions',
    'crm_integration_decisions','crm_integration_outbox','crm_integration_requests',
    'crm_ownership_history','crm_queues','crm_scoring_models','crm_segment_memberships',
    'crm_team_memberships','crm_territories','crm_territory_assignments',
    'crm_territory_closure','crm_transfer_requests'
   ])
 LOOP
   IF NOT row_security_active(format('public.%I',tbl)::regclass)
     OR NOT has_table_privilege(current_user, format('public.%I',tbl), 'SELECT')
     OR has_table_privilege(current_user, format('public.%I',tbl), 'TRUNCATE') THEN
       RAISE EXCEPTION 'G1_CRM_RUNTIME_CATALOG_FAIL table=%',tbl;
   END IF;
   EXECUTE format('SELECT count(*) FROM public.%I',tbl) INTO visible;
   IF visible<>0 THEN
     RAISE EXCEPTION 'G1_CRM_NO_GUC_READ_FAIL table=% visible=%',tbl,visible;
   END IF;
   verified:=verified+1;
 END LOOP;
 IF verified<>19 THEN RAISE EXCEPTION 'G1_CRM_COUNT_FAIL count=%',verified; END IF;
 RAISE NOTICE 'G1_CRM_ALL_19_RUNTIME=PASS active_rls=19 no_guc_rows=0 runtime_truncate=0';
END $every_crm$;
RESET ROLE;
DO $catalog$
DECLARE n integer;
BEGIN
 SELECT count(*) INTO n FROM pg_class c JOIN pg_namespace ns ON ns.oid=c.relnamespace
 WHERE ns.nspname='public' AND c.relname IN
 ('crm_assignment_rule_counters','crm_assignment_rule_versions','crm_customer_score_history',
 'crm_customer_scores','crm_customer_segments','crm_integration_command_artifacts',
 'crm_integration_command_executions','crm_integration_decisions','crm_integration_outbox',
 'crm_integration_requests','crm_ownership_history','crm_queues','crm_scoring_models',
 'crm_segment_memberships','crm_team_memberships','crm_territories','crm_territory_assignments',
 'crm_territory_closure','crm_transfer_requests') AND c.relrowsecurity AND c.relforcerowsecurity;
 IF n<>19 THEN RAISE EXCEPTION 'G1_FORCE_RLS=FAIL count=%',n; END IF;
 RAISE NOTICE 'G1_FORCE_RLS=PASS count=%',n;
END $catalog$;
SELECT set_config('app.tenant_id','11111111-1111-1111-1111-111111111171',true);
SET LOCAL ROLE sanad;
DO $a$
DECLARE own_count integer; foreign_count integer;
BEGIN
 SELECT count(*) INTO own_count FROM public.crm_customer_segments WHERE segment_code='g1-fix-tenant-a';
 SELECT count(*) INTO foreign_count FROM public.crm_customer_segments WHERE segment_code='g1-fix-tenant-b';
 IF own_count<>1 OR foreign_count<>0 THEN RAISE EXCEPTION 'G1_A_B=FAIL own=% foreign=%',own_count,foreign_count; END IF;
 RAISE NOTICE 'G1_A_B=PASS own=% foreign=%',own_count,foreign_count;
END $a$;
DO $write_denial$
DECLARE foreign_changed integer; error_code text; own_changed integer;
BEGIN
 WITH changed AS (
  UPDATE public.crm_customer_segments SET segment_name='G1 forbidden update'
  WHERE segment_code='g1-fix-tenant-b' RETURNING 1
 ) SELECT count(*) INTO foreign_changed FROM changed;
 IF foreign_changed<>0 THEN
   RAISE EXCEPTION 'G1_CROSS_UPDATE=FAIL changed=%',foreign_changed;
 END IF;
 BEGIN
   INSERT INTO public.crm_customer_segments(tenant_id,segment_code,segment_name,segment_type)
   VALUES('22222222-2222-2222-2222-222222222272','g1-fix-cross-insert','G1 forbidden insert','MANUAL');
   RAISE EXCEPTION 'G1_CROSS_INSERT=FAIL unexpectedly allowed';
 EXCEPTION WHEN insufficient_privilege THEN
   GET STACKED DIAGNOSTICS error_code=RETURNED_SQLSTATE;
   IF error_code<>'42501' THEN RAISE; END IF;
 END;
 WITH changed AS (
   UPDATE public.crm_customer_segments SET segment_name='G1 authorized'
   WHERE segment_code='g1-fix-tenant-a' RETURNING 1
 ) SELECT count(*) INTO own_changed FROM changed;
 IF own_changed<>1 THEN
   RAISE EXCEPTION 'G1_OWN_UPDATE=FAIL changed=%',own_changed;
 END IF;
 RAISE NOTICE 'G1_WRITES=PASS foreign_update=0 cross_insert=denied_42501 own_update=1';
END $write_denial$;
SELECT set_config('app.tenant_id','',true);
DO $empty$
DECLARE visible integer;
BEGIN
 SELECT count(*) INTO visible FROM public.crm_customer_segments
 WHERE segment_code IN ('g1-fix-tenant-a','g1-fix-tenant-b');
 IF visible<>0 THEN RAISE EXCEPTION 'G1_EMPTY_GUC=FAIL visible=%',visible; END IF;
 RAISE NOTICE 'G1_EMPTY_GUC=PASS visible=%',visible;
END $empty$;
RESET ROLE;
ROLLBACK;
