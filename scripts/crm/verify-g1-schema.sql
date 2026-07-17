\set ON_ERROR_STOP on

DO $$
DECLARE
    missing_tables TEXT;
    explicit_index_count BIGINT;
    tenant_fk_table_count BIGINT;
    migration_count BIGINT;
BEGIN
    SELECT string_agg(expected.table_name, ', ' ORDER BY expected.table_name)
    INTO missing_tables
    FROM (
        VALUES
            ('crm_tasks'),
            ('crm_assignments'),
            ('crm_transfers'),
            ('crm_notes'),
            ('crm_audit_logs'),
            ('crm_reports'),
            ('crm_phone_numbers'),
            ('crm_contact_lookup_index')
    ) AS expected(table_name)
    LEFT JOIN information_schema.tables actual
      ON actual.table_schema = 'public'
     AND actual.table_name = expected.table_name
    WHERE actual.table_name IS NULL;

    IF missing_tables IS NOT NULL THEN
        RAISE EXCEPTION 'CRM-G1 missing tables: %', missing_tables;
    END IF;

    SELECT COUNT(*)
    INTO explicit_index_count
    FROM pg_indexes
    WHERE schemaname = 'public'
      AND tablename IN (
        'crm_tasks', 'crm_assignments', 'crm_transfers', 'crm_notes',
        'crm_audit_logs', 'crm_reports', 'crm_phone_numbers',
        'crm_contact_lookup_index'
      )
      AND indexname LIKE 'idx_crm_%';

    IF explicit_index_count <> 26 THEN
        RAISE EXCEPTION 'CRM-G1 expected 26 explicit indexes, found %', explicit_index_count;
    END IF;

    SELECT COUNT(DISTINCT tc.table_name)
    INTO tenant_fk_table_count
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu
      ON kcu.constraint_schema = tc.constraint_schema
     AND kcu.constraint_name = tc.constraint_name
    JOIN information_schema.constraint_column_usage ccu
      ON ccu.constraint_schema = tc.constraint_schema
     AND ccu.constraint_name = tc.constraint_name
    WHERE tc.constraint_schema = 'public'
      AND tc.constraint_type = 'FOREIGN KEY'
      AND tc.table_name IN (
        'crm_tasks', 'crm_assignments', 'crm_transfers', 'crm_notes',
        'crm_audit_logs', 'crm_reports', 'crm_phone_numbers',
        'crm_contact_lookup_index'
      )
      AND kcu.column_name = 'tenant_id'
      AND ccu.table_name = 'tenants'
      AND ccu.column_name = 'id';

    IF tenant_fk_table_count <> 8 THEN
        RAISE EXCEPTION 'CRM-G1 expected tenant FK coverage on 8 tables, found %', tenant_fk_table_count;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_schema = 'public'
          AND constraint_name = 'fk_crm_contact_lookup_contact_same_tenant'
          AND constraint_type = 'FOREIGN KEY'
    ) THEN
        RAISE EXCEPTION 'Missing contact lookup same-tenant contact FK';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_schema = 'public'
          AND constraint_name = 'fk_crm_contact_lookup_account_same_tenant'
          AND constraint_type = 'FOREIGN KEY'
    ) THEN
        RAISE EXCEPTION 'Missing contact lookup same-tenant account FK';
    END IF;

    SELECT COUNT(*)
    INTO migration_count
    FROM flyway_schema_history
    WHERE version = '20260717.6'
      AND type = 'SQL'
      AND description = 'complete crm g1 extension tables'
      AND success = TRUE;

    IF migration_count <> 1 THEN
        RAISE EXCEPTION 'Flyway 20260717.6 must exist exactly once and be successful; found %', migration_count;
    END IF;

    RAISE NOTICE 'CRM-G1 schema verification PASS: 8 tables, 26 explicit indexes, tenant FKs, Flyway 20260717.6';
END
$$;
