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
cat tests/performance/sql/validate_crm_scale.sql | docker compose --env-file .env -f compose.yaml -f compose.crm.yaml exec -T postgres psql -U "${POSTGRES_USER:-sanad}" -d "${POSTGRES_DB:-sanad}" | tee artifacts/performance/crm-database-validation.txt

echo "CRM database validation completed."
