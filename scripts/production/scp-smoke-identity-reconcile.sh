#!/usr/bin/env bash
# =============================================================================
# SCP Smoke Identity Diagnose / Reconcile
# =============================================================================
# The canonical control-plane tenant is resolved from production state, not
# from a duplicated GitHub tenant secret. The resolver requires exactly one
# ACTIVE platform_admin user whose tenant is ACTIVE and whose ACTIVE role
# assignment resolves to an ACTIVE ADMIN role. The tenant UUID is masked before
# any later use and is never printed.
# =============================================================================
set -euo pipefail

MODE="${1:-diagnose}"

: "${RENDER_API_KEY:?RENDER_API_KEY is required}"
: "${RENDER_SERVICE_ID:?RENDER_SERVICE_ID is required}"
: "${DATABASE_USERNAME:?DATABASE_USERNAME is required}"
: "${CONTROL_PLANE_ADMIN_EMAIL:?CONTROL_PLANE_ADMIN_EMAIL is required}"
: "${CONTROL_PLANE_ADMIN_PASSWORD:?CONTROL_PLANE_ADMIN_PASSWORD is required}"

RENDER_API="https://api.render.com/v1/services/${RENDER_SERVICE_ID}"
AUTH_HEADERS=(-H "Authorization: Bearer ${RENDER_API_KEY}" -H "Accept: application/json")
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

log()  { echo "[identity] $*" >&2; }
pass() { echo "[identity] PASS: $*" >&2; }
fail() { echo "::error::[identity] $*" >&2; exit 1; }

mask_initial_secrets() {
  echo "::add-mask::${CONTROL_PLANE_ADMIN_EMAIL}"
  echo "::add-mask::${CONTROL_PLANE_ADMIN_PASSWORD}"
  if [ -n "${CONTROL_PLANE_BOOTSTRAP_TOKEN:-}" ]; then
    echo "::add-mask::${CONTROL_PLANE_BOOTSTRAP_TOKEN}"
  fi
}

get_render_var() {
  local key="$1" status rc
  set +e
  status="$(curl --silent --show-error -o "$WORK_DIR/var-$key.json" -w '%{http_code}' \
    "${AUTH_HEADERS[@]}" "${RENDER_API}/env-vars/${key}")"
  rc=$?
  set -e
  if [ "$rc" -ne 0 ] || [ "$status" = "404" ]; then
    echo "__ABSENT__"
    return 0
  fi
  [ "$status" = "200" ] || fail "Render env GET ${key} returned HTTP ${status}"
  jq -r '(.envVar // .).value // empty' "$WORK_DIR/var-$key.json"
}

fetch_render_env() {
  log "Fetching authoritative runtime inputs from Render (values remain masked)..."
  RENDER_ADMIN_EMAIL="$(get_render_var CONTROL_PLANE_ADMIN_EMAIL)"
  RENDER_ADMIN_PASSWORD="$(get_render_var CONTROL_PLANE_ADMIN_PASSWORD)"
  RENDER_TENANT_ID="$(get_render_var CONTROL_PLANE_TENANT_ID)"
  RENDER_SANAD_TENANT_ID="$(get_render_var SANAD_CONTROL_PLANE_TENANT_ID)"
  RENDER_BOOTSTRAP_ENABLED="$(get_render_var CONTROL_PLANE_BOOTSTRAP_ENABLED)"
  RENDER_DATABASE_URL="$(get_render_var DATABASE_URL)"
  RENDER_DATABASE_PASSWORD="$(get_render_var DATABASE_PASSWORD)"
  RENDER_DATABASE_USERNAME="$(get_render_var DATABASE_USERNAME)"
  for value in "$RENDER_ADMIN_EMAIL" "$RENDER_ADMIN_PASSWORD" "$RENDER_TENANT_ID" \
    "$RENDER_SANAD_TENANT_ID" "$RENDER_DATABASE_URL" "$RENDER_DATABASE_PASSWORD" "$RENDER_DATABASE_USERNAME"; do
    [ -n "$value" ] && [ "$value" != "__ABSENT__" ] && echo "::add-mask::${value}"
  done
}

