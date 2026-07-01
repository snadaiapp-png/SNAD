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
VALIDATION_SQL="$(mktemp)"

cleanup() {
  rm -f "${VALIDATION_SQL}"
}
trap cleanup EXIT

sed -n '1,175p' tests/performance/sql/validate_crm_scale.sql > "${VALIDATION_SQL}"
cat tests/performance/sql/validate_crm_queries.sql >> "${VALIDATION_SQL}"

docker compose --env-file .env -f compose.yaml -f compose.crm.yaml exec -T postgres \
  psql -v ON_ERROR_STOP=1 -U "${POSTGRES_USER:-sanad}" -d "${POSTGRES_DB:-sanad}" \
  < "${VALIDATION_SQL}" 2>&1 \
  | tee artifacts/performance/crm-database-validation.txt

echo "CRM database validation completed."
