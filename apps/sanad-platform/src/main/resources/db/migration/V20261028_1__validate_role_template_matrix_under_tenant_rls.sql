-- Forward-only RLS-aware revalidation of the canonical role capability matrix.
--
-- Mandate:  SANAD-G1-FULL-CLOSURE-EXEC-2026-10-28 (defect D1)
-- Base:     main@0616ec7c2581411fed34ad727ab5e3964f2258b8
-- Origin:   re-derived from PR #1409 (V20261014_1) onto the current main tree,
--           because PR #1409 as a whole is diverged and not mergeable.
--
-- V20260820_6 enables FORCE ROW LEVEL SECURITY on role_template_bindings with a
-- fail-closed policy (tenant_id = current_setting('app.tenant_id', true)). The
-- global aggregation in V20260820_7 therefore executes with an unset GUC, sees
-- an EMPTY binding set, and passes trivially — the exact-matrix gate has been
-- blind since V20260820_6 (proven by G1 PG Direct evidence, 2026-10-28).
--
-- This migration re-runs V20260820_7's exact assertions once per tenant under
-- that tenant's RLS context, against the canonical matrices defined by
-- V20260820_5/V20260820_2 (CRM_SALES=16, HR_MANAGER=3, ERP_PURCHASER=3,
-- ERP_APPROVER=2, FINANCE_USER=2, FINANCE_APPROVER=2, STORE_MANAGER=3,
-- WORKFLOW_APPROVER=2). EXECUTIVE_VIEWER is asserted against its CURRENT
-- canonical contract (4 executive capabilities + the '%.read' mirror granted
-- by V20260820_2/V20260830_2 step 4b) rather than the stale 4-cap matrix,
-- which V20260830_2 superseded. The historical migration and its
-- Flyway checksum remain untouched.
--
-- Validation-only: changes no role, capability, or policy data. Fails closed
-- (RAISE EXCEPTION) on any matrix deviation so Flyway aborts the run.
--
-- The reserved control-plane tenant (00000000-0000-0000-0000-000000000001,
-- see V20261001_6 / application-prod.yml sanad.control-plane) is EXCLUDED:
-- it is provisioned with the platform control-plane reader matrix (billing,
-- subscription, audit, usage read capabilities — V20260820_2/3), not with the
-- customer role-template matrices asserted here. It is a reserved scope, not
-- a customer tenant.
DO $$
DECLARE
    tenant_record RECORD;
    bad_count INTEGER;
    expected_count INTEGER;
    actual_count INTEGER;
    control_plane_tenant CONSTANT UUID := '00000000-0000-0000-0000-000000000001'::uuid;
BEGIN
    FOR tenant_record IN SELECT id FROM tenants WHERE id <> control_plane_tenant ORDER BY id LOOP
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

    -- EXECUTIVE_VIEWER current canonical contract (evolved after V20260820_7):
    -- V20260830_2 step 4b turns every role holding EXECUTIVE_VIEW into a
    -- read-only mirror of every '%.read' capability in the catalog, and
    -- V20260901_1 canonicalized the codes to uppercase. The de-facto canonical
    -- set is therefore: the 4 executive capabilities + every '%.read'
    -- capability present at apply time. Asserted as:
    --   (a) NO capability outside (4 executive codes OR '%.read' pattern,
    --       case-insensitive: V20260901_1 upper-cased all codes)
    --       — catches real privilege drift;
    --   (b) all 4 executive capabilities present — catches removal drift.
    -- A strict total-count equality is intentionally NOT asserted because the
    -- mirror only includes '%.read' codes that existed when V20260830_2 ran,
    -- so later-added '%.read' codes legitimately stay ungranted.
    SELECT COUNT(*) INTO bad_count
    FROM roles r
    JOIN role_template_bindings b ON b.tenant_id = r.tenant_id AND b.role_id = r.id
    LEFT JOIN role_capabilities rc ON rc.tenant_id = r.tenant_id AND rc.role_id = r.id
    LEFT JOIN access_capabilities ac ON ac.id = rc.capability_id
    WHERE b.tenant_id = tenant_record.id
      AND b.template_key = 'EXECUTIVE_VIEWER'
      AND rc.capability_id IS NOT NULL
      AND (
        ac.code IS NULL
        OR (
          ac.code NOT IN (
            'EXECUTIVE_VIEW',
            'EXECUTIVE_COMMAND_CENTER.VIEW',
            'EXECUTIVE_MANAGEMENT.VIEW',
            'EXECUTIVE_REPORT.VIEW'
          )
          AND upper(ac.code) NOT LIKE '%.READ'
        )
      );
    IF bad_count > 0 THEN
        RAISE EXCEPTION 'RBAC_EXACT_MATRIX_9_OF_9=FAIL: EXECUTIVE_VIEWER has % rows with EXTRA capabilities', bad_count;
    END IF;
    SELECT COUNT(*) INTO bad_count
    FROM roles r
    JOIN role_template_bindings b ON b.tenant_id = r.tenant_id AND b.role_id = r.id
    WHERE b.tenant_id = tenant_record.id
      AND b.template_key = 'EXECUTIVE_VIEWER'
      AND (
        SELECT COUNT(*)
        FROM role_capabilities rc
        JOIN access_capabilities ac ON ac.id = rc.capability_id
        WHERE rc.tenant_id = r.tenant_id AND rc.role_id = r.id
          AND ac.code IN (
            'EXECUTIVE_VIEW',
            'EXECUTIVE_COMMAND_CENTER.VIEW',
            'EXECUTIVE_MANAGEMENT.VIEW',
            'EXECUTIVE_REPORT.VIEW'
          )
      ) != 4;
    IF bad_count > 0 THEN
        RAISE EXCEPTION 'RBAC_EXACT_MATRIX_9_OF_9=FAIL: EXECUTIVE_VIEWER has % roles missing executive capabilities (expected the 4 canonical executive codes)', bad_count;
    END IF;

    END LOOP;

    -- Restore the handler's reserved fail-closed scope for any subsequent SQL
    -- in the same migration transaction. This is not a tenant and grants no access.
    PERFORM set_config('app.tenant_id', '00000000-0000-0000-0000-000000000000', true);
END $$;
