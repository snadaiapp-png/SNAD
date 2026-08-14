-- ============================================================
-- V20260815_2: Add EXECUTIVE_MANAGEMENT.* capabilities for the Senior Management Operating Layer
--
-- The new Senior Management API requires these capabilities:
--   EXECUTIVE_MANAGEMENT.VIEW  — view objectives, KPIs, initiatives, dashboard
--   EXECUTIVE_MANAGEMENT.WRITE  — create/update objectives, KPIs, measurements
--   EXECUTIVE_MANAGEMENT.ADMIN  — delete objectives, KPIs (full management)
--
-- These are bound to the ADMIN role for ALL existing tenants, preserving
-- the V15 invariant: "ADMIN gets all active capabilities."
-- ============================================================

-- ============================================================
-- STEP 1: Add 3 new EXECUTIVE_MANAGEMENT capabilities
-- ============================================================

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'EXECUTIVE_MANAGEMENT.VIEW',
       'Executive Management View',
       'View strategic objectives, key results, KPIs, initiatives, and the executive dashboard',
       'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'EXECUTIVE_MANAGEMENT.VIEW');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'EXECUTIVE_MANAGEMENT.WRITE',
       'Executive Management Write',
       'Create and update strategic objectives, key results, KPI definitions, targets, and measurements',
       'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'EXECUTIVE_MANAGEMENT.WRITE');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'EXECUTIVE_MANAGEMENT.ADMIN',
       'Executive Management Admin',
       'Full administrative access including deleting objectives, KPIs, and initiatives',
       'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'EXECUTIVE_MANAGEMENT.ADMIN');

-- ============================================================
-- STEP 2: Bind the 3 new capabilities to ALL existing ADMIN roles
-- (preserves the V15 invariant: ADMIN gets all active capabilities)
-- ============================================================

INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), t.id, r.id, ac.id, NOW()
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'ADMIN'
JOIN access_capabilities ac ON ac.status = 'ACTIVE'
   AND ac.code IN (
       'EXECUTIVE_MANAGEMENT.VIEW',
       'EXECUTIVE_MANAGEMENT.WRITE',
       'EXECUTIVE_MANAGEMENT.ADMIN'
   )
WHERE NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);
