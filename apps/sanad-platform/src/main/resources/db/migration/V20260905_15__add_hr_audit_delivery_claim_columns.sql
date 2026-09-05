-- ============================================================
-- HRM-G0 / Master Task 4 / WS4 Task 6
-- V20260904_1__add_hr_audit_delivery_claim_columns.sql
-- ============================================================
-- ROOT CAUSE (documented per governance):
--   hr_audit_delivery (V20260903_2) persists delivery state but has no
--   claim columns. The WS4 Task 6 delivery worker requires exclusive,
--   recoverable claims so that two workers racing for the same row leave
--   exactly one valid claimant (at-least-once delivery without concurrent
--   double dispatch). The domain-event outbox already carries this claim
--   design (claim_token / claimed_by / claim_expires_at); this migration
--   mirrors it onto hr_audit_delivery.
--
-- FORWARD-ONLY, ADDITIVE: no existing column or constraint is modified;
-- prior Task 3 evidence remains intact. Next collision-free version
-- discovered at write time: 20260904.1 (latest was 20260903.2).
-- ============================================================

ALTER TABLE hr_audit_delivery
    ADD COLUMN IF NOT EXISTS claim_token     UUID,
    ADD COLUMN IF NOT EXISTS claimed_by      VARCHAR(200),
    ADD COLUMN IF NOT EXISTS claim_expires_at TIMESTAMPTZ;

-- Claimable rows: PENDING / FAILED (retryable after backoff). The existing
-- idx_hr_audit_delivery_pending covers status <> 'DELIVERED'; this partial
-- index serves the stale-claim recovery predicate (claim_expiry scan) that
-- the worker runs alongside the ready scan.
CREATE INDEX IF NOT EXISTS idx_hr_audit_delivery_claim_ready
    ON hr_audit_delivery (claim_expires_at)
    WHERE status IN ('PENDING', 'FAILED');
