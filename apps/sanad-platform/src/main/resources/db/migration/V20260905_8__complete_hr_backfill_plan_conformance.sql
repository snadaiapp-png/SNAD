-- ============================================================
-- HRM-G0 / WS2 / Task 6 — Plan Conformance Completion
-- ============================================================
-- Forward-only migration that completes the backfill implementation
-- to address plan-conformance gaps:
--
-- 1. Legal Entity assignment on hr_employees
-- 2. Multiple Legal Entity ambiguity detection
-- 3. Effective-dated eligibility (future/inactive excluded)
-- 4. Department canonical backfill (hr_departments → hr_org_units)
-- 5. Position canonical backfill (hr_positions → hr_position_versions)
-- 6. Manager resolution (reports_to_assignment_id)
-- 7. Open review items block CANONICAL
-- 8. Machine-readable reconciliation (hr_reconcile_tenant_report)
-- 9. SECURITY DEFINER hardening (REVOKE EXECUTE FROM PUBLIC)
--
-- This migration REPLACES the functions from V20260831_5 with
-- enhanced versions. The scripts/hrm/*.sql files remain the
-- authoritative source copies for review/audit.
-- ============================================================

-- ============================================================
-- 1. REPLACED PRECHECK FUNCTION
--    Enhancements: effective-dated eligibility, multiple LE detection
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
    v_org_count   INTEGER;
    v_effective_org_count INTEGER;
    v_has_legal_entity BOOLEAN;
    v_legacy_dept_id UUID;
    v_legacy_pos_id UUID;
    v_org_id UUID;
    v_le_id UUID;
BEGIN
    PERFORM set_config('app.tenant_id', p_tenant_id::text, true);

    -- Count ACTIVE legal entities
    SELECT COUNT(*) INTO v_le_count
    FROM legal_entities le
    WHERE le.tenant_id = p_tenant_id AND le.status = 'ACTIVE';

    -- Count ACTIVE organizations with EFFECTIVE legal entity eligibility
    -- (effective_from <= CURRENT_DATE AND (effective_to IS NULL OR effective_to >= CURRENT_DATE))
    SELECT COUNT(DISTINCT ole.organization_id) INTO v_effective_org_count
    FROM organization_legal_entities ole
    JOIN legal_entities le ON le.id = ole.legal_entity_id AND le.tenant_id = ole.tenant_id
    WHERE ole.tenant_id = p_tenant_id
      AND ole.status = 'ACTIVE'
      AND le.status = 'ACTIVE'
      AND ole.effective_from <= CURRENT_DATE
      AND (ole.effective_to IS NULL OR ole.effective_to >= CURRENT_DATE);

    -- Count total ACTIVE orgs (for informational purposes)
    SELECT COUNT(*) INTO v_org_count
    FROM organizations o
    WHERE o.tenant_id = p_tenant_id AND o.status = 'ACTIVE';

    v_has_legal_entity := (v_le_count > 0);

    INSERT INTO hr_migration_tenant_state (tenant_id, state, updated_at)
    VALUES (p_tenant_id, 'MIGRATING', NOW())
    ON CONFLICT (tenant_id) DO UPDATE SET state = 'MIGRATING', updated_at = NOW();

    FOR v_emp_record IN
        SELECT id, user_id, employee_number, first_name, last_name,
               department_id, position_id, manager_id
        FROM hr_employees
        WHERE tenant_id = p_tenant_id
        ORDER BY id
    LOOP
        v_classification := 'AUTO_MIGRATE';
        v_reason := NULL;
        v_issue_code := NULL;

        -- Check 1: No legal entity → BLOCKED
        IF v_le_count = 0 THEN
            v_classification := 'MIGRATION_BLOCKED';
            v_reason := 'No active legal entity';
            v_issue_code := 'MISSING_ORGANIZATION_MAPPING';
        ELSIF v_effective_org_count = 0 THEN
            -- Check 2: No effective eligible organization → BLOCKED
            v_classification := 'MIGRATION_BLOCKED';
            v_reason := 'No effective eligible organization (check effective dates)';
            v_issue_code := 'MISSING_ORGANIZATION_MAPPING';
        ELSIF v_effective_org_count > 1 THEN
            -- Check 3: Multiple eligible orgs → REVIEW_REQUIRED
            v_classification := 'MIGRATION_REVIEW_REQUIRED';
            v_reason := 'Multiple eligible organizations — ambiguous';
            v_issue_code := 'MISSING_ORGANIZATION_MAPPING';
        END IF;

        -- Check 4: Multiple LEs linked to same org → ambiguous employer of record
        IF v_classification = 'AUTO_MIGRATE' AND v_effective_org_count = 1 THEN
            SELECT COUNT(DISTINCT ole.legal_entity_id) INTO v_le_count
            FROM organization_legal_entities ole
            JOIN legal_entities le ON le.id = ole.legal_entity_id AND le.tenant_id = ole.tenant_id
            WHERE ole.tenant_id = p_tenant_id
              AND ole.status = 'ACTIVE'
              AND le.status = 'ACTIVE'
              AND ole.effective_from <= CURRENT_DATE
              AND (ole.effective_to IS NULL OR ole.effective_to >= CURRENT_DATE);

            IF v_le_count > 1 THEN
                v_classification := 'MIGRATION_REVIEW_REQUIRED';
                v_reason := 'Multiple Legal Entities linked to eligible organization — ambiguous employer of record';
                v_issue_code := 'AMBIGUOUS_PERSON_IDENTITY';
            END IF;
        END IF;

        -- Check 5: Duplicate user_id → BLOCKED
        IF v_emp_record.user_id IS NOT NULL THEN
            SELECT COUNT(*) INTO v_dup_count
            FROM hr_employees
            WHERE tenant_id = p_tenant_id AND user_id = v_emp_record.user_id;

            IF v_dup_count > 1 THEN
                v_classification := 'MIGRATION_BLOCKED';
                v_reason := 'Duplicate user_id across multiple employees';
                v_issue_code := 'DUPLICATE_USER_ID';
            END IF;
        END IF;

        -- Check 6: Pre-existing canonical Person with same user_id
        IF v_emp_record.user_id IS NOT NULL AND v_classification = 'AUTO_MIGRATE' THEN
            PERFORM 1 FROM hr_people
            WHERE tenant_id = p_tenant_id AND user_id = v_emp_record.user_id;
            IF FOUND THEN
                v_classification := 'MIGRATION_REVIEW_REQUIRED';
                v_reason := 'Existing canonical Person with same user_id';
                v_issue_code := 'AMBIGUOUS_PERSON_IDENTITY';
            END IF;
        END IF;

        -- Check 7: Department mapping
        IF v_emp_record.department_id IS NOT NULL AND v_classification = 'AUTO_MIGRATE' THEN
            PERFORM 1 FROM hr_org_units ou
            WHERE ou.tenant_id = p_tenant_id AND ou.id = v_emp_record.department_id;
            IF NOT FOUND THEN
                -- Will be backfilled, not a blocker
                NULL;
            END IF;
        END IF;

        -- Check 8: Position mapping
        IF v_emp_record.position_id IS NOT NULL AND v_classification = 'AUTO_MIGRATE' THEN
            PERFORM 1 FROM hr_positions hp
            WHERE hp.tenant_id = p_tenant_id AND hp.id = v_emp_record.position_id;
            IF NOT FOUND THEN
                NULL; -- Will be backfilled
            END IF;
        END IF;

        -- Check 9: Manager mapping
        IF v_emp_record.manager_id IS NOT NULL AND v_classification = 'AUTO_MIGRATE' THEN
            PERFORM 1 FROM hr_employees e
            WHERE e.tenant_id = p_tenant_id AND e.id = v_emp_record.manager_id;
            IF NOT FOUND THEN
                v_classification := 'MIGRATION_REVIEW_REQUIRED';
                v_reason := 'Unresolved manager mapping';
                v_issue_code := 'MISSING_MANAGER_MAPPING';
            END IF;
        END IF;

        -- Insert or update mapping (idempotent)
        SELECT EXISTS(
            SELECT 1 FROM hr_legacy_employee_mappings
            WHERE tenant_id = p_tenant_id AND legacy_employee_id = v_emp_record.id
        ) INTO v_mapping_exists;

        IF v_mapping_exists THEN
            UPDATE hr_legacy_employee_mappings
            SET classification = v_classification, review_reason = v_reason
            WHERE tenant_id = p_tenant_id AND legacy_employee_id = v_emp_record.id;
        ELSE
            INSERT INTO hr_legacy_employee_mappings
                (id, tenant_id, legacy_employee_id, classification, review_reason, created_at)
            VALUES
                (gen_random_uuid(), p_tenant_id, v_emp_record.id, v_classification, v_reason, NOW());
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
-- 2. REPLACED BACKFILL FUNCTION
--    Enhancements: Legal Entity assignment, Department/Position backfill, Manager resolution
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
    v_pos_canonical_id UUID;
    v_dept_record RECORD;
    v_pos_record RECORD;
BEGIN
    PERFORM set_config('app.tenant_id', p_tenant_id::text, true);

    -- Step 1: Run precheck
    PERFORM hr_precheck_tenant(p_tenant_id);

    -- Step 2: Backfill legacy departments to canonical org_units (idempotent)
    FOR v_dept_record IN
        SELECT id, tenant_id, name, code
        FROM hr_departments
        WHERE tenant_id = p_tenant_id
        ORDER BY id
    LOOP
        -- Check if canonical org_unit already exists for this legacy dept
        SELECT id INTO v_dept_org_unit_id
        FROM hr_org_units
        WHERE tenant_id = p_tenant_id AND id = v_dept_record.id
        LIMIT 1;

        IF v_dept_org_unit_id IS NULL THEN
            -- Get the authoritative organization
            SELECT ole.organization_id INTO v_org_id
            FROM organization_legal_entities ole
            JOIN legal_entities le ON le.id = ole.legal_entity_id AND le.tenant_id = ole.tenant_id
            WHERE ole.tenant_id = p_tenant_id
              AND ole.status = 'ACTIVE'
              AND le.status = 'ACTIVE'
              AND ole.effective_from <= CURRENT_DATE
              AND (ole.effective_to IS NULL OR ole.effective_to >= CURRENT_DATE)
            ORDER BY ole.organization_id
            LIMIT 1;

            IF v_org_id IS NOT NULL THEN
                INSERT INTO hr_org_units (id, tenant_id, organization_id, stable_code, created_at)
                VALUES (v_dept_record.id, p_tenant_id, v_org_id,
                        COALESCE(v_dept_record.code, 'DEPT-' || v_dept_record.id::text), NOW())
                ON CONFLICT DO NOTHING;

                INSERT INTO hr_org_unit_versions (id, tenant_id, org_unit_id, name, code, unit_type,
                        effective_from, status)
                VALUES (gen_random_uuid(), p_tenant_id, v_dept_record.id,
                        v_dept_record.name, COALESCE(v_dept_record.code, 'DEPT'),
                        'DEPARTMENT', CURRENT_DATE, 'ACTIVE')
                ON CONFLICT DO NOTHING;
            END IF;
        END IF;
    END LOOP;

    -- Step 3: Backfill legacy positions to canonical position_versions (idempotent)
    FOR v_pos_record IN
        SELECT id, tenant_id, title, code
        FROM hr_positions
        WHERE tenant_id = p_tenant_id
        ORDER BY id
    LOOP
        -- Check if position_version already exists
        PERFORM 1 FROM hr_position_versions pv
        WHERE pv.tenant_id = p_tenant_id AND pv.position_id = v_pos_record.id;
        IF NOT FOUND THEN
            -- Get org for position
            SELECT ole.organization_id INTO v_org_id
            FROM organization_legal_entities ole
            JOIN legal_entities le ON le.id = ole.legal_entity_id AND le.tenant_id = ole.tenant_id
            WHERE ole.tenant_id = p_tenant_id
              AND ole.status = 'ACTIVE'
              AND le.status = 'ACTIVE'
              AND ole.effective_from <= CURRENT_DATE
              AND (ole.effective_to IS NULL OR ole.effective_to >= CURRENT_DATE)
            ORDER BY ole.organization_id
            LIMIT 1;

            IF v_org_id IS NOT NULL THEN
                INSERT INTO hr_position_versions (id, tenant_id, position_id, organization_id, title,
                        effective_from, status)
                VALUES (gen_random_uuid(), p_tenant_id, v_pos_record.id, v_org_id,
                        v_pos_record.title, CURRENT_DATE, 'ACTIVE')
                ON CONFLICT DO NOTHING;
            END IF;
        END IF;
    END LOOP;

    -- Step 4: Create canonical graph for AUTO_MIGRATE employees
    FOR v_emp_record IN
        SELECT e.id, e.user_id, e.employee_number, e.first_name, e.last_name,
               e.display_name, e.hire_date, e.employment_type, e.status,
               e.department_id, e.position_id, e.manager_id
        FROM hr_employees e
        JOIN hr_legacy_employee_mappings m
            ON m.tenant_id = e.tenant_id AND m.legacy_employee_id = e.id
        WHERE e.tenant_id = p_tenant_id
          AND m.classification = 'AUTO_MIGRATE'
          AND m.canonical_person_id IS NULL
        ORDER BY e.id
    LOOP
        -- Create Person (idempotent)
        v_person_id := NULL;
        IF v_emp_record.user_id IS NOT NULL THEN
            SELECT id INTO v_person_id
            FROM hr_people
            WHERE tenant_id = p_tenant_id AND user_id = v_emp_record.user_id
            LIMIT 1;
        END IF;

        IF v_person_id IS NULL THEN
            v_person_id := gen_random_uuid();
            INSERT INTO hr_people
                (id, tenant_id, user_id, first_name, last_name, display_name, version, created_at, updated_at)
            VALUES
                (v_person_id, p_tenant_id, v_emp_record.user_id,
                 v_emp_record.first_name, v_emp_record.last_name, v_emp_record.display_name,
                 0, NOW(), NOW());
        END IF;

        -- Get authoritative Legal Entity and Organization
        SELECT ole.legal_entity_id, ole.organization_id INTO v_le_id, v_org_id
        FROM organization_legal_entities ole
        JOIN legal_entities le ON le.id = ole.legal_entity_id AND le.tenant_id = ole.tenant_id
        WHERE ole.tenant_id = p_tenant_id
          AND ole.status = 'ACTIVE'
          AND le.status = 'ACTIVE'
          AND ole.effective_from <= CURRENT_DATE
          AND (ole.effective_to IS NULL OR ole.effective_to >= CURRENT_DATE)
        ORDER BY ole.organization_id, ole.legal_entity_id
        LIMIT 1;

        -- Link hr_employees.person_id AND legal_entity_id (idempotent)
        UPDATE hr_employees
        SET person_id = v_person_id,
            legal_entity_id = COALESCE(legal_entity_id, v_le_id)
        WHERE id = v_emp_record.id
          AND tenant_id = p_tenant_id
          AND person_id IS NULL;

        -- Update mapping
        UPDATE hr_legacy_employee_mappings
        SET canonical_person_id = v_person_id
        WHERE tenant_id = p_tenant_id
          AND legacy_employee_id = v_emp_record.id
          AND canonical_person_id IS NULL;

        -- Create PRIMARY assignment (idempotent)
        SELECT id INTO v_assignment_id
        FROM hr_employee_assignments
        WHERE tenant_id = p_tenant_id
          AND employment_id = v_emp_record.id
          AND assignment_type = 'PRIMARY'
          AND effective_to IS NULL
        LIMIT 1;

        IF v_assignment_id IS NULL THEN
            v_assignment_id := gen_random_uuid();
            INSERT INTO hr_employee_assignments
                (id, tenant_id, employment_id, organization_id, org_unit_id,
                 assignment_type, occupancy_mode, allocation_percent,
                 effective_from, status, version, created_at, updated_at)
            VALUES
                (v_assignment_id, p_tenant_id, v_emp_record.id, v_org_id,
                 v_emp_record.department_id,
                 'PRIMARY', 'NON_OCCUPYING', 100.00,
                 COALESCE(v_emp_record.hire_date, CURRENT_DATE),
                 'ACTIVE', 0, NOW(), NOW());
        END IF;
    END LOOP;

    -- Step 5: Manager resolution — link reports_to_assignment_id
    FOR v_emp_record IN
        SELECT e.id, e.manager_id, a.id AS assignment_id
        FROM hr_employees e
        JOIN hr_employee_assignments a ON a.employment_id = e.id AND a.tenant_id = e.tenant_id
        WHERE e.tenant_id = p_tenant_id
          AND e.manager_id IS NOT NULL
          AND a.assignment_type = 'PRIMARY'
          AND a.reports_to_assignment_id IS NULL
        ORDER BY e.id
    LOOP
        SELECT a2.id INTO v_mgr_assignment_id
        FROM hr_employee_assignments a2
        WHERE a2.tenant_id = p_tenant_id
          AND a2.employment_id = v_emp_record.manager_id
          AND a2.assignment_type = 'PRIMARY'
          AND a2.effective_to IS NULL
        LIMIT 1;

        IF v_mgr_assignment_id IS NOT NULL THEN
            UPDATE hr_employee_assignments
            SET reports_to_assignment_id = v_mgr_assignment_id
            WHERE id = v_emp_record.assignment_id AND tenant_id = p_tenant_id;
        END IF;
    END LOOP;

    -- Step 6: Run reconciliation
    PERFORM hr_reconcile_tenant(p_tenant_id);
END;
$$;

-- ============================================================
-- 3. REPLACED RECONCILE FUNCTION
--    Enhancement: Open review items block CANONICAL
-- ============================================================

CREATE OR REPLACE FUNCTION hr_reconcile_tenant(p_tenant_id UUID)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_legacy_total    INTEGER;
    v_resolved        INTEGER;
    v_unresolved      INTEGER;
    v_unaccounted     INTEGER;
    v_person_missing  INTEGER;
    v_primary_missing INTEGER;
    v_dup_mapping     INTEGER;
    v_orphan_mapping  INTEGER;
    v_open_reviews    INTEGER;
    v_final_state     VARCHAR(20);
BEGIN
    PERFORM set_config('app.tenant_id', p_tenant_id::text, true);

    SELECT COUNT(*) INTO v_legacy_total FROM hr_employees WHERE tenant_id = p_tenant_id;
    SELECT COUNT(*) INTO v_resolved FROM hr_legacy_employee_mappings
    WHERE tenant_id = p_tenant_id AND classification = 'AUTO_MIGRATE' AND canonical_person_id IS NOT NULL;
    SELECT COUNT(DISTINCT legacy_employee_id) INTO v_unresolved FROM hr_legacy_employee_mappings
    WHERE tenant_id = p_tenant_id AND classification IN ('MIGRATION_REVIEW_REQUIRED', 'MIGRATION_BLOCKED', 'AUTO_MIGRATE');
    SELECT COUNT(*) INTO v_person_missing FROM hr_legacy_employee_mappings m
    WHERE m.tenant_id = p_tenant_id AND m.classification = 'AUTO_MIGRATE' AND m.canonical_person_id IS NULL;
    SELECT COUNT(*) INTO v_primary_missing FROM hr_legacy_employee_mappings m
    WHERE m.tenant_id = p_tenant_id AND m.classification = 'AUTO_MIGRATE' AND m.canonical_person_id IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM hr_employee_assignments a WHERE a.tenant_id = p_tenant_id AND a.employment_id = m.legacy_employee_id AND a.assignment_type = 'PRIMARY');
    SELECT COUNT(*) INTO v_dup_mapping FROM (SELECT legacy_employee_id FROM hr_legacy_employee_mappings WHERE tenant_id = p_tenant_id GROUP BY legacy_employee_id HAVING COUNT(*) > 1) d;
    SELECT COUNT(*) INTO v_orphan_mapping FROM hr_legacy_employee_mappings m WHERE m.tenant_id = p_tenant_id AND NOT EXISTS (SELECT 1 FROM hr_employees e WHERE e.tenant_id = m.tenant_id AND e.id = m.legacy_employee_id);
    SELECT COUNT(*) INTO v_open_reviews FROM hr_migration_review_items WHERE tenant_id = p_tenant_id AND resolution_state = 'OPEN';

    v_unaccounted := v_legacy_total - v_resolved - v_unresolved;

    IF v_unresolved = 0 AND v_person_missing = 0 AND v_primary_missing = 0
       AND v_dup_mapping = 0 AND v_orphan_mapping = 0 AND v_unaccounted = 0
       AND v_open_reviews = 0 AND v_legacy_total > 0
    THEN
        v_final_state := 'CANONICAL';
    ELSE
        v_final_state := 'BLOCKED';
    END IF;

    INSERT INTO hr_migration_tenant_state (tenant_id, state, updated_at)
    VALUES (p_tenant_id, v_final_state, NOW())
    ON CONFLICT (tenant_id) DO UPDATE SET state = v_final_state, updated_at = NOW();
END;
$$;

-- ============================================================
-- 4. NEW: Machine-readable reconciliation report function
-- ============================================================

CREATE OR REPLACE FUNCTION hr_reconcile_tenant_report(p_tenant_id UUID)
RETURNS TABLE(gate_name TEXT, gate_value BIGINT, passed BOOLEAN)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_legacy_total BIGINT;
    v_resolved BIGINT;
    v_unresolved BIGINT;
    v_unaccounted BIGINT;
    v_person_missing BIGINT;
    v_primary_missing BIGINT;
    v_dept_missing BIGINT;
    v_pos_missing BIGINT;
    v_mgr_unresolved BIGINT;
    v_open_reviews BIGINT;
    v_dup_mapping BIGINT;
    v_orphan_mapping BIGINT;
BEGIN
    PERFORM set_config('app.tenant_id', p_tenant_id::text, true);

    SELECT COUNT(*) INTO v_legacy_total FROM hr_employees WHERE tenant_id = p_tenant_id;
    SELECT COUNT(*) INTO v_resolved FROM hr_legacy_employee_mappings WHERE tenant_id = p_tenant_id AND classification = 'AUTO_MIGRATE' AND canonical_person_id IS NOT NULL;
    SELECT COUNT(DISTINCT legacy_employee_id) INTO v_unresolved FROM hr_legacy_employee_mappings WHERE tenant_id = p_tenant_id AND classification IN ('MIGRATION_REVIEW_REQUIRED', 'MIGRATION_BLOCKED');
    v_unaccounted := v_legacy_total - v_resolved - v_unresolved;
    SELECT COUNT(*) INTO v_person_missing FROM hr_legacy_employee_mappings m WHERE m.tenant_id = p_tenant_id AND m.classification = 'AUTO_MIGRATE' AND m.canonical_person_id IS NULL;
    SELECT COUNT(*) INTO v_primary_missing FROM hr_legacy_employee_mappings m WHERE m.tenant_id = p_tenant_id AND m.classification = 'AUTO_MIGRATE' AND m.canonical_person_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM hr_employee_assignments a WHERE a.tenant_id = p_tenant_id AND a.employment_id = m.legacy_employee_id AND a.assignment_type = 'PRIMARY');
    SELECT COUNT(*) INTO v_open_reviews FROM hr_migration_review_items WHERE tenant_id = p_tenant_id AND resolution_state = 'OPEN';
    SELECT COUNT(*) INTO v_dup_mapping FROM (SELECT legacy_employee_id FROM hr_legacy_employee_mappings WHERE tenant_id = p_tenant_id GROUP BY legacy_employee_id HAVING COUNT(*) > 1) d;
    SELECT COUNT(*) INTO v_orphan_mapping FROM hr_legacy_employee_mappings m WHERE m.tenant_id = p_tenant_id AND NOT EXISTS (SELECT 1 FROM hr_employees e WHERE e.tenant_id = m.tenant_id AND e.id = m.legacy_employee_id);

    RETURN QUERY SELECT 'LEGACY_EMPLOYEE_COUNT'::TEXT, v_legacy_total, TRUE;
    RETURN QUERY SELECT 'CANONICAL_EMPLOYMENT_COUNT'::TEXT, v_resolved, TRUE;
    RETURN QUERY SELECT 'PERSON_MAPPING_MISSING'::TEXT, v_person_missing, (v_person_missing = 0);
    RETURN QUERY SELECT 'PRIMARY_ASSIGNMENT_MISSING'::TEXT, v_primary_missing, (v_primary_missing = 0);
    RETURN QUERY SELECT 'UNRESOLVED_MIGRATION_ROWS'::TEXT, v_unresolved, (v_unresolved = 0);
    RETURN QUERY SELECT 'OPEN_REVIEW_ITEMS'::TEXT, v_open_reviews, (v_open_reviews = 0);
    RETURN QUERY SELECT 'DUPLICATE_MAPPING'::TEXT, v_dup_mapping, (v_dup_mapping = 0);
    RETURN QUERY SELECT 'ORPHAN_MAPPING'::TEXT, v_orphan_mapping, (v_orphan_mapping = 0);
    RETURN QUERY SELECT 'UNACCOUNTED_ROWS'::TEXT, v_unaccounted, (v_unaccounted = 0);
    RETURN QUERY SELECT 'ALL_GATES_PASS'::TEXT, CASE WHEN v_unresolved = 0 AND v_person_missing = 0 AND v_primary_missing = 0 AND v_dup_mapping = 0 AND v_orphan_mapping = 0 AND v_unaccounted = 0 AND v_open_reviews = 0 AND v_legacy_total > 0 THEN 1 ELSE 0 END, (v_unresolved = 0 AND v_person_missing = 0 AND v_primary_missing = 0 AND v_dup_mapping = 0 AND v_orphan_mapping = 0 AND v_unaccounted = 0 AND v_open_reviews = 0 AND v_legacy_total > 0);
END;
$$;

-- ============================================================
-- 5. SECURITY DEFINER HARDENING — REVOKE EXECUTE FROM PUBLIC
-- ============================================================

REVOKE ALL ON FUNCTION hr_precheck_tenant(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION hr_backfill_tenant(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION hr_reconcile_tenant(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION hr_reconcile_tenant_report(UUID) FROM PUBLIC;

-- Grant EXECUTE only to the table owner (sanad) — migration admin role
GRANT EXECUTE ON FUNCTION hr_precheck_tenant(UUID) TO CURRENT_USER;
GRANT EXECUTE ON FUNCTION hr_backfill_tenant(UUID) TO CURRENT_USER;
GRANT EXECUTE ON FUNCTION hr_reconcile_tenant(UUID) TO CURRENT_USER;
GRANT EXECUTE ON FUNCTION hr_reconcile_tenant_report(UUID) TO CURRENT_USER;
