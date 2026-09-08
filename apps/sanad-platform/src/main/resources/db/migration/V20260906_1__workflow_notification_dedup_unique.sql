-- ============================================================
-- V20260906_1: Workflow Y2 notification intent deduplication guard
--
-- Wave 2 — Task 15 remediation (T15-D1): the notification enqueue used a
-- read-then-insert pattern without a database-level uniqueness guarantee.
-- Concurrent identical enqueues could insert multiple durable intents for
-- the same (tenant_id, deduplication_key), and a delivery worker would then
-- deliver the same notification multiple times.
--
-- Fix: forward-only additive unique index. Rows with NULL deduplication_key
-- are unaffected (SQL unique indexes treat NULLs as distinct), so intents
-- that opt out of deduplication keep their current behavior.
--
-- Never rewrite applied historical migrations (see V20260902_6 / V20260904_1).
-- ============================================================

CREATE UNIQUE INDEX IF NOT EXISTS uq_wf_notification_dedup
    ON workflow_notification_intents (tenant_id, deduplication_key);
