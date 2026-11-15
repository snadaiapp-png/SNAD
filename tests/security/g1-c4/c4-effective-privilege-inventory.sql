-- C4 read-only effective privilege inventory, disposable separated PG17 ONLY.
-- This is an evidence generator, not a PASS decision or production authorization.
-- Emits one row per SECURITY DEFINER function and RLS-off table with effective
-- runtime/service/public grants for explicit risk assessment.
\set ON_ERROR_STOP on
DO $preflight$
BEGIN
  IF current_database() <> 'separation_cert' THEN
    RAISE EXCEPTION 'C4_ACL_INVENTORY_DISALLOWED_DATABASE=%', current_database();
  END IF;
  IF current_user <> 'postgres' THEN
    RAISE EXCEPTION 'C4_ACL_INVENTORY_EXPECTS_POSTGRES_OPERATOR=%', current_user;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='sanad' AND NOT rolsuper AND NOT rolbypassrls) THEN
    RAISE EXCEPTION 'C4_ACL_INVENTORY_UNSAFE_RUNTIME_ROLE';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_database WHERE datname=current_database()
    AND pg_get_userbyid(datdba)='sanad_migrator') THEN
    RAISE EXCEPTION 'C4_ACL_INVENTORY_UNEXPECTED_DB_OWNER';
  END IF;
END $preflight$;

\pset format unaligned
\pset fieldsep '|'
\pset tuples_only off

SELECT 'C4_DEFINER_ACL' AS record_type,
       p.oid::regprocedure::text AS object_id,
       pg_get_userbyid(p.proowner) AS owner,
       coalesce(array_to_string(p.proconfig,','),'') AS function_config,
       has_function_privilege('sanad',p.oid,'EXECUTE') AS runtime_execute,
       CASE WHEN EXISTS(SELECT 1 FROM pg_roles WHERE rolname='service_role')
            THEN has_function_privilege('service_role',p.oid,'EXECUTE')
            ELSE NULL END AS service_execute,
       EXISTS (SELECT 1 FROM aclexplode(coalesce(p.proacl,acldefault('f',p.proowner))) acl WHERE acl.grantee=0 AND acl.privilege_type='EXECUTE') AS public_execute,
       CASE WHEN EXISTS(SELECT 1 FROM pg_roles WHERE rolname='authenticated')
            THEN has_function_privilege('authenticated',p.oid,'EXECUTE')
            ELSE NULL END AS authenticated_execute,
       CASE WHEN EXISTS(SELECT 1 FROM pg_roles WHERE rolname='anon')
            THEN has_function_privilege('anon',p.oid,'EXECUTE')
            ELSE NULL END AS anon_execute,
       coalesce(p.proacl::text,'(NULL: defaults apply)') AS explicit_acl
FROM pg_proc p
JOIN pg_namespace n ON n.oid=p.pronamespace
WHERE n.nspname='public' AND p.prosecdef
ORDER BY p.oid::regprocedure::text;

SELECT 'C4_RLS_OFF' AS record_type,
       c.oid::regclass::text AS object_id,
       pg_get_userbyid(c.relowner) AS owner,
       c.relkind AS relation_kind,
       c.relrowsecurity AS rls_enabled,
       c.relforcerowsecurity AS rls_forced,
       has_table_privilege('sanad',c.oid,'SELECT') AS runtime_select,
       has_table_privilege('sanad',c.oid,'INSERT') AS runtime_insert,
       has_table_privilege('sanad',c.oid,'UPDATE') AS runtime_update,
       has_table_privilege('sanad',c.oid,'DELETE') AS runtime_delete,
       has_table_privilege('sanad',c.oid,'TRUNCATE') AS runtime_truncate,
       CASE WHEN EXISTS(SELECT 1 FROM pg_roles WHERE rolname='service_role')
            THEN has_table_privilege('service_role',c.oid,'SELECT')
            ELSE NULL END AS service_select,
       EXISTS (SELECT 1 FROM aclexplode(coalesce(c.relacl,acldefault('r',c.relowner))) acl WHERE acl.grantee=0 AND acl.privilege_type='SELECT') AS public_select,
       CASE WHEN EXISTS(SELECT 1 FROM pg_roles WHERE rolname='authenticated')
            THEN has_table_privilege('authenticated',c.oid,'SELECT')
            ELSE NULL END AS authenticated_select,
       CASE WHEN EXISTS(SELECT 1 FROM pg_roles WHERE rolname='anon')
            THEN has_table_privilege('anon',c.oid,'SELECT')
            ELSE NULL END AS anon_select
FROM pg_class c
JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='public' AND c.relkind IN ('r','p') AND NOT c.relrowsecurity
ORDER BY c.oid::regclass::text;

SELECT 'C4_DEFAULT_ACL' AS record_type,
       r.rolname AS grantor_role,
       COALESCE(n.nspname,'(GLOBAL)') AS schema_scope,
       d.defaclobjtype AS object_type,
       d.defaclacl::text AS grants,
       coalesce((SELECT string_agg(grantee::regrole::text || ':' || privilege_type, ', ' ORDER BY grantee, privilege_type)
         FROM aclexplode(d.defaclacl) WHERE grantee<>0),'') AS role_grants,
       coalesce((SELECT string_agg(privilege_type, ', ' ORDER BY privilege_type)
         FROM aclexplode(d.defaclacl) WHERE grantee=0),'') AS public_grants
FROM pg_default_acl d
JOIN pg_roles r ON r.oid=d.defaclrole
LEFT JOIN pg_namespace n ON n.oid=d.defaclnamespace
ORDER BY grantor_role,schema_scope,object_type;

SELECT 'C4_INVENTORY_SCOPE_ONLY_NOT_SECURITY_PASS' AS result,
       (SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
         WHERE n.nspname='public' AND p.prosecdef) AS definer_functions,
       (SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
         WHERE n.nspname='public' AND c.relkind IN ('r','p') AND NOT c.relrowsecurity) AS rls_off_tables;
