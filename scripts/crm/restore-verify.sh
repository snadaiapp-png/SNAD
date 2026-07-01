#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"

if [[ ! -f .env ]]; then
  echo "Missing .env. Run make bootstrap." >&2
  exit 1
fi

set -a
source .env
set +a

LATEST_BACKUP="$(find artifacts/backups -maxdepth 1 -type f -name 'snad-crm-*.dump' 2>/dev/null | sort | tail -1)"
if [[ -z "${LATEST_BACKUP}" ]]; then
  echo "No CRM backup found. Run make crm-backup first." >&2
  exit 1
fi

if [[ -f "${LATEST_BACKUP}.sha256" ]]; then
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum --check "${LATEST_BACKUP}.sha256"
  else
    shasum -a 256 --check "${LATEST_BACKUP}.sha256"
  fi
fi

COMPOSE=(docker compose --env-file .env -f compose.yaml -f compose.crm.yaml)
RESTORE_DB="snad_restore_$(date -u +%Y%m%d%H%M%S)"
CONTAINER_FILE="/tmp/${RESTORE_DB}.dump"

cleanup() {
  "${COMPOSE[@]}" exec -T postgres dropdb -U "${POSTGRES_USER:-sanad}" --if-exists "${RESTORE_DB}" >/dev/null 2>&1 || true
  "${COMPOSE[@]}" exec -T postgres rm -f "${CONTAINER_FILE}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

"${COMPOSE[@]}" cp "${LATEST_BACKUP}" "postgres:${CONTAINER_FILE}"
"${COMPOSE[@]}" exec -T postgres createdb -U "${POSTGRES_USER:-sanad}" "${RESTORE_DB}"
"${COMPOSE[@]}" exec -T postgres pg_restore \
  -U "${POSTGRES_USER:-sanad}" \
  -d "${RESTORE_DB}" \
  --exit-on-error \
  --no-owner \
  --no-acl \
  "${CONTAINER_FILE}"

"${COMPOSE[@]}" exec -T postgres psql \
  -U "${POSTGRES_USER:-sanad}" \
  -d "${RESTORE_DB}" \
  -v ON_ERROR_STOP=1 \
  -c "SELECT current_database(), count(*) AS installed_extensions FROM pg_extension;" \
  -c "SELECT to_regclass('crm_runtime.tenant_capacity') AS tenant_capacity_table;" \
  -c "SELECT to_regclass('public.flyway_schema_history') AS flyway_history_table;"

printf 'CRM restore verification passed using %s\n' "${LATEST_BACKUP}"
