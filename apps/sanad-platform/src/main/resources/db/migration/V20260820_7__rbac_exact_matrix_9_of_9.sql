-- ============================================================
-- V20260820_7: RBAC exact matrix validation for ALL 9 templates
-- ============================================================
-- V20260820_5 validated HR_MANAGER only. This migration validates the
-- EXACT capability matrix for all 9 canonical SNAD role templates,
-- scoped to roles with an authoritative role_template_bindings row.
--
-- For each template, two checks per role:
--   (a) NO extra capabilities: every capability the role has must be in the expected set.
--   (b) NO missing capabilities: the role must have exactly the expected count.
--
-- Customer roles (no binding) are NOT validated — their capability set
-- is customer's choice.
--
-- The migration RAISEs an EXCEPTION on the first template that fails,
-- with a message that names the offending template and the mismatch.
-- ============================================================

-- Helper: count capabilities on a system-managed role
CREATE OR REPLACE FUNCTION count_role_caps(tenant_uuid UUID, role_uuid UUID)
RETURNS INTEGER AS $$
    SELECT COUNT(*) FROM role_capabilities rc WHERE rc.tenant_id = tenant_uuid AND rc.role_id = role_uuid;
$$ LANGUAGE SQL STABLE;

DO $$
DECLARE
    bad_count INTEGER;
    expected_count INTEGER;
    actual_count INTEGER;
