-- ============================================================
-- SANAD Platform - Flyway migration V36 (PostgreSQL only)
-- ------------------------------------------------------------
-- Stage 05A.2.9 §8 — Harden platform_security_audit_events.
--
-- Add append-only triggers (same pattern as audit_events V23)
-- and restrict runtime role privileges to INSERT only.
-- ============================================================

-- Block UPDATE on platform_security_audit_events
CREATE OR REPLACE FUNCTION block_platform_sec_audit_update() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'platform_security_audit_events is append-only: UPDATE is not permitted (event_id=%)', OLD.id
        USING ERRCODE = 'check_violation';
END;
$$;

DROP TRIGGER IF EXISTS trg_block_platform_sec_audit_update ON platform_security_audit_events;
CREATE TRIGGER trg_block_platform_sec_audit_update
    BEFORE UPDATE ON platform_security_audit_events
    FOR EACH ROW
    EXECUTE FUNCTION block_platform_sec_audit_update();

-- Block DELETE on platform_security_audit_events
CREATE OR REPLACE FUNCTION block_platform_sec_audit_delete() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'platform_security_audit_events is append-only: DELETE is not permitted (event_id=%)', OLD.id
        USING ERRCODE = 'check_violation';
END;
$$;

DROP TRIGGER IF EXISTS trg_block_platform_sec_audit_delete ON platform_security_audit_events;
CREATE TRIGGER trg_block_platform_sec_audit_delete
    BEFORE DELETE ON platform_security_audit_events
    FOR EACH ROW
    EXECUTE FUNCTION block_platform_sec_audit_delete();

-- Block TRUNCATE on platform_security_audit_events
CREATE OR REPLACE FUNCTION block_platform_sec_audit_truncate() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'platform_security_audit_events is append-only: TRUNCATE is not permitted'
        USING ERRCODE = 'check_violation';
END;
$$;

DROP TRIGGER IF EXISTS trg_block_platform_sec_audit_truncate ON platform_security_audit_events;
CREATE TRIGGER trg_block_platform_sec_audit_truncate
    BEFORE TRUNCATE ON platform_security_audit_events
    FOR EACH STATEMENT
    EXECUTE FUNCTION block_platform_sec_audit_truncate();

-- Restrict runtime role: INSERT only (no SELECT, UPDATE, DELETE, TRUNCATE)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sanad_runtime_app') THEN
        REVOKE ALL ON platform_security_audit_events FROM sanad_runtime_app;
        GRANT INSERT ON platform_security_audit_events TO sanad_runtime_app;
    END IF;
END
$$;
