-- V20260807_1: Grant CRM capabilities to non-ADMIN roles
-- Fixes B-01 (non-ADMIN roles have zero CRM capabilities) and B-12 (SALES_* lack core CRM caps)
--
-- Capability matrix (least-privilege):
--   VIEWER           → READ-only for core CRM entities
--   MEMBER           → READ + WRITE for core CRM entities
--   MANAGER          → READ + WRITE for core CRM entities + tag/task/note management
--   ORG_ADMIN        → All CRM capabilities except CRM.ADMIN
--   SALES_MANAGER    → Core CRM READ + WRITE + ownership caps (extends V20260722_8)
--   SALES_REPRESENTATIVE → Core CRM READ + ownership read caps (extends V20260722_8)
--
-- Idempotent: uses WHERE NOT EXISTS on role_capabilities.

-- Helper: grant a set of capability codes to a role for all active tenants
-- This is a reusable pattern for each role.

-- ============================================================
-- VIEWER: READ-only core CRM capabilities
-- ============================================================
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), t.id, r.id, ac.id, CURRENT_TIMESTAMP
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'VIEWER'
JOIN access_capabilities ac ON ac.code IN (
    'CRM.ACCOUNT.READ',
    'CRM.CONTACT.READ',
    'CRM.LEAD.READ',
    'CRM.OPPORTUNITY.READ',
    'CRM.ACTIVITY.READ',
    'CRM.TAG.READ',
    'CRM.TASK.READ',
    'CRM.NOTE.READ',
    'CRM.CASE.READ',
    'CRM.EMAIL.READ',
    'CRM.REPORTS.READ',
    'CRM.ADDRESS.READ',
    'CRM.COMMUNICATION.READ',
    'CRM.RELATIONSHIP.READ',
    'CRM.CUSTOM_FIELD.READ',
    'CRM.IMPORT.READ'
) AND ac.status = 'ACTIVE'
WHERE t.status = 'ACTIVE'
AND NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);

-- ============================================================
-- MEMBER: READ + WRITE core CRM capabilities
-- ============================================================
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), t.id, r.id, ac.id, CURRENT_TIMESTAMP
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'MEMBER'
JOIN access_capabilities ac ON ac.code IN (
    'CRM.ACCOUNT.READ', 'CRM.ACCOUNT.WRITE',
    'CRM.CONTACT.READ', 'CRM.CONTACT.WRITE',
    'CRM.LEAD.READ', 'CRM.LEAD.WRITE',
    'CRM.OPPORTUNITY.READ', 'CRM.OPPORTUNITY.WRITE',
    'CRM.ACTIVITY.READ', 'CRM.ACTIVITY.WRITE',
    'CRM.TAG.READ', 'CRM.TAG.WRITE',
    'CRM.TASK.READ', 'CRM.TASK.WRITE',
    'CRM.NOTE.READ', 'CRM.NOTE.WRITE',
    'CRM.CASE.READ', 'CRM.CASE.WRITE',
    'CRM.EMAIL.READ', 'CRM.EMAIL.WRITE',
    'CRM.REPORTS.READ',
    'CRM.ADDRESS.READ', 'CRM.ADDRESS.WRITE',
    'CRM.COMMUNICATION.READ', 'CRM.COMMUNICATION.WRITE',
    'CRM.RELATIONSHIP.READ', 'CRM.RELATIONSHIP.WRITE',
    'CRM.CUSTOM_FIELD.READ', 'CRM.CUSTOM_FIELD.WRITE',
    'CRM.IMPORT.READ', 'CRM.IMPORT.WRITE'
) AND ac.status = 'ACTIVE'
WHERE t.status = 'ACTIVE'
AND NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);

