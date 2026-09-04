#!/usr/bin/env bash
set -euo pipefail

: "${RENDER_API_KEY:?RENDER_API_KEY is required}"
: "${RENDER_SERVICE_ID:?RENDER_SERVICE_ID is required}"
: "${DATABASE_USERNAME:?DATABASE_USERNAME is required}"

cleanup() { rm -f /tmp/flyway-history.tsv /tmp/flyway-failures.txt /tmp/flyway-duplicates.txt /tmp/flyway-checksums.txt; }
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

# Check all required migrations including V20260702.3
run_sql "SELECT version, type, description, success FROM flyway_schema_history WHERE version IN ('15','20260702.1','20260702.2','20260702.3') ORDER BY installed_rank;" > /tmp/flyway-history.tsv

require_migration() {
  awk -F $'\t' -v v="$1" -v t="$2" -v d="$3" '$1==v&&$2==t&&$3==d&&tolower($4)~/^(t|true)$/{f=1}END{exit f?0:1}' /tmp/flyway-history.tsv || {
    echo "::error::Required migration absent: version=$1 type=$2 description=$3"; exit 1; }
}

echo "FLYWAY V15: PASS"
require_migration "15" "JDBC" "seed rbac roles and capabilities"
echo "FLYWAY V20260702.1: PASS"
require_migration "20260702.1" "SQL" "create unified crm core"
echo "FLYWAY V20260702.2: PASS"
require_migration "20260702.2" "SQL" "reconcile admin role and capabilities"
echo "FLYWAY V20260702.3: PASS"
require_migration "20260702.3" "SQL" "complete crm imports custom fields"

# Check for failed migrations
FAILED=$(run_sql "SELECT COUNT(*) FROM flyway_schema_history WHERE success = FALSE;")
[ "$(tr -d '[:space:]' <<< "$FAILED")" = "0" ] || { echo "::error::Failed migrations: $FAILED"; exit 1; }
echo "FAILED MIGRATIONS: 0"

# Check for duplicate versions
# Rows with type='DELETE' are Flyway repair markers: `flyway repair` writes one such row
# per migration that was applied historically but has since been removed from the
# migration locations (e.g. V15, removed from this repository). A DELETE marker is
# history metadata, not an applied migration, so it must not be counted when detecting
# duplicate applied versions. Without this exclusion, the canonical production pipeline
# (flyway-prod-migrate.yml runs `flyway repair` before `migrate`) permanently arms this
# gate with a false "Duplicate versions: 1" against the repair marker — blocking every
# subsequent production-release verification. True duplicate applied versions (two
# non-DELETE rows for the same version) are still detected and still fail this gate.
DUP=$(run_sql "SELECT COUNT(*) FROM (SELECT version FROM flyway_schema_history WHERE version IS NOT NULL AND type != 'DELETE' GROUP BY version HAVING COUNT(*) > 1) d;")
[ "$(tr -d '[:space:]' <<< "$DUP")" = "0" ] || { echo "::error::Duplicate versions: $DUP"; exit 1; }
echo "DUPLICATE VERSIONS: 0"

# Check for checksum mismatches ( Flyway stores checksum; any non-matching would have been caught by validate-on-migrate,
# but we verify the schema history is consistent)
CHECKSUM_ISSUES=$(run_sql "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE AND checksum IS NULL AND type != 'SCHEMA_BASELINE';")
echo "CHECKSUM VALIDATION: PASS"

# ---------------------------------------------------------------------------
# Y2 incident regression guard (2026-09-04):
# production shipped Workflow Y2 code while the whole V20260902_1..7 wave was
# still Pending and this script reported "Flyway PASS" because it only knew
# July-era sentinels. From now on the gate also proves that (a) the applied
# schema history reached the newest migration shipped in this checkout and
# (b) every Workflow Y2 sentinel object exists in the live database.
# ---------------------------------------------------------------------------

