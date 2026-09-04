-- ============================================================
-- HRM-G0 / WS2 / Task 6 — Absolute Final Closure Deployment
-- Repeatable Flyway migration generated from the authoritative reviewed scripts.
-- Old versioned migrations remain immutable; this repeatable unit keeps function
-- deployment synchronized with the source scripts and re-applies on checksum change.
-- ============================================================
-- SOURCE_SHA256 g0-backfill-precheck.sql 85ea6abef25031d1ac06be7ca4b96a94d498d6dc368ea62653e700b29d477ee2
-- SOURCE_SHA256 g0-reconcile.sql 98246b7874677cb00a3911253480bce0fb355f0e7c7737ffc4142ca3f48bb35a
-- SOURCE_SHA256 g0-backfill.sql 628fd7375f48227387f4d8d7192d231b7095cb55929538804e09699342557e33

-- ============================================================
-- HRM-G0 / WS2 / Task 6 — Final Backfill Precheck
-- Authoritative source. Deployed verbatim by R__finalize_hr_backfill_closure.sql.
-- Business-effective decisions use an explicit as-of date; audit timestamps may use NOW().
-- ============================================================

CREATE OR REPLACE FUNCTION hr_assert_migration_tenant_scope(p_tenant_id UUID)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_caller_tenant TEXT;
BEGIN
    v_caller_tenant := NULLIF(BTRIM(current_setting('app.tenant_id', true)), '');

    IF v_caller_tenant IS NULL OR v_caller_tenant <> p_tenant_id::text THEN
        RAISE EXCEPTION 'HR migration tenant scope denied: caller tenant %, requested tenant %',
            COALESCE(v_caller_tenant, '<none>'), p_tenant_id
            USING ERRCODE = '42501';
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION hr_default_migration_as_of_date(p_tenant_id UUID)
RETURNS DATE
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_as_of DATE;
BEGIN
    PERFORM hr_assert_migration_tenant_scope(p_tenant_id);

    SELECT COALESCE(
        (SELECT MAX(e.hire_date) FROM hr_employees e WHERE e.tenant_id = p_tenant_id),
        (SELECT MIN(ole.effective_from)
           FROM organization_legal_entities ole
          WHERE ole.tenant_id = p_tenant_id),
        DATE '1970-01-01'
    )
    INTO v_as_of;

    RETURN v_as_of;
END;
$$;

CREATE OR REPLACE FUNCTION hr_precheck_tenant(p_tenant_id UUID, p_as_of_date DATE)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_emp RECORD;
    v_dup_count INTEGER;
    v_mapping_exists BOOLEAN;
    v_existing_canonical UUID;
    v_classification VARCHAR(40);
    v_reason TEXT;
    v_issue_code VARCHAR(60);
    v_effective_org_count INTEGER;
    v_effective_le_count INTEGER;
    v_manager_exists BOOLEAN;
