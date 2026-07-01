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

mkdir -p artifacts/performance
VALIDATION_BASE="$(mktemp)"
OUTPUT_FILE="artifacts/performance/crm-database-validation.txt"
BASE_CONTAINER_FILE="/tmp/validate-crm-base.sql"
QUERY_CONTAINER_FILE="/tmp/validate-crm-queries.sql"
COMPOSE=(docker compose --env-file .env -f compose.yaml -f compose.crm.yaml)

cleanup() {
  rm -f "${VALIDATION_BASE}"
  "${COMPOSE[@]}" exec -T postgres rm -f "${BASE_CONTAINER_FILE}" "${QUERY_CONTAINER_FILE}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

sed -n '1,173p' tests/performance/sql/validate_crm_scale.sql > "${VALIDATION_BASE}"
: > "${OUTPUT_FILE}"

"${COMPOSE[@]}" cp "${VALIDATION_BASE}" "postgres:${BASE_CONTAINER_FILE}"
"${COMPOSE[@]}" cp tests/performance/sql/validate_crm_queries.sql "postgres:${QUERY_CONTAINER_FILE}"

"${COMPOSE[@]}" exec -T postgres \
  psql -v ON_ERROR_STOP=1 -U "${POSTGRES_USER:-sanad}" -d "${POSTGRES_DB:-sanad}" \
  -f "${BASE_CONTAINER_FILE}" 2>&1 \
  | tee -a "${OUTPUT_FILE}"

"${COMPOSE[@]}" exec -T postgres \
  psql -v ON_ERROR_STOP=1 -U "${POSTGRES_USER:-sanad}" -d "${POSTGRES_DB:-sanad}" \
  -f "${QUERY_CONTAINER_FILE}" 2>&1 \
  | tee -a "${OUTPUT_FILE}"

echo "CRM database validation completed."
