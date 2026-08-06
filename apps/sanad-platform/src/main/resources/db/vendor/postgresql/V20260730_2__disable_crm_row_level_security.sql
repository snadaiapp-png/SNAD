-- ============================================================
-- CRM-018: Disable PostgreSQL Row-Level Security on all CRM tables
-- ============================================================
-- Rollback migration for V20260730_1.
-- Removes the tenant_isolation policy and disables RLS on every
-- crm_* table that has a tenant_id column.
-- Idempotent: safe to re-run.
-- ============================================================

DO $$
DECLARE
    tbl record;
BEGIN
    FOR tbl IN
        SELECT c.table_name
        FROM information_schema.columns c
        JOIN information_schema.tables t
            ON t.table_name = c.table_name
            AND t.table_schema = c.table_schema
        WHERE c.table_schema = 'public'
          AND c.column_name = 'tenant_id'
          AND c.table_name LIKE 'crm_%'
          AND t.table_type = 'BASE TABLE'
        ORDER BY c.table_name
    LOOP
        -- Drop policy if present (idempotent)
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON %I', tbl.table_name);

        -- Disable RLS (idempotent)
        EXECUTE format('ALTER TABLE %I DISABLE ROW LEVEL SECURITY', tbl.table_name);

        RAISE NOTICE 'RLS disabled on %', tbl.table_name;
    END LOOP;
END
$$;
