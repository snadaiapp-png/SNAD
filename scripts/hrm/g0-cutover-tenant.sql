-- ============================================================
-- HRM-G0 / WS5 Task 11 — Deterministic per-tenant cutover
-- (write freeze → backfill → reconcile → CANONICAL)
--
-- Usage (operator, psql — NO credentials are stored in this file):
--   psql -v tenant_id="<uuid>" -f scripts/hrm/g0-cutover-tenant.sql
--
-- Guarantees:
--   * fail-closed: aborts (ON_ERROR_STOP) on any unresolved gate
--   * refuses to touch a tenant already CANONICAL or BLOCKED
--   * tenant-scoped: every phase binds app.tenant_id and the shared
--     migration functions assert caller scope (42501 otherwise)
--   * idempotent on rerun: a previously migrated row remains stable
--   * observable: each phase echoes its state and gate report
--
-- Phase map (authoritative WS5 plan Task 11 Step 2):
--   1. lock/update tenant migration state → MIGRATING
--   2. refuse if already CANONICAL or unresolved precheck state exists
--   3. commit write freeze state
--   4. precheck + backfill scoped to the tenant
--   5. reconcile scoped to the tenant (gate report printed)
--   6. only if EVERY reconciliation gate passes → CANONICAL
--      (hr_reconcile_tenant sets BLOCKED otherwise — never guess)
-- ============================================================

\set ON_ERROR_STOP on

\if :{?tenant_id}
\else
\echo 'FATAL: psql variable tenant_id is required (e.g. -v tenant_id="<uuid>")'
\quit
\endif

\echo '== HRM-G0 tenant cutover =='
\echo 'Phase 0 — validate tenant identifier and bind tenant scope'

-- Fails fast (ON_ERROR_STOP) when tenant_id is not a valid UUID.
SELECT :'tenant_id'::uuid AS tenant;

-- Session-scoped tenant binding: a transaction-local GUC (is_local => true)
-- evaporates between statements under autocommit, which would leave every
-- later RLS check and hr_assert_migration_tenant_scope call fail-closed.
SELECT set_config('app.tenant_id', :'tenant_id', false) AS tenant_scope;

-- psql does not interpolate :variables inside dollar-quoted DO bodies, so
-- the blocks below read the tenant from this temp context table instead.
DROP TABLE IF EXISTS _g0_tenant_ctx;
CREATE TEMP TABLE _g0_tenant_ctx AS
SELECT :'tenant_id'::uuid AS tenant_id;

\echo 'Phase 1+2+3 — write freeze: state → MIGRATING (refuse CANONICAL/BLOCKED)'

DO $freeze$
DECLARE
    v_current   VARCHAR(20);
    v_tenant    UUID;
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
        RAISE EXCEPTION 'cutover refused: tenant % is already CANONICAL', v_tenant
            USING ERRCODE = 'P0001';
    END IF;

    IF v_current = 'BLOCKED' THEN
        RAISE EXCEPTION 'cutover refused: tenant % is BLOCKED — resolve the halted cutover first', v_tenant
            USING ERRCODE = 'P0001';
    END IF;

    -- Commit the write freeze (MIGRATING): v1 reads stay available,
    -- v1 writes are rejected with 409 while backfill/reconcile run.
    INSERT INTO hr_migration_tenant_state (tenant_id, state, updated_at)
    VALUES (v_tenant, 'MIGRATING', NOW())
    ON CONFLICT (tenant_id)
    DO UPDATE SET state = 'MIGRATING', updated_at = NOW();

    RAISE NOTICE 'tenant % frozen for migration (MIGRATING), previous state %', v_tenant, v_current;
END;
$freeze$;

\echo 'Phase 4a — precheck (classifies every legacy row; idempotent)'

SELECT hr_precheck_tenant(:'tenant_id'::uuid);

\echo 'Phase 4b — backfill (creates canonical people/assignments; idempotent)'

SELECT hr_backfill_tenant(:'tenant_id'::uuid);

\echo 'Phase 5 — reconciliation gate report'

SELECT r.gate_name, r.gate_value, r.passed
  FROM hr_reconcile_tenant_report(:'tenant_id'::uuid) r
 ORDER BY r.gate_name;

\echo 'Phase 6 — finalize: CANONICAL only when every gate passes'

-- hr_reconcile_tenant (authoritative WS2 semantics) decides:
--   zero legacy employees          → LEGACY   (nothing to migrate)
--   every reconciliation gate pass → CANONICAL
--   any failed gate                → BLOCKED  (fail-closed, never guess)
SELECT hr_reconcile_tenant(:'tenant_id'::uuid);

\echo 'Resulting tenant state:'

SELECT tenant_id, state, updated_at
  FROM hr_migration_tenant_state
 WHERE tenant_id = :'tenant_id'::uuid;

\echo '== cutover run complete (BLOCKED requires operator resolution before rerun) =='
