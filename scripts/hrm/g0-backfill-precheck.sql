-- ============================================================
-- HRM-G0 / WS2 / Task 6 — Backfill Precheck (Authoritative Source)
-- ============================================================
-- This file is the AUTHORITATIVE SOURCE for the precheck logic.
-- The Flyway migration V20260831_5 installs the equivalent database
-- function. Do NOT allow drift between this file and the migration.
--
-- Classifies every legacy hr_employees row into:
--   AUTO_MIGRATE                — exactly 1 eligible org, unique user_id
--   MIGRATION_REVIEW_REQUIRED   — ambiguous org mapping, existing person, etc.
--   MIGRATION_BLOCKED           — no legal entity / no eligibility at all
--
-- STRICT RULE (no relaxation):
--   Organization alone is NOT sufficient.
--   Requires ACTIVE legal_entities + ACTIVE organization_legal_entities.
--
-- Idempotent — safe to re-run.
-- Does NOT mutate canonical tables (except mapping classification).
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

    -- Set state to MIGRATING
    INSERT INTO hr_migration_tenant_state (tenant_id, state, updated_at)
    VALUES (p_tenant_id, 'MIGRATING', NOW())
    ON CONFLICT (tenant_id) DO UPDATE SET state = 'MIGRATING', updated_at = NOW();

    -- Count ACTIVE eligible organizations (with ACTIVE legal_entity link)
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

        -- Check 1: Organization eligibility (STRICT — requires legal_entity link)
        IF v_eligible_org_count = 0 THEN
            v_classification := 'MIGRATION_BLOCKED';
            v_reason := 'No active legal entity + eligible organization';
            v_issue_code := 'MISSING_ORGANIZATION_MAPPING';
        ELSIF v_eligible_org_count > 1 THEN
            v_classification := 'MIGRATION_REVIEW_REQUIRED';
            v_reason := 'Multiple eligible organizations — ambiguous mapping';
            v_issue_code := 'MISSING_ORGANIZATION_MAPPING';
        END IF;

        -- Check 2: Duplicate user_id → BLOCKED
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

        -- Check 3: Pre-existing canonical Person with same user_id → ambiguous
        IF v_emp_record.user_id IS NOT NULL THEN
            PERFORM 1 FROM hr_people
            WHERE tenant_id = p_tenant_id AND user_id = v_emp_record.user_id;
            IF FOUND THEN
                v_classification := 'MIGRATION_REVIEW_REQUIRED';
                v_reason := COALESCE(v_reason || '; ', '') || 'Existing canonical Person with same user_id';
                v_issue_code := 'AMBIGUOUS_PERSON_IDENTITY';
            END IF;
        END IF;

        -- Check 4: Department mapping unresolved
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

        -- Check 5: Position mapping unresolved
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
        IF v_emp_record.manager_id IS NOT NULL THEN
            PERFORM 1 FROM hr_legacy_employee_mappings m
            WHERE m.tenant_id = p_tenant_id AND m.legacy_employee_id = v_emp_record.manager_id
              AND m.classification = 'AUTO_MIGRATE';
            IF NOT FOUND THEN
                IF v_classification = 'AUTO_MIGRATE' THEN
                    v_classification := 'MIGRATION_REVIEW_REQUIRED';
                    v_reason := 'Unresolved manager mapping';
                    v_issue_code := 'MISSING_MANAGER_MAPPING';
                END IF;
            END IF;
        END IF;

        -- Insert or update mapping (idempotent)
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

        -- Create review items for non-AUTO_MIGRATE (idempotent via unique index)
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
