-- ============================================================
-- HRM-G0 / WS2 / Task 6 — Fix reconcile + review item logic
-- ============================================================
-- Fixes issues found in CI Run #3102:
--
-- 1. Reconcile function counted AUTO_MIGRATE as unresolved,
--    causing false BLOCKED state for clean tenants.
--    Fix: only count MIGRATION_REVIEW_REQUIRED + MIGRATION_BLOCKED
--    as unresolved.
--
-- 2. Department/position checks in precheck were changed to "will
--    be backfilled" (NULL), breaking existing tests that expect
--    review items for MISSING_DEPARTMENT_MAPPING / MISSING_POSITION_MAPPING.
--    Fix: restore review item creation for unresolved dept/pos/manager.
--
-- 3. hr_reconcile_tenant_report had type mismatch (integer vs bigint).
--    Fix: use explicit ::BIGINT casts.
-- ============================================================

-- ============================================================
-- 1. FIXED PRECHECK — restore dept/pos/manager review items
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
BEGIN
    PERFORM set_config('app.tenant_id', p_tenant_id::text, true);

    -- Count ACTIVE legal entities
    SELECT COUNT(*) INTO v_le_count
    FROM legal_entities le
    WHERE le.tenant_id = p_tenant_id AND le.status = 'ACTIVE';

    -- Count ACTIVE organizations with EFFECTIVE legal entity eligibility
    SELECT COUNT(DISTINCT ole.organization_id) INTO v_effective_org_count
    FROM organization_legal_entities ole
    JOIN legal_entities le ON le.id = ole.legal_entity_id AND le.tenant_id = ole.tenant_id
    WHERE ole.tenant_id = p_tenant_id
      AND ole.status = 'ACTIVE'
      AND le.status = 'ACTIVE'
      AND ole.effective_from <= CURRENT_DATE
      AND (ole.effective_to IS NULL OR ole.effective_to >= CURRENT_DATE);

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

        -- Check 1: No legal entity or no effective eligible org → BLOCKED
        IF v_le_count = 0 THEN
            v_classification := 'MIGRATION_BLOCKED';
            v_reason := 'No active legal entity';
            v_issue_code := 'MISSING_ORGANIZATION_MAPPING';
        ELSIF v_effective_org_count = 0 THEN
            v_classification := 'MIGRATION_BLOCKED';
            v_reason := 'No effective eligible organization';
            v_issue_code := 'MISSING_ORGANIZATION_MAPPING';
        ELSIF v_effective_org_count > 1 THEN
            -- Check 2: Multiple eligible orgs → REVIEW_REQUIRED
            v_classification := 'MIGRATION_REVIEW_REQUIRED';
            v_reason := 'Multiple eligible organizations — ambiguous';
            v_issue_code := 'MISSING_ORGANIZATION_MAPPING';
        ELSE
            -- Check 3: Multiple LEs linked to same org → ambiguous employer
            SELECT COUNT(DISTINCT ole.legal_entity_id) INTO v_effective_le_count
            FROM organization_legal_entities ole
            JOIN legal_entities le ON le.id = ole.legal_entity_id AND le.tenant_id = ole.tenant_id
            WHERE ole.tenant_id = p_tenant_id
              AND ole.status = 'ACTIVE'
              AND le.status = 'ACTIVE'
              AND ole.effective_from <= CURRENT_DATE
              AND (ole.effective_to IS NULL OR ole.effective_to >= CURRENT_DATE);

            IF v_effective_le_count > 1 THEN
                v_classification := 'MIGRATION_REVIEW_REQUIRED';
                v_reason := 'Multiple Legal Entities linked to eligible organization — ambiguous employer of record';
                v_issue_code := 'AMBIGUOUS_PERSON_IDENTITY';
            END IF;
        END IF;

        -- Check 4: Duplicate user_id → BLOCKED
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

        -- Check 5: Pre-existing canonical Person with same user_id
        IF v_emp_record.user_id IS NOT NULL AND v_classification = 'AUTO_MIGRATE' THEN
            PERFORM 1 FROM hr_people
            WHERE tenant_id = p_tenant_id AND user_id = v_emp_record.user_id;
            IF FOUND THEN
                v_classification := 'MIGRATION_REVIEW_REQUIRED';
                v_reason := 'Existing canonical Person with same user_id';
                v_issue_code := 'AMBIGUOUS_PERSON_IDENTITY';
            END IF;
        END IF;

        -- Check 6: Department mapping unresolved
        IF v_emp_record.department_id IS NOT NULL AND v_classification = 'AUTO_MIGRATE' THEN
            PERFORM 1 FROM hr_org_units ou
            WHERE ou.tenant_id = p_tenant_id AND ou.id = v_emp_record.department_id;
            IF NOT FOUND THEN
                v_classification := 'MIGRATION_REVIEW_REQUIRED';
                v_reason := 'Unresolved department mapping';
                v_issue_code := 'MISSING_DEPARTMENT_MAPPING';
            END IF;
        END IF;

        -- Check 7: Position mapping unresolved
        IF v_emp_record.position_id IS NOT NULL AND v_classification = 'AUTO_MIGRATE' THEN
            PERFORM 1 FROM hr_position_versions pv
            WHERE pv.tenant_id = p_tenant_id AND pv.position_id = v_emp_record.position_id;
            IF NOT FOUND THEN
                v_classification := 'MIGRATION_REVIEW_REQUIRED';
                v_reason := 'Unresolved position mapping';
                v_issue_code := 'MISSING_POSITION_MAPPING';
            END IF;
        END IF;

        -- Check 8: Manager mapping unresolved
        IF v_emp_record.manager_id IS NOT NULL AND v_classification = 'AUTO_MIGRATE' THEN
            PERFORM 1 FROM hr_legacy_employee_mappings m
            WHERE m.tenant_id = p_tenant_id AND m.legacy_employee_id = v_emp_record.manager_id
              AND m.classification = 'AUTO_MIGRATE';
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
-- 2. FIXED RECONCILE — don't count AUTO_MIGRATE as unresolved
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

    -- ONLY count REVIEW_REQUIRED + BLOCKED as unresolved (NOT AUTO_MIGRATE)
    SELECT COUNT(DISTINCT legacy_employee_id) INTO v_unresolved FROM hr_legacy_employee_mappings
    WHERE tenant_id = p_tenant_id AND classification IN ('MIGRATION_REVIEW_REQUIRED', 'MIGRATION_BLOCKED');

    SELECT COUNT(*) INTO v_person_missing FROM hr_legacy_employee_mappings m
    WHERE m.tenant_id = p_tenant_id AND m.classification = 'AUTO_MIGRATE' AND m.canonical_person_id IS NULL;

    SELECT COUNT(*) INTO v_primary_missing FROM hr_legacy_employee_mappings m
    WHERE m.tenant_id = p_tenant_id AND m.classification = 'AUTO_MIGRATE' AND m.canonical_person_id IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM hr_employee_assignments a WHERE a.tenant_id = p_tenant_id AND a.employment_id = m.legacy_employee_id AND a.assignment_type = 'PRIMARY');

    SELECT COUNT(*) INTO v_dup_mapping FROM (SELECT legacy_employee_id FROM hr_legacy_employee_mappings WHERE tenant_id = p_tenant_id GROUP BY legacy_employee_id HAVING COUNT(*) > 1) d;

    SELECT COUNT(*) INTO v_orphan_mapping FROM hr_legacy_employee_mappings m WHERE m.tenant_id = p_tenant_id AND NOT EXISTS (SELECT 1 FROM hr_employees e WHERE e.tenant_id = m.tenant_id AND e.id = m.legacy_employee_id);

    SELECT COUNT(*) INTO v_open_reviews FROM hr_migration_review_items WHERE tenant_id = p_tenant_id AND resolution_state = 'OPEN';

    -- Count AUTO_MIGRATE without canonical as "pending" (not unresolved, not resolved)
    -- These should be 0 after backfill completes
    v_unaccounted := v_legacy_total - v_resolved - v_unresolved -
        (SELECT COUNT(DISTINCT legacy_employee_id) FROM hr_legacy_employee_mappings
         WHERE tenant_id = p_tenant_id AND classification = 'AUTO_MIGRATE' AND canonical_person_id IS NULL);

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
-- 3. FIXED RECONCILE REPORT — use BIGINT casts
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
    v_open_reviews BIGINT;
    v_dup_mapping BIGINT;
    v_orphan_mapping BIGINT;
