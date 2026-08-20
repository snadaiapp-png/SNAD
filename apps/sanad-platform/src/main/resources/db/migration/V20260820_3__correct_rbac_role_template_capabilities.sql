-- ============================================================
-- V20260820_3: Corrective RBAC Role Template Capabilities
-- ============================================================
-- PROBLEM: V20260820_2 used capability codes that do not match
-- the canonical production capability vocabulary. Production
-- evidence shows ERP_APPROVER had ZERO capabilities because
-- codes like 'ERP.PO.APPROVE' / 'ERP.ITEM.READ' / 'ERP.READ'
-- do not exist in access_capabilities.
--
-- SOLUTION: This migration:
--   1. Adds an `is_system_managed` marker column to roles
--      (default false). Only roles created by SNAD migrations
--      are marked system-managed. Customer-managed roles are
--      NOT touched (RBAC_RECONCILIATION_NON_DESTRUCTIVE).
--   2. Marks the 9 canonical role templates as system-managed
--      (idempotent UPDATE — only roles whose code matches one
--      of the canonical codes).
--   3. Validates that every mandatory capability code exists in
--      access_capabilities BEFORE granting. If any is missing,
--      raise an EXCEPTION to abort the migration. This enforces
--      RBAC_TEMPLATE_CAPABILITY_CODES_VALID=PASS explicitly —
--      no silent skipping.
--   4. Grants the corrected capability matrix to each system-
--      managed role. Idempotent WHERE NOT EXISTS on
--      role_capabilities.
--
-- Corrected matrix (derived from @RequireCapability annotations
-- in the production source tree — see apps/sanad-platform/src/main/java):
--
--   CRM_SALES         → CRM.ACCOUNT.READ+WRITE, CRM.CONTACT.READ+WRITE,
--                       CRM.LEAD.READ+WRITE+CONVERT,
--                       CRM.OPPORTUNITY.READ+WRITE,
--                       CRM.ACTIVITY.READ+WRITE,
--                       CRM.TASK.READ+WRITE,
--                       CRM.NOTE.READ+WRITE, CRM.TAG.READ
--
--   HR_MANAGER        → HR.EMPLOYEE.READ+WRITE+ARCHIVE
--
--   ERP_PURCHASER     → ERP.VIEW, ERP.PROCUREMENT, ERP.WRITE
--                       (cannot APPROVE — SOD vs ERP_APPROVER)
--
--   ERP_APPROVER      → ERP.VIEW, ERP.APPROVE
--                       (cannot WRITE — SOD vs ERP_PURCHASER)
--
--   FINANCE_USER      → FINANCE.VIEW, FINANCE.WRITE
--                       (cannot APPROVE — SOD vs FINANCE_APPROVER)
--
--   FINANCE_APPROVER  → FINANCE.VIEW, FINANCE.APPROVE
--                       (cannot WRITE — SOD vs FINANCE_USER)
--
--   STORE_MANAGER     → ECOMMERCE.VIEW, ECOMMERCE.WRITE, ECOMMERCE.PUBLISH
--
--   WORKFLOW_APPROVER → WORKFLOW.VIEW, WORKFLOW.APPROVE
--                       (cannot WRITE — cannot create the approvals
--                        they must review)
--
--   EXECUTIVE_VIEWER  → EXECUTIVE_VIEW, EXECUTIVE_COMMAND_CENTER.VIEW,
--                       EXECUTIVE_MANAGEMENT.VIEW, EXECUTIVE_REPORT.VIEW
-- ============================================================

-- ============================================================
-- 1. Add is_system_managed marker to roles
-- ============================================================
ALTER TABLE roles
    ADD COLUMN IF NOT EXISTS is_system_managed BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN roles.is_system_managed IS
    'True for canonical role templates provisioned by SNAD migrations. System-managed roles are subject to strict capability-matrix enforcement. Customer-managed roles (false) are never destructively rewritten.';

-- ============================================================
-- 2. Mark the 9 canonical role templates as system-managed
-- ============================================================
UPDATE roles
SET is_system_managed = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE code IN (
    'CRM_SALES', 'HR_MANAGER',
    'ERP_PURCHASER', 'ERP_APPROVER',
    'FINANCE_USER', 'FINANCE_APPROVER',
    'STORE_MANAGER', 'WORKFLOW_APPROVER',
    'EXECUTIVE_VIEWER'
)
AND is_system_managed = FALSE;

