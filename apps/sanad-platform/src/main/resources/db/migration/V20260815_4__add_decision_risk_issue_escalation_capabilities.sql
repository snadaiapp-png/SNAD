-- ============================================================
-- V20260815_4: Add EXECUTIVE_DECISIONS.*, RISK.*, ISSUE.*, ESCALATION.* capabilities
--
-- Phase B+C capabilities for the Senior Management Operating Layer:
--   EXECUTIVE_DECISIONS.VIEW / WRITE / APPROVE / ADMIN
--   RISK.VIEW / WRITE / ADMIN
--   ISSUE.VIEW / WRITE / ADMIN
--   ESCALATION.VIEW / WRITE / ADMIN
--
-- Bound to ADMIN for all tenants (V15 invariant preserved).
-- ============================================================

-- ============================================================
-- STEP 1: Add 13 new capabilities
-- ============================================================
INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), code, name, desc_text, 'ACTIVE', NOW(), NOW()
FROM (VALUES
    ('EXECUTIVE_DECISIONS.VIEW', 'Executive Decisions View', 'View executive decisions and their lifecycle'),
    ('EXECUTIVE_DECISIONS.WRITE', 'Executive Decisions Write', 'Create and update executive decisions'),
    ('EXECUTIVE_DECISIONS.APPROVE', 'Executive Decisions Approve', 'Approve or reject executive decisions'),
    ('EXECUTIVE_DECISIONS.ADMIN', 'Executive Decisions Admin', 'Full administrative access to decisions'),
    ('RISK.VIEW', 'Risk View', 'View risks and their assessments'),
    ('RISK.WRITE', 'Risk Write', 'Create and update risks'),
    ('RISK.ADMIN', 'Risk Admin', 'Full administrative access to risks'),
    ('ISSUE.VIEW', 'Issue View', 'View executive issues'),
    ('ISSUE.WRITE', 'Issue Write', 'Create and update issues'),
    ('ISSUE.ADMIN', 'Issue Admin', 'Full administrative access to issues'),
    ('ESCALATION.VIEW', 'Escalation View', 'View escalations'),
    ('ESCALATION.WRITE', 'Escalation Write', 'Create and update escalations'),
    ('ESCALATION.ADMIN', 'Escalation Admin', 'Full administrative access to escalations')
) AS v(code, name, desc_text)
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities ac WHERE ac.code = v.code);

-- ============================================================
-- STEP 2: Bind all 13 new capabilities to ALL existing ADMIN roles
-- ============================================================
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), t.id, r.id, ac.id, NOW()
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'ADMIN'
JOIN access_capabilities ac ON ac.status = 'ACTIVE'
   AND ac.code IN (
       'EXECUTIVE_DECISIONS.VIEW', 'EXECUTIVE_DECISIONS.WRITE',
       'EXECUTIVE_DECISIONS.APPROVE', 'EXECUTIVE_DECISIONS.ADMIN',
       'RISK.VIEW', 'RISK.WRITE', 'RISK.ADMIN',
       'ISSUE.VIEW', 'ISSUE.WRITE', 'ISSUE.ADMIN',
       'ESCALATION.VIEW', 'ESCALATION.WRITE', 'ESCALATION.ADMIN'
   )
WHERE NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);
