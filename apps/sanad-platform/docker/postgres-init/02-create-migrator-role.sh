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

# ORDER 11: the control-plane connection identity requires an
# environment-provisioned password. Fail closed when it is missing.
if [ -z "${SANAD_CONTROL_PLANE_DB_PASSWORD:-}" ]; then
  echo "ORDER11_CONTROL_PLANE_CREDENTIAL_REQUIRED: set SANAD_CONTROL_PLANE_DB_PASSWORD for the sanad_control_plane LOGIN role (deployment secret store / compose environment)" >&2
  exit 1
fi

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v cp_password="$SANAD_CONTROL_PLANE_DB_PASSWORD" <<'SQL'
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

-- ORDER 11 / G1-C4 PR-2 (V20261118_1/V20261118_2): dedicated control-plane
-- CONNECTION identity holding the tenants + access_capabilities lifecycle
-- DML (SELECT/INSERT/UPDATE/DELETE). LOGIN with a password provisioned
-- ONLY by the deployment environment (SANAD_CONTROL_PLANE_DB_PASSWORD) —
-- never by the migration chain. There is deliberately NO membership
-- between sanad and sanad_control_plane in EITHER direction: the runtime
-- must not be able to SET ROLE into the control plane, and any cluster
-- provisioned with the deprecated membership contract is corrected here.
DO $sep$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sanad_control_plane') THEN
    CREATE ROLE sanad_control_plane LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
  END IF;
END $sep$;
ALTER ROLE sanad_control_plane NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
ALTER ROLE sanad_control_plane LOGIN;
DO $sep$ BEGIN
  IF EXISTS (SELECT 1 FROM pg_auth_members m
               JOIN pg_roles g ON g.oid = m.roleid
               JOIN pg_roles r ON r.oid = m.member
              WHERE g.rolname = 'sanad_control_plane'
                AND r.rolname = 'sanad') THEN
    REVOKE sanad_control_plane FROM sanad;
  END IF;
END $sep$;
DO $sep$ BEGIN
  IF EXISTS (SELECT 1 FROM pg_auth_members m
               JOIN pg_roles g ON g.oid = m.roleid
               JOIN pg_roles r ON r.oid = m.member
              WHERE g.rolname = 'sanad'
                AND r.rolname = 'sanad_control_plane') THEN
    REVOKE sanad FROM sanad_control_plane;
  END IF;
END $sep$;
-- The control-plane password is supplied by the environment (bash guard
-- above fails closed when missing) and interpolated as a quoted psql
-- variable. It never appears in the migration chain.
ALTER ROLE sanad_control_plane PASSWORD :'cp_password';
-- Connection-level access for the dedicated identity (GRANT cannot take
-- an expression, so the database name is interpolated via a DO block).
DO $sep$ BEGIN
  EXECUTE format('GRANT CONNECT ON DATABASE %I TO sanad_control_plane',
                 current_database());
END $sep$;
GRANT USAGE ON SCHEMA public TO sanad_control_plane;
SQL