-- ============================================================
-- 3. Validate that every mandatory capability exists.
--    If any is missing, raise an EXCEPTION and abort.
--    This enforces RBAC_TEMPLATE_CAPABILITY_CODES_VALID=PASS
--    explicitly — no silent skipping.
-- ============================================================
DO $$
DECLARE
    missing_code TEXT;
BEGIN
    SELECT code INTO missing_code
    FROM (
        VALUES
            -- CRM_SALES
            ('CRM.ACCOUNT.READ'), ('CRM.ACCOUNT.WRITE'),
            ('CRM.CONTACT.READ'), ('CRM.CONTACT.WRITE'),
            ('CRM.LEAD.READ'), ('CRM.LEAD.WRITE'), ('CRM.LEAD.CONVERT'),
            ('CRM.OPPORTUNITY.READ'), ('CRM.OPPORTUNITY.WRITE'),
            ('CRM.ACTIVITY.READ'), ('CRM.ACTIVITY.WRITE'),
            ('CRM.TASK.READ'), ('CRM.TASK.WRITE'),
            ('CRM.NOTE.READ'), ('CRM.NOTE.WRITE'),
            ('CRM.TAG.READ'),
            -- HR_MANAGER
            ('HR.EMPLOYEE.READ'), ('HR.EMPLOYEE.WRITE'), ('HR.EMPLOYEE.ARCHIVE'),
            -- ERP_PURCHASER
            ('ERP.VIEW'), ('ERP.PROCUREMENT'), ('ERP.WRITE'),
            -- ERP_APPROVER
            ('ERP.APPROVE'),
            -- FINANCE_USER
            ('FINANCE.VIEW'), ('FINANCE.WRITE'),
            -- FINANCE_APPROVER
            ('FINANCE.APPROVE'),
            -- STORE_MANAGER
            ('ECOMMERCE.VIEW'), ('ECOMMERCE.WRITE'), ('ECOMMERCE.PUBLISH'),
            -- WORKFLOW_APPROVER
            ('WORKFLOW.VIEW'), ('WORKFLOW.APPROVE'),
            -- EXECUTIVE_VIEWER
            ('EXECUTIVE_VIEW'), ('EXECUTIVE_COMMAND_CENTER.VIEW'),
            ('EXECUTIVE_MANAGEMENT.VIEW'), ('EXECUTIVE_REPORT.VIEW')
    ) AS v(code)
    WHERE NOT EXISTS (
        SELECT 1 FROM access_capabilities ac
        WHERE ac.code = v.code AND ac.status = 'ACTIVE'
    )
    LIMIT 1;

    IF missing_code IS NOT NULL THEN
        RAISE EXCEPTION
            'Mandatory capability code % is not present in access_capabilities. RBAC template provisioning cannot proceed — fix the capability seed migration first.',
            missing_code;
    END IF;
END $$;

-- ============================================================
-- 4. Grant CRM_SALES capabilities (idempotent)
-- ============================================================
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), t.id, r.id, ac.id, CURRENT_TIMESTAMP
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'CRM_SALES' AND r.is_system_managed = TRUE
JOIN access_capabilities ac ON ac.status = 'ACTIVE' AND ac.code IN (
    'CRM.ACCOUNT.READ', 'CRM.ACCOUNT.WRITE',
    'CRM.CONTACT.READ', 'CRM.CONTACT.WRITE',
    'CRM.LEAD.READ', 'CRM.LEAD.WRITE', 'CRM.LEAD.CONVERT',
    'CRM.OPPORTUNITY.READ', 'CRM.OPPORTUNITY.WRITE',
    'CRM.ACTIVITY.READ', 'CRM.ACTIVITY.WRITE',
    'CRM.TASK.READ', 'CRM.TASK.WRITE',
    'CRM.NOTE.READ', 'CRM.NOTE.WRITE',
    'CRM.TAG.READ'
)
WHERE t.status = 'ACTIVE'
AND NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);

