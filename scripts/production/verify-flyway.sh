#!/usr/bin/env bash
set -euo pipefail

: "${RENDER_API_KEY:?RENDER_API_KEY is required}"
: "${RENDER_SERVICE_ID:?RENDER_SERVICE_ID is required}"
: "${DATABASE_USERNAME:?DATABASE_USERNAME is required}"

cleanup() { rm -f /tmp/flyway-history.tsv /tmp/flyway-failures.txt /tmp/flyway-duplicates.txt; }
trap cleanup EXIT

RENDER_ENV=$(curl --silent --show-error \
  --header "Authorization: Bearer $RENDER_API_KEY" \
  --header "Accept: application/json" \
  "https://api.render.com/v1/services/$RENDER_SERVICE_ID/env-vars?limit=100")
DATABASE_URL=$(echo "$RENDER_ENV" | jq -r '[.[]? | (.envVar // .)] | .[] | select(.key == "DATABASE_URL") | .value // empty')
DATABASE_PASSWORD=$(echo "$RENDER_ENV" | jq -r '[.[]? | (.envVar // .)] | .[] | select(.key == "DATABASE_PASSWORD") | .value // empty')
test -n "$DATABASE_URL"
test -n "$DATABASE_PASSWORD"
echo "::add-mask::$DATABASE_PASSWORD"

RAW_URL="${DATABASE_URL#jdbc:}"; RAW_URL="${RAW_URL#postgresql://}"; RAW_URL="${RAW_URL#https://}"
HOST_PORT="${RAW_URL%%/*}"; DB_PART="${RAW_URL#*/}"; DB_NAME="${DB_PART%%\?*}"
PGHOST="${HOST_PORT%%:*}"; PGPORT="${HOST_PORT#*:}"; [ "$PGPORT" != "$HOST_PORT" ] || PGPORT=5432
run_sql() {
  PGPASSWORD="$DATABASE_PASSWORD" psql -h "$PGHOST" -p "$PGPORT" -U "$DATABASE_USERNAME" -d "$DB_NAME" \
    --no-psqlrc --set=ON_ERROR_STOP=1 --tuples-only --no-align --field-separator=$'\t' --command="$1"
}

run_sql "SELECT version, type, description, success FROM flyway_schema_history WHERE version IN ('20260717.100','20260717.101') ORDER BY installed_rank;" > /tmp/flyway-history.tsv
require_migration() {
  awk -F $'\t' -v v="$1" -v d="$2" '$1==v&&$2=="SQL"&&$3==d&&tolower($4)~/^(t|true)$/{f=1}END{exit f?0:1}' /tmp/flyway-history.tsv || {
    echo "::error::Required CRM-007 migration absent: version=$1 description=$2"; exit 1; }
}
require_migration "20260717.100" "crm addresses communication methods"
require_migration "20260717.101" "crm addresses communication capabilities"

tables=$(run_sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('crm_party_addresses','crm_party_address_history','crm_communication_policies','crm_communication_methods','crm_communication_method_history');" | tr -d '[:space:]')
[ "$tables" = 5 ]
capabilities=$(run_sql "SELECT COUNT(*) FROM capabilities WHERE code IN ('CRM.ADDRESS.READ','CRM.ADDRESS.WRITE','CRM.ADDRESS.ADMIN','CRM.COMMUNICATION.READ','CRM.COMMUNICATION.WRITE','CRM.COMMUNICATION.ADMIN','CRM.COMMUNICATION.SENSITIVE.READ','CRM.COMMUNICATION.EXPORT');" | tr -d '[:space:]')
[ "$capabilities" = 8 ]
failed=$(run_sql "SELECT COUNT(*) FROM flyway_schema_history WHERE success=FALSE;" | tr -d '[:space:]')
[ "$failed" = 0 ]
duplicates=$(run_sql "SELECT COUNT(*) FROM (SELECT version FROM flyway_schema_history WHERE version IS NOT NULL GROUP BY version HAVING COUNT(*)>1) d;" | tr -d '[:space:]')
[ "$duplicates" = 0 ]

mkdir -p evidence
jq -n --arg release_sha "${TARGET_SHA:-unknown}" --rawfile history /tmp/flyway-history.tsv \
  --argjson tables "$tables" --argjson capabilities "$capabilities" \
  '{release_sha:$release_sha,flyway_history:($history|split("\n")|map(select(length>0))),tables_verified:$tables,capabilities_verified:$capabilities,failed_migrations:0,duplicate_versions:0,result:"PASS"}' \
  > evidence/crm-007-production-database.json

echo "CRM-007 production Flyway verification: PASS"