prepare_db_connection() {
  [ -n "$RENDER_DATABASE_URL" ] && [ "$RENDER_DATABASE_URL" != "__ABSENT__" ] \
    || fail "DATABASE_URL is unavailable from the authoritative Render service"
  [ -n "$RENDER_DATABASE_PASSWORD" ] && [ "$RENDER_DATABASE_PASSWORD" != "__ABSENT__" ] \
    || fail "DATABASE_PASSWORD is unavailable from the authoritative Render service"
  DB_USER="$DATABASE_USERNAME"
  if [ -n "$RENDER_DATABASE_USERNAME" ] && [ "$RENDER_DATABASE_USERNAME" != "__ABSENT__" ]; then DB_USER="$RENDER_DATABASE_USERNAME"; fi
  [ -n "$DB_USER" ] || fail "Database username is unavailable"
  echo "::add-mask::${DB_USER}"
  local raw_url host_port db_part
  raw_url="${RENDER_DATABASE_URL#jdbc:}"
  raw_url="${raw_url#postgresql://}"
  raw_url="${raw_url#postgres://}"
  host_port="${raw_url%%/*}"
  db_part="${raw_url#*/}"
  DB_NAME="${db_part%%\?*}"
  DB_HOST="${host_port%%:*}"
  if [[ "$host_port" == *:* ]]; then DB_PORT="${host_port##*:}"; else DB_PORT="5432"; fi
  [ -n "$DB_HOST" ] && [ -n "$DB_NAME" ] || fail "DATABASE_URL could not be parsed"
  echo "::add-mask::${DB_HOST}"
  echo "::add-mask::${DB_NAME}"
}

psql_ro() {
  PGOPTIONS='-c default_transaction_read_only=on' PGPASSWORD="$RENDER_DATABASE_PASSWORD" \
  psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
    --no-psqlrc --set=ON_ERROR_STOP=1 --tuples-only --no-align "$@"
}

resolve_authoritative_control_plane_tenant() {
  prepare_db_connection
  local candidate_file candidate_count
  candidate_file="$WORK_DIR/control-plane-candidates.txt"
  psql_ro --command="
    SELECT DISTINCT u.tenant_id::text
    FROM users u
    JOIN tenants t ON t.id=u.tenant_id
    WHERE u.status='ACTIVE'
      AND u.platform_admin=TRUE
      AND t.status='ACTIVE'
      AND EXISTS (
        SELECT 1 FROM user_role_assignments ura
        JOIN roles r ON r.id=ura.role_id AND r.tenant_id=ura.tenant_id
        WHERE ura.user_id=u.id AND ura.tenant_id=u.tenant_id
          AND ura.status='ACTIVE' AND r.code='ADMIN' AND r.status='ACTIVE'
      )
    ORDER BY 1;
  " > "$candidate_file" || fail "Read-only canonical tenant query failed"
  sed -i '/^[[:space:]]*$/d' "$candidate_file"
  candidate_count="$(wc -l < "$candidate_file" | tr -d ' ')"
  [ "$candidate_count" = "1" ] || fail "Canonical control-plane tenant must resolve uniquely; candidate_count=${candidate_count}"
  CONTROL_PLANE_TENANT_ID="$(head -n 1 "$candidate_file")"
  [[ "$CONTROL_PLANE_TENANT_ID" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$ ]] \
    || fail "Resolved canonical tenant is not a UUID"
  echo "::add-mask::${CONTROL_PLANE_TENANT_ID}"
  rm -f "$candidate_file"
  pass "canonical control-plane tenant resolved uniquely and validated (masked)"
}

deploy_state() {
  log "Current live deploy state:"
  curl --fail-with-body --silent --show-error "${AUTH_HEADERS[@]}" "${RENDER_API}/deploys?limit=2" \
    | jq -r '.[] | (.deploy // .) | "  deploy=\(.id) status=\(.status) image=\(.image.ref // "n/a") updated=\(.updatedAt)"'
}

trigger_deploy() {
  local status deploy_id rc
  set +e
  status="$(curl --silent --show-error -o "$WORK_DIR/trigger.json" -w '%{http_code}' \
    -X POST "${AUTH_HEADERS[@]}" -H "Content-Type: application/json" \
    --data '{"clearCache":"do_not_clear"}' "${RENDER_API}/deploys")"
  rc=$?
  set -e
  [ "$rc" -eq 0 ] || fail "Render deploy trigger curl failed"
  deploy_id="$(jq -r '(.deploy // .).id // empty' "$WORK_DIR/trigger.json")"
  [ "$status" = "201" ] || fail "Render deploy trigger returned HTTP ${status}"
  [ -n "$deploy_id" ] || fail "Render deploy trigger returned no deploy id"
  log "Deploy trigger accepted."
  printf '%s' "$deploy_id"
}

