-- ============================================================
-- SANAD Platform - Flyway migration V32 (H2 + PostgreSQL)
-- ------------------------------------------------------------
-- Stage 05A.2.1 §6 — Add lease_version column for fencing tokens.
--
-- The lease_version column provides a fencing token that prevents
-- stale workers from completing an idempotency record after
-- ownership has been taken over by a new worker.
--
-- When a reservation is created: lease_version = 1
-- When a lease takeover occurs:  lease_version = lease_version + 1
-- When completing/failing:       the caller must match the lease_version
-- ============================================================

ALTER TABLE idempotency_records ADD COLUMN lease_version BIGINT NOT NULL DEFAULT 0;

-- Backfill existing rows to lease_version = 0 (no active lease)
UPDATE idempotency_records SET lease_version = 0 WHERE lease_version IS NULL;
