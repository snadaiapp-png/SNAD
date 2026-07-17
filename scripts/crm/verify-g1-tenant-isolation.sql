\set ON_ERROR_STOP on

-- CRM-G1 database isolation verification.
-- Run only after Flyway has applied through 20260717.6:
--   psql "$SPRING_DATASOURCE_URL" -v ON_ERROR_STOP=1 \
--     -f scripts/crm/verify-g1-tenant-isolation.sql
--
-- This script is read-only. It validates the schema contract that supports
-- tenant isolation; runtime cross-tenant API/UI denial is covered by
-- apps/web/e2e/crm-tenant-isolation.spec.ts.

BEGIN TRANSACTION READ ONLY;

DO $$
DECLARE
    expected_tables TEXT[] := ARRAY[
        'crm_tasks',
        'crm_assignments',
        'crm_transfers',
        'crm_notes',
        'crm_audit_logs',
        'crm_reports',
        'crm_phone_numbers',
        'crm_contact_lookup_index'
    ];
    table_name TEXT;
    missing_tables TEXT[] := ARRAY[]::TEXT[];
BEGIN
    FOREACH table_name IN ARRAY expected_tables LOOP
        IF to_regclass('public.' || table_name) IS NULL THEN
            missing_tables := array_append(missing_tables, table_name);
        END IF;
    END LOOP;

    IF cardinality(missing_tables) > 0 THEN
        RAISE EXCEPTION 'CRM-G1 missing tables: %', array_to_string(missing_tables, ', ');
    END IF;
END $$;

DO $$
DECLARE
    tenant_column_count BIGINT;
    tenant_fk_count BIGINT;
    explicit_index_count BIGINT;
    non_tenant_first_index_count BIGINT;
BEGIN
    SELECT COUNT(*)
      INTO tenant_column_count
      FROM information_schema.columns
     WHERE table_schema = 'public'
       AND column_name = 'tenant_id'
       AND table_name IN (
           'crm_tasks', 'crm_assignments', 'crm_transfers', 'crm_notes',
           'crm_audit_logs', 'crm_reports', 'crm_phone_numbers', 'crm_contact_lookup_index'
       );

    IF tenant_column_count <> 8 THEN
        RAISE EXCEPTION 'CRM-G1 tenant_id coverage failed: expected 8, found %', tenant_column_count;
    END IF;

    SELECT COUNT(*)
      INTO tenant_fk_count
      FROM pg_constraint constraint_row
      JOIN pg_class table_row ON table_row.oid = constraint_row.conrelid
     WHERE constraint_row.contype = 'f'
       AND constraint_row.confrelid = 'tenants'::regclass
       AND table_row.relname IN (
           'crm_tasks', 'crm_assignments', 'crm_transfers', 'crm_notes',
           'crm_audit_logs', 'crm_reports', 'crm_phone_numbers', 'crm_contact_lookup_index'
       );

    IF tenant_fk_count <> 8 THEN
        RAISE EXCEPTION 'CRM-G1 tenant FK coverage failed: expected 8, found %', tenant_fk_count;
    END IF;

    SELECT COUNT(*)
      INTO explicit_index_count
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND tablename IN (
           'crm_tasks', 'crm_assignments', 'crm_transfers', 'crm_notes',
           'crm_audit_logs', 'crm_reports', 'crm_phone_numbers', 'crm_contact_lookup_index'
       )
       AND indexname LIKE 'idx_crm_%';

    IF explicit_index_count <> 26 THEN
        RAISE EXCEPTION 'CRM-G1 explicit index count failed: expected 26, found %', explicit_index_count;
    END IF;

    SELECT COUNT(*)
      INTO non_tenant_first_index_count
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND tablename IN (
           'crm_tasks', 'crm_assignments', 'crm_transfers', 'crm_notes',
           'crm_audit_logs', 'crm_reports', 'crm_phone_numbers', 'crm_contact_lookup_index'
       )
       AND indexname LIKE 'idx_crm_%'
       AND indexdef NOT LIKE '%(tenant_id,%';

    IF non_tenant_first_index_count <> 0 THEN
        RAISE EXCEPTION 'CRM-G1 index isolation failed: % indexes do not start with tenant_id',
            non_tenant_first_index_count;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conname = 'fk_crm_phone_numbers_contact_same_tenant'
           AND contype = 'f'
    ) THEN
        RAISE EXCEPTION 'Missing same-tenant FK for crm_phone_numbers -> crm_contacts';
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conname = 'fk_crm_contact_lookup_contact_same_tenant'
           AND contype = 'f'
    ) THEN
        RAISE EXCEPTION 'Missing same-tenant FK for crm_contact_lookup_index -> crm_contacts';
    END IF;
END $$;

SELECT
    'PASS' AS result,
    'CRM-G1 schema isolation contract verified' AS verification,
    8 AS extension_tables,
    26 AS explicit_tenant_scoped_indexes,
    current_database() AS database_name,
    CURRENT_TIMESTAMP AS verified_at;

ROLLBACK;
