-- ============================================================
-- HRM-G0 / WS2 / Task 6 — Reconcile (Authoritative Source)
-- ============================================================
-- This file is the AUTHORITATIVE SOURCE for the reconciliation logic.
-- The Flyway migration V20260831_5 installs the equivalent database
-- function. Do NOT allow drift between this file and the migration.
--
-- Computes reconciliation gates and sets the final tenant state:
--   CANONICAL  — all gates pass, 0 unresolved
--   BLOCKED    — any unresolved condition exists
--
-- Reconciliation arithmetic:
--   LEGACY_TOTAL = RESOLVED + UNRESOLVED
--   UNACCOUNTED_ROWS = 0
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
