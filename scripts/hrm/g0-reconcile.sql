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
