#!/usr/bin/env bash
set -euo pipefail

: "${PRODUCTION_DATABASE_URL:?PRODUCTION_DATABASE_URL is required}"

cleanup() {
  rm -f /tmp/flyway-history.tsv /tmp/flyway-failures.txt /tmp/flyway-duplicates.txt
}
trap cleanup EXIT

psql "$PRODUCTION_DATABASE_URL" \
  --no-psqlrc \
  --set=ON_ERROR_STOP=1 \
  --tuples-only \
  --no-align \
  --field-separator=$'\t' \
  --command="
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

psql "$PRODUCTION_DATABASE_URL" \
  --no-psqlrc \
  --set=ON_ERROR_STOP=1 \
  --tuples-only \
  --no-align \
  --command="SELECT COUNT(*) FROM flyway_schema_history WHERE success = FALSE;" \
  > /tmp/flyway-failures.txt

if [ "$(tr -d '[:space:]' < /tmp/flyway-failures.txt)" != "0" ]; then
  echo "::error::Production Flyway history contains failed migrations."
  exit 1
fi

psql "$PRODUCTION_DATABASE_URL" \
  --no-psqlrc \
  --set=ON_ERROR_STOP=1 \
  --tuples-only \
  --no-align \
  --command="
    SELECT COUNT(*)
    FROM (
      SELECT version
      FROM flyway_schema_history
      WHERE version IS NOT NULL
      GROUP BY version
      HAVING COUNT(*) > 1
    ) duplicate_versions;
  " > /tmp/flyway-duplicates.txt

if [ "$(tr -d '[:space:]' < /tmp/flyway-duplicates.txt)" != "0" ]; then
  echo "::error::Production Flyway history contains duplicate version rows."
  exit 1
fi

echo "Flyway production compatibility verified: V15 JDBC, CRM V20260702.1, RBAC V20260702.2, no failures, no duplicate versions."