wait_for_live() {
  local deploy_id="$1" timeout_s="${2:-1200}" waited=0 status
  sleep 10
  log "Waiting for Render deployment to become live..."
  while [ "$waited" -lt "$timeout_s" ]; do
    status="$(curl --fail-with-body --silent --show-error "${AUTH_HEADERS[@]}" "${RENDER_API}/deploys/${deploy_id}" \
      | jq -r '(.deploy // .).status // "unknown"')"
    case "$status" in
      live) pass "Render deployment is live"; return 0 ;;
      build_failed|update_failed|canceled|deactivated) fail "Render deployment entered terminal state ${status}" ;;
    esac
    sleep 15; waited=$((waited + 15))
  done
  fail "Timed out waiting for Render deployment"
}

wait_for_health() {
  local timeout_s="${1:-420}" waited=0 status
  while [ "$waited" -lt "$timeout_s" ]; do
    status="$(curl --silent --show-error --max-time 15 "${PRODUCTION_BASE_URL}/actuator/health" 2>/dev/null \
      | jq -r '.status // "unreachable"' || echo unreachable)"
    if [ "$status" = "UP" ]; then pass "backend health is UP"; return 0; fi
    sleep 10; waited=$((waited + 10))
  done
  fail "Backend health did not reach UP"
}

verify_login() {
  local label="$1" payload status token rc safe_body
  payload="$(jq -n --arg address "$CONTROL_PLANE_ADMIN_EMAIL" --arg pass "$CONTROL_PLANE_ADMIN_PASSWORD" \
    --arg tenant "$CONTROL_PLANE_TENANT_ID" '{email:$address,password:$pass,tenantId:$tenant}')"
  set +e
  status="$(curl --silent --show-error --location --max-time 60 -o "$WORK_DIR/login-check.json" -w '%{http_code}' \
    -X POST "${PRODUCTION_BASE_URL}/api/v1/auth/login" -H 'Content-Type: application/json' --data "$payload")"
  rc=$?
  set -e
  [ "$rc" -eq 0 ] || fail "login request failed (${label})"
  if [ "$status" != "200" ]; then
    safe_body="unparseable"
    if jq -e . "$WORK_DIR/login-check.json" >/dev/null 2>&1; then safe_body="$(jq -c '{status,error,message,path}' "$WORK_DIR/login-check.json")"; fi
    fail "login returned HTTP ${status} (${label}); body=${safe_body}"
  fi
  token="$(jq -r '.accessToken // empty' "$WORK_DIR/login-check.json")"
  [ -n "$token" ] || fail "login 200 returned no access token (${label})"
  echo "::add-mask::${token}"
  pass "login verified (${label})"
}

set_render_var() {
  local key="$1" value="$2" type="${3:-raw}" payload status rc
  if [ "$type" = "secret" ]; then
    payload="$(python3 -c 'import json,sys; print(json.dumps({"value":sys.argv[1],"type":"SECRET"}))' "$value")"
  else
    payload="$(python3 -c 'import json,sys; print(json.dumps({"value":sys.argv[1]}))' "$value")"
  fi
  set +e
  status="$(curl --silent --show-error -o "$WORK_DIR/setvar.json" -w '%{http_code}' -X PUT \
    "${AUTH_HEADERS[@]}" -H 'Content-Type: application/json' --data "$payload" "${RENDER_API}/env-vars/${key}")"
  rc=$?
  set -e
  [ "$rc" -eq 0 ] || fail "Render env PUT ${key} failed"
  case "$status" in 200|201) ;; *) fail "Render env PUT ${key} returned HTTP ${status}" ;; esac
  log "Render env ${key}: updated"
}

diagnose() {
  mask_initial_secrets
  fetch_render_env
  resolve_authoritative_control_plane_tenant
  log "=== Sanitized divergence checks ==="
  if [ "$RENDER_ADMIN_EMAIL" != "__ABSENT__" ] && [ "$RENDER_ADMIN_EMAIL" = "$CONTROL_PLANE_ADMIN_EMAIL" ]; then pass "admin email matches Render env"; else log "DIVERGENCE: admin email missing or different in Render env"; fi
  if [ "$RENDER_ADMIN_PASSWORD" != "__ABSENT__" ] && [ "$RENDER_ADMIN_PASSWORD" = "$CONTROL_PLANE_ADMIN_PASSWORD" ]; then pass "admin password matches Render env"; else log "DIVERGENCE: admin password missing or different in Render env"; fi
  if [ "$RENDER_SANAD_TENANT_ID" != "__ABSENT__" ] && [ "$RENDER_SANAD_TENANT_ID" = "$CONTROL_PLANE_TENANT_ID" ]; then pass "Render canonical tenant matches production-derived tenant"; else log "DIVERGENCE: Render canonical tenant is missing or stale"; fi
  log "Bootstrap enabled state: ${RENDER_BOOTSTRAP_ENABLED:-<unset>}"
  deploy_state
  log "Diagnose complete. No database or environment mutations were performed."
}

