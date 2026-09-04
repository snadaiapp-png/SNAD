-- ============================================================
-- HRM-G0 / WS2 / Task 6 — Install Backfill Orchestration Functions
-- ============================================================
-- This migration installs the three database functions that implement
-- the PRECHECK → BACKFILL → RECONCILE pipeline.
--
-- ONE SOURCE OF TRUTH:
--   The authoritative SQL source for these functions lives in:
--     scripts/hrm/g0-backfill-precheck.sql
--     scripts/hrm/g0-backfill.sql
--     scripts/hrm/g0-reconcile.sql
--
--   This Flyway migration is the DEPLOYMENT MECHANISM that makes the
--   functions available in the database. The scripts/hrm/ files are
--   the canonical source of truth for review and audit.
--
--   DO NOT allow drift between this migration and the scripts.
-- ============================================================

-- ============================================================
-- 1. PRECHECK FUNCTION
--    Source: scripts/hrm/g0-backfill-precheck.sql
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
    v_eligible_org_count INTEGER;
BEGIN
    PERFORM set_config('app.tenant_id', p_tenant_id::text, true);

    INSERT INTO hr_migration_tenant_state (tenant_id, state, updated_at)
    VALUES (p_tenant_id, 'MIGRATING', NOW())
    ON CONFLICT (tenant_id) DO UPDATE SET state = 'MIGRATING', updated_at = NOW();

    -- Count ACTIVE eligible organizations (STRICT: requires legal_entity link)
    SELECT COUNT(DISTINCT ole.organization_id) INTO v_eligible_org_count
    FROM organization_legal_entities ole
    JOIN legal_entities le ON le.id = ole.legal_entity_id AND le.tenant_id = ole.tenant_id
    WHERE ole.tenant_id = p_tenant_id
      AND ole.status = 'ACTIVE'
      AND le.status = 'ACTIVE'
      AND (ole.effective_to IS NULL OR ole.effective_to >= CURRENT_DATE);

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

        IF v_eligible_org_count = 0 THEN
            v_classification := 'MIGRATION_BLOCKED';
            v_reason := 'No active legal entity + eligible organization';
            v_issue_code := 'MISSING_ORGANIZATION_MAPPING';
        ELSIF v_eligible_org_count > 1 THEN
            v_classification := 'MIGRATION_REVIEW_REQUIRED';
            v_reason := 'Multiple eligible organizations — ambiguous mapping';
            v_issue_code := 'MISSING_ORGANIZATION_MAPPING';
        END IF;

        IF v_emp_record.user_id IS NOT NULL THEN
            SELECT COUNT(*) INTO v_dup_count
            FROM hr_employees
            WHERE tenant_id = p_tenant_id AND user_id = v_emp_record.user_id;

            IF v_dup_count > 1 THEN
                v_classification := 'MIGRATION_BLOCKED';
                v_reason := COALESCE(v_reason || '; ', '') || 'Duplicate user_id across multiple employees';
                v_issue_code := 'DUPLICATE_USER_ID';
            END IF;
        END IF;

        IF v_emp_record.user_id IS NOT NULL THEN
            PERFORM 1 FROM hr_people p
            WHERE p.tenant_id = p_tenant_id AND p.user_id = v_emp_record.user_id
              AND NOT EXISTS (
                  SELECT 1 FROM hr_employees e
                  WHERE e.id = v_emp_record.id
                    AND e.tenant_id = p_tenant_id
                    AND e.person_id = p.id
              );
            IF FOUND THEN
                v_classification := 'MIGRATION_REVIEW_REQUIRED';
                v_reason := COALESCE(v_reason || '; ', '') || 'Existing canonical Person with same user_id';
                v_issue_code := 'AMBIGUOUS_PERSON_IDENTITY';
            END IF;
        END IF;

        IF v_emp_record.department_id IS NOT NULL THEN
            PERFORM 1 FROM hr_org_units ou
            WHERE ou.tenant_id = p_tenant_id AND ou.id = v_emp_record.department_id;
            IF NOT FOUND THEN
                IF v_classification = 'AUTO_MIGRATE' THEN
                    v_classification := 'MIGRATION_REVIEW_REQUIRED';
                    v_reason := 'Unresolved department mapping';
                    v_issue_code := 'MISSING_DEPARTMENT_MAPPING';
                END IF;
            END IF;
        END IF;

        IF v_emp_record.position_id IS NOT NULL THEN
            PERFORM 1 FROM hr_position_versions pv
            WHERE pv.tenant_id = p_tenant_id AND pv.position_id = v_emp_record.position_id;
            IF NOT FOUND THEN
                IF v_classification = 'AUTO_MIGRATE' THEN
                    v_classification := 'MIGRATION_REVIEW_REQUIRED';
                    v_reason := 'Unresolved position mapping';
                    v_issue_code := 'MISSING_POSITION_MAPPING';
                END IF;
            END IF;
        END IF;

        -- Check 6: Manager mapping unresolved
        -- The manager_id references another legacy employee. Check if that
        -- employee exists. If it does, the manager will be processed in the
        -- same precheck pass. Create a review item for the manager mapping
        -- so the contract is verifiable. This does NOT block — it creates
        -- a review item that reconciliation will verify.
        IF v_emp_record.manager_id IS NOT NULL THEN
            PERFORM 1 FROM hr_employees
            WHERE tenant_id = p_tenant_id AND id = v_emp_record.manager_id;
            IF NOT FOUND THEN
                -- Manager doesn't exist as legacy employee → unresolved
                IF v_classification = 'AUTO_MIGRATE' THEN
                    v_classification := 'MIGRATION_REVIEW_REQUIRED';
                    v_reason := 'Unresolved manager mapping — manager not found';
                    v_issue_code := 'MISSING_MANAGER_MAPPING';
                END IF;
            ELSE
                -- Manager exists but no canonical assignment yet → create review item
                -- (does not block, but records the unresolved state)
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

        SELECT EXISTS(
            SELECT 1 FROM hr_legacy_employee_mappings
            WHERE tenant_id = p_tenant_id AND legacy_employee_id = v_emp_record.id
        ) INTO v_mapping_exists;

        IF v_mapping_exists THEN
            UPDATE hr_legacy_employee_mappings
            SET classification = v_classification,
                review_reason = v_reason
            WHERE tenant_id = p_tenant_id AND legacy_employee_id = v_emp_record.id;
        ELSE
            INSERT INTO hr_legacy_employee_mappings
                (id, tenant_id, legacy_employee_id, classification, review_reason, created_at)
            VALUES
                (gen_random_uuid(), p_tenant_id, v_emp_record.id, v_classification, v_reason, NOW());
        END IF;

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
-- 2. RECONCILE FUNCTION
--    Source: scripts/hrm/g0-reconcile.sql
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
    v_final_state     VARCHAR(20);
