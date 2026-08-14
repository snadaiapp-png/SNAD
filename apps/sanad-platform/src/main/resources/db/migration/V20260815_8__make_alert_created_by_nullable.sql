-- ============================================================
-- V20260815_8: Make executive_alerts.created_by nullable
--
-- System-generated alerts (from SLA monitoring, auto-escalation)
-- don't have a human creator. The created_by column should be nullable
-- to support system-generated alerts without requiring a system user
-- to exist in every tenant's users table.
-- ============================================================

ALTER TABLE executive_alerts ALTER COLUMN created_by DROP NOT NULL;

-- Drop and re-create the FK with ON DELETE SET NULL
ALTER TABLE executive_alerts DROP CONSTRAINT IF EXISTS fk_alert_created_by;
ALTER TABLE executive_alerts ADD CONSTRAINT fk_alert_created_by
    FOREIGN KEY (tenant_id, created_by) REFERENCES users(tenant_id, id)
    ON DELETE SET NULL;
