-- MOD-003: Reporting Dashboard
-- Adds CRM.REPORTS.READ capability for reporting access.

-- Add reporting capability
INSERT INTO access_capabilities (id, tenant_id, code, description, created_at)
SELECT gen_random_uuid(), t.id, 'CRM.REPORTS.READ', 'View CRM reports and analytics', NOW()
FROM tenants t
WHERE NOT EXISTS (
    SELECT 1 FROM access_capabilities ac
    WHERE ac.tenant_id = t.id AND ac.code = 'CRM.REPORTS.READ'
);

-- Grant to ADMIN role
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT
    gen_random_uuid(),
    t.id,
    r.id,
    ac.id,
    NOW()
FROM tenants t
JOIN access_roles r ON r.tenant_id = t.id AND r.code = 'ADMIN'
JOIN access_capabilities ac ON ac.tenant_id = t.id AND ac.code = 'CRM.REPORTS.READ'
WHERE NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);
