#!/bin/bash
# ============================================================================
# SANAD P0 (Issue #1462) — docker postgres-init step 02:
# provision the dedicated migration role `sanad_migrator` (NOLOGIN).
#
# The role is created NOLOGIN: enabling LOGIN (with a password held only in
# the deployment secret store) is a deliberate operator action performed
# when wiring the one-shot Flyway migration container. Executed by the
# postgres entrypoint init scripts on first boot of the data volume; for
# already-initialized volumes run it once manually as the bootstrap admin.
# ============================================================================
set -euo pipefail
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
DO $sep$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sanad_migrator') THEN
    CREATE ROLE sanad_migrator NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
  END IF;
END $sep$;
ALTER ROLE sanad_migrator NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
SQL
