-- ============================================================
-- V20260924_6 — HRM-G2: fail-closed RLS for empty tenant context
-- ============================================================
-- ROOT CAUSE (directive-verified):
--   G2 RLS policies created by V20260923_2, V20260924_1, V20260924_2
--   use the pattern:
--     current_setting('app.tenant_id')::uuid
--   After RESET app.tenant_id, PostgreSQL may return '' (empty string)
--   instead of NULL. The cast ''::uuid throws:
--     ERROR: invalid input syntax for type uuid: ""
--   This causes HrRlsFailClosedIntegrationTest.noTenantContext_seesZeroRows
--   to fail with 13 errors (one per G2 table).
--
-- CANONICAL FIX:
--   Replace with the repository-canonical fail-closed pattern (used by
--   V20260912_4__r0c13_subscription_billing_foundation.sql):
--     NULLIF(current_setting('app.tenant_id', true), '')::uuid
--   When the setting is absent or empty, NULLIF returns NULL.
--   The comparison tenant_id = NULL yields NULL (not true), so RLS
--   filters out all rows → READ = zero rows, WRITE = rejected.
--   No SQL casting exception.
--
-- This migration is FORWARD-ONLY. It does NOT modify the historical
-- migrations V20260923_2, V20260924_1, V20260924_2. It drops and
-- recreates only the tenant_isolation policy on each G2 table.
--
-- RLS state after this migration (unchanged):
--   - ENABLE ROW LEVEL SECURITY  (still on)
--   - FORCE ROW LEVEL SECURITY   (still on)
--   - No BYPASSRLS grants
--   - No permissive TRUE policies
--   - No ownership changes
-- ============================================================

-- Pattern applied to every G2 tenant table:
--   DROP POLICY IF EXISTS tenant_isolation ON <table>;
--   CREATE POLICY tenant_isolation ON <table> FOR ALL
--     USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
--     WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

DO $$
DECLARE
    tbl TEXT;
    g2_tables TEXT[] := ARRAY[
        'hr_leave_types',
        'hr_leave_balances',
        'hr_attendance_records',
        'hr_leave_requests',
        'hr_timesheets',
        'hr_work_schedules',
        'hr_schedule_versions',
        'hr_schedule_assignments',
        'hr_attendance_events',
        'hr_leave_policies',
        'hr_leave_ledger_entries',
        'hr_g2_idempotency_records',
        'hr_timesheet_entries'
    ];
BEGIN
    FOREACH tbl IN ARRAY g2_tables LOOP
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON %I', tbl);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I FOR ALL
             USING (tenant_id = NULLIF(current_setting(''app.tenant_id'', true), '''')::uuid)
             WITH CHECK (tenant_id = NULLIF(current_setting(''app.tenant_id'', true), '''')::uuid)',
            tbl
        );
    END LOOP;
END $$;

-- Verify RLS is still ENABLE + FORCE on all G2 tables
DO $$
DECLARE
    tbl TEXT;
    g2_tables TEXT[] := ARRAY[
        'hr_leave_types', 'hr_leave_balances', 'hr_attendance_records',
        'hr_leave_requests', 'hr_timesheets', 'hr_work_schedules',
        'hr_schedule_versions', 'hr_schedule_assignments',
        'hr_attendance_events', 'hr_leave_policies', 'hr_leave_ledger_entries',
        'hr_g2_idempotency_records', 'hr_timesheet_entries'
    ];
    r RECORD;
BEGIN
    FOREACH tbl IN ARRAY g2_tables LOOP
        EXECUTE format(
            'SELECT relrowsecurity, relforcerowsecurity FROM pg_class WHERE relname = %L AND relkind = ''r''',
            tbl
        ) INTO r;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'G2 RLS REPAIR FAILED: table % does not exist', tbl;
        END IF;
        IF NOT r.relrowsecurity THEN
            RAISE EXCEPTION 'G2 RLS REPAIR FAILED: RLS not enabled on %', tbl;
        END IF;
        IF NOT r.relforcerowsecurity THEN
            RAISE EXCEPTION 'G2 RLS REPAIR FAILED: FORCE RLS not set on %', tbl;
        END IF;
    END LOOP;
END $$;
