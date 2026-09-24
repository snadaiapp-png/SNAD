-- ============================================================
-- CRM-018: Disable PostgreSQL Row-Level Security — MANUAL ROLLBACK SCRIPT
-- ============================================================
-- STATUS: NOT ON THE FLYWAY FORWARD MIGRATION PATH.
--
-- This script was originally Flyway migration V20260730_2. It was removed
-- from the forward path (db/vendor/postgresql/) under RECOVERY-CRM-022 R1
-- because, when present, `flyway.migrate()` runs V20260730_1 (ENABLE RLS)
-- and then immediately V20260730_2 (DISABLE RLS) in the same pass, leaving
-- tenant isolation silently OFF in every full-migrate environment
-- (including production). That defeated the CRM-018 defense-in-depth goal
-- and broke tenant-isolation tests (CrmRlsTenantIsolationPostgresTest).
--
-- It is retained here as a MANUAL, operator-applied rollback artifact.
-- To use it during a deliberate, supervised RLS rollback, execute this
-- file directly against the target PostgreSQL database (e.g. via psql),
-- NOT through Flyway. RLS is expected to remain ENABLED in all normal
-- production operation.
--
-- Originally: V20260730_2__disable_crm_row_level_security.sql
-- Rollback target of: V20260730_1__enable_crm_row_level_security.sql
-- ============================================================
-- Original body follows.
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
