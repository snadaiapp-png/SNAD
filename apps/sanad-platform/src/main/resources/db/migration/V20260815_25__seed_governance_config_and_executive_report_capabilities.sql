-- ============================================================
-- V20260815_25: Seed GOVERNANCE_CONFIG.* + EXECUTIVE_REPORT.* capabilities
--
-- Closes GAP 25 (Executive Reporting) and GAP 26 (Governance Configuration)
-- by adding the capabilities required to access the new endpoints.
-- Bound to ADMIN role for every tenant (idempotent).
--
-- H2 compatibility: pure idempotent INSERTs, runs unchanged on PG and H2.
-- ============================================================

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), code, name, desc_text, 'ACTIVE', NOW(), NOW()
FROM (VALUES
    ('GOVERNANCE_CONFIG.VIEW',   'Governance Config View',   'View tenant governance configurations (SLA/alert/escalation thresholds)'),
    ('GOVERNANCE_CONFIG.WRITE',  'Governance Config Write',  'Create and update tenant governance configurations'),
    ('GOVERNANCE_CONFIG.ADMIN',  'Governance Config Admin',  'Enable/disable, reset to defaults, and delete governance configurations'),
    ('EXECUTIVE_REPORT.VIEW',    'Executive Report View',    'View and download executive reports (revenue, operations, KPIs, risks)'),
    ('EXECUTIVE_REPORT.GENERATE', 'Executive Report Generate','Trigger generation of an executive report on-demand')
) AS v(code, name, desc_text)
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities ac WHERE ac.code = v.code);

-- Bind to ADMIN for all tenants (idempotent)
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), t.id, r.id, ac.id, NOW()
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'ADMIN'
JOIN access_capabilities ac ON ac.status = 'ACTIVE'
   AND ac.code IN (
       'GOVERNANCE_CONFIG.VIEW', 'GOVERNANCE_CONFIG.WRITE', 'GOVERNANCE_CONFIG.ADMIN',
       'EXECUTIVE_REPORT.VIEW', 'EXECUTIVE_REPORT.GENERATE'
   )
WHERE NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);
