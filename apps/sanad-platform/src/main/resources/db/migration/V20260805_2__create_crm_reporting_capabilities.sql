-- MOD-003: Reporting Dashboard
-- Adds CRM.REPORTS.READ capability for reporting access.

-- 1. Add reporting capability
INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'CRM.REPORTS.READ', 'Read CRM Reports', 'View CRM reports and analytics', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM access_capabilities ac WHERE ac.code = 'CRM.REPORTS.READ'
);

-- 2. Grant CRM.REPORTS.READ to ADMIN roles in every tenant
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT
    gen_random_uuid(),
    role.tenant_id,
    role.id,
    capability.id,
    NOW()
FROM roles role
JOIN access_capabilities capability ON (
    capability.code = 'CRM.REPORTS.READ'
    AND capability.status = 'ACTIVE'
)
WHERE role.code = 'ADMIN'
  AND role.status = 'ACTIVE'
  AND NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = role.tenant_id
      AND rc.role_id = role.id
      AND rc.capability_id = capability.id
  );
