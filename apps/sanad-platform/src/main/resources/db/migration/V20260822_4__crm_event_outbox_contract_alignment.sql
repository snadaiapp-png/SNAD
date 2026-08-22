-- ============================================================
-- SNAD Platform — CRM Event Outbox Contract Alignment (Task 5)
-- ------------------------------------------------------------
-- Forward-only additive migration that aligns the recovered shared V1
-- crm_event_outbox schema with the approved Task 5 durable event
-- envelope (CrmEventOutboxPort.CrmEventEnvelope) WITHOUT rewriting V1.
--
-- V1 is shared and immutable; this migration is purely additive:
--   * Adds aggregate_type / aggregate_id columns as nullable so
--     historical/manual rows (if any) remain valid.
--   * Domain-level enforcement (CrmEventEnvelope) guarantees both
--     fields are non-null on every Task 5 application append.
--   * The JDBC adapter always writes both columns for Task 5 rows,
--     so the table contract is enforced by the application boundary.
--
-- Index strategy:
--   * Adds idx_crm_event_outbox_claim_due — a new partial index whose
--     predicate includes BOTH 'PENDING' AND 'FAILED' so FAILED rows
--     are picked up by retry claims. The old idx_crm_event_outbox_due
--     (predicate = PENDING + PROCESSING only) is intentionally NOT
--     dropped in this recovery wave to keep the migration safe.
--   * The new index columns (tenant_id, available_at, created_at, id)
--     give deterministic claim ordering for Task 5.
-- ============================================================

ALTER TABLE crm_event_outbox
    ADD COLUMN aggregate_type VARCHAR(40),
    ADD COLUMN aggregate_id UUID;

CREATE INDEX idx_crm_event_outbox_claim_due
ON crm_event_outbox (
    tenant_id,
    available_at,
    created_at,
    id
)
WHERE status IN ('PENDING', 'FAILED');
