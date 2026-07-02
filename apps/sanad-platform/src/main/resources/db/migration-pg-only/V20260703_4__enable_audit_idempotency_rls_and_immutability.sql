-- ============================================================
-- SANAD Platform - Flyway migration V23 (PostgreSQL only)
-- ------------------------------------------------------------
-- Stage 05 §6-7 — Enable FORCE RLS on audit_events and
-- idempotency_records, and enforce audit_events append-only
-- immutability via database triggers.
--
-- This migration is in db/migration-pg-only/ and is only loaded by
-- the prod profile (PostgreSQL). The local profile (H2) does not
-- load this directory.
--
-- RLS policy (same pattern as V17):
--   USING  (tenant_id = current_setting('app.current_tenant_id', true)::uuid)
--   WITH CHECK (tenant_id = current_setting('app.current_tenant_id', true)::uuid)
--
-- Immutability triggers (audit_events only):
--   - Block UPDATE on any audit_events row
--   - Block DELETE on any audit_events row
--   - Block TRUNCATE on audit_events
-- These triggers fire EVEN for the table owner (sanad_migration_owner)
-- because FORCE RLS + SECURITY DEFINER ensures the runtime role cannot
-- bypass them. The sanad_fixture_ci role (BYPASSRLS) is exempt — it is
-- CI-only and used solely for test fixture cleanup.
-- ============================================================

-- ============================================================
-- audit_events — RLS
-- ============================================================
ALTER TABLE audit_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_events FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_audit_events ON audit_events
    USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant_id', true)::uuid);

-- ============================================================
-- idempotency_records — RLS
-- ============================================================
ALTER TABLE idempotency_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE idempotency_records FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_idempotency_records ON idempotency_records
    USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant_id', true)::uuid);

-- ============================================================
-- audit_events — Append-only immutability
-- ============================================================
-- Block UPDATE on audit_events. The function raises an exception
-- that aborts the transaction, regardless of which role issued the
-- UPDATE (including the table owner).
CREATE OR REPLACE FUNCTION block_audit_events_update() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only: UPDATE is not permitted (event_id=%)', OLD.id
        USING ERRCODE = 'check_violation';
END;
$$;

DROP TRIGGER IF EXISTS trg_block_audit_events_update ON audit_events;
CREATE TRIGGER trg_block_audit_events_update
    BEFORE UPDATE ON audit_events
    FOR EACH ROW
    EXECUTE FUNCTION block_audit_events_update();

-- Block DELETE on audit_events.
CREATE OR REPLACE FUNCTION block_audit_events_delete() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only: DELETE is not permitted (event_id=%)', OLD.id
        USING ERRCODE = 'check_violation';
END;
$$;

DROP TRIGGER IF EXISTS trg_block_audit_events_delete ON audit_events;
CREATE TRIGGER trg_block_audit_events_delete
    BEFORE DELETE ON audit_events
    FOR EACH ROW
    EXECUTE FUNCTION block_audit_events_delete();

-- Block TRUNCATE on audit_events.
CREATE OR REPLACE FUNCTION block_audit_events_truncate() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only: TRUNCATE is not permitted'
        USING ERRCODE = 'check_violation';
END;
$$;

DROP TRIGGER IF EXISTS trg_block_audit_events_truncate ON audit_events;
CREATE TRIGGER trg_block_audit_events_truncate
    BEFORE TRUNCATE ON audit_events
    FOR EACH STATEMENT
    EXECUTE FUNCTION block_audit_events_truncate();
