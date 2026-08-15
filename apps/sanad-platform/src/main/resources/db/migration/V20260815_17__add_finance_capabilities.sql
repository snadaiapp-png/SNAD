-- ============================================================
-- V20260815_17: Add FINANCE.* capabilities
-- ============================================================

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), code, name, desc_text, 'ACTIVE', NOW(), NOW()
FROM (VALUES
    ('FINANCE.VIEW', 'Finance View', 'View accounts, invoices, payments, and journals'),
    ('FINANCE.WRITE', 'Finance Write', 'Create and update finance records'),
    ('FINANCE.ADMIN', 'Finance Admin', 'Full administrative access including posting and reversal'),
    ('FINANCE.APPROVE', 'Finance Approve', 'Approve payments and journal postings')
) AS v(code, name, desc_text)
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities ac WHERE ac.code = v.code);

-- Bind to ADMIN for all tenants
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), t.id, r.id, ac.id, NOW()
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'ADMIN'
JOIN access_capabilities ac ON ac.status = 'ACTIVE'
   AND ac.code IN ('FINANCE.VIEW', 'FINANCE.WRITE', 'FINANCE.ADMIN', 'FINANCE.APPROVE')
WHERE NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);
