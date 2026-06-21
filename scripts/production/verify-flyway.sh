#!/usr/bin/env bash
set -euo pipefail

: "${PRODUCTION_DATABASE_URL:?PRODUCTION_DATABASE_URL is required}"

cleanup() {
  rm -f /tmp/flyway-history.tsv
}
trap cleanup EXIT

psql "$PRODUCTION_DATABASE_URL" \
  --no-psqlrc \
  --set=ON_ERROR_STOP=1 \
  --tuples-only \
  --no-align \
  --field-separator=$'\t' \
  --command="
    SELECT version, success
    FROM flyway_schema_history
    WHERE version IN ('10', '11')
    ORDER BY installed_rank;
  " > /tmp/flyway-history.tsv

for version in 10 11; do
  awk -F $'\t' -v version="$version" '
    $1 == version && tolower($2) ~ /^(t|true)$/ { found = 1 }
    END { exit found ? 0 : 1 }
  ' /tmp/flyway-history.tsv || {
    echo "::error::Flyway migration V${version} is absent or unsuccessful."
    exit 1
  }
done

echo "Flyway V10 and V11 verified."
