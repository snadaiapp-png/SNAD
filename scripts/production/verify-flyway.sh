#!/usr/bin/env bash
set -euo pipefail

# G1: read-only Flyway audit role. Never borrow the live Backend
# runtime role (sanad) or the privileged migration role (sanad_migrator).
# Required credentials come exclusively from protected release secrets;
# there is deliberately no fallback to Render DATABASE_* variables.
: "${FLYWAY_AUDIT_JDBC_URL:?FLYWAY_AUDIT_JDBC_URL is required}"
: "${FLYWAY_AUDIT_USERNAME:?FLYWAY_AUDIT_USERNAME is required}"
: "${FLYWAY_AUDIT_PASSWORD:?FLYWAY_AUDIT_PASSWORD is required}"

if [[ "$FLYWAY_AUDIT_USERNAME" != "sanad_release_auditor" ]]; then
  echo "::error::FLYWAY_AUDIT_USERNAME must equal sanad_release_auditor (runtime/migrator identities forbidden)" >&2
  exit 1
fi

# libpq URI, not CLI credentials: forbid embedded user/password and
# libpq overrides that could switch authenticated identities.
AUDIT_URI="${FLYWAY_AUDIT_JDBC_URL#jdbc:}"
if [[ "$AUDIT_URI" != postgresql://* || "$AUDIT_URI" == *"@"* || "$AUDIT_URI" =~ [\?\&](user|password|options|service|host|dbname)= ]]; then
  echo "::error::Invalid audit JDBC URL: require postgresql:// URI without embedded credentials or identity overrides" >&2
  exit 1
fi

cleanup() { rm -f /tmp/flyway-history.tsv /tmp/flyway-failures.txt /tmp/flyway-duplicates.txt /tmp/flyway-checksums.txt; }
trap cleanup EXIT

run_sql() {
  PGPASSWORD="$FLYWAY_AUDIT_PASSWORD" PGSSLMODE="${PGSSLMODE:-require}" psql \
    --dbname="$AUDIT_URI" --username="$FLYWAY_AUDIT_USERNAME" \
    --no-psqlrc --set=ON_ERROR_STOP=1 --tuples-only --no-align --field-separator=$'\t' --command="$1"
}

# Verify the *actual* connected role, forbidden privilege/membership
# escalations and read-only access before querying Flyway history.
connected="$(run_sql "SELECT current_user, rolsuper, rolbypassrls, rolcreatedb, rolcreaterole FROM pg_roles WHERE rolname=current_user;")"
if [[ "$connected" != "$(printf "sanad_release_auditor\tf\tf\tf\tf")" ]]; then
  echo "::error::Flyway audit connection role or privilege contract invalid" >&2
  exit 1
fi
audit_acl="$(run_sql "SELECT has_table_privilege(current_user, 'public.flyway_schema_history', 'SELECT'), has_table_privilege(current_user, 'public.flyway_schema_history', 'INSERT'), has_table_privilege(current_user, 'public.flyway_schema_history', 'UPDATE'), has_table_privilege(current_user, 'public.flyway_schema_history', 'DELETE'), has_table_privilege(current_user, 'public.access_capabilities', 'SELECT'), has_schema_privilege(current_user, 'public', 'CREATE'), pg_has_role(current_user, 'sanad_migrator', 'MEMBER'), pg_has_role(current_user, 'sanad', 'MEMBER');")"
if [[ "$audit_acl" != "$(printf "t\tf\tf\tf\tt\tf\tf\tf")" ]]; then
  echo "::error::Flyway audit role ACL violates minimum read-only contract" >&2
  exit 1
fi
# The live runtime MUST remain forbidden from reading migration history
# or escalating to DDL/migrator. Audit success must not hide runtime privilege drift.
runtime_denials="$(run_sql "SELECT has_table_privilege('sanad', 'public.flyway_schema_history', 'SELECT'), has_schema_privilege('sanad', 'public', 'CREATE'), pg_has_role('sanad', 'sanad_migrator', 'MEMBER');")"
if [[ "$runtime_denials" != "$(printf "f\tf\tf")" ]]; then
  echo "::error::Runtime sanad has unauthorized Flyway history/schema CREATE/migrator membership" >&2
  exit 1
fi
echo "FLYWAY_AUDITOR_IDENTITY_AND_ACL=PASS (dedicated non-migrator; runtime history/DDL denied)"


# Check all required migrations including V20260702.3
run_sql "SELECT version, type, description, success FROM flyway_schema_history WHERE version IN ('15','20260702.1','20260702.2','20260702.3') ORDER BY installed_rank;" > /tmp/flyway-history.tsv

require_migration() {
  awk -F $'\t' -v v="$1" -v t="$2" -v d="$3" '$1==v&&$2==t&&$3==d&&tolower($4)~/^(t|true)$/{f=1}END{exit f?0:1}' /tmp/flyway-history.tsv || {
    echo "::error::Required migration absent: version=$1 type=$2 description=$3"; exit 1; }
}

require_migration "15" "JDBC" "seed rbac roles and capabilities"
echo "FLYWAY V15: PASS"
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
CHECKSUM_ISSUES=$(run_sql "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE AND checksum IS NULL AND type = 'SQL';")
if [[ "$(tr -d '[:space:]' <<< "$CHECKSUM_ISSUES")" != "0" ]]; then
  echo "::error::Flyway history contains unexpected missing checksums: $CHECKSUM_ISSUES"
  exit 1
fi
echo "CHECKSUM PRESENCE: PASS (not full checksum equivalence)"

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
# Ledger head MUST be derived by application order (installed_rank DESC), NOT
# by max(version): `version` is VARCHAR, so a SQL max() is a lexicographic
# maximum — the canonical ledger contains legacy numeric versions ("9", "14",
# "15") which sort ABOVE every "2026..." version, making a text max() report
# the ledger head as "9" (production release 34291484570 failed exactly this
# way). installed_rank ordering is how Flyway itself defines the applied head;
# repeatable migrations carry version IS NULL and are excluded like DELETE
# markers.
DB_MAX_VERSION=$(run_sql "SELECT COALESCE((SELECT version FROM flyway_schema_history WHERE success = TRUE AND type != 'DELETE' AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1), '0');")
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
DEF_FAMILY=$(run_sql "SELECT COUNT(*) FROM pg_catalog.pg_attribute a JOIN pg_catalog.pg_class c ON c.oid = a.attrelid JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = 'public' AND c.relname = 'workflow_definitions' AND a.attname = 'definition_family_id' AND a.attnum > 0 AND NOT a.attisdropped;")
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
