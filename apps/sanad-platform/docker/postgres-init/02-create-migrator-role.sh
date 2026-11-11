#!/bin/bash
# ============================================================================
# SANAD P0 (Issue #1462) — docker postgres-init step 02:
# provision the dedicated migration role `sanad_migrator` (NOLOGIN) and the
# callback-replay cleanup role `sanad_callback_replay_cleanup` (NOLOGIN).
#
# Roles are created NOLOGIN: enabling LOGIN (with a password held only in
# the deployment secret store) is a deliberate operator action performed
# when wiring the one-shot Flyway migration container. Executed by the
# postgres entrypoint init scripts on first boot of the data volume; for
# already-initialized volumes run it once manually as the bootstrap admin.
#
# sanad_callback_replay_cleanup (V20261111_1 — P0 RLS closure of
# service_callback_replay): the membership granted to `sanad` must stay
# NON-INHERITING (WITH INHERIT FALSE) — the runtime identity may SET ROLE to
# the cleanup role inside the scheduled sweep's transaction, but must never
# inherit its cross-tenant expired-row visibility as a standing privilege.
# ============================================================================
set -euo pipefail
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
DO $sep$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sanad_migrator') THEN
    CREATE ROLE sanad_migrator NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
  END IF;
END $sep$;
ALTER ROLE sanad_migrator NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;

-- P0 V20261111_1: dedicated least-privilege callback-replay cleanup role.
DO $sep$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sanad_callback_replay_cleanup') THEN
    CREATE ROLE sanad_callback_replay_cleanup NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
  END IF;
END $sep$;
ALTER ROLE sanad_callback_replay_cleanup NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
ALTER ROLE sanad_callback_replay_cleanup NOLOGIN;
-- SET-only membership for the runtime identity (never INHERITING).
DO $sep$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_auth_members m
                   JOIN pg_roles g ON g.oid = m.roleid
                   JOIN pg_roles r ON r.oid = m.member
                  WHERE g.rolname = 'sanad_callback_replay_cleanup'
                    AND r.rolname = 'sanad') THEN
    GRANT sanad_callback_replay_cleanup TO sanad WITH INHERIT FALSE;
  END IF;
END $sep$;
SQL
