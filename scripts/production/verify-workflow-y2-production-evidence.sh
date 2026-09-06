#!/usr/bin/env bash
set -euo pipefail

: "${RENDER_API_KEY:?RENDER_API_KEY is required}"
: "${RENDER_SERVICE_ID:?RENDER_SERVICE_ID is required}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"
ENGINE="$ROOT/scripts/production/workflow_y2_evidence.py"
OUTPUT="${WORKFLOW_Y2_EVIDENCE_OUTPUT:-$ROOT/workflow-y2-production-evidence.json}"
SNAPSHOT="$(mktemp)"
RENDER_RAW="$(mktemp)"
RENDER_SANITIZED="$(mktemp)"
trap 'rm -f "$SNAPSHOT" "$RENDER_RAW" "$RENDER_SANITIZED"' EXIT

test -f "$ENGINE" || { echo "::error::Evidence engine not found: $ENGINE"; exit 1; }

echo "::add-mask::$RENDER_API_KEY"
echo "::add-mask::$RENDER_SERVICE_ID"

http_get() {
  local url="$1"
  curl --fail-with-body --silent --show-error --max-time 45 \
    --header "Authorization: Bearer $RENDER_API_KEY" \
    --header "Accept: application/json" \
    "$url"
}

RENDER_BASE="https://api.render.com/v1/services/$RENDER_SERVICE_ID"
http_get "$RENDER_BASE/env-vars?limit=100" > "$RENDER_RAW"

ENV_COUNT="$(jq 'length' "$RENDER_RAW")"
test "$ENV_COUNT" -lt 100 || {
  echo "::error::Cannot prove complete Render environment pagination."
  exit 1
}

jq '[.[]? | (.envVar // .) | {
      key: .key,
      present: (((.value // "") | tostring | length) > 0)
    }]' "$RENDER_RAW" > "$RENDER_SANITIZED"

get_render_var() {
  local key="$1"
  jq -r --arg key "$key" \
    '[.[]? | (.envVar // .)] | .[] | select(.key == $key) | .value // empty' \
    "$RENDER_RAW" | head -n 1
}

DATABASE_URL="$(get_render_var DATABASE_URL)"
DATABASE_USERNAME="$(get_render_var DATABASE_USERNAME)"
DATABASE_PASSWORD="$(get_render_var DATABASE_PASSWORD)"

for name in DATABASE_URL DATABASE_USERNAME DATABASE_PASSWORD; do
  test -n "${!name:-}" || { echo "::error::$name is missing from Render"; exit 1; }
  echo "::add-mask::${!name}"
done

readarray -t DB_PARTS < <(
  DATABASE_URL_RAW="$DATABASE_URL" python3 - <<'PY'
import os
from urllib.parse import urlparse

raw = os.environ["DATABASE_URL_RAW"]
if raw.startswith("jdbc:"):
    raw = raw[5:]
parsed = urlparse(raw)
if parsed.scheme not in {"postgres", "postgresql"}:
    raise SystemExit("unsupported database URL scheme")
if not parsed.hostname or not parsed.path or parsed.path == "/":
    raise SystemExit("database URL is incomplete")
print(parsed.hostname)
print(parsed.port or 5432)
print(parsed.path.lstrip("/"))
PY
)

PGHOST="${DB_PARTS[0]}"
PGPORT="${DB_PARTS[1]}"
PGDATABASE="${DB_PARTS[2]}"
for value in "$PGHOST" "$PGDATABASE"; do echo "::add-mask::$value"; done

export PGPASSWORD="$DATABASE_PASSWORD"
export PGOPTIONS='-c default_transaction_read_only=on'

READ_ONLY_STATE="$(
  psql -h "$PGHOST" -p "$PGPORT" -U "$DATABASE_USERNAME" -d "$PGDATABASE" \
    --no-psqlrc --set=ON_ERROR_STOP=1 --tuples-only --no-align --quiet \
    --command="SHOW default_transaction_read_only;"
)"
test "$READ_ONLY_STATE" = "on" || {
  echo "::error::PostgreSQL session is not read-only."
  exit 1
}

