\set ON_ERROR_STOP on
BEGIN TRANSACTION READ ONLY;

WITH
expected(table_name) AS (
    VALUES
        ('crm_tasks'),
        ('crm_assignments'),
        ('crm_transfers'),
        ('crm_notes'),
        ('crm_audit_logs'),
        ('crm_reports'),
        ('crm_phone_numbers'),
        ('crm_contact_lookup_index')
),
tables_state AS (
    SELECT
        COUNT(t.table_name)::integer AS present_count,
        COALESCE(
            jsonb_agg(
                jsonb_build_object(
                    'name', e.table_name,
                    'present', t.table_name IS NOT NULL
                )
                ORDER BY e.table_name
            ),
            '[]'::jsonb
        ) AS items
    FROM expected e
    LEFT JOIN information_schema.tables t
      ON t.table_schema = 'public'
     AND t.table_name = e.table_name
),
tenant_columns AS (
    SELECT COUNT(*)::integer AS count
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND column_name = 'tenant_id'
      AND table_name IN (SELECT table_name FROM expected)
),
tenant_fks AS (
    SELECT COUNT(*)::integer AS count
    FROM pg_constraint c
    JOIN pg_class r ON r.oid = c.conrelid
    WHERE c.contype = 'f'
      AND c.confrelid = 'tenants'::regclass
      AND r.relname IN (SELECT table_name FROM expected)
),
explicit_indexes AS (
    SELECT
        COUNT(*)::integer AS count,
        COUNT(*) FILTER (WHERE indexdef NOT LIKE '%(tenant_id,%')::integer
            AS non_tenant_first_count
    FROM pg_indexes
    WHERE schemaname = 'public'
      AND tablename IN (SELECT table_name FROM expected)
      AND indexname LIKE 'idx_crm_%'
),
same_tenant_fks AS (
    SELECT COUNT(*)::integer AS count
    FROM pg_constraint
    WHERE contype = 'f'
      AND conname IN (
          'fk_crm_phone_numbers_contact_same_tenant',
          'fk_crm_contact_lookup_contact_same_tenant'
      )
),
task_note_capabilities AS (
    SELECT COUNT(*)::integer AS count
    FROM access_capabilities
    WHERE code IN (
        'CRM.TASK.READ',
        'CRM.TASK.WRITE',
        'CRM.NOTE.READ',
        'CRM.NOTE.WRITE'
    )
      AND status = 'ACTIVE'
),
repair_history AS (
    SELECT COALESCE(
        jsonb_agg(
            jsonb_build_object(
                'installedRank', installed_rank,
                'version', version,
                'description', description,
                'type', type,
                'script', script,
                'checksum', checksum,
                'installedOn', installed_on,
                'success', success
            )
            ORDER BY installed_rank
        ),
        '[]'::jsonb
    ) AS items
    FROM flyway_schema_history
    WHERE version IN ('20260717.6', '20260717.100', '20260717.101', '20260718.1')
)
SELECT jsonb_pretty(
    jsonb_build_object(
        'status', CASE
            WHEN (SELECT present_count FROM tables_state) = 8
             AND (SELECT count FROM tenant_columns) = 8
             AND (SELECT count FROM tenant_fks) = 8
             AND (SELECT count FROM explicit_indexes) = 26
             AND (SELECT non_tenant_first_count FROM explicit_indexes) = 0
             AND (SELECT count FROM same_tenant_fks) = 2
             AND (SELECT count FROM task_note_capabilities) = 4
            THEN 'PASS'
            ELSE 'FAIL'
        END,
        'inspectionMode', 'READ_ONLY',
        'database', jsonb_build_object(
            'name', current_database(),
            'serverVersion', current_setting('server_version'),
            'transactionReadOnly', current_setting('transaction_read_only'),
            'verifiedAt', CURRENT_TIMESTAMP
        ),
        'tables', jsonb_build_object(
            'present', (SELECT present_count FROM tables_state),
            'expected', 8,
            'items', (SELECT items FROM tables_state)
        ),
        'tenantColumns', (SELECT count FROM tenant_columns),
        'tenantForeignKeys', (SELECT count FROM tenant_fks),
        'explicitIndexes', (SELECT count FROM explicit_indexes),
        'nonTenantFirstIndexes', (SELECT non_tenant_first_count FROM explicit_indexes),
        'sameTenantContactForeignKeys', (SELECT count FROM same_tenant_fks),
        'activeTaskNoteCapabilities', (SELECT count FROM task_note_capabilities),
        'flywayHistory', (SELECT items FROM repair_history)
    )
);

ROLLBACK;
