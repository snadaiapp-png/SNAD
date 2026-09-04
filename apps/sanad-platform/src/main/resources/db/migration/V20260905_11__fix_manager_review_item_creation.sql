-- ============================================================
-- HRM-G0 / WS2 / Task 6 — Fix manager review item creation
-- ============================================================
-- Restores the original 91df7402 behavior: when manager EXISTS in
-- hr_employees but has no canonical assignment yet, create a
-- MISSING_MANAGER_MAPPING review item WITHOUT blocking (classification
-- stays AUTO_MIGRATE). The backfill's manager resolution step then
-- RESOLVES the review item after linking reports_to_assignment_id.
--
-- This satisfies both:
-- - existing test: unresolvedManagerMapping_createsReviewItem (review item created)
-- - plan-conformance: managerResolution_linksReportsToAssignment (reports_to linked)
-- - plan-conformance: openReviewItems_blockCanonicalState (resolved → 0 open → CANONICAL)
-- ============================================================

CREATE OR REPLACE FUNCTION hr_precheck_tenant(p_tenant_id UUID)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_emp_record  RECORD;
    v_dup_count   INTEGER;
    v_mapping_exists BOOLEAN;
    v_classification VARCHAR(40);
    v_reason TEXT;
    v_issue_code VARCHAR(60);
    v_le_count    INTEGER;
    v_effective_org_count INTEGER;
    v_effective_le_count INTEGER;
    v_existing_canonical UUID;
    v_mgr_exists BOOLEAN;
