-- ============================================================
-- V20260815_15: Add AI.* capabilities
-- ============================================================

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), code, name, desc_text, 'ACTIVE', NOW(), NOW()
FROM (VALUES
    ('AI.VIEW', 'AI View', 'View AI agents and inference records'),
    ('AI.WRITE', 'AI Write', 'Create and update AI agent definitions'),
    ('AI.ADMIN', 'AI Admin', 'Full administrative access including archive'),
    ('AI.EXECUTE', 'AI Execute', 'Invoke AI agents and generate inferences')
) AS v(code, name, desc_text)
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities ac WHERE ac.code = v.code);

-- Bind to ADMIN for all tenants
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), t.id, r.id, ac.id, NOW()
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'ADMIN'
JOIN access_capabilities ac ON ac.status = 'ACTIVE'
   AND ac.code IN ('AI.VIEW', 'AI.WRITE', 'AI.ADMIN', 'AI.EXECUTE')
WHERE NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);
