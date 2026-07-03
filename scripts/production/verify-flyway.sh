#!/usr/bin/env bash
set -euo pipefail

: "${RENDER_API_KEY:?RENDER_API_KEY is required}"
: "${RENDER_SERVICE_ID:?RENDER_SERVICE_ID is required}"
: "${DATABASE_USERNAME:?DATABASE_USERNAME is required}"

cleanup() { rm -f /tmp/flyway-history.tsv /tmp/flyway-failures.txt /tmp/flyway-duplicates.txt; }
trap cleanup EXIT

echo "Fetching env vars from Render..."
RENDER_ENV=$(curl --silent --show-error \
  --header "Authorization: Bearer $RENDER_API_KEY" \
  --header "Accept: application/json" \
  "https://api.render.com/v1/services/$RENDER_SERVICE_ID/env-vars?limit=100")

DATABASE_URL=$(echo "$RENDER_ENV" | jq -r '[.[]? | (.envVar // .)] | .[] | select(.key == "DATABASE_URL") | .value // empty')
DATABASE_PASSWORD=$(echo "$RENDER_ENV" | jq -r '[.[]? | (.envVar // .)] | .[] | select(.key == "DATABASE_PASSWORD") | .value // empty')

test -n "$DATABASE_URL" || { echo "::error::DATABASE_URL not found in Render"; exit 1; }
test -n "$DATABASE_PASSWORD" || { echo "::error::DATABASE_PASSWORD not found in Render"; exit 1; }

RAW_URL="$DATABASE_URL"
RAW_URL="${RAW_URL#jdbc:}"
RAW_URL="${RAW_URL#postgresql://}"
RAW_URL="${RAW_URL#https://}"
HOST_PORT="${RAW_URL%%/*}"
DB_PART="${RAW_URL#*/}"
DB_NAME="${DB_PART%%\?*}"
PGHOST="${HOST_PORT%%:*}"
PGPORT="${HOST_PORT#*:}"
PGPORT="${PGPORT:-5432}"

echo "Connecting to: host=$PGHOST port=$PGPORT dbname=$DB_NAME"

run_sql() {
  PGPASSWORD="$DATABASE_PASSWORD" psql -h "$PGHOST" -p "$PGPORT" -U "$DATABASE_USERNAME" -d "$DB_NAME" \
    --no-psqlrc --set=ON_ERROR_STOP=1 --tuples-only --no-align --field-separator=$'\t' --command="$1"
}

run_sql "SELECT version, type, description, success FROM flyway_schema_history WHERE version IN ('15','20260702.1','20260702.2') ORDER BY installed_rank;" > /tmp/flyway-history.tsv

require_migration() {
  awk -F $'\t' -v v="$1" -v t="$2" -v d="$3" '$1==v&&$2==t&&$3==d&&tolower($4)~/^(t|true)$/{f=1}END{exit f?0:1}' /tmp/flyway-history.tsv || {
    echo "::error::Required migration absent: version=$1 type=$2 description=$3"; exit 1; }
}

require_migration "15" "JDBC" "seed rbac roles and capabilities"
require_migration "20260702.1" "SQL" "create unified crm core"
require_migration "20260702.2" "SQL" "reconcile admin role and capabilities"

FAILED=$(run_sql "SELECT COUNT(*) FROM flyway_schema_history WHERE success = FALSE;")
[ "$(tr -d '[:space:]' <<< "$FAILED")" = "0" ] || { echo "::error::Failed migrations"; exit 1; }

DUP=$(run_sql "SELECT COUNT(*) FROM (SELECT version FROM flyway_schema_history WHERE version IS NOT NULL GROUP BY version HAVING COUNT(*) > 1) d;")
[ "$(tr -d '[:space:]' <<< "$DUP")" = "0" ] || { echo "::error::Duplicate versions"; exit 1; }

echo "Flyway verified: V15 JDBC, CRM V20260702.1, RBAC V20260702.2, 0 failures, 0 duplicates."