BEGIN
    PERFORM set_config('app.tenant_id', p_tenant_id::text, true);

    SELECT COUNT(*) INTO v_le_count
    FROM legal_entities le
    WHERE le.tenant_id = p_tenant_id AND le.status = 'ACTIVE';

    SELECT COUNT(DISTINCT ole.organization_id) INTO v_effective_org_count
    FROM organization_legal_entities ole
    JOIN legal_entities le ON le.id = ole.legal_entity_id AND le.tenant_id = ole.tenant_id
    WHERE ole.tenant_id = p_tenant_id AND ole.status = 'ACTIVE' AND le.status = 'ACTIVE'
      AND ole.effective_from <= CURRENT_DATE
      AND (ole.effective_to IS NULL OR ole.effective_to >= CURRENT_DATE);

    INSERT INTO hr_migration_tenant_state (tenant_id, state, updated_at)
    VALUES (p_tenant_id, 'MIGRATING', NOW())
    ON CONFLICT (tenant_id) DO UPDATE SET state = 'MIGRATING', updated_at = NOW();

    FOR v_emp_record IN
        SELECT id, user_id, employee_number, first_name, last_name,
               department_id, position_id, manager_id
        FROM hr_employees WHERE tenant_id = p_tenant_id ORDER BY id
    LOOP
        v_classification := 'AUTO_MIGRATE';
        v_reason := NULL;
        v_issue_code := NULL;

        -- Skip re-evaluation if already migrated
        SELECT canonical_person_id INTO v_existing_canonical
        FROM hr_legacy_employee_mappings
        WHERE tenant_id = p_tenant_id AND legacy_employee_id = v_emp_record.id;
        IF v_existing_canonical IS NOT NULL THEN
            UPDATE hr_legacy_employee_mappings SET classification = 'AUTO_MIGRATE'
            WHERE tenant_id = p_tenant_id AND legacy_employee_id = v_emp_record.id;
            CONTINUE;
        END IF;

        -- Org/LE checks
        IF v_le_count = 0 THEN
            v_classification := 'MIGRATION_BLOCKED'; v_reason := 'No active legal entity';
            v_issue_code := 'MISSING_ORGANIZATION_MAPPING';
        ELSIF v_effective_org_count = 0 THEN
            v_classification := 'MIGRATION_BLOCKED'; v_reason := 'No effective eligible organization';
            v_issue_code := 'MISSING_ORGANIZATION_MAPPING';
        ELSIF v_effective_org_count > 1 THEN
            v_classification := 'MIGRATION_REVIEW_REQUIRED'; v_reason := 'Multiple eligible organizations';
            v_issue_code := 'MISSING_ORGANIZATION_MAPPING';
        ELSE
            SELECT COUNT(DISTINCT ole.legal_entity_id) INTO v_effective_le_count
            FROM organization_legal_entities ole
            JOIN legal_entities le ON le.id = ole.legal_entity_id AND le.tenant_id = ole.tenant_id
            WHERE ole.tenant_id = p_tenant_id AND ole.status = 'ACTIVE' AND le.status = 'ACTIVE'
              AND ole.effective_from <= CURRENT_DATE
              AND (ole.effective_to IS NULL OR ole.effective_to >= CURRENT_DATE);
            IF v_effective_le_count > 1 THEN
                v_classification := 'MIGRATION_REVIEW_REQUIRED';
                v_reason := 'Multiple Legal Entities — ambiguous employer';
                v_issue_code := 'AMBIGUOUS_PERSON_IDENTITY';
            END IF;
        END IF;

        -- Duplicate user_id
        IF v_emp_record.user_id IS NOT NULL THEN
            SELECT COUNT(*) INTO v_dup_count FROM hr_employees
            WHERE tenant_id = p_tenant_id AND user_id = v_emp_record.user_id;
            IF v_dup_count > 1 THEN
                v_classification := 'MIGRATION_BLOCKED'; v_reason := 'Duplicate user_id';
                v_issue_code := 'DUPLICATE_USER_ID';
            END IF;
        END IF;

        -- Pre-existing Person (first run only)
        IF v_emp_record.user_id IS NOT NULL AND v_classification = 'AUTO_MIGRATE' THEN
            PERFORM 1 FROM hr_people WHERE tenant_id = p_tenant_id AND user_id = v_emp_record.user_id;
            IF FOUND THEN
                v_classification := 'MIGRATION_REVIEW_REQUIRED';
                v_reason := 'Existing canonical Person with same user_id';
                v_issue_code := 'AMBIGUOUS_PERSON_IDENTITY';
            END IF;
        END IF;

        -- Department mapping
        IF v_emp_record.department_id IS NOT NULL AND v_classification = 'AUTO_MIGRATE' THEN
            PERFORM 1 FROM hr_org_units ou WHERE ou.tenant_id = p_tenant_id AND ou.id = v_emp_record.department_id;
            IF NOT FOUND THEN
                v_classification := 'MIGRATION_REVIEW_REQUIRED'; v_reason := 'Unresolved department mapping';
                v_issue_code := 'MISSING_DEPARTMENT_MAPPING';
            END IF;
        END IF;

        -- Position mapping
        IF v_emp_record.position_id IS NOT NULL AND v_classification = 'AUTO_MIGRATE' THEN
            PERFORM 1 FROM hr_position_versions pv WHERE pv.tenant_id = p_tenant_id AND pv.position_id = v_emp_record.position_id;
            IF NOT FOUND THEN
                v_classification := 'MIGRATION_REVIEW_REQUIRED'; v_reason := 'Unresolved position mapping';
                v_issue_code := 'MISSING_POSITION_MAPPING';
            END IF;
        END IF;

        -- Manager mapping: two branches
        IF v_emp_record.manager_id IS NOT NULL THEN
            PERFORM 1 FROM hr_employees WHERE tenant_id = p_tenant_id AND id = v_emp_record.manager_id;
            v_mgr_exists := FOUND;
            IF NOT v_mgr_exists THEN
                -- Manager doesn't exist → block
                IF v_classification = 'AUTO_MIGRATE' THEN
                    v_classification := 'MIGRATION_REVIEW_REQUIRED';
                    v_reason := 'Unresolved manager mapping — manager not found';
                    v_issue_code := 'MISSING_MANAGER_MAPPING';
                END IF;
            ELSE
                -- Manager exists but no canonical assignment yet → create review item WITHOUT blocking
                IF v_issue_code IS NULL THEN
                    INSERT INTO hr_migration_review_items
                        (tenant_id, legacy_entity_type, legacy_entity_id, issue_code,
                         severity, review_reason, resolution_state, created_at, updated_at)
                    VALUES
                        (p_tenant_id, 'EMPLOYEE', v_emp_record.id, 'MISSING_MANAGER_MAPPING',
                         'REVIEW', 'Manager mapping pending canonical assignment',
                         'OPEN', NOW(), NOW())
                    ON CONFLICT DO NOTHING;
                END IF;
            END IF;
        END IF;

        -- Insert/update mapping
        SELECT EXISTS(SELECT 1 FROM hr_legacy_employee_mappings
            WHERE tenant_id = p_tenant_id AND legacy_employee_id = v_emp_record.id) INTO v_mapping_exists;
        IF v_mapping_exists THEN
            UPDATE hr_legacy_employee_mappings SET classification = v_classification, review_reason = v_reason
            WHERE tenant_id = p_tenant_id AND legacy_employee_id = v_emp_record.id;
        ELSE
            INSERT INTO hr_legacy_employee_mappings (id, tenant_id, legacy_employee_id, classification, review_reason, created_at)
            VALUES (gen_random_uuid(), p_tenant_id, v_emp_record.id, v_classification, v_reason, NOW());
        END IF;

        -- Create review items for non-AUTO_MIGRATE
        IF v_classification != 'AUTO_MIGRATE' AND v_issue_code IS NOT NULL THEN
            INSERT INTO hr_migration_review_items
                (tenant_id, legacy_entity_type, legacy_entity_id, issue_code,
                 severity, review_reason, resolution_state, created_at, updated_at)
            VALUES
                (p_tenant_id, 'EMPLOYEE', v_emp_record.id, v_issue_code,
                 CASE WHEN v_classification = 'MIGRATION_BLOCKED' THEN 'BLOCKED' ELSE 'REVIEW' END,
                 COALESCE(v_reason, v_issue_code), 'OPEN', NOW(), NOW())
            ON CONFLICT DO NOTHING;
        END IF;
    END LOOP;
