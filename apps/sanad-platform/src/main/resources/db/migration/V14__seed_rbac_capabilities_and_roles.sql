-- ============================================================
-- V14: Seed RBAC capabilities catalog
-- DEFECT-007: RBAC Authorization Matrix Validation
-- ============================================================
-- This migration creates the platform-level capability catalog.
-- Uses fixed UUIDs with explicit CAST for cross-database compatibility.
--
-- Role seeding and capability assignments are handled by the
-- Java migration V15 to ensure cross-database UUID generation.
-- ============================================================

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT uuid_val, code, name, description, status, created_at, updated_at
FROM (
    VALUES
    -- User management
    (CAST('a0000001-0000-0000-0000-000000000001' AS uuid), 'USER.READ',   'Read Users',          'View user profiles and list users', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (CAST('a0000001-0000-0000-0000-000000000002' AS uuid), 'USER.CREATE', 'Create Users',        'Create new user accounts',           'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (CAST('a0000001-0000-0000-0000-000000000003' AS uuid), 'USER.WRITE',  'Update Users',        'Modify user profile and status',     'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (CAST('a0000001-0000-0000-0000-000000000004' AS uuid), 'USER.DELETE', 'Delete/Archive Users','Archive or remove users',            'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    -- Organization management
    (CAST('a0000002-0000-0000-0000-000000000001' AS uuid), 'ORGANIZATION.READ',   'Read Organizations',   'View organization details and list', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (CAST('a0000002-0000-0000-0000-000000000002' AS uuid), 'ORGANIZATION.CREATE', 'Create Organizations', 'Create new organizations',            'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (CAST('a0000002-0000-0000-0000-000000000003' AS uuid), 'ORGANIZATION.WRITE',  'Update Organizations', 'Modify organization details/status',  'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (CAST('a0000002-0000-0000-0000-000000000004' AS uuid), 'ORGANIZATION.DELETE', 'Delete Organizations', 'Archive or remove organizations',     'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    -- Membership management
    (CAST('a0000003-0000-0000-0000-000000000001' AS uuid), 'MEMBERSHIP.READ',   'Read Memberships',   'View organization memberships',     'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (CAST('a0000003-0000-0000-0000-000000000002' AS uuid), 'MEMBERSHIP.CREATE', 'Create Memberships',  'Invite members to organizations',   'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (CAST('a0000003-0000-0000-0000-000000000003' AS uuid), 'MEMBERSHIP.WRITE',  'Update Memberships',  'Activate/deactivate memberships',   'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (CAST('a0000003-0000-0000-0000-000000000004' AS uuid), 'MEMBERSHIP.DELETE', 'Remove Memberships',  'Remove memberships from orgs',      'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    -- Role management
    (CAST('a0000004-0000-0000-0000-000000000001' AS uuid), 'ROLE.READ',   'Read Roles',   'View roles and their capabilities', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (CAST('a0000004-0000-0000-0000-000000000002' AS uuid), 'ROLE.WRITE',  'Manage Roles', 'Create, update, and configure roles', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    -- Capability catalog management
    (CAST('a0000005-0000-0000-0000-000000000001' AS uuid), 'CAPABILITY.READ',   'Read Capabilities',  'View the capability catalog',       'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (CAST('a0000005-0000-0000-0000-000000000002' AS uuid), 'CAPABILITY.MANAGE', 'Manage Capabilities','Create, update, activate/deactivate capabilities', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    -- Access evaluation
    (CAST('a0000006-0000-0000-0000-000000000001' AS uuid), 'ACCESS.EVALUATE', 'Evaluate Access', 'Check user capabilities and permissions', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    -- User role grant management
    (CAST('a0000007-0000-0000-0000-000000000001' AS uuid), 'USER.GRANT_ROLE',  'Grant Roles',  'Assign roles to users',              'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (CAST('a0000007-0000-0000-0000-000000000002' AS uuid), 'USER.REVOKE_ROLE', 'Revoke Roles', 'Remove role assignments from users', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
) AS t(uuid_val, code, name, description, status, created_at, updated_at)
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = t.code);
