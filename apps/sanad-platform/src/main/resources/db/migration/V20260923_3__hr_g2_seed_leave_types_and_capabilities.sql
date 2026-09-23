-- ============================================================
-- V20260923_3 — HRM-G2: seed leave type identity + RBAC capabilities
-- ============================================================
-- Seeds leave TYPE IDENTITY only (code, names, is_paid, requires_attachment).
-- NO statutory entitlement values are seeded — leave type ≠ leave policy.
-- Entitlement days must come from a separate Leave Policy / Entitlement Rule
-- that is country-specific (Saudi Country Pack) or tenant-configured.
--
-- Per G2 corrective directive §8: "G2 Core يجب أن يكون country-neutral"
--
-- MIGRATION-SAFE RLS PATTERN (follows V20260918_4 G1 seed convention):
-- FORCE RLS is already live on hr_leave_types (V20260923_2), so the seed
-- sets app.tenant_id per tenant via set_config(..., is_local => true) —
-- the same scoped path the application uses. No BYPASSRLS, no superuser:
-- each INSERT passes the policy's WITH CHECK by carrying the matching
-- tenant context.
--
-- access_capabilities is a platform-level table WITHOUT RLS — plain
-- INSERT with ON CONFLICT (code) DO NOTHING is safe.
-- ============================================================

-- ------------------------------------------------------------
-- 1. Seed leave type IDENTITY (no statutory days) per tenant
-- ------------------------------------------------------------
-- default_days_per_year is explicitly NULL — entitlement values are
-- configured separately via Leave Policy, not hardcoded here.
DO $$
DECLARE
    t RECORD;
BEGIN
    FOR t IN SELECT id FROM tenants WHERE status = 'ACTIVE' LOOP
        PERFORM set_config('app.tenant_id', t.id::text, true);

        INSERT INTO hr_leave_types (tenant_id, code, name_ar, name_en, is_paid, requires_attachment, default_days_per_year, state)
        VALUES (t.id, 'ANNUAL', 'إجازة سنوية', 'Annual Leave', true, false, NULL, 'ACTIVE')
        ON CONFLICT (tenant_id, code) DO NOTHING;

        INSERT INTO hr_leave_types (tenant_id, code, name_ar, name_en, is_paid, requires_attachment, default_days_per_year, state)
        VALUES (t.id, 'SICK', 'إجازة مرضية', 'Sick Leave', true, true, NULL, 'ACTIVE')
        ON CONFLICT (tenant_id, code) DO NOTHING;

        INSERT INTO hr_leave_types (tenant_id, code, name_ar, name_en, is_paid, requires_attachment, default_days_per_year, state)
        VALUES (t.id, 'CASUAL', 'إجازة عادية', 'Casual Leave', true, false, NULL, 'ACTIVE')
        ON CONFLICT (tenant_id, code) DO NOTHING;

        INSERT INTO hr_leave_types (tenant_id, code, name_ar, name_en, is_paid, requires_attachment, default_days_per_year, state)
        VALUES (t.id, 'MATERNITY', 'إجازة أمومة', 'Maternity Leave', true, true, NULL, 'ACTIVE')
        ON CONFLICT (tenant_id, code) DO NOTHING;

        INSERT INTO hr_leave_types (tenant_id, code, name_ar, name_en, is_paid, requires_attachment, default_days_per_year, state)
        VALUES (t.id, 'PATERNITY', 'إجازة أبوة', 'Paternity Leave', true, false, NULL, 'ACTIVE')
        ON CONFLICT (tenant_id, code) DO NOTHING;

        INSERT INTO hr_leave_types (tenant_id, code, name_ar, name_en, is_paid, requires_attachment, default_days_per_year, state)
        VALUES (t.id, 'UNPAID', 'إجازة بدون راتب', 'Unpaid Leave', false, false, NULL, 'ACTIVE')
        ON CONFLICT (tenant_id, code) DO NOTHING;
    END LOOP;
    PERFORM set_config('app.tenant_id', '', true);
END $$;

-- ------------------------------------------------------------
-- 2. Seed RBAC capabilities for G2 time/attendance/leave
-- ------------------------------------------------------------
-- access_capabilities is a platform-level table WITHOUT RLS.
-- Uses the same column names as V7__create_access_capabilities.sql.
-- Capabilities are scope-separated per §15: SELF / TEAM / HR.
INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'HRM.ATTENDANCE.SELF_RECORD', 'Record Own Attendance', 'Clock in/out for own attendance', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'HRM.ATTENDANCE.SELF_RECORD');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'HRM.ATTENDANCE.SELF_VIEW', 'View Own Attendance', 'View own attendance records', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'HRM.ATTENDANCE.SELF_VIEW');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'HRM.ATTENDANCE.TEAM_VIEW', 'View Team Attendance', 'View team attendance records', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'HRM.ATTENDANCE.TEAM_VIEW');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'HRM.ATTENDANCE.ADMIN', 'Administer Attendance', 'Administer attendance records', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'HRM.ATTENDANCE.ADMIN');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'HRM.ATTENDANCE.CORRECT', 'Correct Attendance', 'Manually correct attendance records', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'HRM.ATTENDANCE.CORRECT');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'HRM.LEAVE.SELF_REQUEST', 'Submit Own Leave', 'Submit own leave requests', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'HRM.LEAVE.SELF_REQUEST');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'HRM.LEAVE.SELF_VIEW', 'View Own Leave', 'View own leave balances and requests', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'HRM.LEAVE.SELF_VIEW');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'HRM.LEAVE.TEAM_APPROVE', 'Approve Team Leave', 'Approve/reject team leave requests (manager)', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'HRM.LEAVE.TEAM_APPROVE');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'HRM.LEAVE.HR_APPROVE', 'HR Approve Leave', 'Final HR approval of leave requests', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'HRM.LEAVE.HR_APPROVE');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'HRM.LEAVE.POLICY_ADMIN', 'Manage Leave Policies', 'Manage leave types and policies', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'HRM.LEAVE.POLICY_ADMIN');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'HRM.TIMESHEET.SELF_VIEW', 'View Own Timesheets', 'View own timesheets', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'HRM.TIMESHEET.SELF_VIEW');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'HRM.TIMESHEET.SELF_SUBMIT', 'Submit Own Timesheets', 'Submit own timesheets for approval', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'HRM.TIMESHEET.SELF_SUBMIT');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'HRM.TIMESHEET.TEAM_APPROVE', 'Approve Team Timesheets', 'Approve/reject team timesheets (manager)', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'HRM.TIMESHEET.TEAM_APPROVE');