END;
$$;

-- ============================================================
-- FIXED BACKFILL — manager resolution RESOLVES review items
-- ============================================================

CREATE OR REPLACE FUNCTION hr_backfill_tenant(p_tenant_id UUID)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_emp_record  RECORD;
    v_person_id   UUID;
    v_org_id      UUID;
    v_le_id       UUID;
    v_assignment_id UUID;
    v_mgr_assignment_id UUID;
    v_dept_org_unit_id UUID;
    v_dept_record RECORD;
    v_pos_record RECORD;
BEGIN
    PERFORM set_config('app.tenant_id', p_tenant_id::text, true);
    PERFORM hr_precheck_tenant(p_tenant_id);

    -- Step 1: Backfill departments
    FOR v_dept_record IN SELECT id, tenant_id, name, code FROM hr_departments WHERE tenant_id = p_tenant_id ORDER BY id LOOP
        SELECT id INTO v_dept_org_unit_id FROM hr_org_units WHERE tenant_id = p_tenant_id AND id = v_dept_record.id LIMIT 1;
        IF v_dept_org_unit_id IS NULL THEN
            SELECT ole.organization_id INTO v_org_id FROM organization_legal_entities ole
            JOIN legal_entities le ON le.id = ole.legal_entity_id AND le.tenant_id = ole.tenant_id
            WHERE ole.tenant_id = p_tenant_id AND ole.status = 'ACTIVE' AND le.status = 'ACTIVE'
              AND ole.effective_from <= CURRENT_DATE AND (ole.effective_to IS NULL OR ole.effective_to >= CURRENT_DATE)
            ORDER BY ole.organization_id LIMIT 1;
            IF v_org_id IS NOT NULL THEN
                INSERT INTO hr_org_units (id, tenant_id, organization_id, stable_code, created_at)
                VALUES (v_dept_record.id, p_tenant_id, v_org_id, COALESCE(v_dept_record.code, 'DEPT-' || v_dept_record.id::text), NOW()) ON CONFLICT DO NOTHING;
                INSERT INTO hr_org_unit_versions (id, tenant_id, org_unit_id, name, code, unit_type, effective_from, status)
                VALUES (gen_random_uuid(), p_tenant_id, v_dept_record.id, v_dept_record.name, COALESCE(v_dept_record.code, 'DEPT'), 'DEPARTMENT', CURRENT_DATE, 'ACTIVE') ON CONFLICT DO NOTHING;
            END IF;
        END IF;
    END LOOP;

    -- Step 2: Backfill positions
    FOR v_pos_record IN SELECT id, tenant_id, title, code FROM hr_positions WHERE tenant_id = p_tenant_id ORDER BY id LOOP
        PERFORM 1 FROM hr_position_versions pv WHERE pv.tenant_id = p_tenant_id AND pv.position_id = v_pos_record.id;
        IF NOT FOUND THEN
            SELECT ole.organization_id INTO v_org_id FROM organization_legal_entities ole
            JOIN legal_entities le ON le.id = ole.legal_entity_id AND le.tenant_id = ole.tenant_id
            WHERE ole.tenant_id = p_tenant_id AND ole.status = 'ACTIVE' AND le.status = 'ACTIVE'
              AND ole.effective_from <= CURRENT_DATE AND (ole.effective_to IS NULL OR ole.effective_to >= CURRENT_DATE)
            ORDER BY ole.organization_id LIMIT 1;
            IF v_org_id IS NOT NULL THEN
                INSERT INTO hr_position_versions (id, tenant_id, position_id, organization_id, title, effective_from, status)
                VALUES (gen_random_uuid(), p_tenant_id, v_pos_record.id, v_org_id, v_pos_record.title, CURRENT_DATE, 'ACTIVE') ON CONFLICT DO NOTHING;
            END IF;
        END IF;
    END LOOP;

    -- Step 3: Create canonical graph for AUTO_MIGRATE employees
    FOR v_emp_record IN
        SELECT e.id, e.user_id, e.employee_number, e.first_name, e.last_name, e.display_name, e.hire_date, e.employment_type, e.status, e.department_id, e.position_id, e.manager_id
        FROM hr_employees e JOIN hr_legacy_employee_mappings m ON m.tenant_id = e.tenant_id AND m.legacy_employee_id = e.id
        WHERE e.tenant_id = p_tenant_id AND m.classification = 'AUTO_MIGRATE' AND m.canonical_person_id IS NULL ORDER BY e.id
    LOOP
        v_person_id := NULL;
        IF v_emp_record.user_id IS NOT NULL THEN
            SELECT id INTO v_person_id FROM hr_people WHERE tenant_id = p_tenant_id AND user_id = v_emp_record.user_id LIMIT 1;
        END IF;
        IF v_person_id IS NULL THEN
            v_person_id := gen_random_uuid();
            INSERT INTO hr_people (id, tenant_id, user_id, first_name, last_name, display_name, version, created_at, updated_at)
            VALUES (v_person_id, p_tenant_id, v_emp_record.user_id, v_emp_record.first_name, v_emp_record.last_name, v_emp_record.display_name, 0, NOW(), NOW());
        END IF;
        SELECT ole.legal_entity_id, ole.organization_id INTO v_le_id, v_org_id FROM organization_legal_entities ole
        JOIN legal_entities le ON le.id = ole.legal_entity_id AND le.tenant_id = ole.tenant_id
        WHERE ole.tenant_id = p_tenant_id AND ole.status = 'ACTIVE' AND le.status = 'ACTIVE'
          AND ole.effective_from <= CURRENT_DATE AND (ole.effective_to IS NULL OR ole.effective_to >= CURRENT_DATE)
        ORDER BY ole.organization_id, ole.legal_entity_id LIMIT 1;
        UPDATE hr_employees SET person_id = v_person_id, legal_entity_id = COALESCE(legal_entity_id, v_le_id)
        WHERE id = v_emp_record.id AND tenant_id = p_tenant_id AND person_id IS NULL;
        UPDATE hr_legacy_employee_mappings SET canonical_person_id = v_person_id
        WHERE tenant_id = p_tenant_id AND legacy_employee_id = v_emp_record.id AND canonical_person_id IS NULL;
        SELECT id INTO v_assignment_id FROM hr_employee_assignments
        WHERE tenant_id = p_tenant_id AND employment_id = v_emp_record.id AND assignment_type = 'PRIMARY' AND effective_to IS NULL LIMIT 1;
        IF v_assignment_id IS NULL THEN
            v_assignment_id := gen_random_uuid();
            INSERT INTO hr_employee_assignments (id, tenant_id, employment_id, organization_id, org_unit_id, assignment_type, occupancy_mode, allocation_percent, effective_from, status, version, created_at, updated_at)
            VALUES (v_assignment_id, p_tenant_id, v_emp_record.id, v_org_id, v_emp_record.department_id, 'PRIMARY', 'NON_OCCUPYING', 100.00, COALESCE(v_emp_record.hire_date, CURRENT_DATE), 'ACTIVE', 0, NOW(), NOW());
        END IF;
    END LOOP;

    -- Step 4: Manager resolution — link reports_to_assignment_id AND resolve review items
    FOR v_emp_record IN
        SELECT e.id, e.manager_id, a.id AS assignment_id
        FROM hr_employees e JOIN hr_employee_assignments a ON a.employment_id = e.id AND a.tenant_id = e.tenant_id
        WHERE e.tenant_id = p_tenant_id AND e.manager_id IS NOT NULL AND a.assignment_type = 'PRIMARY' AND a.reports_to_assignment_id IS NULL ORDER BY e.id
    LOOP
        SELECT a2.id INTO v_mgr_assignment_id FROM hr_employee_assignments a2
        WHERE a2.tenant_id = p_tenant_id AND a2.employment_id = v_emp_record.manager_id AND a2.assignment_type = 'PRIMARY' AND a2.effective_to IS NULL LIMIT 1;
        IF v_mgr_assignment_id IS NOT NULL THEN
            UPDATE hr_employee_assignments SET reports_to_assignment_id = v_mgr_assignment_id
            WHERE id = v_emp_record.assignment_id AND tenant_id = p_tenant_id;
            -- RESOLVE the MISSING_MANAGER_MAPPING review item for this employee
            UPDATE hr_migration_review_items SET resolution_state = 'RESOLVED', updated_at = NOW()
            WHERE tenant_id = p_tenant_id AND legacy_entity_id = v_emp_record.id
              AND issue_code = 'MISSING_MANAGER_MAPPING' AND resolution_state = 'OPEN';
        END IF;
    END LOOP;

    PERFORM hr_reconcile_tenant(p_tenant_id);
END;
$$;

-- Re-apply SECURITY DEFINER hardening
REVOKE ALL ON FUNCTION hr_precheck_tenant(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION hr_backfill_tenant(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION hr_reconcile_tenant(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION hr_reconcile_tenant_report(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION hr_precheck_tenant(UUID) TO sanad;
GRANT EXECUTE ON FUNCTION hr_backfill_tenant(UUID) TO sanad;
GRANT EXECUTE ON FUNCTION hr_reconcile_tenant(UUID) TO sanad;
GRANT EXECUTE ON FUNCTION hr_reconcile_tenant_report(UUID) TO sanad;