BEGIN
    -- CRM_SALES expected: 17 capabilities
    expected_count := 17;
    -- Check (a): no extra caps
    SELECT COUNT(*) INTO bad_count
    FROM roles r
    JOIN role_template_bindings b ON b.tenant_id = r.tenant_id AND b.role_id = r.id
    LEFT JOIN role_capabilities rc ON rc.tenant_id = r.tenant_id AND rc.role_id = r.id
    LEFT JOIN access_capabilities ac ON ac.id = rc.capability_id
    WHERE b.template_key = 'CRM_SALES'
      AND rc.capability_id IS NOT NULL
      AND (ac.code IS NULL OR ac.code NOT IN (
          'CRM.ACCOUNT.READ', 'CRM.ACCOUNT.WRITE',
          'CRM.CONTACT.READ', 'CRM.CONTACT.WRITE',
          'CRM.LEAD.READ', 'CRM.LEAD.WRITE', 'CRM.LEAD.CONVERT',
          'CRM.OPPORTUNITY.READ', 'CRM.OPPORTUNITY.WRITE',
          'CRM.ACTIVITY.READ', 'CRM.ACTIVITY.WRITE',
          'CRM.TASK.READ', 'CRM.TASK.WRITE',
          'CRM.NOTE.READ', 'CRM.NOTE.WRITE',
          'CRM.TAG.READ'
      ));
    IF bad_count > 0 THEN
        RAISE EXCEPTION 'RBAC_EXACT_MATRIX_9_OF_9=FAIL: CRM_SALES has % rows with EXTRA capabilities', bad_count;
    END IF;
    -- Check (b): exact count
    SELECT COUNT(*) INTO bad_count
    FROM roles r
    JOIN role_template_bindings b ON b.tenant_id = r.tenant_id AND b.role_id = r.id
    WHERE b.template_key = 'CRM_SALES'
      AND count_role_caps(r.tenant_id, r.id) != expected_count;
    IF bad_count > 0 THEN
        RAISE EXCEPTION 'RBAC_EXACT_MATRIX_9_OF_9=FAIL: CRM_SALES has % roles with wrong cap count (expected %)', bad_count, expected_count;
    END IF;

    -- HR_MANAGER expected: 3 capabilities
    expected_count := 3;
    SELECT COUNT(*) INTO bad_count
    FROM roles r
    JOIN role_template_bindings b ON b.tenant_id = r.tenant_id AND b.role_id = r.id
    LEFT JOIN role_capabilities rc ON rc.tenant_id = r.tenant_id AND rc.role_id = r.id
    LEFT JOIN access_capabilities ac ON ac.id = rc.capability_id
    WHERE b.template_key = 'HR_MANAGER'
      AND rc.capability_id IS NOT NULL
      AND (ac.code IS NULL OR ac.code NOT IN (
          'HR.EMPLOYEE.READ', 'HR.EMPLOYEE.WRITE', 'HR.EMPLOYEE.ARCHIVE'
      ));
    IF bad_count > 0 THEN
        RAISE EXCEPTION 'RBAC_EXACT_MATRIX_9_OF_9=FAIL: HR_MANAGER has % rows with EXTRA capabilities', bad_count;
    END IF;
    SELECT COUNT(*) INTO bad_count
    FROM roles r
    JOIN role_template_bindings b ON b.tenant_id = r.tenant_id AND b.role_id = r.id
    WHERE b.template_key = 'HR_MANAGER'
      AND count_role_caps(r.tenant_id, r.id) != expected_count;
    IF bad_count > 0 THEN
        RAISE EXCEPTION 'RBAC_EXACT_MATRIX_9_OF_9=FAIL: HR_MANAGER has % roles with wrong cap count (expected %)', bad_count, expected_count;
    END IF;

    -- ERP_PURCHASER expected: 3 capabilities
    expected_count := 3;
    SELECT COUNT(*) INTO bad_count
    FROM roles r
    JOIN role_template_bindings b ON b.tenant_id = r.tenant_id AND b.role_id = r.id
    LEFT JOIN role_capabilities rc ON rc.tenant_id = r.tenant_id AND rc.role_id = r.id
    LEFT JOIN access_capabilities ac ON ac.id = rc.capability_id
    WHERE b.template_key = 'ERP_PURCHASER'
      AND rc.capability_id IS NOT NULL
      AND (ac.code IS NULL OR ac.code NOT IN (
          'ERP.VIEW', 'ERP.PROCUREMENT', 'ERP.WRITE'
      ));
    IF bad_count > 0 THEN
        RAISE EXCEPTION 'RBAC_EXACT_MATRIX_9_OF_9=FAIL: ERP_PURCHASER has % rows with EXTRA capabilities', bad_count;
    END IF;
    SELECT COUNT(*) INTO bad_count
    FROM roles r
    JOIN role_template_bindings b ON b.tenant_id = r.tenant_id AND b.role_id = r.id
    WHERE b.template_key = 'ERP_PURCHASER'
      AND count_role_caps(r.tenant_id, r.id) != expected_count;
    IF bad_count > 0 THEN
        RAISE EXCEPTION 'RBAC_EXACT_MATRIX_9_OF_9=FAIL: ERP_PURCHASER has % roles with wrong cap count (expected %)', bad_count, expected_count;
    END IF;

    -- ERP_APPROVER expected: 2 capabilities
    expected_count := 2;
    SELECT COUNT(*) INTO bad_count
    FROM roles r
    JOIN role_template_bindings b ON b.tenant_id = r.tenant_id AND b.role_id = r.id
    LEFT JOIN role_capabilities rc ON rc.tenant_id = r.tenant_id AND rc.role_id = r.id
    LEFT JOIN access_capabilities ac ON ac.id = rc.capability_id
    WHERE b.template_key = 'ERP_APPROVER'
      AND rc.capability_id IS NOT NULL
      AND (ac.code IS NULL OR ac.code NOT IN (
          'ERP.VIEW', 'ERP.APPROVE'
      ));
    IF bad_count > 0 THEN
        RAISE EXCEPTION 'RBAC_EXACT_MATRIX_9_OF_9=FAIL: ERP_APPROVER has % rows with EXTRA capabilities', bad_count;
    END IF;
    SELECT COUNT(*) INTO bad_count
    FROM roles r
    JOIN role_template_bindings b ON b.tenant_id = r.tenant_id AND b.role_id = r.id
    WHERE b.template_key = 'ERP_APPROVER'
      AND count_role_caps(r.tenant_id, r.id) != expected_count;
    IF bad_count > 0 THEN
        RAISE EXCEPTION 'RBAC_EXACT_MATRIX_9_OF_9=FAIL: ERP_APPROVER has % roles with wrong cap count (expected %)', bad_count, expected_count;
    END IF;

    -- FINANCE_USER expected: 2 capabilities
    expected_count := 2;
    SELECT COUNT(*) INTO bad_count
    FROM roles r
    JOIN role_template_bindings b ON b.tenant_id = r.tenant_id AND b.role_id = r.id
    LEFT JOIN role_capabilities rc ON rc.tenant_id = r.tenant_id AND rc.role_id = r.id
    LEFT JOIN access_capabilities ac ON ac.id = rc.capability_id
    WHERE b.template_key = 'FINANCE_USER'
      AND rc.capability_id IS NOT NULL
      AND (ac.code IS NULL OR ac.code NOT IN (
          'FINANCE.VIEW', 'FINANCE.WRITE'
      ));
    IF bad_count > 0 THEN
        RAISE EXCEPTION 'RBAC_EXACT_MATRIX_9_OF_9=FAIL: FINANCE_USER has % rows with EXTRA capabilities', bad_count;
    END IF;
    SELECT COUNT(*) INTO bad_count
    FROM roles r
    JOIN role_template_bindings b ON b.tenant_id = r.tenant_id AND b.role_id = r.id
    WHERE b.template_key = 'FINANCE_USER'
      AND count_role_caps(r.tenant_id, r.id) != expected_count;
    IF bad_count > 0 THEN
        RAISE EXCEPTION 'RBAC_EXACT_MATRIX_9_OF_9=FAIL: FINANCE_USER has % roles with wrong cap count (expected %)', bad_count, expected_count;
    END IF;

    -- FINANCE_APPROVER expected: 2 capabilities
    expected_count := 2;
    SELECT COUNT(*) INTO bad_count
    FROM roles r
    JOIN role_template_bindings b ON b.tenant_id = r.tenant_id AND b.role_id = r.id
    LEFT JOIN role_capabilities rc ON rc.tenant_id = r.tenant_id AND rc.role_id = r.id
    LEFT JOIN access_capabilities ac ON ac.id = rc.capability_id
    WHERE b.template_key = 'FINANCE_APPROVER'
      AND rc.capability_id IS NOT NULL
      AND (ac.code IS NULL OR ac.code NOT IN (
          'FINANCE.VIEW', 'FINANCE.APPROVE'
      ));
    IF bad_count > 0 THEN
        RAISE EXCEPTION 'RBAC_EXACT_MATRIX_9_OF_9=FAIL: FINANCE_APPROVER has % rows with EXTRA capabilities', bad_count;
    END IF;
    SELECT COUNT(*) INTO bad_count
    FROM roles r
    JOIN role_template_bindings b ON b.tenant_id = r.tenant_id AND b.role_id = r.id
    WHERE b.template_key = 'FINANCE_APPROVER'
      AND count_role_caps(r.tenant_id, r.id) != expected_count;
    IF bad_count > 0 THEN
        RAISE EXCEPTION 'RBAC_EXACT_MATRIX_9_OF_9=FAIL: FINANCE_APPROVER has % roles with wrong cap count (expected %)', bad_count, expected_count;
    END IF;

    -- STORE_MANAGER expected: 3 capabilities
    expected_count := 3;
    SELECT COUNT(*) INTO bad_count
    FROM roles r
    JOIN role_template_bindings b ON b.tenant_id = r.tenant_id AND b.role_id = r.id
    LEFT JOIN role_capabilities rc ON rc.tenant_id = r.tenant_id AND rc.role_id = r.id
    LEFT JOIN access_capabilities ac ON ac.id = rc.capability_id
    WHERE b.template_key = 'STORE_MANAGER'
      AND rc.capability_id IS NOT NULL
      AND (ac.code IS NULL OR ac.code NOT IN (
          'ECOMMERCE.VIEW', 'ECOMMERCE.WRITE', 'ECOMMERCE.PUBLISH'
      ));
    IF bad_count > 0 THEN
        RAISE EXCEPTION 'RBAC_EXACT_MATRIX_9_OF_9=FAIL: STORE_MANAGER has % rows with EXTRA capabilities', bad_count;
    END IF;
    SELECT COUNT(*) INTO bad_count
    FROM roles r
    JOIN role_template_bindings b ON b.tenant_id = r.tenant_id AND b.role_id = r.id
    WHERE b.template_key = 'STORE_MANAGER'
      AND count_role_caps(r.tenant_id, r.id) != expected_count;
    IF bad_count > 0 THEN
        RAISE EXCEPTION 'RBAC_EXACT_MATRIX_9_OF_9=FAIL: STORE_MANAGER has % roles with wrong cap count (expected %)', bad_count, expected_count;
    END IF;

    -- WORKFLOW_APPROVER expected: 2 capabilities
    expected_count := 2;
    SELECT COUNT(*) INTO bad_count
    FROM roles r
    JOIN role_template_bindings b ON b.tenant_id = r.tenant_id AND b.role_id = r.id
    LEFT JOIN role_capabilities rc ON rc.tenant_id = r.tenant_id AND rc.role_id = r.id
    LEFT JOIN access_capabilities ac ON ac.id = rc.capability_id
    WHERE b.template_key = 'WORKFLOW_APPROVER'
      AND rc.capability_id IS NOT NULL
      AND (ac.code IS NULL OR ac.code NOT IN (
          'WORKFLOW.VIEW', 'WORKFLOW.APPROVE'
      ));
    IF bad_count > 0 THEN
        RAISE EXCEPTION 'RBAC_EXACT_MATRIX_9_OF_9=FAIL: WORKFLOW_APPROVER has % rows with EXTRA capabilities', bad_count;
    END IF;
    SELECT COUNT(*) INTO bad_count
    FROM roles r
    JOIN role_template_bindings b ON b.tenant_id = r.tenant_id AND b.role_id = r.id
    WHERE b.template_key = 'WORKFLOW_APPROVER'
      AND count_role_caps(r.tenant_id, r.id) != expected_count;
    IF bad_count > 0 THEN
        RAISE EXCEPTION 'RBAC_EXACT_MATRIX_9_OF_9=FAIL: WORKFLOW_APPROVER has % roles with wrong cap count (expected %)', bad_count, expected_count;
    END IF;

    -- EXECUTIVE_VIEWER expected: 4 capabilities
    expected_count := 4;
    SELECT COUNT(*) INTO bad_count
    FROM roles r
    JOIN role_template_bindings b ON b.tenant_id = r.tenant_id AND b.role_id = r.id
    LEFT JOIN role_capabilities rc ON rc.tenant_id = r.tenant_id AND rc.role_id = r.id
    LEFT JOIN access_capabilities ac ON ac.id = rc.capability_id
    WHERE b.template_key = 'EXECUTIVE_VIEWER'
      AND rc.capability_id IS NOT NULL
      AND (ac.code IS NULL OR ac.code NOT IN (
          'EXECUTIVE_VIEW',
          'EXECUTIVE_COMMAND_CENTER.VIEW',
          'EXECUTIVE_MANAGEMENT.VIEW',
          'EXECUTIVE_REPORT.VIEW'
      ));
    IF bad_count > 0 THEN
        RAISE EXCEPTION 'RBAC_EXACT_MATRIX_9_OF_9=FAIL: EXECUTIVE_VIEWER has % rows with EXTRA capabilities', bad_count;
    END IF;
    SELECT COUNT(*) INTO bad_count
    FROM roles r
    JOIN role_template_bindings b ON b.tenant_id = r.tenant_id AND b.role_id = r.id
    WHERE b.template_key = 'EXECUTIVE_VIEWER'
      AND count_role_caps(r.tenant_id, r.id) != expected_count;
    IF bad_count > 0 THEN
        RAISE EXCEPTION 'RBAC_EXACT_MATRIX_9_OF_9=FAIL: EXECUTIVE_VIEWER has % roles with wrong cap count (expected %)', bad_count, expected_count;
    END IF;

END $$;
