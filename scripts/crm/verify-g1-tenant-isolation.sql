\set ON_ERROR_STOP on

-- CRM-G1 database isolation verification.
-- Run only after Flyway has applied through 20260717.6:
--   PGPASSWORD="$DATABASE_PASSWORD" psql \
--     -h "$DATABASE_HOST" -U "$DATABASE_USERNAME" -d "$DATABASE_NAME" \
--     -v ON_ERROR_STOP=1 -f scripts/crm/verify-g1-tenant-isolation.sql
--
-- The script is strictly read-only: it uses catalog SELECTs and psql fail-closed
-- conditionals only. Runtime cross-tenant API/UI denial is covered by
-- apps/web/e2e/crm-tenant-isolation.spec.ts.

BEGIN TRANSACTION READ ONLY;

WITH expected(table_name) AS (
    VALUES
        ('crm_tasks'),
        ('crm_assignments'),
        ('crm_transfers'),
        ('crm_notes'),
        ('crm_audit_logs'),
        ('crm_reports'),
        ('crm_phone_numbers'),
        ('crm_contact_lookup_index')
), present AS (
    SELECT table_name
      FROM information_schema.tables
     WHERE table_schema = 'public'
)
SELECT
    (COUNT(present.table_name) = 8) AS g1_tables_ok,
    COUNT(present.table_name) AS g1_table_count,
    COALESCE(
        string_agg(expected.table_name, ', ' ORDER BY expected.table_name)
            FILTER (WHERE present.table_name IS NULL),
        'none'
    ) AS g1_missing_tables
  FROM expected
  LEFT JOIN present USING (table_name)
\gset

\if :g1_tables_ok
  \echo 'PASS: all 8 CRM-G1 extension tables exist'
\else
  \echo 'FAIL: expected 8 CRM-G1 tables, found' :g1_table_count '; missing:' :g1_missing_tables
  \quit 1
\endif

SELECT
    (COUNT(*) = 8) AS g1_tenant_columns_ok,
    COUNT(*) AS g1_tenant_column_count
  FROM information_schema.columns
 WHERE table_schema = 'public'
   AND column_name = 'tenant_id'
   AND table_name IN (
       'crm_tasks', 'crm_assignments', 'crm_transfers', 'crm_notes',
       'crm_audit_logs', 'crm_reports', 'crm_phone_numbers', 'crm_contact_lookup_index'
   )
\gset

\if :g1_tenant_columns_ok
  \echo 'PASS: tenant_id exists on all 8 CRM-G1 extension tables'
\else
  \echo 'FAIL: expected tenant_id on 8 tables, found' :g1_tenant_column_count
  \quit 1
\endif

SELECT
    (COUNT(*) = 8) AS g1_tenant_fks_ok,
    COUNT(*) AS g1_tenant_fk_count
  FROM pg_constraint constraint_row
  JOIN pg_class table_row ON table_row.oid = constraint_row.conrelid
 WHERE constraint_row.contype = 'f'
   AND constraint_row.confrelid = 'tenants'::regclass
   AND table_row.relname IN (
       'crm_tasks', 'crm_assignments', 'crm_transfers', 'crm_notes',
       'crm_audit_logs', 'crm_reports', 'crm_phone_numbers', 'crm_contact_lookup_index'
   )
\gset

\if :g1_tenant_fks_ok
  \echo 'PASS: all 8 CRM-G1 extension tables reference tenants(id)'
\else
  \echo 'FAIL: expected 8 tenant foreign keys, found' :g1_tenant_fk_count
  \quit 1
\endif

SELECT
    (COUNT(*) = 26) AS g1_indexes_ok,
    COUNT(*) AS g1_index_count
  FROM pg_indexes
 WHERE schemaname = 'public'
   AND tablename IN (
       'crm_tasks', 'crm_assignments', 'crm_transfers', 'crm_notes',
       'crm_audit_logs', 'crm_reports', 'crm_phone_numbers', 'crm_contact_lookup_index'
   )
   AND indexname LIKE 'idx_crm_%'
\gset

\if :g1_indexes_ok
  \echo 'PASS: exactly 26 explicit CRM-G1 indexes exist'
\else
  \echo 'FAIL: expected exactly 26 explicit CRM-G1 indexes, found' :g1_index_count
  \quit 1
\endif

SELECT
    (COUNT(*) = 0) AS g1_tenant_first_indexes_ok,
    COUNT(*) AS g1_non_tenant_first_index_count,
    COALESCE(string_agg(indexname, ', ' ORDER BY indexname), 'none') AS g1_non_tenant_first_indexes
  FROM pg_indexes
 WHERE schemaname = 'public'
   AND tablename IN (
       'crm_tasks', 'crm_assignments', 'crm_transfers', 'crm_notes',
       'crm_audit_logs', 'crm_reports', 'crm_phone_numbers', 'crm_contact_lookup_index'
   )
   AND indexname LIKE 'idx_crm_%'
   AND indexdef NOT LIKE '%(tenant_id,%'
\gset

\if :g1_tenant_first_indexes_ok
  \echo 'PASS: tenant_id is the leading key on all 26 explicit indexes'
\else
  \echo 'FAIL:' :g1_non_tenant_first_index_count 'indexes do not lead with tenant_id:' :g1_non_tenant_first_indexes
  \quit 1
\endif

SELECT
    (COUNT(*) = 2) AS g1_same_tenant_fks_ok,
    COUNT(*) AS g1_same_tenant_fk_count
  FROM pg_constraint
 WHERE contype = 'f'
   AND conname IN (
       'fk_crm_phone_numbers_contact_same_tenant',
       'fk_crm_contact_lookup_contact_same_tenant'
   )
\gset

\if :g1_same_tenant_fks_ok
  \echo 'PASS: concrete contact relationships have same-tenant composite foreign keys'
\else
  \echo 'FAIL: expected 2 same-tenant contact foreign keys, found' :g1_same_tenant_fk_count
  \quit 1
\endif

SELECT
    'PASS' AS result,
    'CRM-G1 schema isolation contract verified' AS verification,
    8 AS extension_tables,
    26 AS explicit_tenant_scoped_indexes,
    current_database() AS database_name,
    CURRENT_TIMESTAMP AS verified_at;

ROLLBACK;
