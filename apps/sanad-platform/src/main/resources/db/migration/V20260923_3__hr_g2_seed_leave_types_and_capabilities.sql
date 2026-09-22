-- ============================================================
-- V20260923_3 — HRM-G2: seed leave types + RBAC capabilities
-- ============================================================
-- Seeds default leave types (Arabic-first names per SNAD convention)
-- and the RBAC capabilities for the G2 time/attendance/leave module.
-- ============================================================

-- ------------------------------------------------------------
-- 1. Seed default leave types for all existing tenants
-- ------------------------------------------------------------
INSERT INTO hr_leave_types (tenant_id, code, name_ar, name_en, is_paid, requires_attachment, default_days_per_year, state)
SELECT t.id, 'ANNUAL', 'إجازة سنوية', 'Annual Leave', true, false, 21, 'ACTIVE'
FROM tenants t
WHERE NOT EXISTS (
    SELECT 1 FROM hr_leave_types lt WHERE lt.tenant_id = t.id AND lt.code = 'ANNUAL'
);

INSERT INTO hr_leave_types (tenant_id, code, name_ar, name_en, is_paid, requires_attachment, default_days_per_year, state)
SELECT t.id, 'SICK', 'إجازة مرضية', 'Sick Leave', true, true, 30, 'ACTIVE'
FROM tenants t
WHERE NOT EXISTS (
    SELECT 1 FROM hr_leave_types lt WHERE lt.tenant_id = t.id AND lt.code = 'SICK'
);

INSERT INTO hr_leave_types (tenant_id, code, name_ar, name_en, is_paid, requires_attachment, default_days_per_year, state)
SELECT t.id, 'CASUAL', 'إجازة عادية', 'Casual Leave', true, false, 7, 'ACTIVE'
FROM tenants t
WHERE NOT EXISTS (
    SELECT 1 FROM hr_leave_types lt WHERE lt.tenant_id = t.id AND lt.code = 'CASUAL'
);

INSERT INTO hr_leave_types (tenant_id, code, name_ar, name_en, is_paid, requires_attachment, default_days_per_year, state)
SELECT t.id, 'MATERNITY', 'إجازة أمومة', 'Maternity Leave', true, true, 70, 'ACTIVE'
FROM tenants t
WHERE NOT EXISTS (
    SELECT 1 FROM hr_leave_types lt WHERE lt.tenant_id = t.id AND lt.code = 'MATERNITY'
);

INSERT INTO hr_leave_types (tenant_id, code, name_ar, name_en, is_paid, requires_attachment, default_days_per_year, state)
SELECT t.id, 'PATERNITY', 'إجازة أبوة', 'Paternity Leave', true, false, 3, 'ACTIVE'
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
INSERT INTO access_capabilities (capability_code, description, created_at)
SELECT 'HRM.ATTENDANCE.VIEW', 'View attendance records', NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE capability_code = 'HRM.ATTENDANCE.VIEW')
ON CONFLICT DO NOTHING;

INSERT INTO access_capabilities (capability_code, description, created_at)
SELECT 'HRM.ATTENDANCE.MANAGE', 'Clock in/out and manage attendance records', NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE capability_code = 'HRM.ATTENDANCE.MANAGE')
ON CONFLICT DO NOTHING;

INSERT INTO access_capabilities (capability_code, description, created_at)
SELECT 'HRM.ATTENDANCE.APPROVE', 'Approve/correct attendance records', NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE capability_code = 'HRM.ATTENDANCE.APPROVE')
ON CONFLICT DO NOTHING;

INSERT INTO access_capabilities (capability_code, description, created_at)
SELECT 'HRM.LEAVE.VIEW', 'View leave requests and balances', NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE capability_code = 'HRM.LEAVE.VIEW')
ON CONFLICT DO NOTHING;

INSERT INTO access_capabilities (capability_code, description, created_at)
SELECT 'HRM.LEAVE.REQUEST', 'Submit leave requests', NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE capability_code = 'HRM.LEAVE.REQUEST')
ON CONFLICT DO NOTHING;

INSERT INTO access_capabilities (capability_code, description, created_at)
SELECT 'HRM.LEAVE.APPROVE', 'Approve/reject leave requests', NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE capability_code = 'HRM.LEAVE.APPROVE')
ON CONFLICT DO NOTHING;

INSERT INTO access_capabilities (capability_code, description, created_at)
SELECT 'HRM.LEAVE.MANAGE', 'Manage leave types and balances', NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE capability_code = 'HRM.LEAVE.MANAGE')
ON CONFLICT DO NOTHING;

INSERT INTO access_capabilities (capability_code, description, created_at)
SELECT 'HRM.TIMESHEET.VIEW', 'View timesheets', NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE capability_code = 'HRM.TIMESHEET.VIEW')
ON CONFLICT DO NOTHING;

INSERT INTO access_capabilities (capability_code, description, created_at)
SELECT 'HRM.TIMESHEET.SUBMIT', 'Submit timesheets for approval', NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE capability_code = 'HRM.TIMESHEET.SUBMIT')
ON CONFLICT DO NOTHING;

INSERT INTO access_capabilities (capability_code, description, created_at)
SELECT 'HRM.TIMESHEET.APPROVE', 'Approve/reject timesheets', NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE capability_code = 'HRM.TIMESHEET.APPROVE')
ON CONFLICT DO NOTHING;
