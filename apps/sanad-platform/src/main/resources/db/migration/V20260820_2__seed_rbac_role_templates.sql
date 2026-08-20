-- ============================================================
-- V20260820_2: RBAC Source-Controlled Role Templates
-- ============================================================
-- Adds canonical role templates required for production RBAC
-- reproducibility. These roles are seeded for every active tenant
-- (idempotent) so the production role matrix is fully reproducible
-- from source.
--
-- Role matrix (least-privilege per domain contract):
--   CRM_SALES           → core CRM READ + WRITE + lead convert
--   HR_MANAGER          → HR READ + WRITE (HR-001/002/003)
--   ERP_PURCHASER       → ERP READ + WRITE for purchase orders + items
--   ERP_APPROVER        → ERP.READ + ERP.PO.APPROVE
--   FINANCE_USER        → FINANCE.READ + FINANCE.WRITE (no APPROVE)
--   FINANCE_APPROVER    → FINANCE.READ + FINANCE.APPROVE (SOD vs FINANCE_USER)
--   STORE_MANAGER       → STORES.READ + WRITE + COMMERCE.READ + WRITE
--   WORKFLOW_APPROVER   → WORKFLOW.VIEW + WORKFLOW.APPROVE (no WRITE)
--   EXECUTIVE_VIEWER    → EXECUTIVE.DASHBOARD.VIEW + EXECUTIVE_COMMAND_CENTER.VIEW
--
-- All inserts are idempotent via WHERE NOT EXISTS.
-- Capabilities are seeded only for those that already exist in
-- access_capabilities; missing capabilities are silently skipped
-- so the migration remains safe to apply on environments where
-- some capability codes have not yet been provisioned.
-- ============================================================

-- ============================================================
-- 1. Seed the role templates for every active tenant
-- ============================================================
INSERT INTO roles (id, tenant_id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), t.id, v.code, v.name, v.desc_text, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM tenants t
CROSS JOIN (VALUES
    ('CRM_SALES',         'CRM Sales',           'Sales representative: core CRM read + write + lead convert'),
    ('HR_MANAGER',        'HR Manager',           'HR manager: full HR module read + write'),
    ('ERP_PURCHASER',     'ERP Purchaser',        'ERP purchaser: create and submit purchase orders'),
    ('ERP_APPROVER',      'ERP Approver',         'ERP approver: approve purchase orders (SOD vs purchaser)'),
    ('FINANCE_USER',      'Finance User',         'Finance user: create invoices and payments; cannot approve settlement'),
    ('FINANCE_APPROVER',  'Finance Approver',    'Finance approver: approve settlement (SOD vs FINANCE_USER)'),
    ('STORE_MANAGER',     'Store Manager',        'Stores manager: manage store + commerce catalog + orders'),
    ('WORKFLOW_APPROVER', 'Workflow Approver',    'Workflow approver: approve workflow requests only'),
    ('EXECUTIVE_VIEWER',  'Executive Viewer',     'Executive viewer: read-only access to dashboards + command center')
) AS v(code, name, desc_text)
WHERE t.status = 'ACTIVE'
AND NOT EXISTS (
    SELECT 1 FROM roles r WHERE r.tenant_id = t.id AND r.code = v.code
);

-- ============================================================
-- 2. CRM_SALES: core CRM READ + WRITE + lead convert
-- ============================================================
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), t.id, r.id, ac.id, CURRENT_TIMESTAMP
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'CRM_SALES'
JOIN access_capabilities ac ON ac.code IN (
    'CRM.ACCOUNT.READ', 'CRM.ACCOUNT.WRITE',
    'CRM.CONTACT.READ', 'CRM.CONTACT.WRITE',
    'CRM.LEAD.READ', 'CRM.LEAD.WRITE', 'CRM.LEAD.CONVERT',
    'CRM.OPPORTUNITY.READ', 'CRM.OPPORTUNITY.WRITE',
    'CRM.ACTIVITY.READ', 'CRM.ACTIVITY.WRITE',
    'CRM.TASK.READ', 'CRM.TASK.WRITE',
    'CRM.NOTE.READ', 'CRM.NOTE.WRITE',
    'CRM.TAG.READ'
) AND ac.status = 'ACTIVE'
WHERE t.status = 'ACTIVE'
AND NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);

-- ============================================================
-- 3. HR_MANAGER: full HR read + write
-- ============================================================
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), t.id, r.id, ac.id, CURRENT_TIMESTAMP
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'HR_MANAGER'
JOIN access_capabilities ac ON ac.code IN (
    'HR.READ', 'HR.WRITE',
    'HR.EMPLOYEE.READ', 'HR.EMPLOYEE.WRITE',
    'HR.DEPARTMENT.READ', 'HR.DEPARTMENT.WRITE',
    'HR.POSITION.READ', 'HR.POSITION.WRITE'
) AND ac.status = 'ACTIVE'
WHERE t.status = 'ACTIVE'
AND NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);

