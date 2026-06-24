-- ============================================================
-- V15: Seed ADMIN role and all capability assignments
-- DEFECT-014: ADMIN capability migration seeding
-- ============================================================
-- This migration ensures every tenant has an ACTIVE ADMIN role
-- with all 19 platform capabilities assigned.
--
-- Idempotent: uses WHERE NOT EXISTS to skip existing records.
-- Safe on both fresh and upgraded databases.
-- Preserves customized role assignments.
-- ============================================================

-- Step 1: Ensure ADMIN role exists for every tenant
INSERT INTO roles (id, tenant_id, code, name, description, status, created_at, updated_at)
SELECT
    gen_random_uuid(),
    t.id,
    'ADMIN',
    'Administrator',
    'Tenant-wide administrative access',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM tenants t
WHERE NOT EXISTS (
    SELECT 1 FROM roles r
    WHERE r.tenant_id = t.id AND r.code = 'ADMIN'
);

-- Step 2: Assign all 19 capabilities to every ADMIN role
INSERT INTO role_capabilities (id, role_id, capability_id, created_at)
SELECT
    gen_random_uuid(),
    r.id,
    ac.id,
    CURRENT_TIMESTAMP
FROM roles r
CROSS JOIN access_capabilities ac
WHERE r.code = 'ADMIN'
  AND ac.status = 'ACTIVE'
  AND NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.role_id = r.id AND rc.capability_id = ac.id
  );