BEGIN
    PERFORM set_config('app.tenant_id', p_tenant_id::text, true);

    SELECT COUNT(*) INTO v_legacy_total
    FROM hr_employees WHERE tenant_id = p_tenant_id;

    SELECT COUNT(*) INTO v_resolved
    FROM hr_legacy_employee_mappings
    WHERE tenant_id = p_tenant_id
      AND classification = 'AUTO_MIGRATE'
      AND canonical_person_id IS NOT NULL;

    SELECT COUNT(DISTINCT legacy_employee_id) INTO v_unresolved
    FROM hr_legacy_employee_mappings
    WHERE tenant_id = p_tenant_id
      AND classification IN ('MIGRATION_REVIEW_REQUIRED', 'MIGRATION_BLOCKED', 'AUTO_MIGRATE')
      AND canonical_person_id IS NULL;

    SELECT COUNT(*) INTO v_person_missing
    FROM hr_legacy_employee_mappings m
    WHERE m.tenant_id = p_tenant_id
      AND m.classification = 'AUTO_MIGRATE'
      AND m.canonical_person_id IS NULL;

    SELECT COUNT(*) INTO v_primary_missing
    FROM hr_legacy_employee_mappings m
    WHERE m.tenant_id = p_tenant_id
      AND m.classification = 'AUTO_MIGRATE'
      AND m.canonical_person_id IS NOT NULL
      AND NOT EXISTS (
          SELECT 1 FROM hr_employee_assignments a
          WHERE a.tenant_id = p_tenant_id
            AND a.employment_id = m.legacy_employee_id
            AND a.assignment_type = 'PRIMARY'
      );

    SELECT COUNT(*) INTO v_dup_mapping
    FROM (
        SELECT legacy_employee_id, COUNT(*) AS cnt
        FROM hr_legacy_employee_mappings
        WHERE tenant_id = p_tenant_id
        GROUP BY legacy_employee_id
        HAVING COUNT(*) > 1
    ) dups;

    SELECT COUNT(*) INTO v_orphan_mapping
    FROM hr_legacy_employee_mappings m
    WHERE m.tenant_id = p_tenant_id
      AND NOT EXISTS (
          SELECT 1 FROM hr_employees e
          WHERE e.tenant_id = m.tenant_id AND e.id = m.legacy_employee_id
      );

    v_unaccounted := v_legacy_total - v_resolved - v_unresolved;

    IF v_unresolved = 0
       AND v_person_missing = 0
       AND v_primary_missing = 0
       AND v_dup_mapping = 0
       AND v_orphan_mapping = 0
       AND v_unaccounted = 0
       AND v_legacy_total > 0
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
-- 3. BACKFILL FUNCTION (orchestration adapter)
--    Source: scripts/hrm/g0-backfill.sql
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
    v_assignment_id UUID;
