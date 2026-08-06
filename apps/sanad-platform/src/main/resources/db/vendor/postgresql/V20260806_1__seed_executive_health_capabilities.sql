-- ============================================================
-- CRM-022 Recovery: Seed dedicated EXECUTIVE_* and SYSTEM_HEALTH_* capabilities
-- ============================================================

-- Executive Management capabilities
INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), code, name, description, 'ACTIVE', NOW(), NOW()
FROM (VALUES
    ('EXECUTIVE_VIEW', 'View Executive Dashboard', 'Access tenant management, directory, plans, and billing'),
    ('EXECUTIVE_MANAGE', 'Manage Tenants and Operations', 'Create, update, and manage tenants and platform operations'),
    ('EXECUTIVE_BILLING', 'Manage Billing', 'Manage plans, subscriptions, invoices, and billing operations')
) AS t(code, name, description)
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = t.code);

-- System Health capabilities
INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), code, name, description, 'ACTIVE', NOW(), NOW()
FROM (VALUES
    ('SYSTEM_HEALTH_VIEW', 'View System Health', 'Access system health dashboard, monitoring, and diagnostics'),
    ('SYSTEM_HEALTH_MONITOR', 'Monitor System Health', 'Execute health monitoring actions and self-healing'),
    ('SYSTEM_HEALTH_ALERTS', 'Manage System Alerts', 'Update system service status and manage alerts')
) AS t(code, name, description)
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = t.code);

-- Grant EXECUTIVE_* and SYSTEM_HEALTH_* to ADMIN role for every tenant
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), r.tenant_id, r.id, c.id, NOW()
FROM roles r
JOIN access_capabilities c ON c.code IN (
    'EXECUTIVE_VIEW', 'EXECUTIVE_MANAGE', 'EXECUTIVE_BILLING',
    'SYSTEM_HEALTH_VIEW', 'SYSTEM_HEALTH_MONITOR', 'SYSTEM_HEALTH_ALERTS'
)
WHERE r.code = 'ADMIN' AND r.status = 'ACTIVE'
AND NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = r.tenant_id AND rc.role_id = r.id AND rc.capability_id = c.id
);
