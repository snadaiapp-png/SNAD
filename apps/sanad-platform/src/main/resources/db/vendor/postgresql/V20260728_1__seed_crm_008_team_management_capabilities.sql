-- V20260728_1: CRM-008 Team Management capabilities
-- Seeds capability catalog entries for CRM-008 Team Management features.

-- ── Team Management ──────────────────────────────────────────────────────

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'CRM.TEAM.WRITE', 'CRM Team Write', 'Create, update, and manage CRM teams', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'CRM.TEAM.WRITE');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'CRM.TEAM.MANAGE', 'CRM Team Manage', 'Full team management including archive and activate', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'CRM.TEAM.MANAGE');

-- ── Shift Management ─────────────────────────────────────────────────────

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'CRM.SHIFT.READ', 'CRM Shift Read', 'View shift templates and assignments', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'CRM.SHIFT.READ');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'CRM.SHIFT.MANAGE', 'CRM Shift Manage', 'Create, update, and manage shift templates and assignments', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'CRM.SHIFT.MANAGE');

-- ── Availability Management ──────────────────────────────────────────────

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'CRM.AVAILABILITY.READ', 'CRM Availability Read', 'View staff availability', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'CRM.AVAILABILITY.READ');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'CRM.AVAILABILITY.MANAGE', 'CRM Availability Manage', 'Submit, approve, and reject staff availability', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'CRM.AVAILABILITY.MANAGE');

-- ── Skills Management ────────────────────────────────────────────────────

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'CRM.SKILLS.READ', 'CRM Skills Read', 'View staff skills', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'CRM.SKILLS.READ');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'CRM.SKILLS.MANAGE', 'CRM Skills Manage', 'Register, update, and delete staff skills', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'CRM.SKILLS.MANAGE');

-- ── Capacity Management ──────────────────────────────────────────────────

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'CRM.CAPACITY.READ', 'CRM Capacity Read', 'View capacity plans and forecasts', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'CRM.CAPACITY.READ');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'CRM.CAPACITY.MANAGE', 'CRM Capacity Manage', 'Create and adjust capacity plans', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'CRM.CAPACITY.MANAGE');

-- ── Workload Management ──────────────────────────────────────────────────

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'CRM.WORKLOAD.READ', 'CRM Workload Read', 'View workload assignments and hours', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'CRM.WORKLOAD.READ');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'CRM.WORKLOAD.MANAGE', 'CRM Workload Manage', 'Assign, reassign, and release workload', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'CRM.WORKLOAD.MANAGE');

-- ── Service Assignment Management ────────────────────────────────────────

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'CRM.ASSIGNMENT.MANAGE', 'CRM Assignment Manage', 'Assign, reassign, and manage service assignments', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'CRM.ASSIGNMENT.MANAGE');
