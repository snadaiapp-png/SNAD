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