-- ============================================================
-- 5. Grant HR_MANAGER capabilities
-- ============================================================
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), t.id, r.id, ac.id, CURRENT_TIMESTAMP
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'HR_MANAGER' AND r.is_system_managed = TRUE
JOIN access_capabilities ac ON ac.status = 'ACTIVE' AND ac.code IN (
    'HR.EMPLOYEE.READ', 'HR.EMPLOYEE.WRITE', 'HR.EMPLOYEE.ARCHIVE'
)
WHERE t.status = 'ACTIVE'
AND NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);

-- ============================================================
-- 6. Grant ERP_PURCHASER capabilities (no APPROVE — SOD)
-- ============================================================
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), t.id, r.id, ac.id, CURRENT_TIMESTAMP
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'ERP_PURCHASER' AND r.is_system_managed = TRUE
JOIN access_capabilities ac ON ac.status = 'ACTIVE' AND ac.code IN (
    'ERP.VIEW', 'ERP.PROCUREMENT', 'ERP.WRITE'
)
WHERE t.status = 'ACTIVE'
AND NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);

-- ============================================================
-- 7. Grant ERP_APPROVER capabilities (no WRITE — SOD)
-- ============================================================
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), t.id, r.id, ac.id, CURRENT_TIMESTAMP
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'ERP_APPROVER' AND r.is_system_managed = TRUE
JOIN access_capabilities ac ON ac.status = 'ACTIVE' AND ac.code IN (
    'ERP.VIEW', 'ERP.APPROVE'
)
WHERE t.status = 'ACTIVE'
AND NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);

-- ============================================================
-- 8. Grant FINANCE_USER capabilities (no APPROVE — SOD)
-- ============================================================
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), t.id, r.id, ac.id, CURRENT_TIMESTAMP
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'FINANCE_USER' AND r.is_system_managed = TRUE
JOIN access_capabilities ac ON ac.status = 'ACTIVE' AND ac.code IN (
    'FINANCE.VIEW', 'FINANCE.WRITE'
)
WHERE t.status = 'ACTIVE'
AND NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);

-- ============================================================
-- 9. Grant FINANCE_APPROVER capabilities (no WRITE — SOD)
-- ============================================================
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), t.id, r.id, ac.id, CURRENT_TIMESTAMP
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'FINANCE_APPROVER' AND r.is_system_managed = TRUE
JOIN access_capabilities ac ON ac.status = 'ACTIVE' AND ac.code IN (
    'FINANCE.VIEW', 'FINANCE.APPROVE'
)
WHERE t.status = 'ACTIVE'
AND NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);

-- ============================================================
-- 10. Grant STORE_MANAGER capabilities
-- ============================================================
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), t.id, r.id, ac.id, CURRENT_TIMESTAMP
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'STORE_MANAGER' AND r.is_system_managed = TRUE
JOIN access_capabilities ac ON ac.status = 'ACTIVE' AND ac.code IN (
    'ECOMMERCE.VIEW', 'ECOMMERCE.WRITE', 'ECOMMERCE.PUBLISH'
)
WHERE t.status = 'ACTIVE'
AND NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);

-- ============================================================
-- 11. Grant WORKFLOW_APPROVER capabilities (no WRITE — SOD)
-- ============================================================
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), t.id, r.id, ac.id, CURRENT_TIMESTAMP
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'WORKFLOW_APPROVER' AND r.is_system_managed = TRUE
JOIN access_capabilities ac ON ac.status = 'ACTIVE' AND ac.code IN (
    'WORKFLOW.VIEW', 'WORKFLOW.APPROVE'
)
WHERE t.status = 'ACTIVE'
AND NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);

-- ============================================================
-- 12. Grant EXECUTIVE_VIEWER capabilities
-- ============================================================
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), t.id, r.id, ac.id, CURRENT_TIMESTAMP
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'EXECUTIVE_VIEWER' AND r.is_system_managed = TRUE
JOIN access_capabilities ac ON ac.status = 'ACTIVE' AND ac.code IN (
    'EXECUTIVE_VIEW',
    'EXECUTIVE_COMMAND_CENTER.VIEW',
    'EXECUTIVE_MANAGEMENT.VIEW',
    'EXECUTIVE_REPORT.VIEW'
)
WHERE t.status = 'ACTIVE'
AND NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);
