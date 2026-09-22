-- ============================================================
-- V20260923_2 — HRM-G2: RLS policies for time & attendance, leave
-- ============================================================
-- Row-Level Security for all G2 tables following the G1 RLS pattern
-- (V20260918_3). Every table gets:
--   1. ENABLE ROW LEVEL SECURITY
--   2. FORCE ROW LEVEL SECURITY (so even the table owner is subject)
--   3. tenant_isolation policy: USING (tenant_id = current_setting('app.tenant_id')::uuid)
--      WITH CHECK (tenant_id = current_setting('app.tenant_id')::uuid)
--
-- The application role `sanad` (NOSUPERUSER NOBYPASSRLS) connects with
-- SET app.tenant_id set by the JwtAuthenticationFilter. Cross-tenant
-- access returns empty set (read) or 42501 (write).
-- ============================================================

-- hr_leave_types
ALTER TABLE hr_leave_types ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_leave_types FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON hr_leave_types
    USING (tenant_id = current_setting('app.tenant_id')::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id')::uuid);

-- hr_leave_balances
ALTER TABLE hr_leave_balances ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_leave_balances FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON hr_leave_balances
    USING (tenant_id = current_setting('app.tenant_id')::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id')::uuid);

-- hr_attendance_records
ALTER TABLE hr_attendance_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_attendance_records FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON hr_attendance_records
    USING (tenant_id = current_setting('app.tenant_id')::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id')::uuid);

-- hr_leave_requests
ALTER TABLE hr_leave_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_leave_requests FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON hr_leave_requests
    USING (tenant_id = current_setting('app.tenant_id')::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id')::uuid);

-- hr_timesheets
ALTER TABLE hr_timesheets ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_timesheets FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON hr_timesheets
    USING (tenant_id = current_setting('app.tenant_id')::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id')::uuid);
