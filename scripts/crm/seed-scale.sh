#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"

if [[ ! -f .env ]]; then
  echo "Missing .env. Run: make bootstrap" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1091
source .env
set +a

TENANT_COUNT="${CRM_LOAD_TENANTS:-1000}"
ACCOUNTS_PER_TENANT="${CRM_LOAD_ACCOUNTS_PER_TENANT:-100}"
CONTACTS_PER_ACCOUNT="${CRM_LOAD_CONTACTS_PER_ACCOUNT:-2}"
COMPOSE=(docker compose --env-file .env -f compose.yaml -f compose.crm.yaml)

case "${TENANT_COUNT}:${ACCOUNTS_PER_TENANT}:${CONTACTS_PER_ACCOUNT}" in
  *[!0-9:]*|'')
    echo "Scale parameters must be positive integers" >&2
    exit 1
    ;;
esac

if (( TENANT_COUNT < 1 || ACCOUNTS_PER_TENANT < 1 || CONTACTS_PER_ACCOUNT < 1 )); then
  echo "Scale parameters must be greater than zero" >&2
  exit 1
fi

EXPECTED_ACCOUNTS=$((TENANT_COUNT * ACCOUNTS_PER_TENANT))
EXPECTED_CONTACTS=$((EXPECTED_ACCOUNTS * CONTACTS_PER_ACCOUNT))

cat <<EOF
Generating CRM benchmark dataset:
  tenants:              ${TENANT_COUNT}
  accounts per tenant:  ${ACCOUNTS_PER_TENANT}
  contacts per account: ${CONTACTS_PER_ACCOUNT}
  expected accounts:    ${EXPECTED_ACCOUNTS}
  expected contacts:    ${EXPECTED_CONTACTS}
EOF

"${COMPOSE[@]}" exec -T postgres \
  psql \
  -U "${POSTGRES_USER:-sanad}" \
  -d "${POSTGRES_DB:-sanad}" \
  -v tenant_count="${TENANT_COUNT}" \
  -v accounts_per_tenant="${ACCOUNTS_PER_TENANT}" \
  -v contacts_per_account="${CONTACTS_PER_ACCOUNT}" \
  < tests/performance/sql/seed_crm_scale.sql

printf 'CRM scale dataset generation completed.\n'
