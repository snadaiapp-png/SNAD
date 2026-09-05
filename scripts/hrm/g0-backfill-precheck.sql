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
