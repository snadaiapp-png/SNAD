#!/usr/bin/env bash
set -euo pipefail

: "${PRODUCTION_DATABASE_URL:?PRODUCTION_DATABASE_URL is required}"
: "${DATABASE_USERNAME:?DATABASE_USERNAME is required}"
: "${DATABASE_PASSWORD:?DATABASE_PASSWORD is required}"

cleanup() {
  rm -f /tmp/flyway-history.tsv /tmp/flyway-failures.txt /tmp/flyway-duplicates.txt
}
trap cleanup EXIT

# Parse PRODUCTION_DATABASE_URL into host, port, dbname
# Handles both jdbc:postgresql://host:port/db?params and postgresql://host:port/db?params
RAW_URL="$PRODUCTION_DATABASE_URL"
RAW_URL="${RAW_URL#jdbc:}"
RAW_URL="${RAW_URL#postgresql://}"
HOST_PORT="${RAW_URL%%/*}"
DB_PART="${RAW_URL#*/}"
DB_NAME="${DB_PART%%\?*}"
PGHOST="${HOST_PORT%%:*}"
PGPORT="${HOST_PORT#*:}"
PGPORT="${PGPORT:-5432}"

echo "Connecting to: host=$PGHOST port=$PGPORT dbname=$DB_NAME user=$DATABASE_USERNAME"

run_sql() {
  PGPASSWORD="$DATABASE_PASSWORD" psql \
    -h "$PGHOST" \
    -p "$PGPORT" \
    -U "$DATABASE_USERNAME" \
    -d "$DB_NAME" \
    --no-psqlrc \
    --set=ON_ERROR_STOP=1 \
    --tuples-only \
    --no-align \
    --field-separator=$'\t' \
    --command="$1"
}

run_sql "
    SELECT version, type, description, success
    FROM flyway_schema_history
    WHERE version IN ('15', '20260702.1', '20260702.2')
    ORDER BY installed_rank;
  " > /tmp/flyway-history.tsv

require_migration() {
  local version="$1"
  local type="$2"
  local description="$3"

  awk -F $'\t' \
    -v version="$version" \
    -v type="$type" \
    -v description="$description" '
      $1 == version && $2 == type && $3 == description && tolower($4) ~ /^(t|true)$/ { found = 1 }
      END { exit found ? 0 : 1 }
    ' /tmp/flyway-history.tsv || {
      echo "::error::Required Flyway migration is absent, mismatched, or unsuccessful: version=${version}, type=${type}, description=${description}"
      exit 1
    }
}

require_migration "15" "JDBC" "seed rbac roles and capabilities"
require_migration "20260702.1" "SQL" "create unified crm core"
require_migration "20260702.2" "SQL" "reconcile admin role and capabilities"

FAILED_COUNT=$(run_sql "SELECT COUNT(*) FROM flyway_schema_history WHERE success = FALSE;")
if [ "$(tr -d '[:space:]' <<< "$FAILED_COUNT")" != "0" ]; then
  echo "::error::Production Flyway history contains failed migrations."
  exit 1
fi

DUP_COUNT=$(run_sql "
    SELECT COUNT(*)
    FROM (
      SELECT version
      FROM flyway_schema_history
      WHERE version IS NOT NULL
      GROUP BY version
      HAVING COUNT(*) > 1
    ) duplicate_versions;
  ")
if [ "$(tr -d '[:space:]' <<< "$DUP_COUNT")" != "0" ]; then
  echo "::error::Production Flyway history contains duplicate version rows."
  exit 1
fi

echo "Flyway production compatibility verified: V15 JDBC, CRM V20260702.1, RBAC V20260702.2, no failures, no duplicate versions."
