-- ============================================================
-- V14: Seed RBAC capabilities, predefined roles, and role-capability mappings
-- DEFECT-007: RBAC Authorization Matrix Validation
-- ============================================================
-- This migration creates the platform-level capability catalog and
-- assigns capabilities to predefined roles for each existing tenant.
-- Compatible with both H2 (local/test) and PostgreSQL (production).
-- ============================================================

-- -----------------------------------------------------------
-- Step 1: Create global capability catalog
-- Capabilities are NOT tenant-scoped; they are shared across all tenants.
-- Uses fixed UUIDs for deterministic inserts.
-- -----------------------------------------------------------

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

-- -----------------------------------------------------------
-- Step 2: Create predefined roles for each existing tenant
-- Roles are tenant-scoped. Uses RANDOM_UUID() for H2/PG compatibility.
-- -----------------------------------------------------------

INSERT INTO roles (id, tenant_id, code, name, description, status, created_at, updated_at)
SELECT RANDOM_UUID(), t.id, r.code, r.name, r.description, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM tenants t
CROSS JOIN (
    VALUES
        ('SUPER_ADMIN', 'Super Administrator', 'Full administrative access with all capabilities'),
        ('ORG_ADMIN',   'Organization Admin',   'Manage users, organizations, and memberships within the tenant'),
        ('MANAGER',     'Manager',              'Read access and membership management within assigned organizations'),
        ('MEMBER',      'Member',               'Basic read access to resources within the tenant'),
        ('VIEWER',      'Viewer',               'Read-only access to non-sensitive resources')
) AS r(code, name, description)
WHERE t.status = 'ACTIVE'
  AND NOT EXISTS (
    SELECT 1 FROM roles ro WHERE ro.tenant_id = t.id AND ro.code = r.code
  );

-- -----------------------------------------------------------
-- Step 3: Assign capabilities to roles
-- SUPER_ADMIN gets ALL capabilities
-- -----------------------------------------------------------

INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT RANDOM_UUID(), t.id, r.id, ac.id, CURRENT_TIMESTAMP
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'SUPER_ADMIN'
JOIN access_capabilities ac ON ac.status = 'ACTIVE'
WHERE t.status = 'ACTIVE'
  AND NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
  );

-- ORG_ADMIN capabilities
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT RANDOM_UUID(), t.id, r.id, ac.id, CURRENT_TIMESTAMP
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'ORG_ADMIN'
JOIN access_capabilities ac ON ac.code IN (
    'USER.READ', 'USER.CREATE', 'USER.WRITE',
    'ORGANIZATION.READ', 'ORGANIZATION.CREATE', 'ORGANIZATION.WRITE',
    'MEMBERSHIP.READ', 'MEMBERSHIP.CREATE', 'MEMBERSHIP.WRITE',
    'ROLE.READ', 'CAPABILITY.READ', 'ACCESS.EVALUATE',
    'USER.GRANT_ROLE', 'USER.REVOKE_ROLE'
) AND ac.status = 'ACTIVE'
WHERE t.status = 'ACTIVE'
  AND NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
  );

-- MANAGER capabilities
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT RANDOM_UUID(), t.id, r.id, ac.id, CURRENT_TIMESTAMP
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'MANAGER'
JOIN access_capabilities ac ON ac.code IN (
    'USER.READ',
    'ORGANIZATION.READ',
    'MEMBERSHIP.READ', 'MEMBERSHIP.CREATE', 'MEMBERSHIP.WRITE',
    'ROLE.READ', 'CAPABILITY.READ', 'ACCESS.EVALUATE'
) AND ac.status = 'ACTIVE'
WHERE t.status = 'ACTIVE'
  AND NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
  );

-- MEMBER capabilities
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT RANDOM_UUID(), t.id, r.id, ac.id, CURRENT_TIMESTAMP
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'MEMBER'
JOIN access_capabilities ac ON ac.code IN (
    'USER.READ',
    'ORGANIZATION.READ',
    'MEMBERSHIP.READ',
    'ROLE.READ', 'CAPABILITY.READ', 'ACCESS.EVALUATE'
) AND ac.status = 'ACTIVE'
WHERE t.status = 'ACTIVE'
  AND NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
  );

-- VIEWER capabilities
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT RANDOM_UUID(), t.id, r.id, ac.id, CURRENT_TIMESTAMP
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'VIEWER'
JOIN access_capabilities ac ON ac.code IN (
    'USER.READ',
    'ORGANIZATION.READ',
    'MEMBERSHIP.READ',
    'ROLE.READ', 'CAPABILITY.READ', 'ACCESS.EVALUATE'
) AND ac.status = 'ACTIVE'
WHERE t.status = 'ACTIVE'
  AND NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
  );

-- -----------------------------------------------------------
-- Step 4: Ensure existing ADMIN role grants get all capabilities
-- The bootstrap creates an ADMIN role; we treat it like SUPER_ADMIN.
-- -----------------------------------------------------------

INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT RANDOM_UUID(), t.id, r.id, ac.id, CURRENT_TIMESTAMP
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'ADMIN'
JOIN access_capabilities ac ON ac.status = 'ACTIVE'
WHERE t.status = 'ACTIVE'
  AND NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
  );
