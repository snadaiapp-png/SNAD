-- ============================================================
-- V20260815_6: Add EXECUTIVE_COMMAND_CENTER + ALERTS + INTELLIGENCE + WORKFLOW capabilities
-- ============================================================

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), code, name, desc_text, 'ACTIVE', NOW(), NOW()
FROM (VALUES
    ('EXECUTIVE_COMMAND_CENTER.VIEW', 'Command Center View', 'View the executive command center dashboard'),
    ('EXECUTIVE_ALERTS.VIEW', 'Executive Alerts View', 'View executive alerts'),
    ('EXECUTIVE_ALERTS.WRITE', 'Executive Alerts Write', 'Acknowledge and resolve executive alerts'),
    ('EXECUTIVE_ALERTS.ADMIN', 'Executive Alerts Admin', 'Full administrative access to alerts'),
    ('EXECUTIVE_INTELLIGENCE.VIEW', 'Executive Intelligence View', 'View AI-generated insights and recommendations'),
    ('EXECUTIVE_INTELLIGENCE.ADMIN', 'Executive Intelligence Admin', 'Trigger AI analysis and manage insights'),
    ('EXECUTIVE_WORKFLOW.ADMIN', 'Executive Workflow Admin', 'Manage cross-domain workflow rules and triggers')
) AS v(code, name, desc_text)
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities ac WHERE ac.code = v.code);

-- Bind to ADMIN for all tenants
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), t.id, r.id, ac.id, NOW()
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'ADMIN'
JOIN access_capabilities ac ON ac.status = 'ACTIVE'
   AND ac.code IN (
       'EXECUTIVE_COMMAND_CENTER.VIEW',
       'EXECUTIVE_ALERTS.VIEW', 'EXECUTIVE_ALERTS.WRITE', 'EXECUTIVE_ALERTS.ADMIN',
       'EXECUTIVE_INTELLIGENCE.VIEW', 'EXECUTIVE_INTELLIGENCE.ADMIN',
       'EXECUTIVE_WORKFLOW.ADMIN'
   )
WHERE NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);
