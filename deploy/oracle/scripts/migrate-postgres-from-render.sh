#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ORACLE_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${ORACLE_DIR}/.env"
COMPOSE_FILE="${ORACLE_DIR}/docker-compose.oracle.yml"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/snad/postgres}"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
DUMP_FILE="${BACKUP_DIR}/render-${TIMESTAMP}.dump"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Missing ${ENV_FILE}. Create it from .env.oracle.example first." >&2
  exit 1
fi

if [[ -z "${SOURCE_DATABASE_URL:-}" ]]; then
  read -r -s -p "Paste the Render PostgreSQL external URL: " SOURCE_DATABASE_URL
  echo
fi

if [[ "${SOURCE_DATABASE_URL}" != postgres://* && "${SOURCE_DATABASE_URL}" != postgresql://* ]]; then
  echo "SOURCE_DATABASE_URL must start with postgres:// or postgresql://" >&2
  exit 1
fi

mkdir -p "${BACKUP_DIR}"
chmod 750 "${BACKUP_DIR}"

COMPOSE=(docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}")

echo "[1/6] Exporting Render PostgreSQL into ${DUMP_FILE}"
docker run --rm \
  -e SOURCE_DATABASE_URL="${SOURCE_DATABASE_URL}" \
  -v "${BACKUP_DIR}:/backup" \
  postgres:16-alpine \
  sh -ec 'pg_dump "$SOURCE_DATABASE_URL" --format=custom --no-owner --no-privileges --file=/backup/'"$(basename "${DUMP_FILE}")"
unset SOURCE_DATABASE_URL

echo "[2/6] Verifying dump structure"
docker run --rm -v "${BACKUP_DIR}:/backup:ro" postgres:16-alpine \
  pg_restore --list "/backup/$(basename "${DUMP_FILE}")" >/dev/null
sha256sum "${DUMP_FILE}" | tee "${DUMP_FILE}.sha256"

printf '\nThis will replace objects in the Oracle target database.\n'
read -r -p "Type RESTORE to continue: " CONFIRMATION
if [[ "${CONFIRMATION}" != "RESTORE" ]]; then
  echo "Restore cancelled. The verified dump remains at ${DUMP_FILE}."
  exit 0
fi

echo "[3/6] Starting only the Oracle PostgreSQL service"
"${COMPOSE[@]}" up -d postgres

READY=false
for _ in $(seq 1 30); do
  if "${COMPOSE[@]}" exec -T postgres sh -ec 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"' >/dev/null 2>&1; then
    READY=true
    break
  fi
  sleep 2
done

if [[ "${READY}" != "true" ]]; then
  echo "Target PostgreSQL did not become ready." >&2
  "${COMPOSE[@]}" logs --tail=100 postgres >&2
  exit 1
fi

echo "[4/6] Terminating target connections before restore"
"${COMPOSE[@]}" exec -T postgres sh -ec \
  'psql -U "$POSTGRES_USER" -d postgres -v ON_ERROR_STOP=1 -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '\''$POSTGRES_DB'\'' AND pid <> pg_backend_pid();"'

echo "[5/6] Restoring into Oracle PostgreSQL"
cat "${DUMP_FILE}" | "${COMPOSE[@]}" exec -T postgres sh -ec \
  'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner --no-privileges --exit-on-error'

echo "[6/6] Running target integrity checks"
"${COMPOSE[@]}" exec -T postgres sh -ec \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -c "SELECT current_database(), current_user, count(*) AS public_tables FROM pg_tables WHERE schemaname = '\''public'\'';"'

echo "Migration restore completed. Do not switch DNS yet. Start and validate the backend using the runbook."
