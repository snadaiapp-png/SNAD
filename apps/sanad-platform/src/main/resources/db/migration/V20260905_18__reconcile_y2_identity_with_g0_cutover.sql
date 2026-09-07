-- ============================================================
-- V20260905_18: Reconcile Y2 Employee<->User identity uniqueness
--               with the HRM-G0 cutover lifecycle
--
-- Reconciliation of two reviewed contracts that collide on
-- hr_employees:
--
--   * Workflow Y2 (plan 2026-08-30-workflow-orchestration-y2-
--     implementation.md, Task 2; deployed on main via #956 and
--     swept into this line by the reconciliation merge b4cd5e65):
--     flat partial UNIQUE INDEX uq_hr_employees_tenant_user ON
--     (tenant_id, user_id) WHERE user_id IS NOT NULL — the
--     Employee<->User identity bridge for workflow actionability.
--
--   * HRM-G0 (plan 2026-08-27-hrm-g0-05-api-ui-cutover.md and the
--     WS2 Task 6 authoritative scripts, checksum-deployed by
--     R__finalize_hr_backfill_closure.sql): hr_employees IS the
--     legacy source table. Pre-cutover legacy data may contain
--     several employments sharing one user_id; the fail-closed
--     precheck MUST classify them MIGRATION_BLOCKED with a
--     DUPLICATE_USER_ID review item (g0-backfill-precheck.sql).
--     The flat index makes that reviewed contract physically
--     impossible and breaks the DIRECT_RED test fixtures.
--
-- Resolution (neither reviewed test weakened):
--   The Y2 one-to-one invariant is enforced OUTSIDE the cutover
--   domain and DEFERRED to the G0 fail-closed pipeline INSIDE it:
--
--     - hr_migration_tenant_state.state IN ('LEGACY','MIGRATING',
--       'BLOCKED')  -> cutover domain: duplicates representable;
--       guarded by hr_precheck_tenant (DUPLICATE_USER_ID) and the
--       reconcile gate matrix before CANONICAL is reachable.
--     - no state row (pre-G0 tenants — identical behavior to the
--       previous index), state = 'CANONICAL', or any unknown
--       state -> enforce one-to-one, ERRCODE 23505 (fail-closed).
--
-- The enforcement trigger fires BEFORE INSERT OR UPDATE OF
-- user_id only; the backfill (UPDATE ... SET person_id,
-- legal_entity_id) never fires it. A transaction-scoped advisory
-- lock closes the check-then-insert race. A non-unique index
-- preserves findByUserId query performance.
--
-- Terminal migration version moves 20260905.17 -> 20260905.18.
-- No hardcoded role grants (environment-portable by construction).
-- ============================================================

DROP INDEX IF EXISTS uq_hr_employees_tenant_user;

CREATE INDEX IF NOT EXISTS idx_hr_employees_tenant_user
    ON hr_employees (tenant_id, user_id)
    WHERE user_id IS NOT NULL;

CREATE OR REPLACE FUNCTION hr_enforce_employee_user_identity()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_state     VARCHAR(20);
    v_dup_count INTEGER;
BEGIN
    IF NEW.user_id IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT state INTO v_state
      FROM hr_migration_tenant_state
     WHERE tenant_id = NEW.tenant_id;

    -- G0 cutover domain: legacy data may hold duplicate user_id across
    -- employments; the fail-closed precheck/reconcile pipeline gates
    -- the tenant before CANONICAL can be reached.
    IF v_state IN ('LEGACY', 'MIGRATING', 'BLOCKED') THEN
        RETURN NEW;
    END IF;

    -- No state row (pre-G0 tenants), CANONICAL, or unknown state:
    -- enforce the Y2 one-to-one Employee<->User identity bridge.
    -- Transaction-scoped advisory lock closes the check/insert race.
    PERFORM pg_advisory_xact_lock(
        hashtext('hr_employees:' || NEW.tenant_id::text || ':' || NEW.user_id::text));

    SELECT COUNT(*) INTO v_dup_count
      FROM hr_employees
     WHERE tenant_id = NEW.tenant_id
       AND user_id = NEW.user_id
       AND id <> NEW.id;

    IF v_dup_count > 0 THEN
        RAISE EXCEPTION
            'duplicate key value violates unique constraint "uq_hr_employees_tenant_user": one user cannot link to two employees in the same tenant'
            USING ERRCODE = '23505',
                  DETAIL = 'Key (tenant_id, user_id)=(' || NEW.tenant_id || ', ' || NEW.user_id || ') already exists or is blocked by the G0 cutover domain guard.',
                  CONSTRAINT = 'uq_hr_employees_tenant_user';
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS hr_trg_enforce_employee_user_identity ON hr_employees;

CREATE TRIGGER hr_trg_enforce_employee_user_identity
    BEFORE INSERT OR UPDATE OF user_id ON hr_employees
    FOR EACH ROW
    EXECUTE FUNCTION hr_enforce_employee_user_identity();

COMMENT ON FUNCTION hr_enforce_employee_user_identity() IS
    'Reconciles Workflow Y2 one-to-one Employee<->User identity (plan 2026-08-30 Task 2) with the HRM-G0 cutover lifecycle (plan 2026-08-27, WS2 Task 6): enforced outside the cutover domain (no hr_migration_tenant_state row / CANONICAL / unknown), deferred to the fail-closed DUPLICATE_USER_ID precheck inside LEGACY/MIGRATING/BLOCKED. Fail-closed by default.';

COMMENT ON TRIGGER hr_trg_enforce_employee_user_identity ON hr_employees IS
    'Replaces the lifecycle-blind uq_hr_employees_tenant_user unique index (dropped by V20260905_18) with cutover-state-aware enforcement; raises SQLSTATE 23505 to preserve Y2 DataIntegrityViolationException semantics.';
