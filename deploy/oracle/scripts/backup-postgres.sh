#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ORACLE_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${ORACLE_DIR}/.env"
COMPOSE_FILE="${ORACLE_DIR}/docker-compose.oracle.yml"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/snad/postgres}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
DUMP_FILE="${BACKUP_DIR}/sanad-${TIMESTAMP}.dump"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Missing ${ENV_FILE}" >&2
  exit 1
fi

mkdir -p "${BACKUP_DIR}"
chmod 750 "${BACKUP_DIR}"
COMPOSE=(docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}")

if ! "${COMPOSE[@]}" exec -T postgres sh -ec 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"' >/dev/null; then
  echo "PostgreSQL is not ready; backup aborted." >&2
  exit 1
fi

"${COMPOSE[@]}" exec -T postgres sh -ec \
  'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --no-owner --no-privileges' \
  > "${DUMP_FILE}"

"${COMPOSE[@]}" exec -T postgres pg_restore --list < "${DUMP_FILE}" >/dev/null
sha256sum "${DUMP_FILE}" > "${DUMP_FILE}.sha256"
chmod 600 "${DUMP_FILE}" "${DUMP_FILE}.sha256"

find "${BACKUP_DIR}" -type f \( -name 'sanad-*.dump' -o -name 'sanad-*.dump.sha256' \) \
  -mtime "+${RETENTION_DAYS}" -delete

echo "Verified PostgreSQL backup: ${DUMP_FILE}"
