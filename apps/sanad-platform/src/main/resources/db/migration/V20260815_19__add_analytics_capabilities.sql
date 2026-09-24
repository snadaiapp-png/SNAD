-- ============================================================
-- V20260815_19: Add ANALYTICS.* capabilities
-- ============================================================

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), code, name, desc_text, 'ACTIVE', NOW(), NOW()
FROM (VALUES
    ('ANALYTICS.VIEW', 'Analytics View', 'View dashboards, reports, and data sources'),
    ('ANALYTICS.WRITE', 'Analytics Write', 'Create and update analytics dashboards and reports'),
    ('ANALYTICS.ADMIN', 'Analytics Admin', 'Full administrative access including archive and data source management'),
    ('ANALYTICS.EXECUTE', 'Analytics Execute', 'Execute reports and generate analytics output')
) AS v(code, name, desc_text)
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities ac WHERE ac.code = v.code);

-- Bind to ADMIN for all tenants
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), t.id, r.id, ac.id, NOW()
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'ADMIN'
JOIN access_capabilities ac ON ac.status = 'ACTIVE'
   AND ac.code IN ('ANALYTICS.VIEW', 'ANALYTICS.WRITE', 'ANALYTICS.ADMIN', 'ANALYTICS.EXECUTE')
WHERE NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);