BEGIN
    PERFORM hr_assert_migration_tenant_scope(p_tenant_id);

    IF p_as_of_date IS NULL THEN
        RAISE EXCEPTION 'Task 6 as-of date is required'
            USING ERRCODE = '22004';
    END IF;

    SELECT COUNT(DISTINCT ole.organization_id),
           COUNT(DISTINCT ole.legal_entity_id)
      INTO v_effective_org_count, v_effective_le_count
      FROM organization_legal_entities ole
      JOIN legal_entities le
        ON le.id = ole.legal_entity_id
       AND le.tenant_id = ole.tenant_id
     WHERE ole.tenant_id = p_tenant_id
       AND ole.status = 'ACTIVE'
       AND le.status = 'ACTIVE'
       AND ole.effective_from <= p_as_of_date
       AND (ole.effective_to IS NULL OR ole.effective_to >= p_as_of_date);

    INSERT INTO hr_migration_tenant_state (tenant_id, state, updated_at)
    VALUES (p_tenant_id, 'MIGRATING', NOW())
    ON CONFLICT (tenant_id)
    DO UPDATE SET state = 'MIGRATING', updated_at = NOW();

    FOR v_emp IN
        SELECT id, user_id, employee_number, first_name, last_name,
               department_id, position_id, manager_id
          FROM hr_employees
         WHERE tenant_id = p_tenant_id
         ORDER BY id
    LOOP
        v_classification := 'AUTO_MIGRATE';
        v_reason := NULL;
        v_issue_code := NULL;

        SELECT canonical_person_id
          INTO v_existing_canonical
          FROM hr_legacy_employee_mappings
         WHERE tenant_id = p_tenant_id
           AND legacy_employee_id = v_emp.id;

        -- A successfully migrated row remains stable on rerun.
        IF v_existing_canonical IS NOT NULL THEN
            UPDATE hr_legacy_employee_mappings
               SET classification = 'AUTO_MIGRATE',
                   review_reason = NULL
             WHERE tenant_id = p_tenant_id
               AND legacy_employee_id = v_emp.id;
            CONTINUE;
        END IF;

        IF v_effective_org_count = 0 OR v_effective_le_count = 0 THEN
            v_classification := 'MIGRATION_BLOCKED';
            v_reason := 'No effective Legal Entity + Organization eligibility at the migration as-of date';
            v_issue_code := 'MISSING_ORGANIZATION_MAPPING';
        ELSIF v_effective_org_count > 1 THEN
            v_classification := 'MIGRATION_REVIEW_REQUIRED';
            v_reason := 'Multiple effective eligible Organizations';
            v_issue_code := 'MISSING_ORGANIZATION_MAPPING';
        ELSIF v_effective_le_count > 1 THEN
            v_classification := 'MIGRATION_REVIEW_REQUIRED';
            v_reason := 'Multiple effective Legal Entities — ambiguous employer of record';
            v_issue_code := 'MISSING_ORGANIZATION_MAPPING';
        END IF;

        IF v_emp.user_id IS NOT NULL THEN
            SELECT COUNT(*)
              INTO v_dup_count
              FROM hr_employees
             WHERE tenant_id = p_tenant_id
               AND user_id = v_emp.user_id;

            IF v_dup_count > 1 THEN
                v_classification := 'MIGRATION_BLOCKED';
                v_reason := 'Duplicate user_id across multiple legacy employments';
                v_issue_code := 'DUPLICATE_USER_ID';
            END IF;
        END IF;

        IF v_emp.user_id IS NOT NULL AND v_classification = 'AUTO_MIGRATE' THEN
            PERFORM 1
              FROM hr_people p
             WHERE p.tenant_id = p_tenant_id
               AND p.user_id = v_emp.user_id;

            IF FOUND THEN
                v_classification := 'MIGRATION_REVIEW_REQUIRED';
                v_reason := 'Existing canonical Person with the same user_id';
                v_issue_code := 'AMBIGUOUS_PERSON_IDENTITY';
            END IF;
        END IF;

        IF v_emp.department_id IS NOT NULL AND v_classification = 'AUTO_MIGRATE' THEN
            PERFORM 1
              FROM hr_org_units ou
              JOIN hr_org_unit_versions ouv
                ON ouv.tenant_id = ou.tenant_id
               AND ouv.org_unit_id = ou.id
             WHERE ou.tenant_id = p_tenant_id
               AND ou.id = v_emp.department_id
               AND ouv.status = 'ACTIVE'
               AND ouv.effective_from <= p_as_of_date
               AND (ouv.effective_to IS NULL OR ouv.effective_to >= p_as_of_date);

            IF NOT FOUND THEN
                v_classification := 'MIGRATION_REVIEW_REQUIRED';
                v_reason := 'Unresolved department mapping';
                v_issue_code := 'MISSING_DEPARTMENT_MAPPING';
            END IF;
        END IF;

        IF v_emp.position_id IS NOT NULL AND v_classification = 'AUTO_MIGRATE' THEN
            PERFORM 1
              FROM hr_position_versions pv
             WHERE pv.tenant_id = p_tenant_id
               AND pv.position_id = v_emp.position_id
               AND pv.status = 'ACTIVE'
               AND pv.effective_from <= p_as_of_date
               AND (pv.effective_to IS NULL OR pv.effective_to >= p_as_of_date);

            IF NOT FOUND THEN
                v_classification := 'MIGRATION_REVIEW_REQUIRED';
                v_reason := 'Unresolved position mapping';
                v_issue_code := 'MISSING_POSITION_MAPPING';
            END IF;
        END IF;

        IF v_emp.manager_id IS NOT NULL THEN
            SELECT EXISTS (
                SELECT 1
                  FROM hr_employees manager
                 WHERE manager.tenant_id = p_tenant_id
                   AND manager.id = v_emp.manager_id
            )
            INTO v_manager_exists;

            IF NOT v_manager_exists THEN
                IF v_classification = 'AUTO_MIGRATE' THEN
                    v_classification := 'MIGRATION_REVIEW_REQUIRED';
                    v_reason := 'Manager legacy employment does not exist in the tenant';
                    v_issue_code := 'MISSING_MANAGER_MAPPING';
                END IF;
            ELSIF v_issue_code IS NULL THEN
                -- Manager exists, but its canonical assignment may not exist yet.
                -- Record a review item without blocking; backfill resolves it after
                -- all PRIMARY assignments are created.
                INSERT INTO hr_migration_review_items
                    (tenant_id, legacy_entity_type, legacy_entity_id, issue_code,
                     severity, review_reason, resolution_state, created_at, updated_at)
                VALUES
                    (p_tenant_id, 'EMPLOYEE', v_emp.id, 'MISSING_MANAGER_MAPPING',
                     'REVIEW', 'Manager mapping pending canonical assignment',
                     'OPEN', NOW(), NOW())
                ON CONFLICT DO NOTHING;
            END IF;
        END IF;

        SELECT EXISTS (
            SELECT 1
              FROM hr_legacy_employee_mappings
             WHERE tenant_id = p_tenant_id
               AND legacy_employee_id = v_emp.id
        )
        INTO v_mapping_exists;

        IF v_mapping_exists THEN
            UPDATE hr_legacy_employee_mappings
               SET classification = v_classification,
                   review_reason = v_reason
             WHERE tenant_id = p_tenant_id
               AND legacy_employee_id = v_emp.id;
        ELSE
            INSERT INTO hr_legacy_employee_mappings
                (id, tenant_id, legacy_employee_id, classification, review_reason, created_at)
            VALUES
                (gen_random_uuid(), p_tenant_id, v_emp.id, v_classification, v_reason, NOW());
        END IF;

        IF v_classification <> 'AUTO_MIGRATE' AND v_issue_code IS NOT NULL THEN
            INSERT INTO hr_migration_review_items
                (tenant_id, legacy_entity_type, legacy_entity_id, issue_code,
                 severity, review_reason, resolution_state, created_at, updated_at)
            VALUES
                (p_tenant_id, 'EMPLOYEE', v_emp.id, v_issue_code,
                 CASE WHEN v_classification = 'MIGRATION_BLOCKED' THEN 'BLOCKED' ELSE 'REVIEW' END,
                 COALESCE(v_reason, v_issue_code), 'OPEN', NOW(), NOW())
            ON CONFLICT DO NOTHING;
        END IF;
    END LOOP;
