-- ============================================================================
-- V20261001_12 — Wave 1 / Task 6: access-administration audit scan support.
--
-- Revision D mandate:
--   Every new or extended access-administration mutation must write the
--   authoritative platform_audit_logs record and the corresponding
--   authorization_change_events record. The behavioral/structural enforcement
--   is completed with the Task 10 access-admin audit contract; this migration
--   only adds the supporting scan index.
--
-- Execution overlay for original V20260924_6.
-- ============================================================================

CREATE INDEX IF NOT EXISTS idx_pal_resource_type_time
    ON platform_audit_logs (resource_type, created_at DESC);
