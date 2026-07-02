-- ============================================================
-- SANAD Platform - Flyway migration V25 (PostgreSQL only)
-- ------------------------------------------------------------
-- Stage 05 — Allow CI fixture cleanup of audit_events.
--
-- V23 created triggers that block UPDATE/DELETE/TRUNCATE on
-- audit_events. These triggers fire for ALL roles, including
-- sanad_fixture_ci (which has BYPASSRLS but cannot bypass
-- triggers).
--
-- The fixture role needs to DELETE audit_events rows during test
-- cleanup. This migration recreates the trigger functions with a
-- guard that skips enforcement when the current user is
-- sanad_fixture_ci.
--
-- In production, sanad_fixture_ci does not exist (it is created
-- only in CI), so the guard has no effect.
-- ============================================================

CREATE OR REPLACE FUNCTION block_audit_events_update() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF current_user = 'sanad_fixture_ci' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'audit_events is append-only: UPDATE is not permitted (event_id=%)', OLD.id
        USING ERRCODE = 'check_violation';
END;
$$;

CREATE OR REPLACE FUNCTION block_audit_events_delete() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF current_user = 'sanad_fixture_ci' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'audit_events is append-only: DELETE is not permitted (event_id=%)', OLD.id
        USING ERRCODE = 'check_violation';
END;
$$;

CREATE OR REPLACE FUNCTION block_audit_events_truncate() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF current_user = 'sanad_fixture_ci' THEN
        RETURN NULL;
    END IF;
    RAISE EXCEPTION 'audit_events is append-only: TRUNCATE is not permitted'
        USING ERRCODE = 'check_violation';
END;
$$;
