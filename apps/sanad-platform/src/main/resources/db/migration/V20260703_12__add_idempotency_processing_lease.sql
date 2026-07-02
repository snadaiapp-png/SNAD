-- ============================================================
-- SANAD Platform - Flyway migration V31 (H2 + PostgreSQL)
-- ------------------------------------------------------------
-- Stage 05A.2 §14 — Add processing lease columns to
-- idempotency_records.
--
-- The existing expires_at column serves as the overall record TTL.
-- Processing lease needs separate columns to track:
--   - Which request currently owns the processing lock
--   - When that lease expires (for takeover by a retry)
--   - How many attempts have been made
--   - When the last attempt started
--
-- This enables atomic takeover of stale PROCESSING records.
-- ============================================================

ALTER TABLE idempotency_records ADD COLUMN lease_owner_request_id VARCHAR(128);
ALTER TABLE idempotency_records ADD COLUMN lease_expires_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE idempotency_records ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE idempotency_records ADD COLUMN last_attempt_at TIMESTAMP WITH TIME ZONE;

-- Index for efficient lease-expiry queries
CREATE INDEX idx_idempotency_tenant_lease_expires
    ON idempotency_records (tenant_id, lease_expires_at);
