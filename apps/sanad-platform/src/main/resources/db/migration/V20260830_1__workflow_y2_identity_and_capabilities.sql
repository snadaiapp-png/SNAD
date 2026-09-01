-- ============================================================
-- V20260830_1: Workflow Y2 identity bridge and capabilities
--
-- Task 2 scope:
--   * Enforce optional one-to-one Employee <-> User linkage per tenant.
--
-- Task 3 scope (Wave 0):
--   * Seed the fine-grained Y2 Workflow capability catalog additively.
--   * Bind every new capability to ADMIN for all tenants, preserving the
--     platform invariant "ADMIN gets all active capabilities"
--     (same tenant/role join pattern as V20260815_11).
--   * WORKFLOW.VIEW / WORKFLOW.WRITE / WORKFLOW.ADMIN / WORKFLOW.APPROVE
--     remain untouched for backward compatibility (design decision AB3/Z3).
-- ============================================================

CREATE UNIQUE INDEX IF NOT EXISTS uq_hr_employees_tenant_user
    ON hr_employees (tenant_id, user_id)
    WHERE user_id IS NOT NULL;

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), code, name, desc_text, 'ACTIVE', NOW(), NOW()
FROM (VALUES
    ('WORKFLOW.DESIGN', 'Workflow Design', 'Create and edit draft workflow definitions'),
    ('WORKFLOW.VALIDATE', 'Workflow Validate', 'Validate and simulate workflow drafts'),
    ('WORKFLOW.PUBLISH', 'Workflow Publish', 'Publish immutable workflow versions'),
    ('WORKFLOW.START', 'Workflow Start', 'Start workflow instances'),
    ('WORKFLOW.TASK_EXECUTE', 'Workflow Task Execute', 'Claim and complete workflow tasks'),
    ('WORKFLOW.REASSIGN', 'Workflow Reassign', 'Reassign workflow work items'),
    ('WORKFLOW.DELEGATE', 'Workflow Delegate', 'Manage workflow delegation'),
    ('WORKFLOW.CANCEL', 'Workflow Cancel', 'Cancel workflow instances'),
    ('WORKFLOW.INCIDENT_MANAGE', 'Workflow Incident Manage', 'Acknowledge and resolve workflow incidents'),
    ('WORKFLOW.MONITOR', 'Workflow Monitor', 'View operational workflow monitoring'),
    ('WORKFLOW.AUDIT_VIEW', 'Workflow Audit View', 'Read workflow business audit'),
    ('WORKFLOW.BREAK_GLASS', 'Workflow Break Glass', 'Execute audited emergency workflow overrides'),
    ('WORKFLOW.SELF_APPROVAL_OVERRIDE', 'Workflow Self Approval Override', 'Permit explicitly configured exceptional self approval')
) AS v(code, name, desc_text)
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities ac WHERE ac.code = v.code);

-- Compatibility mapping: grant every new Y2 capability to ADMIN in all
-- tenants so existing administrators keep full workflow authority.
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), t.id, r.id, ac.id, NOW()
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'ADMIN'
JOIN access_capabilities ac ON ac.status = 'ACTIVE'
   AND ac.code IN (
       'WORKFLOW.DESIGN', 'WORKFLOW.VALIDATE', 'WORKFLOW.PUBLISH',
       'WORKFLOW.START', 'WORKFLOW.TASK_EXECUTE', 'WORKFLOW.REASSIGN',
       'WORKFLOW.DELEGATE', 'WORKFLOW.CANCEL', 'WORKFLOW.INCIDENT_MANAGE',
       'WORKFLOW.MONITOR', 'WORKFLOW.AUDIT_VIEW', 'WORKFLOW.BREAK_GLASS',
       'WORKFLOW.SELF_APPROVAL_OVERRIDE'
   )
WHERE NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);
