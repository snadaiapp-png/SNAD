-- SANAD G1 Amendment A01: READ-ONLY release catalog guard.
-- Valid for the source migration head reviewed at main f1f8157ea5ae2ce49ea5d146f09c984b497fb0d5.
-- This is not a tenant A/B functional RLS test and does not certify security or authorize changes.
-- Run only with separately approved production access; NEVER connect using application secrets in logs.
-- 100% SELECT on pg_catalog and Flyway history. No DDL/DML, SET ROLE or customer records.
WITH desired (table_name, want_rls, want_sel, want_ins, want_upd, want_del) AS (
  VALUES
    ('refresh_tokens',          true, true, true, true,  true),
    ('password_reset_tokens',   true, true, true, true,  true),
    ('login_tenant_index',      false,true, true, false, true), -- intentionally non-RLS
    ('provisioning_jobs',       true, true, true, true,  false),
    ('provisioning_job_steps',  true, true, true, true,  false),
    ('subscription_commands',   true, true, true, false, false)
), table_probe AS (
  SELECT
    d.table_name,
    c.oid AS relid,
    c.relrowsecurity AS actual_rls,
    c.relforcerowsecurity AS actual_force,
    pg_get_userbyid(c.relowner) AS actual_owner,
    (SELECT COUNT(*) FROM pg_policy p WHERE p.polrelid = c.oid) AS policy_count,
    d.want_rls, d.want_sel, d.want_ins, d.want_upd, d.want_del,
    has_table_privilege('sanad', c.oid, 'SELECT') AS actual_sel,
    has_table_privilege('sanad', c.oid, 'INSERT') AS actual_ins,
    has_table_privilege('sanad', c.oid, 'UPDATE') AS actual_upd,
    has_table_privilege('sanad', c.oid, 'DELETE') AS actual_del
  FROM desired d
  LEFT JOIN pg_class c ON c.oid = to_regclass('public.' || d.table_name)
), checks AS (
  SELECT 'TABLE:' || table_name AS check_name,
    (
      relid IS NOT NULL
      AND actual_rls IS NOT DISTINCT FROM want_rls
      AND actual_force IS NOT DISTINCT FROM want_rls
      AND ((want_rls AND policy_count > 0) OR (NOT want_rls AND policy_count = 0))
      AND actual_owner = 'sanad_migrator'
      AND actual_sel IS NOT DISTINCT FROM want_sel
      AND actual_ins IS NOT DISTINCT FROM want_ins
      AND actual_upd IS NOT DISTINCT FROM want_upd
      AND actual_del IS NOT DISTINCT FROM want_del
    ) IS TRUE AS pass,
    FORMAT('present=%s; rls=%s; force=%s; policies=%s; owner=%s; S/I/U/D=%s/%s/%s/%s',
      relid IS NOT NULL, actual_rls, actual_force, policy_count, actual_owner,
      actual_sel, actual_ins, actual_upd, actual_del) AS metadata
  FROM table_probe
  UNION ALL
  SELECT 'ROLE:sanad_runtime',
    (SELECT COUNT(*)=1 FROM pg_roles WHERE rolname='sanad'
       AND rolcanlogin AND NOT rolsuper AND NOT rolbypassrls AND NOT rolcreatedb AND NOT rolcreaterole),
    'runtime LOGIN, no superuser/bypassrls/createdb/createrole'
  UNION ALL
  SELECT 'ROLE:sanad_migrator',
    (SELECT COUNT(*)=1 FROM pg_roles WHERE rolname='sanad_migrator'
       AND rolcanlogin AND NOT rolsuper AND NOT rolbypassrls AND NOT rolcreatedb AND NOT rolcreaterole),
    'dedicated migrator LOGIN; no superuser/bypassrls/createdb/createrole'
  UNION ALL
  SELECT 'ROLE:membership_separation',
    (SELECT COUNT(*)=1 FROM pg_roles migrator
      JOIN pg_roles runtime ON runtime.rolname='sanad'
      WHERE migrator.rolname='sanad_migrator'
        AND NOT pg_has_role(runtime.oid, migrator.oid, 'MEMBER')),
    'sanad cannot inherit dedicated migrator membership'
  UNION ALL
  SELECT 'ACL:schema_and_flyway',
    NOT has_schema_privilege('sanad', 'public', 'CREATE')
      AND NOT has_table_privilege('sanad', 'public.flyway_schema_history', 'SELECT'),
    'runtime denied public.CREATE and flyway history SELECT'
  UNION ALL
  SELECT 'FLYWAY:installed_head',
    (SELECT version FROM public.flyway_schema_history
      WHERE success ORDER BY installed_rank DESC LIMIT 1)='20261107.1',
    'expected full-chain head 20261107.1 on this reviewed baseline; confirm source SHA before future use'
)
SELECT check_name,
       CASE WHEN pass THEN 'PASS' ELSE 'FAIL' END AS result,
       metadata
FROM checks
ORDER BY check_name;
