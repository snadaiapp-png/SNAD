-- MOD-004: Customer Portal
-- Adds CRM.PORTAL.READ and CRM.PORTAL.WRITE capabilities.

-- 1. Add portal capabilities
INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), capability.code, capability.name, capability.description, 'ACTIVE', NOW(), NOW()
FROM (VALUES
    ('CRM.PORTAL.READ',  'Read Customer Portal',  'View customer portal data including profile, tickets, and opportunities'),
    ('CRM.PORTAL.WRITE', 'Write Customer Portal', 'Update customer profile and create support tickets')
) AS capability(code, name, description)
WHERE NOT EXISTS (
    SELECT 1 FROM access_capabilities ac WHERE ac.code = capability.code
);

-- 2. Grant portal capabilities to ADMIN and CUSTOMER roles
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT
    gen_random_uuid(),
    role.tenant_id,
    role.id,
    capability.id,
    NOW()
FROM roles role
JOIN access_capabilities capability ON (
    capability.code IN ('CRM.PORTAL.READ', 'CRM.PORTAL.WRITE')
    AND capability.status = 'ACTIVE'
)
WHERE role.code IN ('ADMIN', 'CUSTOMER')
  AND role.status = 'ACTIVE'
  AND NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = role.tenant_id
      AND rc.role_id = role.id
      AND rc.capability_id = capability.id
  );
