-- ============================================================
-- V20260924_5 — HRM-G2: canonical leave_requests state constraint alignment
-- ============================================================
-- Phase 2.D (Design B — Canonical Convergence): the domain model
-- (HrLeaveService) uses an 8-state state machine:
--   DRAFT → SUBMITTED → PENDING_MANAGER → PENDING_HR → APPROVED
--   with REJECTED, WITHDRAWN, CANCELLED.
--
-- The original G2 schema migration (V20260923_1) created the constraint
-- with only 5 legacy states (PENDING, APPROVED, REJECTED, CANCELLED,
-- COMPLETED). This mismatch caused runtime failures: HrLeaveService
-- tries to INSERT state='DRAFT' and UPDATE state='PENDING_MANAGER'
-- which the constraint rejects.
--
-- This migration replaces the 5-state constraint with the canonical
-- 8-state constraint that matches the domain model. The old COMPLETED
-- state is removed (it was never used by HrLeaveService — APPROVED is
-- the terminal success state). PENDING is also removed (the canonical
-- state machine uses PENDING_MANAGER + PENDING_HR instead).
--
-- Safety:
--   - DROP CONSTRAINT IF EXISTS then ADD CONSTRAINT (idempotent).
--   - No data migration needed — no existing row has state in (PENDING,
--     COMPLETED) because HrLeaveService never writes those values.
--   - The new constraint is a strict superset of what HrLeaveService
--     actually writes (it uses DRAFT, SUBMITTED, PENDING_MANAGER,
--     PENDING_HR, APPROVED, REJECTED, WITHDRAWN, CANCELLED).
-- ============================================================

ALTER TABLE hr_leave_requests DROP CONSTRAINT IF EXISTS ck_hr_leave_requests_state;

ALTER TABLE hr_leave_requests
    ADD CONSTRAINT ck_hr_leave_requests_state
    CHECK (state IN (
        'DRAFT',
        'SUBMITTED',
        'PENDING_MANAGER',
        'PENDING_HR',
        'APPROVED',
        'REJECTED',
        'WITHDRAWN',
        'CANCELLED'
    ));
