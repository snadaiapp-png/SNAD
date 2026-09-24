-- ============================================================
-- V20260815_9: Make management_audit_trail.actor_user_id nullable
--
-- System-generated operations (SLA monitoring, auto-escalation)
-- don't have a human actor. The actor_user_id column should be
-- nullable to support system-generated audit entries.
-- ============================================================

ALTER TABLE management_audit_trail ALTER COLUMN actor_user_id DROP NOT NULL;

ALTER TABLE management_audit_trail DROP CONSTRAINT IF EXISTS fk_audit_actor;
ALTER TABLE management_audit_trail ADD CONSTRAINT fk_audit_actor
    FOREIGN KEY (tenant_id, actor_user_id) REFERENCES users(tenant_id, id)
    ON DELETE SET NULL;
