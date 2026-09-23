-- ============================================================
-- V20260923_3 — HRM-G2: seed leave type identity + RBAC capabilities
-- ============================================================
-- Seeds leave TYPE IDENTITY only (code, names, is_paid, requires_attachment).
-- NO statutory entitlement values are seeded — leave type ≠ leave policy.
-- Entitlement days must come from a separate Leave Policy / Entitlement Rule
-- that is country-specific (Saudi Country Pack) or tenant-configured.
--
-- Per G2 corrective directive §8: "G2 Core يجب أن يكون country-neutral"
-- and "Saudi statutory configuration يجب أن يأتي من Country Pack مستقل
-- ولا يُدعى قانونيًا معتمدًا بدون مراجعة مستقلة."
-- ============================================================

-- ------------------------------------------------------------
-- 1. Seed leave type IDENTITY (no statutory days) for all tenants
-- ------------------------------------------------------------
-- default_days_per_year is explicitly NULL — entitlement values are
-- configured separately via Leave Policy, not hardcoded here.
INSERT INTO hr_leave_types (tenant_id, code, name_ar, name_en, is_paid, requires_attachment, default_days_per_year, state)
SELECT t.id, 'ANNUAL', 'إجازة سنوية', 'Annual Leave', true, false, NULL, 'ACTIVE'
FROM tenants t
WHERE NOT EXISTS (
    SELECT 1 FROM hr_leave_types lt WHERE lt.tenant_id = t.id AND lt.code = 'ANNUAL'
);

INSERT INTO hr_leave_types (tenant_id, code, name_ar, name_en, is_paid, requires_attachment, default_days_per_year, state)
SELECT t.id, 'SICK', 'إجازة مرضية', 'Sick Leave', true, true, NULL, 'ACTIVE'
FROM tenants t
WHERE NOT EXISTS (
    SELECT 1 FROM hr_leave_types lt WHERE lt.tenant_id = t.id AND lt.code = 'SICK'
);

INSERT INTO hr_leave_types (tenant_id, code, name_ar, name_en, is_paid, requires_attachment, default_days_per_year, state)
SELECT t.id, 'CASUAL', 'إجازة عادية', 'Casual Leave', true, false, NULL, 'ACTIVE'
FROM tenants t
WHERE NOT EXISTS (
    SELECT 1 FROM hr_leave_types lt WHERE lt.tenant_id = t.id AND lt.code = 'CASUAL'
);

INSERT INTO hr_leave_types (tenant_id, code, name_ar, name_en, is_paid, requires_attachment, default_days_per_year, state)
SELECT t.id, 'MATERNITY', 'إجازة أمومة', 'Maternity Leave', true, true, NULL, 'ACTIVE'
FROM tenants t
WHERE NOT EXISTS (
    SELECT 1 FROM hr_leave_types lt WHERE lt.tenant_id = t.id AND lt.code = 'MATERNITY'
);

INSERT INTO hr_leave_types (tenant_id, code, name_ar, name_en, is_paid, requires_attachment, default_days_per_year, state)
SELECT t.id, 'PATERNITY', 'إجازة أبوة', 'Paternity Leave', true, false, NULL, 'ACTIVE'
FROM tenants t
WHERE NOT EXISTS (
    SELECT 1 FROM hr_leave_types lt WHERE lt.tenant_id = t.id AND lt.code = 'PATERNITY'
);

INSERT INTO hr_leave_types (tenant_id, code, name_ar, name_en, is_paid, requires_attachment, default_days_per_year, state)
SELECT t.id, 'UNPAID', 'إجازة بدون راتب', 'Unpaid Leave', false, false, NULL, 'ACTIVE'
FROM tenants t
WHERE NOT EXISTS (
    SELECT 1 FROM hr_leave_types lt WHERE lt.tenant_id = t.id AND lt.code = 'UNPAID'
);

