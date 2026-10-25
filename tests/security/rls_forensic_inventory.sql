-- Read-only RLS forensic inventory. Run only against a disposable/test database
-- or use catalog-only inspection on production. This script does not mutate schema/data.
\echo '=== public table RLS status ==='
SELECT n.nspname AS schema_name, c.relname AS table_name,
       c.relrowsecurity AS rls_enabled,
       c.relforcerowsecurity AS force_rls,
       has_table_privilege('anon', c.oid, 'SELECT') AS anon_select,
       has_table_privilege('authenticated', c.oid, 'SELECT') AS authenticated_select,
       has_table_privilege('public', c.oid, 'SELECT') AS public_select
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