-- ============================================================
-- 4. ERP_PURCHASER: ERP read + write for items + purchase orders
-- ============================================================
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), t.id, r.id, ac.id, CURRENT_TIMESTAMP
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'ERP_PURCHASER'
JOIN access_capabilities ac ON ac.code IN (
    'ERP.READ', 'ERP.WRITE',
    'ERP.ITEM.READ', 'ERP.ITEM.WRITE',
    'ERP.SUPPLIER.READ', 'ERP.SUPPLIER.WRITE',
    'ERP.WAREHOUSE.READ',
    'ERP.PO.READ', 'ERP.PO.WRITE',
    'ERP.PO.SUBMIT'
) AND ac.status = 'ACTIVE'
WHERE t.status = 'ACTIVE'
AND NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);

-- ============================================================
-- 5. ERP_APPROVER: ERP read + PO approve (SOD vs purchaser — no WRITE)
-- ============================================================
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), t.id, r.id, ac.id, CURRENT_TIMESTAMP
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'ERP_APPROVER'
JOIN access_capabilities ac ON ac.code IN (
    'ERP.READ',
    'ERP.ITEM.READ',
    'ERP.SUPPLIER.READ',
    'ERP.WAREHOUSE.READ',
    'ERP.PO.READ',
    'ERP.PO.APPROVE'
) AND ac.status = 'ACTIVE'
WHERE t.status = 'ACTIVE'
AND NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);

-- ============================================================
-- 6. FINANCE_USER: Finance read + write (no APPROVE)
-- ============================================================
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), t.id, r.id, ac.id, CURRENT_TIMESTAMP
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'FINANCE_USER'
JOIN access_capabilities ac ON ac.code IN (
    'FINANCE.READ', 'FINANCE.WRITE',
    'FINANCE.ACCOUNT.READ', 'FINANCE.ACCOUNT.WRITE',
    'FINANCE.INVOICE.READ', 'FINANCE.INVOICE.WRITE',
    'FINANCE.PAYMENT.READ', 'FINANCE.PAYMENT.WRITE'
) AND ac.status = 'ACTIVE'
WHERE t.status = 'ACTIVE'
AND NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);

-- ============================================================
-- 7. FINANCE_APPROVER: Finance read + APPROVE only (SOD vs FINANCE_USER)
-- ============================================================
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), t.id, r.id, ac.id, CURRENT_TIMESTAMP
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'FINANCE_APPROVER'
JOIN access_capabilities ac ON ac.code IN (
    'FINANCE.READ',
    'FINANCE.ACCOUNT.READ',
    'FINANCE.INVOICE.READ',
    'FINANCE.PAYMENT.READ',
    'FINANCE.APPROVE'
) AND ac.status = 'ACTIVE'
WHERE t.status = 'ACTIVE'
AND NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);

-- ============================================================
-- 8. STORE_MANAGER: stores + commerce
-- ============================================================
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), t.id, r.id, ac.id, CURRENT_TIMESTAMP
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'STORE_MANAGER'
JOIN access_capabilities ac ON ac.code IN (
    'STORES.READ', 'STORES.WRITE',
    'COMMERCE.READ', 'COMMERCE.WRITE',
    'COMMERCE.STORE.READ', 'COMMERCE.STORE.WRITE',
    'COMMERCE.PRODUCT.READ', 'COMMERCE.PRODUCT.WRITE',
    'COMMERCE.CART.READ', 'COMMERCE.CART.WRITE',
    'COMMERCE.ORDER.READ', 'COMMERCE.ORDER.WRITE',
    'COMMERCE.PRICE.READ', 'COMMERCE.PRICE.WRITE'
) AND ac.status = 'ACTIVE'
WHERE t.status = 'ACTIVE'
AND NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);

-- ============================================================
-- 9. WORKFLOW_APPROVER: workflow view + approve only (no WRITE)
-- ============================================================
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), t.id, r.id, ac.id, CURRENT_TIMESTAMP
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'WORKFLOW_APPROVER'
JOIN access_capabilities ac ON ac.code IN (
    'WORKFLOW.VIEW', 'WORKFLOW.APPROVE'
) AND ac.status = 'ACTIVE'
WHERE t.status = 'ACTIVE'
AND NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);

-- ============================================================
-- 10. EXECUTIVE_VIEWER: dashboard + command center read-only
-- ============================================================
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), t.id, r.id, ac.id, CURRENT_TIMESTAMP
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'EXECUTIVE_VIEWER'
JOIN access_capabilities ac ON ac.code IN (
    'EXECUTIVE.DASHBOARD.VIEW',
    'EXECUTIVE_COMMAND_CENTER.VIEW',
    'EXECUTIVE.REPORTS.VIEW',
    'EXECUTIVE.KPI.VIEW',
    'EXECUTIVE.REVENUE.VIEW',
    'EXECUTIVE.OPERATIONS.VIEW'
) AND ac.status = 'ACTIVE'
WHERE t.status = 'ACTIVE'
AND NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);