reconcile() {
  mask_initial_secrets
  fetch_render_env
  resolve_authoritative_control_plane_tenant
  : "${CONTROL_PLANE_BOOTSTRAP_TOKEN:?CONTROL_PLANE_BOOTSTRAP_TOKEN is required for reconcile}"
  : "${PRODUCTION_BASE_URL:?PRODUCTION_BASE_URL is required for reconcile}"
  [ "${#CONTROL_PLANE_ADMIN_PASSWORD}" -ge 12 ] || fail "CONTROL_PLANE_ADMIN_PASSWORD shorter than 12 chars"
  [[ "$PRODUCTION_BASE_URL" == https://* ]] || fail "PRODUCTION_BASE_URL must be https"

  log "=== Step 1/6: sync validated smoke identity to Render ==="
  set_render_var "CONTROL_PLANE_ADMIN_EMAIL" "$CONTROL_PLANE_ADMIN_EMAIL" secret
  set_render_var "CONTROL_PLANE_ADMIN_PASSWORD" "$CONTROL_PLANE_ADMIN_PASSWORD" secret
  set_render_var "CONTROL_PLANE_TENANT_ID" "$CONTROL_PLANE_TENANT_ID" secret
  set_render_var "SANAD_CONTROL_PLANE_TENANT_ID" "$CONTROL_PLANE_TENANT_ID" secret
  set_render_var "CONTROL_PLANE_BOOTSTRAP_TOKEN" "$CONTROL_PLANE_BOOTSTRAP_TOKEN" secret
  set_render_var "CONTROL_PLANE_BOOTSTRAP_ENABLED" "true"

  log "=== Step 2/6: deploy current live image with reconciled env ==="
  local deploy_id bootstrap_status bootstrap_result rc
  deploy_id="$(trigger_deploy)"
  wait_for_live "$deploy_id" 1200
  wait_for_health 420

  log "=== Step 3/6: invoke canonical bootstrap ==="
  set +e
  bootstrap_status="$(curl --silent --show-error --max-time 60 -o "$WORK_DIR/bootstrap.json" -w '%{http_code}' \
    -X POST "${PRODUCTION_BASE_URL}/api/v1/internal/control-plane/bootstrap-admin" -H 'Content-Type: application/json' \
    -H "X-Control-Plane-Bootstrap-Token: ${CONTROL_PLANE_BOOTSTRAP_TOKEN}" --data '{}')"
  rc=$?
  set -e
  [ "$rc" -eq 0 ] || fail "bootstrap request failed"
  bootstrap_result="$(jq -r 'if .status=="ok" and .bootstrap=="complete" then "complete" else "not_complete" end' "$WORK_DIR/bootstrap.json" 2>/dev/null || echo invalid)"
  [ "$bootstrap_status" = "200" ] && [ "$bootstrap_result" = "complete" ] \
    || fail "bootstrap returned HTTP ${bootstrap_status}; sanitized=$(jq -c '{status,code,message}' "$WORK_DIR/bootstrap.json" 2>/dev/null || echo unparseable)"
  pass "bootstrap complete"

  log "=== Step 4/6: verify post-bootstrap login ==="
  verify_login post-bootstrap

  log "=== Step 5/6: disable bootstrap and redeploy ==="
  set_render_var "CONTROL_PLANE_BOOTSTRAP_ENABLED" "false"
  deploy_id="$(trigger_deploy)"
  wait_for_live "$deploy_id" 1200
  wait_for_health 420

  log "=== Step 6/6: verify steady-state login ==="
  verify_login final
  deploy_state
  pass "reconcile complete; smoke identity is production-coherent"
}

case "$MODE" in
  diagnose) diagnose ;;
  reconcile) reconcile ;;
  *) fail "usage: scp-smoke-identity-reconcile.sh [diagnose|reconcile]" ;;
esac
