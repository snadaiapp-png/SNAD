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

BACKUP_DIR="artifacts/backups"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
BACKUP_FILE="${BACKUP_DIR}/snad-crm-${TIMESTAMP}.dump"
CONTAINER_FILE="/tmp/snad-crm-${TIMESTAMP}.dump"
mkdir -p "${BACKUP_DIR}"

COMPOSE=(docker compose --env-file .env -f compose.yaml -f compose.crm.yaml)

"${COMPOSE[@]}" exec -T postgres pg_dump \
  -U "${POSTGRES_USER:-sanad}" \
  -d "${POSTGRES_DB:-sanad}" \
  --format=custom \
  --compress=6 \
  --no-owner \
  --no-acl \
  --file="${CONTAINER_FILE}"

"${COMPOSE[@]}" cp "postgres:${CONTAINER_FILE}" "${BACKUP_FILE}"
"${COMPOSE[@]}" exec -T postgres rm -f "${CONTAINER_FILE}"

if command -v sha256sum >/dev/null 2>&1; then
  sha256sum "${BACKUP_FILE}" > "${BACKUP_FILE}.sha256"
else
  shasum -a 256 "${BACKUP_FILE}" > "${BACKUP_FILE}.sha256"
fi

printf 'CRM backup created: %s\n' "${BACKUP_FILE}"