BEGIN
    PERFORM set_config('app.tenant_id', p_tenant_id::text, true);

    SELECT COUNT(*)::BIGINT INTO v_legacy_total FROM hr_employees WHERE tenant_id = p_tenant_id;
    SELECT COUNT(*)::BIGINT INTO v_resolved FROM hr_legacy_employee_mappings WHERE tenant_id = p_tenant_id AND classification = 'AUTO_MIGRATE' AND canonical_person_id IS NOT NULL;
    SELECT COUNT(DISTINCT legacy_employee_id)::BIGINT INTO v_unresolved FROM hr_legacy_employee_mappings WHERE tenant_id = p_tenant_id AND classification IN ('MIGRATION_REVIEW_REQUIRED', 'MIGRATION_BLOCKED');
    v_unaccounted := v_legacy_total - v_resolved - v_unresolved;
    SELECT COUNT(*)::BIGINT INTO v_person_missing FROM hr_legacy_employee_mappings m WHERE m.tenant_id = p_tenant_id AND m.classification = 'AUTO_MIGRATE' AND m.canonical_person_id IS NULL;
    SELECT COUNT(*)::BIGINT INTO v_primary_missing FROM hr_legacy_employee_mappings m WHERE m.tenant_id = p_tenant_id AND m.classification = 'AUTO_MIGRATE' AND m.canonical_person_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM hr_employee_assignments a WHERE a.tenant_id = p_tenant_id AND a.employment_id = m.legacy_employee_id AND a.assignment_type = 'PRIMARY');
    SELECT COUNT(*)::BIGINT INTO v_open_reviews FROM hr_migration_review_items WHERE tenant_id = p_tenant_id AND resolution_state = 'OPEN';
    SELECT COUNT(*)::BIGINT INTO v_dup_mapping FROM (SELECT legacy_employee_id FROM hr_legacy_employee_mappings WHERE tenant_id = p_tenant_id GROUP BY legacy_employee_id HAVING COUNT(*) > 1) d;
    SELECT COUNT(*)::BIGINT INTO v_orphan_mapping FROM hr_legacy_employee_mappings m WHERE m.tenant_id = p_tenant_id AND NOT EXISTS (SELECT 1 FROM hr_employees e WHERE e.tenant_id = m.tenant_id AND e.id = m.legacy_employee_id);

    RETURN QUERY SELECT 'LEGACY_EMPLOYEE_COUNT'::TEXT, v_legacy_total, TRUE;
    RETURN QUERY SELECT 'CANONICAL_EMPLOYMENT_COUNT'::TEXT, v_resolved, TRUE;
    RETURN QUERY SELECT 'PERSON_MAPPING_MISSING'::TEXT, v_person_missing, (v_person_missing = 0);
    RETURN QUERY SELECT 'PRIMARY_ASSIGNMENT_MISSING'::TEXT, v_primary_missing, (v_primary_missing = 0);
    RETURN QUERY SELECT 'UNRESOLVED_MIGRATION_ROWS'::TEXT, v_unresolved, (v_unresolved = 0);
    RETURN QUERY SELECT 'OPEN_REVIEW_ITEMS'::TEXT, v_open_reviews, (v_open_reviews = 0);
    RETURN QUERY SELECT 'DUPLICATE_MAPPING'::TEXT, v_dup_mapping, (v_dup_mapping = 0);
    RETURN QUERY SELECT 'ORPHAN_MAPPING'::TEXT, v_orphan_mapping, (v_orphan_mapping = 0);
    RETURN QUERY SELECT 'UNACCOUNTED_ROWS'::TEXT, v_unaccounted, (v_unaccounted = 0);
END;
$$;

-- Re-apply SECURITY DEFINER hardening
REVOKE ALL ON FUNCTION hr_precheck_tenant(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION hr_backfill_tenant(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION hr_reconcile_tenant(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION hr_reconcile_tenant_report(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION hr_precheck_tenant(UUID) TO CURRENT_USER;
GRANT EXECUTE ON FUNCTION hr_backfill_tenant(UUID) TO CURRENT_USER;
GRANT EXECUTE ON FUNCTION hr_reconcile_tenant(UUID) TO CURRENT_USER;
GRANT EXECUTE ON FUNCTION hr_reconcile_tenant_report(UUID) TO CURRENT_USER;