BEGIN
    PERFORM set_config('app.tenant_id', p_tenant_id::text, true);

    -- Step 1: Run precheck
    PERFORM hr_precheck_tenant(p_tenant_id);

    -- Step 2: Create canonical graph for AUTO_MIGRATE employees (pending)
    FOR v_emp_record IN
        SELECT e.id, e.user_id, e.employee_number, e.first_name, e.last_name,
               e.display_name, e.hire_date, e.employment_type, e.status
        FROM hr_employees e
        JOIN hr_legacy_employee_mappings m
            ON m.tenant_id = e.tenant_id AND m.legacy_employee_id = e.id
        WHERE e.tenant_id = p_tenant_id
          AND m.classification = 'AUTO_MIGRATE'
          AND m.canonical_person_id IS NULL
        ORDER BY e.id
    LOOP
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

        UPDATE hr_employees
        SET person_id = v_person_id
        WHERE id = v_emp_record.id
          AND tenant_id = p_tenant_id
          AND person_id IS NULL;

        UPDATE hr_legacy_employee_mappings
        SET canonical_person_id = v_person_id
        WHERE tenant_id = p_tenant_id
          AND legacy_employee_id = v_emp_record.id
          AND canonical_person_id IS NULL;

        SELECT ole.organization_id INTO v_org_id
        FROM organization_legal_entities ole
        JOIN legal_entities le ON le.id = ole.legal_entity_id AND le.tenant_id = ole.tenant_id
        WHERE ole.tenant_id = p_tenant_id
          AND ole.status = 'ACTIVE'
          AND le.status = 'ACTIVE'
          AND (ole.effective_to IS NULL OR ole.effective_to >= CURRENT_DATE)
        ORDER BY ole.organization_id
        LIMIT 1;

        IF v_org_id IS NOT NULL THEN
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
                    (id, tenant_id, employment_id, organization_id,
                     assignment_type, occupancy_mode, allocation_percent,
                     effective_from, status, version, created_at, updated_at)
                VALUES
                    (v_assignment_id, p_tenant_id, v_emp_record.id, v_org_id,
                     'PRIMARY', 'NON_OCCUPYING', 100.00,
                     COALESCE(v_emp_record.hire_date, '2026-01-01'::date),
                     'ACTIVE', 0, NOW(), NOW());
            END IF;
        END IF;
    END LOOP;

    -- Step 3: Run reconciliation
    PERFORM hr_reconcile_tenant(p_tenant_id);
END;
$$;
