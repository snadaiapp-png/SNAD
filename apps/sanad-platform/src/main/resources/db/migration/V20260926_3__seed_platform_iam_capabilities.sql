-- ============================================================
-- V20260926_3 — Platform IAM: seed Platform capability catalog
-- ============================================================
-- Seeds all PLATFORM.* capability codes defined in the Platform IAM
-- design spec (docs/superpowers/specs/2026-09-25-platform-iam-design.md).
--
-- This migration:
--   - Reuses the existing access_capabilities table (no new engine).
--   - Is idempotent (WHERE NOT EXISTS per code).
--   - Is tenant-safe (access_capabilities is platform-level, no RLS).
--   - Does NOT modify any non-Platform capabilities.
-- ============================================================

-- 17 PLATFORM.* capability codes from the spec
INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'PLATFORM.USER.READ', 'Platform User Read', 'Read platform users across tenants', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'PLATFORM.USER.READ');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'PLATFORM.USER.CREATE', 'Platform User Create', 'Create platform users', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'PLATFORM.USER.CREATE');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'PLATFORM.USER.UPDATE', 'Platform User Update', 'Update platform user profiles', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'PLATFORM.USER.UPDATE');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'PLATFORM.USER.SUSPEND', 'Platform User Suspend', 'Suspend platform users', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'PLATFORM.USER.SUSPEND');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'PLATFORM.USER.DISABLE', 'Platform User Disable', 'Disable platform users', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'PLATFORM.USER.DISABLE');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'PLATFORM.ROLE.READ', 'Platform Role Read', 'Read platform roles', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'PLATFORM.ROLE.READ');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'PLATFORM.ROLE.CREATE', 'Platform Role Create', 'Create custom platform roles', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'PLATFORM.ROLE.CREATE');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'PLATFORM.ROLE.UPDATE', 'Platform Role Update', 'Update platform roles', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'PLATFORM.ROLE.UPDATE');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'PLATFORM.ROLE.ASSIGN', 'Platform Role Assign', 'Assign platform roles to users', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'PLATFORM.ROLE.ASSIGN');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'PLATFORM.ROLE.DELETE', 'Platform Role Delete', 'Delete custom platform roles', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'PLATFORM.ROLE.DELETE');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'PLATFORM.PERMISSION.READ', 'Platform Permission Read', 'Read platform permissions and scope grants', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'PLATFORM.PERMISSION.READ');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'PLATFORM.PERMISSION.MANAGE', 'Platform Permission Manage', 'Manage platform scope grants and exceptions', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'PLATFORM.PERMISSION.MANAGE');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'PLATFORM.SESSION.READ', 'Platform Session Read', 'Read platform user sessions', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'PLATFORM.SESSION.READ');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'PLATFORM.SESSION.REVOKE', 'Platform Session Revoke', 'Revoke platform user sessions', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'PLATFORM.SESSION.REVOKE');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'PLATFORM.AUDIT.READ', 'Platform Audit Read', 'Read platform audit logs', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'PLATFORM.AUDIT.READ');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'PLATFORM.SECURITY.READ', 'Platform Security Read', 'Read platform security events', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'PLATFORM.SECURITY.READ');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'PLATFORM.SECURITY.MANAGE', 'Platform Security Manage', 'Manage platform security policies', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'PLATFORM.SECURITY.MANAGE');
