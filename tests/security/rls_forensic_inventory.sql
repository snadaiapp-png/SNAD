-- Read-only RLS forensic inventory. Run only against a disposable/test database
-- or use catalog-only inspection on production. This script does not mutate schema/data.
\echo '=== public table RLS status ==='
SELECT n.nspname AS schema_name, c.relname AS table_name,
       c.relrowsecurity AS rls_enabled,
       c.relforcerowsecurity AS force_rls,
       has_table_privilege('anon', c.oid, 'SELECT') AS anon_select,
       has_table_privilege('authenticated', c.oid, 'SELECT') AS authenticated_select,
       EXISTS (
         SELECT 1
         FROM aclexplode(coalesce(c.relacl, acldefault('r', c.relowner))) AS acl
         WHERE acl.grantee = 0 AND acl.privilege_type = 'SELECT'
       ) AS public_select
FROM pg_catalog.pg_class c
JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public' AND c.relkind IN ('r','p')
ORDER BY c.relrowsecurity, c.relname;

\echo '=== candidate tenant-context NULL-allow policies ==='
SELECT schemaname, tablename, policyname, roles, cmd, qual, with_check
FROM pg_catalog.pg_policies
WHERE schemaname = 'public'
  AND (
    coalesce(qual, '') ILIKE '%current_setting%app.tenant_id%'
    OR coalesce(with_check, '') ILIKE '%current_setting%app.tenant_id%'
  )
  AND (
    coalesce(qual, '') ILIKE '%IS NULL%'
    OR coalesce(with_check, '') ILIKE '%IS NULL%'
  )
ORDER BY tablename, policyname;

\echo '=== SECURITY DEFINER functions and client EXECUTE privileges ==='
SELECT n.nspname AS schema_name, p.proname AS function_name,
       pg_catalog.pg_get_function_identity_arguments(p.oid) AS arguments,
       p.prosecdef AS security_definer, p.proconfig AS function_config,
       has_function_privilege('anon', p.oid, 'EXECUTE') AS anon_execute,
       has_function_privilege('authenticated', p.oid, 'EXECUTE') AS authenticated_execute
FROM pg_catalog.pg_proc p
JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace
WHERE n.nspname = 'public' AND p.prosecdef
ORDER BY p.proname, arguments;

\echo '=== functions without pinned search_path ==='
SELECT n.nspname AS schema_name, p.proname AS function_name,
       pg_catalog.pg_get_function_identity_arguments(p.oid) AS arguments,
       p.prosecdef AS security_definer, p.proconfig AS function_config
FROM pg_catalog.pg_proc p
JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace
WHERE n.nspname = 'public'
  AND (
    p.proconfig IS NULL
    OR NOT EXISTS (
      SELECT 1 FROM unnest(p.proconfig) AS cfg
      WHERE cfg LIKE 'search_path=%'
    )
  )
ORDER BY p.proname, arguments;


-- Additional catalog-only G1 review surfaces. These queries deliberately do
-- NOT issue ALTER TABLE, REVOKE, GRANT, SET ROLE, or change session GUCs.
-- A reported gap is a review finding, NOT permission to FORCE RLS blindly.
\echo '=== ENABLE RLS without FORCE (requires per-table threat-model review) ==='
SELECT n.nspname AS schema_name, c.relname AS table_name,
       pg_catalog.pg_get_userbyid(c.relowner) AS table_owner,
       c.relrowsecurity AS rls_enabled, c.relforcerowsecurity AS force_rls,
       (SELECT count(*) FROM pg_catalog.pg_policy p WHERE p.polrelid=c.oid) AS policy_count
FROM pg_catalog.pg_class c
JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='public' AND c.relkind IN ('r','p')
  AND c.relrowsecurity AND NOT c.relforcerowsecurity
ORDER BY c.relname;

\echo '=== subscription ledger effective privileges for runtime (role-aware) ==='
SELECT c.oid::regclass AS relation_name,
       pg_catalog.pg_get_userbyid(c.relowner) AS owner_name,
       c.relrowsecurity AS rls_enabled, c.relforcerowsecurity AS force_rls,
       EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname='sanad') AS runtime_role_exists,
       CASE WHEN EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname='sanad')
            THEN has_table_privilege('sanad',c.oid,'SELECT') END AS runtime_select,
       CASE WHEN EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname='sanad')
            THEN has_table_privilege('sanad',c.oid,'INSERT') END AS runtime_insert,
       CASE WHEN EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname='sanad')
            THEN has_table_privilege('sanad',c.oid,'UPDATE') END AS runtime_update,
       CASE WHEN EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname='sanad')
            THEN has_table_privilege('sanad',c.oid,'DELETE') END AS runtime_delete,
       CASE WHEN EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname='sanad')
            THEN has_table_privilege('sanad',c.oid,'TRUNCATE') END AS runtime_truncate
FROM pg_catalog.pg_class c
WHERE c.oid=to_regclass('public.subscription_commands');

\echo '=== SECURITY DEFINER owner and effective EXECUTE including PUBLIC ==='
SELECT p.oid::regprocedure AS routine,
       pg_catalog.pg_get_userbyid(p.proowner) AS owner_name,
       p.proconfig AS routine_config,
       has_function_privilege('PUBLIC',p.oid,'EXECUTE') AS public_execute,
       CASE WHEN EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname='anon')
            THEN has_function_privilege('anon',p.oid,'EXECUTE') END AS anon_execute,
       CASE WHEN EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname='authenticated')
            THEN has_function_privilege('authenticated',p.oid,'EXECUTE') END AS authenticated_execute,
       CASE WHEN EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname='sanad')
            THEN has_function_privilege('sanad',p.oid,'EXECUTE') END AS runtime_execute
FROM pg_catalog.pg_proc p
JOIN pg_catalog.pg_namespace n ON n.oid=p.pronamespace
WHERE n.nspname='public' AND p.prosecdef
ORDER BY p.oid::regprocedure::text;
