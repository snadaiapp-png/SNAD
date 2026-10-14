-- Forward-only RLS-aware revalidation of the canonical role capability matrix.
-- V20260820_6 enables FORCE RLS on role_template_bindings; V20260820_7's
-- global aggregation therefore cannot be trusted when executed without a tenant
-- scope. Keep the historical migration/checksum unchanged and re-run its exact
-- assertions once per tenant under that tenant's RLS context.
--
-- This migration is validation-only: it changes no role, capability, or policy data.
DO $$
DECLARE
    tenant_record RECORD;
    bad_count INTEGER;
    expected_count INTEGER;
    actual_count INTEGER;
BEGIN
    FOR tenant_record IN SELECT id FROM tenants ORDER BY id LOOP
        PERFORM set_config('app.tenant_id', tenant_record.id::text, true);
    -- CRM_SALES expected: 16 capabilities
    -- v12.1 fix: original v12 code had expected_count=17 but the canonical
    -- capability set defined by V20260820_2 (and @RequireCapability annotations)
    -- contains exactly 16 codes:
    --   CRM.ACCOUNT.READ+WRITE (2)
    --   CRM.CONTACT.READ+WRITE (2) → 4
    --   CRM.LEAD.READ+WRITE+CONVERT (3) → 7
    --   CRM.OPPORTUNITY.READ+WRITE (2) → 9
    --   CRM.ACTIVITY.READ+WRITE (2) → 11
    --   CRM.TASK.READ+WRITE (2) → 13
    --   CRM.NOTE.READ+WRITE (2) → 15
    --   CRM.TAG.READ (1) → 16
    -- Production evidence: bound SNAD_TEMPLATE CRM_SALES roles have exactly
    -- those 16 capabilities. The 17th expected was a typo.
    expected_count := 16;
    -- Check (a): no extra caps
    SELECT COUNT(*) INTO bad_count
    FROM roles r
    JOIN role_template_bindings b ON b.tenant_id = r.tenant_id AND b.role_id = r.id
    LEFT JOIN role_capabilities rc ON rc.tenant_id = r.tenant_id AND rc.role_id = r.id
    LEFT JOIN access_capabilities ac ON ac.id = rc.capability_id
    WHERE b.tenant_id = tenant_record.id
      AND b.template_key = 'CRM_SALES'
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
    WHERE b.tenant_id = tenant_record.id
      AND b.template_key = 'CRM_SALES'
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
    WHERE b.tenant_id = tenant_record.id
      AND b.template_key = 'HR_MANAGER'
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
    WHERE b.tenant_id = tenant_record.id
      AND b.template_key = 'HR_MANAGER'
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
    WHERE b.tenant_id = tenant_record.id
      AND b.template_key = 'ERP_PURCHASER'
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
    WHERE b.tenant_id = tenant_record.id
      AND b.template_key = 'ERP_PURCHASER'
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
    WHERE b.tenant_id = tenant_record.id
      AND b.template_key = 'ERP_APPROVER'
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
    WHERE b.tenant_id = tenant_record.id
      AND b.template_key = 'ERP_APPROVER'
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
    WHERE b.tenant_id = tenant_record.id
      AND b.template_key = 'FINANCE_USER'
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
    WHERE b.tenant_id = tenant_record.id
      AND b.template_key = 'FINANCE_USER'
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
    WHERE b.tenant_id = tenant_record.id
      AND b.template_key = 'FINANCE_APPROVER'
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
    WHERE b.tenant_id = tenant_record.id
      AND b.template_key = 'FINANCE_APPROVER'
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
    WHERE b.tenant_id = tenant_record.id
      AND b.template_key = 'STORE_MANAGER'
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
    WHERE b.tenant_id = tenant_record.id
      AND b.template_key = 'STORE_MANAGER'
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
    WHERE b.tenant_id = tenant_record.id
      AND b.template_key = 'WORKFLOW_APPROVER'
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
    WHERE b.tenant_id = tenant_record.id
      AND b.template_key = 'WORKFLOW_APPROVER'
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
    WHERE b.tenant_id = tenant_record.id
      AND b.template_key = 'EXECUTIVE_VIEWER'
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
    WHERE b.tenant_id = tenant_record.id
      AND b.template_key = 'EXECUTIVE_VIEWER'
      AND count_role_caps(r.tenant_id, r.id) != expected_count;
    IF bad_count > 0 THEN
        RAISE EXCEPTION 'RBAC_EXACT_MATRIX_9_OF_9=FAIL: EXECUTIVE_VIEWER has % roles with wrong cap count (expected %)', bad_count, expected_count;
    END IF;

    END LOOP;

    -- Restore the handler's reserved fail-closed scope for any subsequent SQL
    -- in the same migration transaction. This is not a tenant and grants no access.
    PERFORM set_config('app.tenant_id', '00000000-0000-0000-0000-000000000000', true);
END $$;
