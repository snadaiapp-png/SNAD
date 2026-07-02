-- ============================================================
-- SANAD Platform - Flyway migration V29 (PostgreSQL only)
-- ------------------------------------------------------------
-- Stage 05A.1 — Remove FK from audit_events to tenants.
--
-- audit_events is append-only (triggers block DELETE). If a tenant
-- is deleted (e.g. in test cleanup), the FK constraint on
-- audit_events.tenant_id prevents the deletion because audit_events
-- rows cannot be removed. This creates a cleanup deadlock.
--
-- The tenant_id on audit_events is a tenant scope identifier, not a
-- referential integrity constraint. RLS already enforces that only
-- rows matching the current tenant context are visible. The FK is
-- not needed for data integrity and blocks legitimate cleanup.
--
-- This migration drops the FK constraint. The same change applies to
-- audit_chain_heads and idempotency_records.
-- ============================================================

ALTER TABLE audit_events DROP CONSTRAINT IF EXISTS fk_audit_events_tenant;
ALTER TABLE audit_chain_heads DROP CONSTRAINT IF EXISTS fk_audit_chain_heads_tenant;
ALTER TABLE idempotency_records DROP CONSTRAINT IF EXISTS fk_idempotency_records_tenant;