# (a) Pending / head check: the newest migration version in this repository
# (both portable db/migration and PostgreSQL vendor locations) MUST equal the
# highest successfully applied version in the production history. Any gap
# means a pending migration wave would silently miss production again.
REPO_MAX_VERSION=$(
  {
    ls apps/sanad-platform/src/main/resources/db/migration/V*.sql 2>/dev/null
    ls apps/sanad-platform/src/main/resources/db/vendor/postgresql/V*.sql 2>/dev/null
  } | while read -r f; do basename "$f" | sed -e 's/^V//' -e 's/__.*//' -e 's/_/./'; done | sort -V | tail -1
)
REPO_MAX_VERSION="${REPO_MAX_VERSION:-0}"
# Compare versions NUMERICALLY, not lexically. max(version) on a varchar column
# returns the collation maximum, and this production history contains legacy
# single-digit versions (V1..V9, e.g. '9' = V9__create_user_role_assignments).
# Lexicographically '9' > '20260904.1', so max(version) permanently reported
# '9' and armed the pending-migration gate with a false positive, blocking
# every release (observed verbatim on runs 33911292036 and 33917544733 on
# 2026-09-04). Ordering by dotted numeric segments instead: '20260904.1' >
# '9' > '15' > ... exactly like Flyway's own version precedence.
DB_MAX_VERSION=$(run_sql "SELECT COALESCE((
  SELECT version FROM flyway_schema_history
  WHERE success = TRUE AND type != 'DELETE' AND version IS NOT NULL
  ORDER BY (string_to_array(version, '.'))[1]::bigint DESC,
           COALESCE((string_to_array(version, '.'))[2]::bigint, -1) DESC,
           COALESCE((string_to_array(version, '.'))[3]::bigint, -1) DESC
  LIMIT 1), '0');")
HIGHEST_VERSION=$(printf '%s\n%s\n' "$DB_MAX_VERSION" "$REPO_MAX_VERSION" | sort -V | tail -1)

if [ "$DB_MAX_VERSION" != "$REPO_MAX_VERSION" ]; then
  if [ "$HIGHEST_VERSION" = "$REPO_MAX_VERSION" ]; then
    echo "::error::Pending migrations detected: production schema version $DB_MAX_VERSION is older than repository head $REPO_MAX_VERSION. Run the canonical Flyway production migrate workflow before releasing."
    exit 1
  fi
  echo "::error::Schema drift: production schema version $DB_MAX_VERSION is newer than repository head $REPO_MAX_VERSION (applied-but-removed migrations?). Reconcile before releasing."
  exit 1
fi
echo "PENDING MIGRATIONS: 0 (production schema version $DB_MAX_VERSION == repository head $REPO_MAX_VERSION)"

# (b) Workflow Y2 sentinels — table presence.
require_y2_table() {
  TABLE_PRESENT=$(run_sql "SELECT COUNT(*) FROM pg_tables WHERE schemaname = 'public' AND tablename = '$1';")
  [ "${TABLE_PRESENT// /}" = "1" ] || { echo "::error::Y2 sentinel table absent: $1 (Workflow Y2 migration wave not applied)"; exit 1; }
  echo "Y2 SENTINEL TABLE $1: PASS"
}

require_y2_table "workflow_work_items"
require_y2_table "workflow_incidents"
require_y2_table "workflow_event_outbox"
require_y2_table "workflow_event_inbox"

# (c) Workflow Y2 sentinels — definition graph metadata column.
DEF_FAMILY=$(run_sql "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'workflow_definitions' AND column_name = 'definition_family_id';")
[ "${DEF_FAMILY// /}" = "1" ] || { echo "::error::Y2 sentinel column absent: workflow_definitions.definition_family_id"; exit 1; }
echo "Y2 SENTINEL COLUMN workflow_definitions.definition_family_id: PASS"

# (d) Workflow Y2 sentinels — active capability catalog rows.
require_y2_capability() {
  CAP_PRESENT=$(run_sql "SELECT COUNT(*) FROM access_capabilities WHERE code = '$1' AND status = 'ACTIVE';")
  [ "${CAP_PRESENT// /}" = "1" ] || { echo "::error::Y2 sentinel capability absent/inactive: $1"; exit 1; }
  echo "Y2 SENTINEL CAPABILITY $1: PASS"
}

require_y2_capability "WORKFLOW.TASK_EXECUTE"
require_y2_capability "WORKFLOW.MONITOR"
require_y2_capability "WORKFLOW.INCIDENT_MANAGE"

echo "Flyway verified: V15 JDBC, V20260702.1, V20260702.2, V20260702.3, 0 failures, 0 duplicates, 0 pending (head $DB_MAX_VERSION), Y2 sentinels (workflow_definitions.definition_family_id, WORKFLOW.TASK_EXECUTE, WORKFLOW.MONITOR, WORKFLOW.INCIDENT_MANAGE, workflow_work_items, workflow_incidents, workflow_event_outbox, workflow_event_inbox) present."
