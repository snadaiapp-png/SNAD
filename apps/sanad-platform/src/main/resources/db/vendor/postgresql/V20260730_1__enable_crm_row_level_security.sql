-- ============================================================
-- CRM-018: Enable PostgreSQL Row-Level Security on all CRM tables
-- ============================================================
-- Defense-in-depth tenant isolation layer.
--
-- Policy: permissive-when-unset, strict-when-set.
--   - When app.tenant_id IS NOT set (NULL): all rows visible (backward compatible).
--   - When app.tenant_id IS set: only matching tenant rows visible.
--
-- Table owner (Flyway/migration role) bypasses RLS by default
-- (FORCE ROW LEVEL SECURITY is intentionally NOT used).
--
-- Applies to all crm_* tables that have a tenant_id column.
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
        -- Enable RLS (idempotent)
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tbl.table_name);

        -- Drop existing policy if present (idempotent)
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON %I', tbl.table_name);

        -- Create permissive-when-unset policy
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I FOR ALL
             USING (
                 current_setting(''app.tenant_id'', true) IS NULL
                 OR tenant_id::text = current_setting(''app.tenant_id'', true)
             )
             WITH CHECK (
                 current_setting(''app.tenant_id'', true) IS NULL
                 OR tenant_id::text = current_setting(''app.tenant_id'', true)
             )',
            tbl.table_name
        );

        RAISE NOTICE 'RLS enabled on %', tbl.table_name;
    END LOOP;
END
$$;