-- ============================================================
-- MANAGER: READ + WRITE + tag/task/note management
-- ============================================================
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), t.id, r.id, ac.id, CURRENT_TIMESTAMP
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'MANAGER'
JOIN access_capabilities ac ON ac.code IN (
    'CRM.ACCOUNT.READ', 'CRM.ACCOUNT.WRITE',
    'CRM.CONTACT.READ', 'CRM.CONTACT.WRITE',
    'CRM.LEAD.READ', 'CRM.LEAD.WRITE', 'CRM.LEAD.CONVERT',
    'CRM.OPPORTUNITY.READ', 'CRM.OPPORTUNITY.WRITE',
    'CRM.ACTIVITY.READ', 'CRM.ACTIVITY.WRITE',
    'CRM.TAG.READ', 'CRM.TAG.WRITE',
    'CRM.TASK.READ', 'CRM.TASK.WRITE',
    'CRM.NOTE.READ', 'CRM.NOTE.WRITE',
    'CRM.CASE.READ', 'CRM.CASE.WRITE',
    'CRM.EMAIL.READ', 'CRM.EMAIL.WRITE',
    'CRM.REPORTS.READ',
    'CRM.ADDRESS.READ', 'CRM.ADDRESS.WRITE', 'CRM.ADDRESS.ADMIN',
    'CRM.COMMUNICATION.READ', 'CRM.COMMUNICATION.WRITE',
    'CRM.RELATIONSHIP.READ', 'CRM.RELATIONSHIP.WRITE',
    'CRM.CUSTOM_FIELD.READ', 'CRM.CUSTOM_FIELD.WRITE',
    'CRM.IMPORT.READ', 'CRM.IMPORT.WRITE',
    'CRM.PORTAL.READ'
) AND ac.status = 'ACTIVE'
WHERE t.status = 'ACTIVE'
AND NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);

-- ============================================================
-- ORG_ADMIN: All CRM capabilities except CRM.ADMIN
-- ============================================================
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), t.id, r.id, ac.id, CURRENT_TIMESTAMP
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'ORG_ADMIN'
JOIN access_capabilities ac ON ac.code LIKE 'CRM.%' AND ac.code != 'CRM.ADMIN' AND ac.status = 'ACTIVE'
WHERE t.status = 'ACTIVE'
AND NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);

-- ============================================================
-- SALES_MANAGER: Core CRM READ + WRITE (extends ownership caps from V20260722_8)
-- ============================================================
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), t.id, r.id, ac.id, CURRENT_TIMESTAMP
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'SALES_MANAGER'
JOIN access_capabilities ac ON ac.code IN (
    'CRM.ACCOUNT.READ', 'CRM.ACCOUNT.WRITE',
    'CRM.CONTACT.READ', 'CRM.CONTACT.WRITE',
    'CRM.LEAD.READ', 'CRM.LEAD.WRITE', 'CRM.LEAD.CONVERT',
    'CRM.OPPORTUNITY.READ', 'CRM.OPPORTUNITY.WRITE',
    'CRM.ACTIVITY.READ', 'CRM.ACTIVITY.WRITE',
    'CRM.TAG.READ', 'CRM.TAG.WRITE',
    'CRM.TASK.READ', 'CRM.TASK.WRITE',
    'CRM.NOTE.READ', 'CRM.NOTE.WRITE',
    'CRM.CASE.READ', 'CRM.CASE.WRITE',
    'CRM.EMAIL.READ', 'CRM.EMAIL.WRITE',
    'CRM.REPORTS.READ'
) AND ac.status = 'ACTIVE'
WHERE t.status = 'ACTIVE'
AND NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);

-- ============================================================
-- SALES_REPRESENTATIVE: Core CRM READ (extends ownership caps from V20260722_8)
-- ============================================================
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), t.id, r.id, ac.id, CURRENT_TIMESTAMP
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'SALES_REPRESENTATIVE'
JOIN access_capabilities ac ON ac.code IN (
    'CRM.ACCOUNT.READ',
    'CRM.CONTACT.READ',
    'CRM.LEAD.READ',
    'CRM.OPPORTUNITY.READ',
    'CRM.ACTIVITY.READ',
    'CRM.TAG.READ',
    'CRM.TASK.READ',
    'CRM.NOTE.READ',
    'CRM.CASE.READ',
    'CRM.EMAIL.READ',
    'CRM.REPORTS.READ'
) AND ac.status = 'ACTIVE'
WHERE t.status = 'ACTIVE'
AND NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);
