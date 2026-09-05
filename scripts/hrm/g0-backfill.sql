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
