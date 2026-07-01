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

cleanup() {
  rm -f "${VALIDATION_BASE}"
}
trap cleanup EXIT

sed -n '1,175p' tests/performance/sql/validate_crm_scale.sql > "${VALIDATION_BASE}"
: > "${OUTPUT_FILE}"

docker compose --env-file .env -f compose.yaml -f compose.crm.yaml exec -T postgres \
  psql -v ON_ERROR_STOP=1 -U "${POSTGRES_USER:-sanad}" -d "${POSTGRES_DB:-sanad}" \
  < "${VALIDATION_BASE}" 2>&1 \
  | tee -a "${OUTPUT_FILE}"

docker compose --env-file .env -f compose.yaml -f compose.crm.yaml exec -T postgres \
  psql -v ON_ERROR_STOP=1 -U "${POSTGRES_USER:-sanad}" -d "${POSTGRES_DB:-sanad}" \
  < tests/performance/sql/validate_crm_queries.sql 2>&1 \
  | tee -a "${OUTPUT_FILE}"

echo "CRM database validation completed."