run_read_only_sql() {
  local sql="$1"
  PYTHONPATH="$ROOT/scripts/production" python3 -c \
    'import sys; from workflow_y2_evidence import assert_read_only_sql; assert_read_only_sql(sys.stdin.read())' \
    <<< "$sql"

  psql -h "$PGHOST" -p "$PGPORT" -U "$DATABASE_USERNAME" -d "$PGDATABASE" \
    --no-psqlrc --set=ON_ERROR_STOP=1 --tuples-only --no-align --quiet <<SQL
BEGIN TRANSACTION READ ONLY;
$sql
COMMIT;
SQL
}

DB_HISTORY_SQL="$(cat <<'SQL'
SELECT COALESCE(
  json_agg(
    json_build_object(
      'installedRank', installed_rank,
      'version', version,
      'type', type,
      'success', success,
      'checksum', checksum
    )
    ORDER BY installed_rank
  ) FILTER (WHERE version IS NOT NULL),
  '[]'::json
)::text
FROM flyway_schema_history;
SQL
)"
DB_HISTORY_JSON="$(run_read_only_sql "$DB_HISTORY_SQL")"

TABLE_METADATA_SQL="$(cat <<'SQL'
WITH expected(table_name) AS (
  VALUES
    ('workflow_step_transitions'),
    ('workflow_work_items'),
    ('workflow_work_item_candidates'),
    ('workflow_branch_tokens'),
    ('workflow_business_calendars'),
    ('workflow_calendar_holidays'),
    ('workflow_delegations'),
    ('workflow_execution_attempts'),
    ('workflow_incidents'),
    ('workflow_event_inbox'),
    ('workflow_event_outbox'),
    ('workflow_notification_intents')
),
facts AS (
  SELECT
    e.table_name,
    c.oid IS NOT NULL AS exists,
    EXISTS (
      SELECT 1
      FROM information_schema.columns ic
      WHERE ic.table_schema = 'public'
        AND ic.table_name = e.table_name
        AND ic.column_name = 'tenant_id'
    ) AS tenant_id,
    COALESCE(c.relrowsecurity, false) AS rls,
    COALESCE(c.relforcerowsecurity, false) AS force_rls,
    EXISTS (
      SELECT 1
      FROM pg_policies p
      WHERE p.schemaname = 'public'
        AND p.tablename = e.table_name
        AND p.policyname = 'tenant_isolation'
    ) AS tenant_policy
  FROM expected e
  LEFT JOIN pg_namespace n ON n.nspname = 'public'
  LEFT JOIN pg_class c
    ON c.relnamespace = n.oid
   AND c.relname = e.table_name
   AND c.relkind IN ('r','p')
)
SELECT COALESCE(
  json_object_agg(
    table_name,
    json_build_object(
      'exists', exists,
      'tenantId', tenant_id,
      'rls', rls,
      'forceRls', force_rls,
      'tenantPolicy', tenant_policy
    )
  ),
  '{}'::json
)::text
FROM facts;
SQL
)"
TABLE_METADATA_JSON="$(run_read_only_sql "$TABLE_METADATA_SQL")"

COLUMN_METADATA_SQL="$(cat <<'SQL'
SELECT COALESCE(
  json_agg(table_name || '.' || column_name ORDER BY table_name, ordinal_position),
  '[]'::json
)::text
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name IN (
    'workflow_definitions',
    'workflow_instances',
    'workflow_approval_requests'
  );
SQL
)"
COLUMN_METADATA_JSON="$(run_read_only_sql "$COLUMN_METADATA_SQL")"

CAPABILITY_SQL="$(cat <<'SQL'
SELECT COALESCE(
  json_agg(
    json_build_object('code', code, 'status', status)
    ORDER BY code
  ),
  '[]'::json
)::text
FROM access_capabilities
WHERE code LIKE 'WORKFLOW.%';
SQL
)"
CAPABILITY_JSON="$(run_read_only_sql "$CAPABILITY_SQL")"

