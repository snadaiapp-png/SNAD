#!/bin/bash
# SANAD — provision the restricted runtime role on first database init.
# Runs once inside the postgres container entrypoint (docker-entrypoint-initdb.d)
# as $POSTGRES_USER (bootstrap superuser). The application/runtime identity is
# deliberately NOT the bootstrap superuser so that FORCE RLS is enforceable:
# a superuser bypasses RLS even with FORCE, and the migration chain's
# preconditions (V20261011_3 / V20261028_2 / V20261031_1) require a restricted
# `sanad` (NOSUPERUSER, NOBYPASSRLS) before tenant RLS is applied.
set -euo pipefail
: "${DATABASE_PASSWORD:?DATABASE_PASSWORD must be set so the runtime role can be provisioned}"
RUNTIME_ROLE="${DATABASE_USERNAME:-sanad}"
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<SQL
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${RUNTIME_ROLE}') THEN
    CREATE ROLE ${RUNTIME_ROLE} LOGIN PASSWORD '${DATABASE_PASSWORD}'
      NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS NOREPLICATION;
  ELSE
    -- Existing volume: ensure the role is restricted before anything else runs.
    ALTER ROLE ${RUNTIME_ROLE} WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS NOREPLICATION;
  END IF;
END
\$\$;
SQL
echo "[sanad-init] restricted runtime role '${RUNTIME_ROLE}' provisioned (NOSUPERUSER, NOBYPASSRLS)"