-- ------------------------------------------------------------
-- 2. Seed RBAC capabilities for G2 time/attendance/leave
-- ------------------------------------------------------------
-- Capabilities are separated by scope per §15:
--   SELF scope: employee's own attendance/leave
--   MANAGER scope: direct reports' timesheet/leave approval
--   HR scope: tenant-wide administration
INSERT INTO access_capabilities (capability_code, description, created_at)
SELECT 'HRM.ATTENDANCE.SELF_RECORD', 'Record own attendance (clock in/out)', NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE capability_code = 'HRM.ATTENDANCE.SELF_RECORD')
ON CONFLICT DO NOTHING;

INSERT INTO access_capabilities (capability_code, description, created_at)
SELECT 'HRM.ATTENDANCE.SELF_VIEW', 'View own attendance records', NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE capability_code = 'HRM.ATTENDANCE.SELF_VIEW')
ON CONFLICT DO NOTHING;

INSERT INTO access_capabilities (capability_code, description, created_at)
SELECT 'HRM.ATTENDANCE.TEAM_VIEW', 'View team attendance records', NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE capability_code = 'HRM.ATTENDANCE.TEAM_VIEW')
ON CONFLICT DO NOTHING;

INSERT INTO access_capabilities (capability_code, description, created_at)
SELECT 'HRM.ATTENDANCE.ADMIN', 'Administer attendance records', NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE capability_code = 'HRM.ATTENDANCE.ADMIN')
ON CONFLICT DO NOTHING;

INSERT INTO access_capabilities (capability_code, description, created_at)
SELECT 'HRM.ATTENDANCE.CORRECT', 'Manually correct attendance records', NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE capability_code = 'HRM.ATTENDANCE.CORRECT')
ON CONFLICT DO NOTHING;

INSERT INTO access_capabilities (capability_code, description, created_at)
SELECT 'HRM.LEAVE.SELF_REQUEST', 'Submit own leave requests', NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE capability_code = 'HRM.LEAVE.SELF_REQUEST')
ON CONFLICT DO NOTHING;

INSERT INTO access_capabilities (capability_code, description, created_at)
SELECT 'HRM.LEAVE.SELF_VIEW', 'View own leave balances and requests', NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE capability_code = 'HRM.LEAVE.SELF_VIEW')
ON CONFLICT DO NOTHING;

INSERT INTO access_capabilities (capability_code, description, created_at)
SELECT 'HRM.LEAVE.TEAM_APPROVE', 'Approve/reject team leave requests (manager)', NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE capability_code = 'HRM.LEAVE.TEAM_APPROVE')
ON CONFLICT DO NOTHING;

INSERT INTO access_capabilities (capability_code, description, created_at)
SELECT 'HRM.LEAVE.HR_APPROVE', 'Final HR approval of leave requests', NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE capability_code = 'HRM.LEAVE.HR_APPROVE')
ON CONFLICT DO NOTHING;

INSERT INTO access_capabilities (capability_code, description, created_at)
SELECT 'HRM.LEAVE.POLICY_ADMIN', 'Manage leave types and policies', NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE capability_code = 'HRM.LEAVE.POLICY_ADMIN')
ON CONFLICT DO NOTHING;

INSERT INTO access_capabilities (capability_code, description, created_at)
SELECT 'HRM.TIMESHEET.SELF_VIEW', 'View own timesheets', NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE capability_code = 'HRM.TIMESHEET.SELF_VIEW')
ON CONFLICT DO NOTHING;

INSERT INTO access_capabilities (capability_code, description, created_at)
SELECT 'HRM.TIMESHEET.SELF_SUBMIT', 'Submit own timesheets for approval', NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE capability_code = 'HRM.TIMESHEET.SELF_SUBMIT')
ON CONFLICT DO NOTHING;

INSERT INTO access_capabilities (capability_code, description, created_at)
SELECT 'HRM.TIMESHEET.TEAM_APPROVE', 'Approve/reject team timesheets (manager)', NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE capability_code = 'HRM.TIMESHEET.TEAM_APPROVE')
ON CONFLICT DO NOTHING;
