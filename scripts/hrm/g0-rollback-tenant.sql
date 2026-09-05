-- ============================================================
-- HRM-G0 / WS5 Task 11 — Deterministic per-tenant rollback
--
-- Usage (operator, psql — NO credentials are stored in this file):
--   psql -v tenant_id="<uuid>" -f scripts/hrm/g0-rollback-tenant.sql
--
-- Authoritative semantics (WS5 plan Task 11 Step 3):
--   * BEFORE canonical writes begin (state = MIGRATING or BLOCKED):
--     rollback returns the tenant to LEGACY after removing ONLY the
--     incomplete canonical rows created by the failed attempt, using
--     hr_legacy_employee_mappings as the ledger of created rows.
--   * AFTER canonical writes begin (state = CANONICAL): destructive
--     data rollback is FORBIDDEN. Use application rollback / forward-fix
--     while canonical data remains authoritative. This script refuses.
--
-- Guarantees:
--   * fail-closed (ON_ERROR_STOP), tenant-scoped, auditable echoes
--   * idempotent: rerunning after success leaves a LEGACY tenant intact
--   * non-destructive beyond the attempt ledger: legacy hr_employees
--     rows are never deleted; deterministic structure (org units /
--     positions versions) is reused idempotently on rerun
-- ============================================================

\set ON_ERROR_STOP on

\if :{?tenant_id}
\else
\echo 'FATAL: psql variable tenant_id is required (e.g. -v tenant_id="<uuid>")'
\quit
\endif

\echo '== HRM-G0 tenant rollback =='
\echo 'Phase 0 — validate tenant identifier and bind tenant scope'

SELECT :'tenant_id'::uuid AS tenant;

-- Session-scoped tenant binding: a transaction-local GUC (is_local => true)
-- evaporates between statements under autocommit, which would leave every
-- later RLS check and hr_assert_migration_tenant_scope call fail-closed.
SELECT set_config('app.tenant_id', :'tenant_id', false) AS tenant_scope;

-- psql does not interpolate :variables inside dollar-quoted DO bodies, so
-- the block below reads the tenant from this temp context table instead.
DROP TABLE IF EXISTS _g0_tenant_ctx;
CREATE TEMP TABLE _g0_tenant_ctx AS
SELECT :'tenant_id'::uuid AS tenant_id;

\echo 'Phase 1 — read + lock migration state (refuse CANONICAL)'

DO $rollback$
DECLARE
    v_current VARCHAR(20);
    v_tenant  UUID;
    v_ledger  BIGINT;
    v_unlinked INTEGER;
    v_assignments_removed BIGINT;
    v_people_removed BIGINT;
BEGIN
    SELECT tenant_id INTO v_tenant FROM _g0_tenant_ctx;

    PERFORM hr_assert_migration_tenant_scope(v_tenant);

    SELECT state INTO v_current
      FROM hr_migration_tenant_state
     WHERE tenant_id = v_tenant
       FOR UPDATE;

    IF v_current IS NULL THEN
        v_current := 'LEGACY';
    END IF;

    IF v_current = 'CANONICAL' THEN
        RAISE EXCEPTION
            'rollback refused: tenant % is CANONICAL — canonical data is authoritative; use application rollback / forward-fix (no destructive data rollback)',
            v_tenant
            USING ERRCODE = 'P0001';
    END IF;

    IF v_current = 'LEGACY' THEN
        RAISE NOTICE 'tenant % is LEGACY — nothing to roll back', v_tenant;
        RETURN;
    END IF;

    -- ==================== attempt-ledger cleanup (MIGRATING/BLOCKED) ====
    -- The mapping ledger lists every legacy row the attempt touched.

    SELECT COUNT(*) INTO v_ledger
      FROM hr_legacy_employee_mappings
     WHERE tenant_id = v_tenant;

    -- 1. Remove canonical assignments created by the attempt: in a
    --    MIGRATING/BLOCKED tenant every canonical assignment originates
    --    from the failed backfill (v2 canonical writes are not possible
    --    before CANONICAL).
    WITH legacy_ids AS (
        SELECT m.legacy_employee_id
          FROM hr_legacy_employee_mappings m
         WHERE m.tenant_id = v_tenant
    )
    DELETE FROM hr_employee_assignments a
     WHERE a.tenant_id = v_tenant
       AND a.employment_id IN (SELECT legacy_employee_id FROM legacy_ids);
    GET DIAGNOSTICS v_assignments_removed = ROW_COUNT;

    -- 2. Capture the ledger-owned canonical people BEFORE clearing the
    --    ledger (the FK fk_hr_legacy_employee_mappings_person forbids
    --    deleting people while the ledger still references them, and the
    --    ledger itself defines which people belong to the attempt).
    CREATE TEMP TABLE _g0_rollback_people ON COMMIT DROP AS
    SELECT m.canonical_person_id AS id
      FROM hr_legacy_employee_mappings m
     WHERE m.tenant_id = v_tenant
       AND m.canonical_person_id IS NOT NULL;

    -- 3. Unlink canonical people from the legacy rows (non-destructive to
    --    the legacy employment record itself).
    UPDATE hr_employees e
       SET person_id = NULL,
           updated_at = NOW()
     WHERE e.tenant_id = v_tenant
       AND e.person_id IS NOT NULL;
    GET DIAGNOSTICS v_unlinked = ROW_COUNT;

    -- 4. Remove private/identifier sub-records of the captured people
    --    (incomplete canonical artifacts of the attempt, if any exist).
    DELETE FROM hr_person_private pp
     WHERE pp.tenant_id = v_tenant
       AND pp.person_id IN (SELECT id FROM _g0_rollback_people);
    DELETE FROM hr_person_identifiers pi
     WHERE pi.tenant_id = v_tenant
       AND pi.person_id IN (SELECT id FROM _g0_rollback_people);

    -- 5. Clear the canonical references in the ledger so the people rows
    --    become unreferenced and a rerun starts from a deterministic,
    --    dangle-free state. Classification history is re-derived by
    --    precheck on the next attempt.
    UPDATE hr_legacy_employee_mappings
       SET canonical_person_id = NULL,
           canonical_employment_id = NULL
     WHERE tenant_id = v_tenant;

    -- 6. Remove canonical people created by the attempt: in MIGRATING/
    --    BLOCKED state, hr_people rows for this tenant were created by the
    --    backfill itself (or by an earlier failed attempt being re-rolled).
    DELETE FROM hr_people p
     WHERE p.tenant_id = v_tenant
       AND p.id IN (SELECT id FROM _g0_rollback_people);
    GET DIAGNOSTICS v_people_removed = ROW_COUNT;

    -- 7. Return the tenant to LEGACY (v1 authoritative again; v1 writes
    --    resume under the unambiguity rules).
    UPDATE hr_migration_tenant_state
       SET state = 'LEGACY', updated_at = NOW()
     WHERE tenant_id = v_tenant;

    RAISE NOTICE 'tenant % rolled back to LEGACY (ledger rows %, assignments removed %, people removed %, employments unlinked %)',
        v_tenant, v_ledger, v_assignments_removed, v_people_removed, v_unlinked;
END;
$rollback$;

\echo 'Resulting tenant state:'

SELECT tenant_id, state, updated_at
  FROM hr_migration_tenant_state
 WHERE tenant_id = :'tenant_id'::uuid;

\echo '== rollback complete (tenant is LEGACY; rerun cutover when ready) =='
