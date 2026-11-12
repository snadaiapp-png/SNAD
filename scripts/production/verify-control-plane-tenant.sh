#!/usr/bin/env bash
set -euo pipefail

: "${RENDER_API_KEY:?RENDER_API_KEY is required}"
: "${RENDER_SERVICE_ID:?RENDER_SERVICE_ID is required}"
: "${DATABASE_USERNAME:?DATABASE_USERNAME is required}"
: "${CONTROL_PLANE_TENANT_ID:?CONTROL_PLANE_TENANT_ID is required}"

[[ "$CONTROL_PLANE_TENANT_ID" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$ ]] || {
  echo "::error::CONTROL_PLANE_TENANT_ID must be a valid UUID."
  exit 1
}
echo "::add-mask::$CONTROL_PLANE_TENANT_ID"
echo "::add-mask::$DATABASE_USERNAME"

RENDER_ENV=$(curl --silent --show-error \
  --header "Authorization: Bearer $RENDER_API_KEY" \
  --header "Accept: application/json" \
  "https://api.render.com/v1/services/$RENDER_SERVICE_ID/env-vars?limit=100")

DATABASE_URL=$(echo "$RENDER_ENV" | jq -r '[.[]? | (.envVar // .)] | .[] | select(.key == "DATABASE_URL") | .value // empty')
DATABASE_PASSWORD=$(echo "$RENDER_ENV" | jq -r '[.[]? | (.envVar // .)] | .[] | select(.key == "DATABASE_PASSWORD") | .value // empty')

test -n "$DATABASE_URL" || { echo "::error::DATABASE_URL not found in Render"; exit 1; }
test -n "$DATABASE_PASSWORD" || { echo "::error::DATABASE_PASSWORD not found in Render"; exit 1; }
echo "::add-mask::$DATABASE_URL"
echo "::add-mask::$DATABASE_PASSWORD"

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
echo "::add-mask::$PGHOST"
echo "::add-mask::$DB_NAME"

# ---------------------------------------------------------------------------
# Strict psql output contract (production release incident 43189957654).
#
# psql executes `--command="SET app.tenant_id = ...; SELECT COUNT(*) ..."` in
# ONE session and prints the SET command tag line ("SET") before the SELECT
# row. The previous implementation fed that raw two-line stdout directly to
# `[ "$ADMIN_EXISTS" -ge 1 ]`, which aborted with "integer expression
# expected" and failed an otherwise healthy production database with the
# misleading message "No ACTIVE ADMIN role" (job "Verify Control Plane tenant
# in production database", run 43189957654). Parsing is now explicit and
# fail-closed. Accepted shape, nothing else:
#
#   [ "SET" ] <non-negative integer> EOF
#
#   * at most ONE leading "SET" command tag — the single in-SQL GUC bind of
#     the roles check (the tenants check emits no tag);
#   * then EXACTLY ONE integer count line;
#   * no further output of any kind.
#
# Empty, non-numeric, duplicated, out-of-order or trailing output is a hard
# error: the output is NEVER interpreted as a count and never silently
# ignored (no `|| true`, no fallback value). The error reports only the
# observed non-blank line count — never the raw bytes — so unexpected psql
# output cannot relay secret values into the log.
# ---------------------------------------------------------------------------
COUNT_VALUE=""
parse_single_count() {
  local label="$1" raw="$2"
  local line value="" saw_set=0 saw_value=0 malformed=0 nonblank=0

  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"
    line="${line#"${line%%[![:space:]]*}"}"
    line="${line%"${line##*[![:space:]]}"}"
    if [[ -z "$line" ]]; then
      continue
    fi
    nonblank=$((nonblank + 1))
    if [[ "$line" == "SET" ]]; then
      # Command tag of the in-SQL GUC bind; it must precede the value line
      # and may appear at most once.
      if [[ "$saw_set" -eq 1 || "$saw_value" -eq 1 ]]; then
        malformed=1
      fi
      saw_set=1
      continue
    fi
    if [[ "$saw_value" -eq 1 ]] || ! [[ "$line" =~ ^[0-9]+$ ]]; then
      malformed=1
      continue
    fi
    value="$line"
    saw_value=1
  done <<< "$raw"

  if [[ "$malformed" -eq 1 || "$saw_value" -ne 1 ]]; then
    echo "::error::Unparseable psql output for the $label check: expected at most one leading 'SET' command tag followed by exactly one integer count; found $nonblank non-blank line(s) of unexpected shape. Failing closed without interpreting the database state."
    exit 1
  fi
  COUNT_VALUE="$value"
}

TENANT_COUNT_RAW=$(PGOPTIONS='-c default_transaction_read_only=on' PGPASSWORD="$DATABASE_PASSWORD" psql \
  -h "$PGHOST" -p "$PGPORT" -U "$DATABASE_USERNAME" -d "$DB_NAME" \
  --no-psqlrc --set=ON_ERROR_STOP=1 --tuples-only --no-align \
  --command="SELECT COUNT(*) FROM tenants WHERE id = '${CONTROL_PLANE_TENANT_ID}' AND status = 'ACTIVE';") || {
  echo "::error::psql failed while verifying the Control Plane tenant exists (see psql stderr above); failing closed."
  exit 1
}
parse_single_count "Control Plane tenant existence" "$TENANT_COUNT_RAW"
[ "$COUNT_VALUE" -ge 1 ] || {
  echo "::error::Control Plane tenant not found or not ACTIVE"
  exit 1
}

# The roles table is tenant-owned and carries fail-closed RLS
# (V20261029_1: USING/WITH CHECK tenant_id::text =
# NULLIF(current_setting('app.tenant_id', true), '')). A raw runtime-role
# query without the tenant GUC is default-denied and would report a healthy
# ACTIVE ADMIN role as missing (observed on the first post-migration release
# attempt, run 42945339351). The Supabase session pooler strips startup
# options — including PGOPTIONS — so the tenant context is bound with an
# in-SQL SET on the same psql invocation, evaluated by the same fail-closed
# policy exactly as the application binds it per request. The SET and the
# SELECT therefore MUST stay in ONE --command (one session): splitting them
# into separate psql calls would drop the GUC and default-deny the query.
ADMIN_COUNT_RAW=$(PGPASSWORD="$DATABASE_PASSWORD" psql \
  -h "$PGHOST" -p "$PGPORT" -U "$DATABASE_USERNAME" -d "$DB_NAME" \
  --no-psqlrc --set=ON_ERROR_STOP=1 --tuples-only --no-align \
  --command="SET app.tenant_id = '${CONTROL_PLANE_TENANT_ID}'; SELECT COUNT(*) FROM roles WHERE tenant_id = '${CONTROL_PLANE_TENANT_ID}' AND code = 'ADMIN' AND status = 'ACTIVE';") || {
  echo "::error::psql failed while verifying the ACTIVE ADMIN role exists (see psql stderr above); failing closed."
  exit 1
}
# This single-session SET+SELECT prints "SET" before the count row — the
# exact shape that broke release 43189957654; parse_single_count accepts it
# and fails closed on every other shape.
parse_single_count "ACTIVE ADMIN role existence" "$ADMIN_COUNT_RAW"
[ "$COUNT_VALUE" -ge 1 ] || {
  echo "::error::No ACTIVE ADMIN role for Control Plane tenant"
  exit 1
}

echo "Control Plane tenant verified: ACTIVE tenant with ADMIN role exists."
