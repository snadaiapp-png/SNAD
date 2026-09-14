-- ============================================================================
-- V20260910_1__workflow_y2_sla_and_cancellation_hardening.sql
-- SNAD Workflow Y2 — Wave 2 verification-closure hardening (Tasks 12/13)
--
-- Forward-only + additive-first (Z3). No historical migration is rewritten.
--
-- 1) Y2 two-phase cancellation (design decision P3): an ACTIVE Y2 instance
--    enters CANCELLING while durable compensations run; only after every
--    compensatable side effect is reconciled (or its failure is captured as
--    a governed incident) does the instance finalize to CANCELLED. LEGACY
--    cancellation keeps its existing direct RUNNING -> CANCELLED semantics.
--
-- 2) Y2 SLA modes (design decision V3): steps may declare WALL_CLOCK,
--    CALENDAR_TIME, or BUSINESS_TIME. BUSINESS_TIME resolves the due
--    instant through the tenant business calendar pinned on the step and
--    fails closed when the calendar reference is missing. Step instances
--    persist the resolved SLA policy snapshot (mode, calendar, hours) at
--    activation so later definition edits never make historical evidence
--    ambiguous.
-- ============================================================================

-- (1) Widen the instance status domain with the durable CANCELLING phase.
ALTER TABLE workflow_instances DROP CONSTRAINT IF EXISTS ck_wf_inst_status;
ALTER TABLE workflow_instances ADD CONSTRAINT ck_wf_inst_status
    CHECK (status IN ('RUNNING', 'PAUSED', 'CANCELLING', 'COMPLETED', 'CANCELLED', 'FAILED'));

-- (2a) Step-level SLA policy declaration.
ALTER TABLE workflow_steps
    ADD COLUMN IF NOT EXISTS sla_mode VARCHAR(20) NOT NULL DEFAULT 'WALL_CLOCK';
ALTER TABLE workflow_steps DROP CONSTRAINT IF EXISTS ck_wf_step_sla_mode;
ALTER TABLE workflow_steps ADD CONSTRAINT ck_wf_step_sla_mode
    CHECK (sla_mode IN ('WALL_CLOCK', 'CALENDAR_TIME', 'BUSINESS_TIME'));
ALTER TABLE workflow_steps
    ADD COLUMN IF NOT EXISTS sla_calendar_id UUID;

-- (2b) Activation-time SLA policy snapshot persisted with the step instance.
--      Nullable: step instances created before this migration carry no
--      snapshot and remain interpretable through their pinned definition
--      version (S3 schema evolution rule).
ALTER TABLE workflow_step_instances ADD COLUMN IF NOT EXISTS sla_mode VARCHAR(20);
ALTER TABLE workflow_step_instances ADD COLUMN IF NOT EXISTS sla_calendar_id UUID;
ALTER TABLE workflow_step_instances ADD COLUMN IF NOT EXISTS sla_hours INTEGER;

CREATE INDEX IF NOT EXISTS idx_wf_steps_sla_calendar
    ON workflow_steps (sla_calendar_id)
    WHERE sla_calendar_id IS NOT NULL;
