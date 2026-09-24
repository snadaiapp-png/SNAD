-- ============================================================
-- V20260924_6 — HRM-G2: fail-closed RLS for empty tenant context
-- ============================================================
-- ROOT CAUSE (directive-verified):
--   G2 RLS policies (V20260923_2, V20260924_1, V20260924_2) use:
--     current_setting('app.tenant_id')::uuid
--   After RESET app.tenant_id, PostgreSQL may return '' (empty string).
--   The cast ''::uuid throws: invalid input syntax for type uuid: ""
--   This causes HrRlsFailClosedIntegrationTest.noTenantContext_seesZeroRows
--   to fail with 13 errors (one per G2 table).
--
-- CANONICAL FIX:
--   Replace with the repository-canonical fail-closed pattern (used by
--   V20260912_4__r0c13_subscription_billing_foundation.sql):
--     NULLIF(current_setting('app.tenant_id', true), '')::uuid
--   When the setting is absent or empty, NULLIF returns NULL.
--   The comparison tenant_id = NULL yields NULL (not true), so RLS
--   filters all rows → READ = zero rows, WRITE = rejected. No SQL exception.
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

-- 1. hr_leave_types
DROP POLICY IF EXISTS tenant_isolation ON hr_leave_types;
CREATE POLICY tenant_isolation ON hr_leave_types FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- 2. hr_leave_balances
DROP POLICY IF EXISTS tenant_isolation ON hr_leave_balances;
CREATE POLICY tenant_isolation ON hr_leave_balances FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- 3. hr_attendance_records
DROP POLICY IF EXISTS tenant_isolation ON hr_attendance_records;
CREATE POLICY tenant_isolation ON hr_attendance_records FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- 4. hr_leave_requests
DROP POLICY IF EXISTS tenant_isolation ON hr_leave_requests;
CREATE POLICY tenant_isolation ON hr_leave_requests FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- 5. hr_timesheets
DROP POLICY IF EXISTS tenant_isolation ON hr_timesheets;
CREATE POLICY tenant_isolation ON hr_timesheets FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- 6. hr_work_schedules
DROP POLICY IF EXISTS tenant_isolation ON hr_work_schedules;
CREATE POLICY tenant_isolation ON hr_work_schedules FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- 7. hr_schedule_versions
DROP POLICY IF EXISTS tenant_isolation ON hr_schedule_versions;
CREATE POLICY tenant_isolation ON hr_schedule_versions FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- 8. hr_schedule_assignments
DROP POLICY IF EXISTS tenant_isolation ON hr_schedule_assignments;
CREATE POLICY tenant_isolation ON hr_schedule_assignments FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- 9. hr_attendance_events
DROP POLICY IF EXISTS tenant_isolation ON hr_attendance_events;
CREATE POLICY tenant_isolation ON hr_attendance_events FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- 10. hr_leave_policies
DROP POLICY IF EXISTS tenant_isolation ON hr_leave_policies;
CREATE POLICY tenant_isolation ON hr_leave_policies FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- 11. hr_leave_ledger_entries
DROP POLICY IF EXISTS tenant_isolation ON hr_leave_ledger_entries;
CREATE POLICY tenant_isolation ON hr_leave_ledger_entries FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- 12. hr_g2_idempotency_records
DROP POLICY IF EXISTS tenant_isolation ON hr_g2_idempotency_records;
CREATE POLICY tenant_isolation ON hr_g2_idempotency_records FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- 13. hr_timesheet_entries
DROP POLICY IF EXISTS tenant_isolation ON hr_timesheet_entries;
CREATE POLICY tenant_isolation ON hr_timesheet_entries FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
