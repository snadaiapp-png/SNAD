-- ============================================================
-- HRM-G0 / WS2 / Task 6 — Backfill (Authoritative Source)
-- ============================================================
-- This file is the AUTHORITATIVE SOURCE for the backfill logic.
-- The Flyway migration V20260831_5 installs the equivalent database
-- function. Do NOT allow drift between this file and the migration.
--
-- For each legacy employee classified AUTO_MIGRATE by precheck:
--   1. Create exactly one canonical Person (or reuse existing)
--   2. Link hr_employees.person_id to the canonical Person
--   3. Create one PRIMARY Assignment (respecting Task 4 invariants)
--   4. Update hr_legacy_employee_mappings with canonical_person_id
--
-- For non-AUTO_MIGRATE employees: skip (no guessing).
-- Idempotent: re-running creates zero duplicates.
-- No hard deletes of legacy rows.
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

    -- Step 1: Run precheck (idempotent)
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
        -- Check if canonical Person already exists (idempotent)
        v_person_id := NULL;
        IF v_emp_record.user_id IS NOT NULL THEN
            SELECT id INTO v_person_id
            FROM hr_people
            WHERE tenant_id = p_tenant_id AND user_id = v_emp_record.user_id
            LIMIT 1;
        END IF;

        -- If no existing Person, create one
        IF v_person_id IS NULL THEN
            v_person_id := gen_random_uuid();
            INSERT INTO hr_people
                (id, tenant_id, user_id, first_name, last_name, display_name, version, created_at, updated_at)
            VALUES
                (v_person_id, p_tenant_id, v_emp_record.user_id,
                 v_emp_record.first_name, v_emp_record.last_name, v_emp_record.display_name,
                 0, NOW(), NOW());
        END IF;

        -- Link hr_employees.person_id (idempotent)
        UPDATE hr_employees
        SET person_id = v_person_id
        WHERE id = v_emp_record.id
          AND tenant_id = p_tenant_id
          AND person_id IS NULL;

        -- Update mapping with canonical_person_id (idempotent)
        UPDATE hr_legacy_employee_mappings
        SET canonical_person_id = v_person_id
        WHERE tenant_id = p_tenant_id
          AND legacy_employee_id = v_emp_record.id
          AND canonical_person_id IS NULL;

        -- Get THE single eligible organization (exactly 1 for AUTO_MIGRATE)
        SELECT ole.organization_id INTO v_org_id
        FROM organization_legal_entities ole
        JOIN legal_entities le ON le.id = ole.legal_entity_id AND le.tenant_id = ole.tenant_id
        WHERE ole.tenant_id = p_tenant_id
          AND ole.status = 'ACTIVE'
          AND le.status = 'ACTIVE'
          AND (ole.effective_to IS NULL OR ole.effective_to >= CURRENT_DATE)
        ORDER BY ole.organization_id
        LIMIT 1;

        -- Create PRIMARY assignment if it doesn't exist (idempotent)
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

    -- Step 3: Run reconciliation to decide final state
    PERFORM hr_reconcile_tenant(p_tenant_id);
END;
$$;
