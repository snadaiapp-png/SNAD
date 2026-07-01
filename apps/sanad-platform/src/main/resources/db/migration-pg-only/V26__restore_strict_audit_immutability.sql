-- ============================================================
-- SANAD Platform - Flyway migration V26 (PostgreSQL only)
-- ------------------------------------------------------------
-- Stage 05A.1 §3 — Restore strict audit immutability triggers.
--
-- V25 introduced a guard that skipped enforcement for
-- sanad_fixture_ci. This was a production security defect
-- (CD-05-P1-013): a CI fixture role should NEVER be able to
-- bypass audit immutability in a production schema.
--
-- V26 recreates the three trigger functions WITHOUT any
-- current_user exception. UPDATE, DELETE, and TRUNCATE are
-- ALWAYS rejected, regardless of the connecting role.
--
-- Test cleanup must use an ephemeral database that is destroyed
-- after the CI job, not a fixture role that bypasses triggers.
-- ============================================================

CREATE OR REPLACE FUNCTION block_audit_events_update() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only: UPDATE is not permitted (event_id=%)', OLD.id
        USING ERRCODE = 'check_violation';
END;
$$;

CREATE OR REPLACE FUNCTION block_audit_events_delete() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only: DELETE is not permitted (event_id=%)', OLD.id
        USING ERRCODE = 'check_violation';
END;
$$;

CREATE OR REPLACE FUNCTION block_audit_events_truncate() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only: TRUNCATE is not permitted'
        USING ERRCODE = 'check_violation';
END;
$$;

-- ============================================================
-- Stage 05A.1 §5 — Revoke UPDATE/DELETE from runtime role.
-- The runtime role may only INSERT and SELECT on audit_events.
-- ============================================================
REVOKE ALL ON audit_events FROM sanad_runtime_app;
GRANT SELECT, INSERT ON audit_events TO sanad_runtime_app;