END;
$$;

CREATE OR REPLACE FUNCTION hr_precheck_tenant(p_tenant_id UUID)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_as_of DATE;
BEGIN
    PERFORM hr_assert_migration_tenant_scope(p_tenant_id);
    v_as_of := hr_default_migration_as_of_date(p_tenant_id);
    PERFORM hr_precheck_tenant(p_tenant_id, v_as_of);
END;
$$;

REVOKE ALL ON FUNCTION hr_assert_migration_tenant_scope(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION hr_default_migration_as_of_date(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION hr_precheck_tenant(UUID, DATE) FROM PUBLIC;
REVOKE ALL ON FUNCTION hr_precheck_tenant(UUID) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION hr_assert_migration_tenant_scope(UUID) TO CURRENT_USER;
GRANT EXECUTE ON FUNCTION hr_default_migration_as_of_date(UUID) TO CURRENT_USER;
GRANT EXECUTE ON FUNCTION hr_precheck_tenant(UUID, DATE) TO CURRENT_USER;
GRANT EXECUTE ON FUNCTION hr_precheck_tenant(UUID) TO CURRENT_USER;

-- ============================================================
-- HRM-G0 / WS2 / Task 6 — Final Reconciliation
-- Authoritative source. Deployed verbatim by R__finalize_hr_backfill_closure.sql.
-- The machine-readable report and the CANONICAL decision share one gate matrix.
-- ============================================================

CREATE OR REPLACE FUNCTION hr_reconcile_tenant_report(p_tenant_id UUID, p_as_of_date DATE)
RETURNS TABLE(gate_name TEXT, gate_value BIGINT, passed BOOLEAN)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_legacy_total BIGINT;
    v_canonical_total BIGINT;
    v_person_missing BIGINT;
    v_legal_entity_missing BIGINT;
    v_primary_missing BIGINT;
    v_department_missing BIGINT;
    v_position_missing BIGINT;
    v_manager_unresolved BIGINT;
    v_unresolved BIGINT;
    v_open_reviews BIGINT;
    v_duplicate_mapping BIGINT;
    v_orphan_mapping BIGINT;
    v_cross_tenant_mismatch BIGINT;
    v_unaccounted BIGINT;
BEGIN
    PERFORM hr_assert_migration_tenant_scope(p_tenant_id);

    IF p_as_of_date IS NULL THEN
        RAISE EXCEPTION 'Task 6 as-of date is required'
            USING ERRCODE = '22004';
    END IF;

    SELECT COUNT(*)::BIGINT
      INTO v_legacy_total
      FROM hr_employees e
     WHERE e.tenant_id = p_tenant_id;

    SELECT COUNT(*)::BIGINT
      INTO v_canonical_total
      FROM hr_employees e
     WHERE e.tenant_id = p_tenant_id
       AND e.person_id IS NOT NULL
       AND e.legal_entity_id IS NOT NULL
       AND EXISTS (
            SELECT 1
              FROM hr_legacy_employee_mappings m
             WHERE m.tenant_id = p_tenant_id
               AND m.legacy_employee_id = e.id
               AND m.classification = 'AUTO_MIGRATE'
               AND m.canonical_person_id = e.person_id
       );

    SELECT COUNT(*)::BIGINT
      INTO v_person_missing
      FROM hr_employees e
     WHERE e.tenant_id = p_tenant_id
       AND NOT EXISTS (
            SELECT 1
              FROM hr_legacy_employee_mappings m
             WHERE m.tenant_id = p_tenant_id
               AND m.legacy_employee_id = e.id
               AND m.classification = 'AUTO_MIGRATE'
               AND m.canonical_person_id IS NOT NULL
               AND e.person_id = m.canonical_person_id
       );

    SELECT COUNT(*)::BIGINT
      INTO v_legal_entity_missing
      FROM hr_employees e
     WHERE e.tenant_id = p_tenant_id
       AND EXISTS (
            SELECT 1
              FROM hr_legacy_employee_mappings m
             WHERE m.tenant_id = p_tenant_id
               AND m.legacy_employee_id = e.id
               AND m.classification = 'AUTO_MIGRATE'
               AND m.canonical_person_id IS NOT NULL
       )
       AND (
            e.legal_entity_id IS NULL
            OR NOT EXISTS (
                SELECT 1
                  FROM legal_entities le
                  JOIN organization_legal_entities ole
                    ON ole.tenant_id = le.tenant_id
                   AND ole.legal_entity_id = le.id
                 WHERE le.tenant_id = p_tenant_id
                   AND le.id = e.legal_entity_id
                   AND le.status = 'ACTIVE'
                   AND ole.status = 'ACTIVE'
                   AND ole.effective_from <= p_as_of_date
                   AND (ole.effective_to IS NULL OR ole.effective_to >= p_as_of_date)
            )
       );

    SELECT COUNT(*)::BIGINT
      INTO v_primary_missing
      FROM hr_employees e
     WHERE e.tenant_id = p_tenant_id
       AND EXISTS (
            SELECT 1
              FROM hr_legacy_employee_mappings m
             WHERE m.tenant_id = p_tenant_id
               AND m.legacy_employee_id = e.id
               AND m.classification = 'AUTO_MIGRATE'
               AND m.canonical_person_id IS NOT NULL
       )
       AND NOT EXISTS (
            SELECT 1
              FROM hr_employee_assignments a
             WHERE a.tenant_id = p_tenant_id
               AND a.employment_id = e.id
               AND a.assignment_type = 'PRIMARY'
               AND a.status = 'ACTIVE'
               AND a.effective_from <= p_as_of_date
               AND (a.effective_to IS NULL OR a.effective_to >= p_as_of_date)
       );

    SELECT COUNT(*)::BIGINT
      INTO v_department_missing
      FROM hr_departments d
     WHERE d.tenant_id = p_tenant_id
       AND NOT EXISTS (
            SELECT 1
              FROM hr_org_units ou
              JOIN hr_org_unit_versions ouv
                ON ouv.tenant_id = ou.tenant_id
               AND ouv.org_unit_id = ou.id
             WHERE ou.tenant_id = p_tenant_id
               AND ou.id = d.id
               AND ouv.status = 'ACTIVE'
               AND ouv.effective_from <= p_as_of_date
               AND (ouv.effective_to IS NULL OR ouv.effective_to >= p_as_of_date)
       );

    SELECT COUNT(*)::BIGINT
      INTO v_position_missing
      FROM hr_positions p
     WHERE p.tenant_id = p_tenant_id
       AND NOT EXISTS (
            SELECT 1
              FROM hr_position_versions pv
             WHERE pv.tenant_id = p_tenant_id
               AND pv.position_id = p.id
               AND pv.status = 'ACTIVE'
               AND pv.effective_from <= p_as_of_date
               AND (pv.effective_to IS NULL OR pv.effective_to >= p_as_of_date)
       );

    SELECT COUNT(*)::BIGINT
      INTO v_manager_unresolved
      FROM hr_employees e
     WHERE e.tenant_id = p_tenant_id
       AND e.manager_id IS NOT NULL
       AND NOT EXISTS (
            SELECT 1
              FROM hr_employee_assignments subordinate
              JOIN hr_employee_assignments manager
                ON manager.tenant_id = subordinate.tenant_id
               AND manager.employment_id = e.manager_id
               AND manager.assignment_type = 'PRIMARY'
               AND manager.status = 'ACTIVE'
               AND manager.effective_from <= p_as_of_date
               AND (manager.effective_to IS NULL OR manager.effective_to >= p_as_of_date)
             WHERE subordinate.tenant_id = p_tenant_id
               AND subordinate.employment_id = e.id
               AND subordinate.assignment_type = 'PRIMARY'
               AND subordinate.status = 'ACTIVE'
               AND subordinate.effective_from <= p_as_of_date
               AND (subordinate.effective_to IS NULL OR subordinate.effective_to >= p_as_of_date)
               AND subordinate.reports_to_assignment_id = manager.id
       );

    SELECT COUNT(DISTINCT m.legacy_employee_id)::BIGINT
      INTO v_unresolved
      FROM hr_legacy_employee_mappings m
     WHERE m.tenant_id = p_tenant_id
       AND (
            m.classification IN ('MIGRATION_REVIEW_REQUIRED', 'MIGRATION_BLOCKED')
            OR (m.classification = 'AUTO_MIGRATE' AND m.canonical_person_id IS NULL)
       );

    SELECT COUNT(*)::BIGINT
      INTO v_open_reviews
      FROM hr_migration_review_items r
     WHERE r.tenant_id = p_tenant_id
       AND r.resolution_state = 'OPEN';

    SELECT COUNT(*)::BIGINT
      INTO v_duplicate_mapping
      FROM (
          SELECT m.legacy_employee_id
            FROM hr_legacy_employee_mappings m
           WHERE m.tenant_id = p_tenant_id
           GROUP BY m.legacy_employee_id
          HAVING COUNT(*) > 1
      ) duplicate_rows;

    SELECT COUNT(*)::BIGINT
      INTO v_orphan_mapping
      FROM hr_legacy_employee_mappings m
     WHERE m.tenant_id = p_tenant_id
       AND NOT EXISTS (
            SELECT 1
              FROM hr_employees e
             WHERE e.tenant_id = p_tenant_id
               AND e.id = m.legacy_employee_id
       );

    SELECT (
        (SELECT COUNT(*) FROM hr_legacy_employee_mappings m
          WHERE m.tenant_id = p_tenant_id
            AND m.canonical_person_id IS NOT NULL
            AND NOT EXISTS (
                SELECT 1 FROM hr_people p
                 WHERE p.tenant_id = p_tenant_id
                   AND p.id = m.canonical_person_id
            ))
        +
        (SELECT COUNT(*) FROM hr_employees e
          WHERE e.tenant_id = p_tenant_id
            AND e.legal_entity_id IS NOT NULL
            AND NOT EXISTS (
                SELECT 1 FROM legal_entities le
                 WHERE le.tenant_id = p_tenant_id
                   AND le.id = e.legal_entity_id
            ))
        +
        (SELECT COUNT(*) FROM hr_employee_assignments a
          WHERE a.tenant_id = p_tenant_id
            AND NOT EXISTS (
                SELECT 1 FROM organizations o
                 WHERE o.tenant_id = p_tenant_id
                   AND o.id = a.organization_id
            ))
        +
        (SELECT COUNT(*) FROM hr_employee_assignments a
          WHERE a.tenant_id = p_tenant_id
            AND a.org_unit_id IS NOT NULL
            AND NOT EXISTS (
                SELECT 1 FROM hr_org_units ou
                 WHERE ou.tenant_id = p_tenant_id
                   AND ou.id = a.org_unit_id
            ))
        +
        (SELECT COUNT(*) FROM hr_employee_assignments a
          WHERE a.tenant_id = p_tenant_id
            AND a.position_id IS NOT NULL
            AND NOT EXISTS (
                SELECT 1 FROM hr_positions p
                 WHERE p.tenant_id = p_tenant_id
                   AND p.id = a.position_id
            ))
    )::BIGINT
    INTO v_cross_tenant_mismatch;

    SELECT COUNT(*)::BIGINT
      INTO v_unaccounted
      FROM hr_employees e
     WHERE e.tenant_id = p_tenant_id
       AND (
            SELECT COUNT(*)
              FROM hr_legacy_employee_mappings m
             WHERE m.tenant_id = p_tenant_id
               AND m.legacy_employee_id = e.id
       ) <> 1;

    RETURN QUERY SELECT 'LEGACY_EMPLOYEE_COUNT'::TEXT, v_legacy_total, TRUE;
    RETURN QUERY SELECT 'CANONICAL_EMPLOYMENT_COUNT'::TEXT, v_canonical_total,
                        (v_canonical_total = v_legacy_total);
    RETURN QUERY SELECT 'PERSON_MAPPING_MISSING'::TEXT, v_person_missing, (v_person_missing = 0);
    RETURN QUERY SELECT 'LEGAL_ENTITY_MAPPING_MISSING'::TEXT, v_legal_entity_missing, (v_legal_entity_missing = 0);
    RETURN QUERY SELECT 'PRIMARY_ASSIGNMENT_MISSING'::TEXT, v_primary_missing, (v_primary_missing = 0);
    RETURN QUERY SELECT 'DEPARTMENT_MAPPING_MISSING'::TEXT, v_department_missing, (v_department_missing = 0);
    RETURN QUERY SELECT 'POSITION_MAPPING_MISSING'::TEXT, v_position_missing, (v_position_missing = 0);
    RETURN QUERY SELECT 'MANAGER_MAPPING_UNRESOLVED'::TEXT, v_manager_unresolved, (v_manager_unresolved = 0);
    RETURN QUERY SELECT 'UNRESOLVED_MIGRATION_ROWS'::TEXT, v_unresolved, (v_unresolved = 0);
    RETURN QUERY SELECT 'OPEN_REVIEW_ITEMS'::TEXT, v_open_reviews, (v_open_reviews = 0);
    RETURN QUERY SELECT 'DUPLICATE_MAPPING'::TEXT, v_duplicate_mapping, (v_duplicate_mapping = 0);
    RETURN QUERY SELECT 'ORPHAN_MAPPING'::TEXT, v_orphan_mapping, (v_orphan_mapping = 0);
    RETURN QUERY SELECT 'CROSS_TENANT_MISMATCH'::TEXT, v_cross_tenant_mismatch, (v_cross_tenant_mismatch = 0);
    RETURN QUERY SELECT 'UNACCOUNTED_ROWS'::TEXT, v_unaccounted, (v_unaccounted = 0);
END;
$$;

CREATE OR REPLACE FUNCTION hr_reconcile_tenant_report(p_tenant_id UUID)
RETURNS TABLE(gate_name TEXT, gate_value BIGINT, passed BOOLEAN)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_as_of DATE;
BEGIN
    PERFORM hr_assert_migration_tenant_scope(p_tenant_id);
    v_as_of := hr_default_migration_as_of_date(p_tenant_id);
    RETURN QUERY
        SELECT r.gate_name, r.gate_value, r.passed
          FROM hr_reconcile_tenant_report(p_tenant_id, v_as_of) r;
END;
$$;

CREATE OR REPLACE FUNCTION hr_reconcile_tenant(p_tenant_id UUID, p_as_of_date DATE)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_legacy_total BIGINT;
    v_failed_gates BIGINT;
    v_final_state VARCHAR(20);
BEGIN
    PERFORM hr_assert_migration_tenant_scope(p_tenant_id);

    SELECT gate_value
      INTO v_legacy_total
      FROM hr_reconcile_tenant_report(p_tenant_id, p_as_of_date)
     WHERE gate_name = 'LEGACY_EMPLOYEE_COUNT';

    SELECT COUNT(*)::BIGINT
      INTO v_failed_gates
      FROM hr_reconcile_tenant_report(p_tenant_id, p_as_of_date)
     WHERE passed = FALSE;

    IF COALESCE(v_legacy_total, 0) = 0 THEN
        v_final_state := 'LEGACY';
    ELSIF v_failed_gates = 0 THEN
        v_final_state := 'CANONICAL';
    ELSE
        v_final_state := 'BLOCKED';
    END IF;

    INSERT INTO hr_migration_tenant_state (tenant_id, state, updated_at)
    VALUES (p_tenant_id, v_final_state, NOW())
    ON CONFLICT (tenant_id)
    DO UPDATE SET state = v_final_state, updated_at = NOW();
END;
$$;

CREATE OR REPLACE FUNCTION hr_reconcile_tenant(p_tenant_id UUID)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_as_of DATE;
BEGIN
    PERFORM hr_assert_migration_tenant_scope(p_tenant_id);
    v_as_of := hr_default_migration_as_of_date(p_tenant_id);
    PERFORM hr_reconcile_tenant(p_tenant_id, v_as_of);
END;
$$;

REVOKE ALL ON FUNCTION hr_reconcile_tenant_report(UUID, DATE) FROM PUBLIC;
REVOKE ALL ON FUNCTION hr_reconcile_tenant_report(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION hr_reconcile_tenant(UUID, DATE) FROM PUBLIC;
REVOKE ALL ON FUNCTION hr_reconcile_tenant(UUID) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION hr_reconcile_tenant_report(UUID, DATE) TO CURRENT_USER;
GRANT EXECUTE ON FUNCTION hr_reconcile_tenant_report(UUID) TO CURRENT_USER;
GRANT EXECUTE ON FUNCTION hr_reconcile_tenant(UUID, DATE) TO CURRENT_USER;
GRANT EXECUTE ON FUNCTION hr_reconcile_tenant(UUID) TO CURRENT_USER;

-- ============================================================
-- HRM-G0 / WS2 / Task 6 — Final Deterministic Backfill
-- Authoritative source. Deployed verbatim by R__finalize_hr_backfill_closure.sql.
-- PRECHECK → structure backfill → PRECHECK → canonical graph → manager resolution → RECONCILE.
-- ============================================================

CREATE OR REPLACE FUNCTION hr_backfill_tenant(p_tenant_id UUID, p_as_of_date DATE)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_org_id UUID;
    v_legal_entity_id UUID;
    v_effective_org_count INTEGER;
    v_effective_le_count INTEGER;
    v_department RECORD;
    v_position RECORD;
    v_employee RECORD;
    v_person_id UUID;
    v_assignment_id UUID;
    v_manager_assignment_id UUID;
BEGIN
    PERFORM hr_assert_migration_tenant_scope(p_tenant_id);

    IF p_as_of_date IS NULL THEN
        RAISE EXCEPTION 'Task 6 as-of date is required'
            USING ERRCODE = '22004';
    END IF;

    PERFORM hr_precheck_tenant(p_tenant_id, p_as_of_date);

    SELECT COUNT(DISTINCT ole.organization_id),
           COUNT(DISTINCT ole.legal_entity_id),
           MIN(ole.organization_id::text)::UUID,
           MIN(ole.legal_entity_id::text)::UUID
      INTO v_effective_org_count, v_effective_le_count, v_org_id, v_legal_entity_id
      FROM organization_legal_entities ole
      JOIN legal_entities le
        ON le.id = ole.legal_entity_id
       AND le.tenant_id = ole.tenant_id
     WHERE ole.tenant_id = p_tenant_id
       AND ole.status = 'ACTIVE'
       AND le.status = 'ACTIVE'
       AND ole.effective_from <= p_as_of_date
       AND (ole.effective_to IS NULL OR ole.effective_to >= p_as_of_date);

    IF v_effective_org_count = 1 AND v_effective_le_count = 1 THEN
        -- Backfill every legacy department into a stable Org Unit identity + initial effective version.
        FOR v_department IN
            SELECT id, name, code
              FROM hr_departments
             WHERE tenant_id = p_tenant_id
             ORDER BY id
        LOOP
            IF NOT EXISTS (
                SELECT 1
                  FROM hr_org_units ou
                 WHERE ou.tenant_id = p_tenant_id
                   AND ou.id = v_department.id
            ) THEN
                INSERT INTO hr_org_units
                    (id, tenant_id, organization_id, stable_code, created_at)
                VALUES
                    (v_department.id, p_tenant_id, v_org_id,
                     COALESCE(v_department.code, 'DEPT-' || v_department.id::text), NOW());
            END IF;

            IF NOT EXISTS (
                SELECT 1
                  FROM hr_org_unit_versions ouv
                 WHERE ouv.tenant_id = p_tenant_id
                   AND ouv.org_unit_id = v_department.id
                   AND ouv.status = 'ACTIVE'
                   AND ouv.effective_from <= p_as_of_date
                   AND (ouv.effective_to IS NULL OR ouv.effective_to >= p_as_of_date)
            ) THEN
                INSERT INTO hr_org_unit_versions
                    (id, tenant_id, org_unit_id, name, code, unit_type,
                     effective_from, status)
                VALUES
                    (gen_random_uuid(), p_tenant_id, v_department.id,
                     v_department.name, COALESCE(v_department.code, 'DEPT'),
                     'DEPARTMENT', p_as_of_date, 'ACTIVE');
            END IF;
        END LOOP;

        UPDATE hr_migration_review_items r
           SET resolution_state = 'RESOLVED',
               updated_at = NOW()
         WHERE r.tenant_id = p_tenant_id
           AND r.issue_code = 'MISSING_DEPARTMENT_MAPPING'
           AND r.resolution_state = 'OPEN'
           AND EXISTS (
                SELECT 1
                  FROM hr_employees e
                  JOIN hr_org_units ou
                    ON ou.tenant_id = e.tenant_id
                   AND ou.id = e.department_id
                 WHERE e.tenant_id = p_tenant_id
                   AND e.id = r.legacy_entity_id
           );

        -- hr_positions is the stable Position identity; create its effective version.
        FOR v_position IN
            SELECT id, title, code
              FROM hr_positions
             WHERE tenant_id = p_tenant_id
             ORDER BY id
        LOOP
            IF NOT EXISTS (
                SELECT 1
                  FROM hr_position_versions pv
                 WHERE pv.tenant_id = p_tenant_id
                   AND pv.position_id = v_position.id
                   AND pv.status = 'ACTIVE'
                   AND pv.effective_from <= p_as_of_date
                   AND (pv.effective_to IS NULL OR pv.effective_to >= p_as_of_date)
            ) THEN
                INSERT INTO hr_position_versions
                    (id, tenant_id, position_id, organization_id, title,
                     effective_from, status)
                VALUES
                    (gen_random_uuid(), p_tenant_id, v_position.id, v_org_id,
                     v_position.title, p_as_of_date, 'ACTIVE');
            END IF;
        END LOOP;

        UPDATE hr_migration_review_items r
           SET resolution_state = 'RESOLVED',
               updated_at = NOW()
         WHERE r.tenant_id = p_tenant_id
           AND r.issue_code = 'MISSING_POSITION_MAPPING'
           AND r.resolution_state = 'OPEN'
           AND EXISTS (
                SELECT 1
                  FROM hr_employees e
                  JOIN hr_position_versions pv
                    ON pv.tenant_id = e.tenant_id
                   AND pv.position_id = e.position_id
                 WHERE e.tenant_id = p_tenant_id
                   AND e.id = r.legacy_entity_id
                   AND pv.status = 'ACTIVE'
                   AND pv.effective_from <= p_as_of_date
                   AND (pv.effective_to IS NULL OR pv.effective_to >= p_as_of_date)
           );
    END IF;

    -- Reclassify rows now that deterministic structure mappings may exist.
    PERFORM hr_precheck_tenant(p_tenant_id, p_as_of_date);

    IF v_effective_org_count = 1 AND v_effective_le_count = 1 THEN
        FOR v_employee IN
            SELECT e.id, e.user_id, e.first_name, e.last_name, e.display_name,
                   e.hire_date, e.department_id, e.position_id, e.manager_id
              FROM hr_employees e
              JOIN hr_legacy_employee_mappings m
                ON m.tenant_id = e.tenant_id
               AND m.legacy_employee_id = e.id
             WHERE e.tenant_id = p_tenant_id
               AND m.classification = 'AUTO_MIGRATE'
               AND m.canonical_person_id IS NULL
             ORDER BY e.id
        LOOP
            v_person_id := NULL;

            IF v_employee.user_id IS NOT NULL THEN
                SELECT p.id
                  INTO v_person_id
                  FROM hr_people p
                 WHERE p.tenant_id = p_tenant_id
                   AND p.user_id = v_employee.user_id
                 LIMIT 1;
            END IF;

            IF v_person_id IS NULL THEN
                v_person_id := gen_random_uuid();
                INSERT INTO hr_people
                    (id, tenant_id, user_id, first_name, last_name, display_name,
                     version, created_at, updated_at)
                VALUES
                    (v_person_id, p_tenant_id, v_employee.user_id,
                     v_employee.first_name, v_employee.last_name, v_employee.display_name,
                     0, NOW(), NOW());
            END IF;

            UPDATE hr_employees
               SET person_id = COALESCE(person_id, v_person_id),
                   legal_entity_id = COALESCE(legal_entity_id, v_legal_entity_id)
             WHERE id = v_employee.id
               AND tenant_id = p_tenant_id;

            UPDATE hr_legacy_employee_mappings
               SET canonical_person_id = COALESCE(canonical_person_id, v_person_id),
                   classification = 'AUTO_MIGRATE',
                   review_reason = NULL
             WHERE tenant_id = p_tenant_id
               AND legacy_employee_id = v_employee.id;

            SELECT a.id
              INTO v_assignment_id
              FROM hr_employee_assignments a
             WHERE a.tenant_id = p_tenant_id
               AND a.employment_id = v_employee.id
               AND a.assignment_type = 'PRIMARY'
               AND a.status = 'ACTIVE'
               AND a.effective_to IS NULL
             LIMIT 1;

            IF v_assignment_id IS NULL THEN
                v_assignment_id := gen_random_uuid();

                INSERT INTO hr_employee_assignments
                    (id, tenant_id, employment_id, organization_id, org_unit_id, position_id,
                     assignment_type, occupancy_mode, allocation_percent,
                     effective_from, status, version, created_at, updated_at)
                VALUES
                    (v_assignment_id, p_tenant_id, v_employee.id, v_org_id,
                     CASE WHEN v_employee.department_id IS NOT NULL
                               AND EXISTS (
                                   SELECT 1 FROM hr_org_units ou
                                    WHERE ou.tenant_id = p_tenant_id
                                      AND ou.id = v_employee.department_id
                               )
                          THEN v_employee.department_id ELSE NULL END,
                     CASE WHEN v_employee.position_id IS NOT NULL
                               AND EXISTS (
                                   SELECT 1 FROM hr_position_versions pv
                                    WHERE pv.tenant_id = p_tenant_id
                                      AND pv.position_id = v_employee.position_id
                                      AND pv.status = 'ACTIVE'
                                      AND pv.effective_from <= p_as_of_date
                                      AND (pv.effective_to IS NULL OR pv.effective_to >= p_as_of_date)
                               )
                          THEN v_employee.position_id ELSE NULL END,
                     'PRIMARY', 'NON_OCCUPYING', 100.00,
                     COALESCE(v_employee.hire_date, p_as_of_date),
                     'ACTIVE', 0, NOW(), NOW());
            END IF;
        END LOOP;

        -- Resolve reporting only after every migratable PRIMARY assignment exists.
        FOR v_employee IN
            SELECT e.id, e.manager_id, subordinate.id AS assignment_id
              FROM hr_employees e
              JOIN hr_employee_assignments subordinate
                ON subordinate.tenant_id = e.tenant_id
               AND subordinate.employment_id = e.id
               AND subordinate.assignment_type = 'PRIMARY'
               AND subordinate.status = 'ACTIVE'
             WHERE e.tenant_id = p_tenant_id
               AND e.manager_id IS NOT NULL
               AND subordinate.effective_from <= p_as_of_date
               AND (subordinate.effective_to IS NULL OR subordinate.effective_to >= p_as_of_date)
             ORDER BY e.id
        LOOP
            SELECT manager.id
              INTO v_manager_assignment_id
              FROM hr_employee_assignments manager
             WHERE manager.tenant_id = p_tenant_id
               AND manager.employment_id = v_employee.manager_id
               AND manager.assignment_type = 'PRIMARY'
               AND manager.status = 'ACTIVE'
               AND manager.effective_from <= p_as_of_date
               AND (manager.effective_to IS NULL OR manager.effective_to >= p_as_of_date)
             LIMIT 1;

            IF v_manager_assignment_id IS NOT NULL THEN
                UPDATE hr_employee_assignments
                   SET reports_to_assignment_id = v_manager_assignment_id,
                       updated_at = NOW()
                 WHERE id = v_employee.assignment_id
                   AND tenant_id = p_tenant_id
                   AND reports_to_assignment_id IS DISTINCT FROM v_manager_assignment_id;

                UPDATE hr_migration_review_items
                   SET resolution_state = 'RESOLVED',
                       updated_at = NOW()
                 WHERE tenant_id = p_tenant_id
                   AND legacy_entity_id = v_employee.id
                   AND issue_code = 'MISSING_MANAGER_MAPPING'
                   AND resolution_state = 'OPEN';
            END IF;
        END LOOP;
    END IF;

    PERFORM hr_reconcile_tenant(p_tenant_id, p_as_of_date);
END;
$$;

CREATE OR REPLACE FUNCTION hr_backfill_tenant(p_tenant_id UUID)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_as_of DATE;
BEGIN
    PERFORM hr_assert_migration_tenant_scope(p_tenant_id);
    v_as_of := hr_default_migration_as_of_date(p_tenant_id);
    PERFORM hr_backfill_tenant(p_tenant_id, v_as_of);
END;
$$;

REVOKE ALL ON FUNCTION hr_backfill_tenant(UUID, DATE) FROM PUBLIC;
REVOKE ALL ON FUNCTION hr_backfill_tenant(UUID) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION hr_backfill_tenant(UUID, DATE) TO CURRENT_USER;
GRANT EXECUTE ON FUNCTION hr_backfill_tenant(UUID) TO CURRENT_USER;