ADMIN_BINDING_SQL="$(cat <<'SQL'
WITH y2(code) AS (
  VALUES
    ('WORKFLOW.DESIGN'),
    ('WORKFLOW.VALIDATE'),
    ('WORKFLOW.PUBLISH'),
    ('WORKFLOW.START'),
    ('WORKFLOW.TASK_EXECUTE'),
    ('WORKFLOW.REASSIGN'),
    ('WORKFLOW.DELEGATE'),
    ('WORKFLOW.CANCEL'),
    ('WORKFLOW.INCIDENT_MANAGE'),
    ('WORKFLOW.MONITOR'),
    ('WORKFLOW.AUDIT_VIEW'),
    ('WORKFLOW.BREAK_GLASS'),
    ('WORKFLOW.SELF_APPROVAL_OVERRIDE')
),
active_tenants AS (
  SELECT id FROM tenants WHERE status = 'ACTIVE'
),
admin_roles AS (
  SELECT DISTINCT ON (tenant_id) tenant_id, id
  FROM roles
  WHERE code = 'ADMIN' AND status = 'ACTIVE'
  ORDER BY tenant_id, id
),
complete AS (
  SELECT ar.tenant_id
  FROM admin_roles ar
  WHERE (
    SELECT COUNT(DISTINCT y.code)
    FROM y2 y
    JOIN access_capabilities ac
      ON ac.code = y.code AND ac.status = 'ACTIVE'
    JOIN role_capabilities rc
      ON rc.tenant_id = ar.tenant_id
     AND rc.role_id = ar.id
     AND rc.capability_id = ac.id
  ) = 13
)
SELECT json_build_object(
  'activeTenants', (SELECT COUNT(*) FROM active_tenants),
  'activeTenantsWithAdmin', (
    SELECT COUNT(*)
    FROM active_tenants t
    JOIN admin_roles ar ON ar.tenant_id = t.id
  ),
  'activeTenantsWithCompleteY2AdminBinding', (
    SELECT COUNT(*)
    FROM active_tenants t
    JOIN complete c ON c.tenant_id = t.id
  ),
  'incompleteBindings', (
    SELECT COUNT(*) FROM active_tenants
  ) - (
    SELECT COUNT(*)
    FROM active_tenants t
    JOIN complete c ON c.tenant_id = t.id
  )
)::text;
SQL
)"
ADMIN_BINDING_JSON="$(run_read_only_sql "$ADMIN_BINDING_SQL")"

MAIN_SHA="${GITHUB_SHA:-$(git -C "$ROOT" rev-parse HEAD 2>/dev/null || true)}"

jq -n \
  --arg mainSha "$MAIN_SHA" \
  --slurpfile renderEnv "$RENDER_SANITIZED" \
  --argjson dbHistory "$DB_HISTORY_JSON" \
  --argjson tables "$TABLE_METADATA_JSON" \
  --argjson columns "$COLUMN_METADATA_JSON" \
  --argjson capabilities "$CAPABILITY_JSON" \
  --argjson adminBindings "$ADMIN_BINDING_JSON" \
  '{
    mainSha: $mainSha,
    databaseReadOnly: true,
    renderEnv: $renderEnv[0],
    dbHistory: $dbHistory,
    schema: {
      tables: $tables,
      columns: $columns
    },
    capabilities: $capabilities,
    adminBindings: $adminBindings
  }' > "$SNAPSHOT"

set +e
python3 "$ENGINE" \
  --snapshot "$SNAPSHOT" \
  --repo-root "$ROOT" \
  --output "$OUTPUT"
ENGINE_STATUS=$?
set -e

case "$ENGINE_STATUS" in
  0)
    echo "WORKFLOW_Y2_PRODUCTION_EVIDENCE=PASS"
    ;;
  3)
    echo "::error::Workflow Y2 production evidence found contract drift."
    exit 3
    ;;
  *)
    echo "::error::Workflow Y2 production evidence failed."
    exit "$ENGINE_STATUS"
    ;;
esac