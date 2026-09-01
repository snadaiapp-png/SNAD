#!/usr/bin/env bash
# =============================================================================
# SCP Smoke Identity Diagnose / Reconcile
# =============================================================================
# Purpose:
#   The SCP production contract smoke gate authenticates with the Control
#   Plane smoke identity (CONTROL_PLANE_ADMIN_* GitHub secrets). This tool:
#
#   diagnose  (read-only)  — proves WHERE the smoke identity diverges:
#       a) GitHub secrets vs authoritative Render service env (equality
#          booleans only; secret values are masked and never printed)
#       b) Production DB user-row state for both identities
#       c) Current live Render image reference and deploy status
#
#   reconcile (mutating)   — surgically re-provisions the smoke identity
#       through the CANONICAL, token-gated bootstrap endpoint
#       (POST /api/v1/internal/control-plane/bootstrap-admin), which reads
#       credentials from server-side env only, is idempotent, and rotates
#       the password hash / re-activates the user, membership, and role
#       grants atomically (CredentialBootstrapService forceReset=true).
#       No database mutation happens from this script. No JWT secret or
#       unrelated env var is touched (unlike the legacy emergency workflows).
#
# Modes:  ./scp-smoke-identity-reconcile.sh diagnose
#         ./scp-smoke-identity-reconcile.sh reconcile
# =============================================================================
set -euo pipefail

MODE="${1:-diagnose}"

: "${RENDER_API_KEY:?RENDER_API_KEY is required}"
: "${RENDER_SERVICE_ID:?RENDER_SERVICE_ID is required}"
: "${DATABASE_USERNAME:?DATABASE_USERNAME is required}"
: "${CONTROL_PLANE_TENANT_ID:?CONTROL_PLANE_TENANT_ID is required}"
: "${CONTROL_PLANE_ADMIN_EMAIL:?CONTROL_PLANE_ADMIN_EMAIL is required}"
: "${CONTROL_PLANE_ADMIN_PASSWORD:?CONTROL_PLANE_ADMIN_PASSWORD is required}"

RENDER_API="https://api.render.com/v1/services/${RENDER_SERVICE_ID}"
AUTH_HEADERS=(-H "Authorization: Bearer ${RENDER_API_KEY}" -H "Accept: application/json")
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

log()  { echo "[identity] $*"; }
pass() { echo "[identity] PASS: $*"; }
fail() { echo "::error::[identity] $*"; exit 1; }

# Mask every secret-bearing value so GitHub Actions redacts them anywhere.
mask_all() {
  echo "::add-mask::${CONTROL_PLANE_ADMIN_EMAIL}"
  echo "::add-mask::${CONTROL_PLANE_ADMIN_PASSWORD}"
  echo "::add-mask::${CONTROL_PLANE_TENANT_ID}"
}

# -----------------------------------------------------------------------------
# Fetch authoritative env vars from the Render service.
# Per-key GET is definitive (immune to list pagination); the one list call
# provides the KEY-NAME inventory (names are not secret) for the evidence log.
# -----------------------------------------------------------------------------
get_render_var() {
  local key="$1" status rc
  set +e
  status="$(curl --silent --show-error -o "$WORK_DIR/var-$key.json" -w '%{http_code}' \
    "${AUTH_HEADERS[@]}" "${RENDER_API}/env-vars/${key}")"
  rc=$?
  set -e
  if [ $rc -ne 0 ] || [ "$status" = "404" ]; then
    echo "__ABSENT__"
    return 0
  fi
  if [ "$status" != "200" ]; then
    fail "Render env GET ${key} returned HTTP ${status}"
  fi
  jq -r '(.envVar // .).value // empty' "$WORK_DIR/var-$key.json"
}

fetch_render_env() {
  log "Fetching authoritative env vars from Render service env (per-key, definitive)..."
  RENDER_ADMIN_EMAIL="$(get_render_var CONTROL_PLANE_ADMIN_EMAIL)"
  RENDER_ADMIN_PASSWORD="$(get_render_var CONTROL_PLANE_ADMIN_PASSWORD)"
  RENDER_TENANT_ID="$(get_render_var CONTROL_PLANE_TENANT_ID)"
  RENDER_SANAD_TENANT_ID="$(get_render_var SANAD_CONTROL_PLANE_TENANT_ID)"
  RENDER_BOOTSTRAP_ENABLED="$(get_render_var CONTROL_PLANE_BOOTSTRAP_ENABLED)"
  RENDER_DATABASE_URL="$(get_render_var DATABASE_URL)"
  RENDER_DATABASE_PASSWORD="$(get_render_var DATABASE_PASSWORD)"

  for v in "$RENDER_ADMIN_EMAIL" "$RENDER_ADMIN_PASSWORD" "$RENDER_DATABASE_PASSWORD"; do
    [ -n "$v" ] && [ "$v" != "__ABSENT__" ] && echo "::add-mask::${v}"
  done

  # Key-name inventory (names only, never values) — proves list completeness
  # and documents which vars the production service actually carries.
  curl --fail-with-body --silent --show-error "${AUTH_HEADERS[@]}" \
    "${RENDER_API}/env-vars?limit=100" > "$WORK_DIR/render-env.json" || true
  ENV_KEY_COUNT="$(jq -r '[.[]? | (.envVar // .)] | length' "$WORK_DIR/render-env.json" 2>/dev/null || echo 0)"
  log "Render env var inventory (count=${ENV_KEY_COUNT}, names only):"
  jq -r '[.[]? | (.envVar // .)] | .[] | .key' "$WORK_DIR/render-env.json" 2>/dev/null \
    | sort | sed 's/^/  - /' || log "  (inventory unavailable)"
}

# -----------------------------------------------------------------------------
# Read-only DB state checks (psql, SELECT only).
# -----------------------------------------------------------------------------
db_identity_state() {
  local label="$1" email="$2" tenant="$3"

  [ -n "$email" ] || { log "(${label}) no email to check"; return 0; }

  local raw_url host port db_name esc_email esc_tenant
  raw_url="${RENDER_DATABASE_URL}"
  raw_url="${raw_url#jdbc:}"
  raw_url="${raw_url#postgresql://}"
  local host_port="${raw_url%%/*}"
  local db_part="${raw_url#*/}"
  db_name="${db_part%%\?*}"
  host="${host_port%%:*}"
  port="${host_port#*:}"; port="${port:-5432}"

  esc_email="${email//\'/\'\'}"
  esc_tenant="${tenant//\'/\'\'}"

  psqlq() {
    PGPASSWORD="$RENDER_DATABASE_PASSWORD" psql \
      -h "$host" -p "$port" -U "$DATABASE_USERNAME" -d "$db_name" \
      --no-psqlrc --set=ON_ERROR_STOP=1 --tuples-only --no-align "$@"
  }

  log "DB state for (${label}) identity — email masked, tenant auto-masked:"
  psqlq --command="SELECT COALESCE((SELECT status FROM tenants WHERE id = '${esc_tenant}'), 'TENANT_MISSING');" \
    | sed 's/^/  tenant_status=/' || true
  psqlq --command="SELECT CASE WHEN COUNT(*) = 0 THEN 'USER_ROW_MISSING' ELSE 'USER_ROW_PRESENT' END FROM users WHERE email = lower('${esc_email}') AND tenant_id = '${esc_tenant}';" \
    | sed 's/^/  cp_user=/' || true
  psqlq --command="SELECT COALESCE((SELECT status FROM users WHERE email = lower('${esc_email}') AND tenant_id = '${esc_tenant}'), 'n/a');" \
    | sed 's/^/  cp_user_status=/' || true
  psqlq --command="SELECT COALESCE((SELECT 'has_pwd=' || (password_hash IS NOT NULL) || ', must_change=' || must_change_password FROM users WHERE email = lower('${esc_email}') AND tenant_id = '${esc_tenant}'), 'n/a');" \
    | sed 's/^/  cp_user_creds=/' || true
  psqlq --command="SELECT COALESCE(CAST(last_login_at AS text), 'never') FROM users WHERE email = lower('${esc_email}') AND tenant_id = '${esc_tenant}';" \
    | sed 's/^/  cp_last_login=/' || true
  psqlq --command="SELECT COUNT(DISTINCT tenant_id) FROM users WHERE email = lower('${esc_email}');" \
    | sed 's/^/  email_tenant_count=/' || true
}

# -----------------------------------------------------------------------------
# Live deploy state.
# -----------------------------------------------------------------------------
deploy_state() {
  log "Current live deploy state:"
  curl --fail-with-body --silent --show-error "${AUTH_HEADERS[@]}" \
    "${RENDER_API}/deploys?limit=2" | jq -r '.[] | (.deploy // .) | "  deploy=\(.id) status=\(.status) image=\(.image.ref // "n/a") updated=\(.updatedAt)"'
}

trigger_deploy() {
  local status deploy_id rc
  set +e
  status="$(curl --silent --show-error -o "$WORK_DIR/trigger.json" -w '%{http_code}' \
    -X POST "${AUTH_HEADERS[@]}" -H "Content-Type: application/json" \
    --data '{"clearCache":"do_not_clear"}' "${RENDER_API}/deploys")"
  rc=$?
  set -e
  [ $rc -eq 0 ] || fail "Render deploy trigger curl failed (rc=${rc})"
  deploy_id="$(jq -r '.id // empty' "$WORK_DIR/trigger.json" 2>/dev/null || echo empty)"
  log "Deploy trigger HTTP ${status} (deploy ${deploy_id})"
  if [ "$status" != "201" ]; then
    fail "Render deploy trigger returned HTTP ${status}; body: $(head -c 300 "$WORK_DIR/trigger.json" 2>/dev/null || echo n/a)"
  fi
  echo "$deploy_id"
}

# -----------------------------------------------------------------------------
# diagnose — read-only divergence proof. Collects ALL evidence (never stops at
# the first divergence) and prints a final verdict summary.
# -----------------------------------------------------------------------------
diagnose() {
  mask_all
  fetch_render_env

  local present_email=1 present_password=1 present_tenant=1 present_db=1
  [ -n "$RENDER_ADMIN_EMAIL" ] && [ "$RENDER_ADMIN_EMAIL" != "__ABSENT__" ] && present_email=0
  [ -n "$RENDER_ADMIN_PASSWORD" ] && [ "$RENDER_ADMIN_PASSWORD" != "__ABSENT__" ] && present_password=0
  [ -n "$RENDER_TENANT_ID" ] && [ "$RENDER_TENANT_ID" != "__ABSENT__" ] && present_tenant=0
  [ -n "$RENDER_DATABASE_URL" ] && [ "$RENDER_DATABASE_URL" != "__ABSENT__" ] \
    && [ -n "$RENDER_DATABASE_PASSWORD" ] && [ "$RENDER_DATABASE_PASSWORD" != "__ABSENT__" ] && present_db=0

  log "=== GitHub secrets vs Render service env (equality booleans only) ==="
  if [ "$present_email" -eq 0 ]; then
    [ "$CONTROL_PLANE_ADMIN_EMAIL" = "$RENDER_ADMIN_EMAIL" ] \
      && pass "admin email matches Render env" \
      || log "DIVERGENCE: admin email differs between GitHub secret and Render env"
  else
    log "DIVERGENCE: CONTROL_PLANE_ADMIN_EMAIL is ABSENT from the Render service env (bootstrap would refuse: admin email not configured)"
  fi
  if [ "$present_password" -eq 0 ]; then
    [ "$CONTROL_PLANE_ADMIN_PASSWORD" = "$RENDER_ADMIN_PASSWORD" ] \
      && pass "admin password matches Render env" \
      || log "DIVERGENCE: admin password differs between GitHub secret and Render env"
  else
    log "DIVERGENCE: CONTROL_PLANE_ADMIN_PASSWORD is ABSENT from the Render service env"
  fi
  if [ "$present_tenant" -eq 0 ]; then
    [ "$CONTROL_PLANE_TENANT_ID" = "$RENDER_TENANT_ID" ] \
      && pass "tenant id matches Render CONTROL_PLANE_TENANT_ID" \
      || log "DIVERGENCE: tenant id differs between GitHub secret and Render CONTROL_PLANE_TENANT_ID"
  else
    log "NOTE: CONTROL_PLANE_TENANT_ID not present in Render env (bootstrap uses SANAD_CONTROL_PLANE_TENANT_ID)"
  fi
  if [ -n "$RENDER_SANAD_TENANT_ID" ] && [ "$RENDER_SANAD_TENANT_ID" != "__ABSENT__" ]; then
    [ "$CONTROL_PLANE_TENANT_ID" = "$RENDER_SANAD_TENANT_ID" ] \
      && pass "tenant id matches Render SANAD_CONTROL_PLANE_TENANT_ID" \
      || log "DIVERGENCE: tenant id differs from Render SANAD_CONTROL_PLANE_TENANT_ID"
  else
    log "NOTE: SANAD_CONTROL_PLANE_TENANT_ID not present in Render env (bootstrap would refuse: control-plane tenant not configured)"
  fi
  log "Render CONTROL_PLANE_BOOTSTRAP_ENABLED='${RENDER_BOOTSTRAP_ENABLED:-<unset>}'"

  log "=== Production DB identity state (read-only) ==="
  if [ "$present_db" -eq 1 ]; then
    log "WARN: DATABASE_URL/DATABASE_PASSWORD unavailable from Render env; skipping DB checks"
  else
    db_identity_state "github-secret" "$CONTROL_PLANE_ADMIN_EMAIL" "$CONTROL_PLANE_TENANT_ID"
    if [ "$present_email" -eq 0 ] && [ "$RENDER_ADMIN_EMAIL" != "$CONTROL_PLANE_ADMIN_EMAIL" ]; then
      db_identity_state "render-env" "$RENDER_ADMIN_EMAIL" "${RENDER_TENANT_ID:-$CONTROL_PLANE_TENANT_ID}"
    fi
  fi

  deploy_state

  log "=== DIAGNOSE VERDICT (read-only evidence; reconcile is the corrective action) ==="
  log "GitHub-secret email present in Render env: $([ $present_email -eq 0 ] && echo YES || echo NO)"
  log "GitHub-secret password present in Render env: $([ $present_password -eq 0 ] && echo YES || echo NO)"
  log "DB checks executed: $([ $present_db -eq 0 ] && echo YES || echo NO)"
  log "Diagnose complete (read-only)."
}

# -----------------------------------------------------------------------------
# reconcile — surgical, confirm-gated identity re-provisioning.
# -----------------------------------------------------------------------------
set_render_var() {
  local key="$1" value="$2" type="${3:-raw}" payload status rc
  if [ "$type" = "secret" ]; then
    payload="$(python3 -c 'import json,sys; print(json.dumps({"value": sys.argv[1], "type": "SECRET"}))' "$value")"
  else
    payload="$(python3 -c 'import json,sys; print(json.dumps({"value": sys.argv[1]}))' "$value")"
  fi
  set +e
  status="$(curl --silent --show-error -o "$WORK_DIR/setvar.json" -w '%{http_code}' \
    -X PUT "${AUTH_HEADERS[@]}" -H "Content-Type: application/json" \
    --data "$payload" "${RENDER_API}/env-vars/${key}")"
  rc=$?
  set -e
  [ $rc -eq 0 ] || fail "Render env PUT ${key} curl failed (rc=${rc})"
  log "  Render env ${key}: HTTP ${status}"
  if [ "$status" != "200" ] && [ "$status" != "201" ]; then
    fail "Render env PUT ${key} returned HTTP ${status}; body: $(head -c 300 "$WORK_DIR/setvar.json" 2>/dev/null || echo n/a)"
  fi
}

wait_for_live() {
  local deploy_id="${1:-}" timeout_s="${2:-1200}" waited=0 status
  sleep 10
  if [ -z "$deploy_id" ]; then
    deploy_id="$(curl --fail-with-body --silent --show-error "${AUTH_HEADERS[@]}" \
      "${RENDER_API}/deploys?limit=1" | jq -r '.[0].deploy.id // .[0].id // empty')"
  fi
  log "Waiting for deploy ${deploy_id:-<none>} to go live (timeout ${timeout_s}s)..."
  while [ "$waited" -lt "$timeout_s" ]; do
    if [ -n "$deploy_id" ]; then
      status="$(curl --fail-with-body --silent --show-error "${AUTH_HEADERS[@]}" \
        "${RENDER_API}/deploys/${deploy_id}" | jq -r '.status // .deploy.status // "unknown"')"
    else
      status="unknown"
    fi
    case "$status" in
      live) log "Deploy ${deploy_id} is LIVE."; return 0 ;;
      *failed*|*canceled*|*deactivated*) fail "Deploy ${deploy_id} entered terminal state: ${status}" ;;
    esac
    sleep 15; waited=$((waited + 15))
  done
  fail "Timed out waiting for deploy to go live"
}

wait_for_health() {
  local timeout_s="${1:-300}" waited=0 status
  log "Waiting for /actuator/health UP (timeout ${timeout_s}s)..."
  while [ "$waited" -lt "$timeout_s" ]; do
    status="$(curl --silent --show-error --max-time 15 "${PRODUCTION_BASE_URL}/actuator/health" 2>/dev/null | jq -r '.status // "unreachable"' || echo "unreachable")"
    if [ "$status" = "UP" ]; then log "Backend health UP."; return 0; fi
    sleep 10; waited=$((waited + 10))
  done
  fail "Backend health did not reach UP in ${timeout_s}s"
}

verify_login() {
  local label="$1" status token rc payload
  payload="$(jq -n \
    --arg address "$CONTROL_PLANE_ADMIN_EMAIL" \
    --arg pass "$CONTROL_PLANE_ADMIN_PASSWORD" \
    --arg tenant "$CONTROL_PLANE_TENANT_ID" \
    '{email:$address,password:$pass,tenantId:$tenant}')"
  set +e
  status="$(curl --silent --show-error --location --max-time 60 \
    -o "$WORK_DIR/login-check.json" -w '%{http_code}' \
    -X POST "${PRODUCTION_BASE_URL}/api/v1/auth/login" \
    -H "Content-Type: application/json" --data "$payload")"
  rc=$?
  set -e
  [ $rc -eq 0 ] || fail "login request failed (rc=${rc}) (${label})"
  if [ "$status" = "200" ]; then
    token="$(jq -r '.accessToken // empty' "$WORK_DIR/login-check.json")"
    [ -n "$token" ] && echo "::add-mask::${token}"
    [ -n "$token" ] && pass "login verified (${label}) — access token issued" \
      || fail "login 200 but no access token (${label})"
  else
    local body="unparseable"
    jq -c '{status, error, message, path}' "$WORK_DIR/login-check.json" 2>/dev/null > "$WORK_DIR/safe-body.json" \
      && body="$(cat "$WORK_DIR/safe-body.json")" || true
    fail "login returned HTTP ${status} (${label}); body: ${body}"
  fi
}

reconcile() {
  mask_all
  fetch_render_env

  log "=== Pre-flight validation for reconcile ==="
  [ ${#CONTROL_PLANE_ADMIN_PASSWORD} -ge 12 ] || fail "CONTROL_PLANE_ADMIN_PASSWORD shorter than 12 chars; rotate the GitHub secret first."
  [[ "$CONTROL_PLANE_TENANT_ID" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$ ]] \
    || fail "CONTROL_PLANE_TENANT_ID is not a valid UUID."
  : "${CONTROL_PLANE_BOOTSTRAP_TOKEN:?CONTROL_PLANE_BOOTSTRAP_TOKEN is required for reconcile}"
  : "${PRODUCTION_BASE_URL:?PRODUCTION_BASE_URL is required for reconcile}"
  echo "::add-mask::${CONTROL_PLANE_BOOTSTRAP_TOKEN}"
  [[ "$PRODUCTION_BASE_URL" == https://* ]] || fail "PRODUCTION_BASE_URL must be https"

  log "=== Step 1/6: sync smoke identity env vars (GitHub secrets -> Render env) ==="
  set_render_var "CONTROL_PLANE_ADMIN_EMAIL" "$CONTROL_PLANE_ADMIN_EMAIL" "secret"
  set_render_var "CONTROL_PLANE_ADMIN_PASSWORD" "$CONTROL_PLANE_ADMIN_PASSWORD" "secret"
  set_render_var "CONTROL_PLANE_TENANT_ID" "$CONTROL_PLANE_TENANT_ID" "secret"
  set_render_var "SANAD_CONTROL_PLANE_TENANT_ID" "$CONTROL_PLANE_TENANT_ID" "secret"
  set_render_var "CONTROL_PLANE_BOOTSTRAP_ENABLED" "true"

  log "=== Step 2/6: deploy current live image with updated env ==="
  DEPLOY_ID="$(trigger_deploy)"
  wait_for_live "$DEPLOY_ID" 1200
  wait_for_health 420

  log "=== Step 3/6: call canonical bootstrap endpoint (idempotent forceReset) ==="
  local bs_status bs_result rc
  set +e
  bs_status="$(curl --silent --show-error --max-time 60 \
    -o "$WORK_DIR/bootstrap.json" -w '%{http_code}' \
    -X POST "${PRODUCTION_BASE_URL}/api/v1/internal/control-plane/bootstrap-admin" \
    -H "Content-Type: application/json" \
    -H "X-Control-Plane-Bootstrap-Token: ${CONTROL_PLANE_BOOTSTRAP_TOKEN}" \
    --data '{}')"
  rc=$?
  set -e
  [ $rc -eq 0 ] || fail "bootstrap endpoint curl failed (rc=${rc})"
  bs_result="$(jq -r 'if .status == "ok" and .bootstrap == "complete" then "complete" else "not_complete" end' "$WORK_DIR/bootstrap.json" 2>/dev/null || echo invalid)"
  [ "$bs_status" = "200" ] && [ "$bs_result" = "complete" ] \
    || fail "bootstrap endpoint returned HTTP ${bs_status} (expected 200/complete); sanitized body: $(jq -c '{status, code, message}' "$WORK_DIR/bootstrap.json" 2>/dev/null || echo unparseable)"
  pass "bootstrap complete — user (re)provisioned ACTIVE with rotated password hash"

  log "=== Step 4/6: verify smoke identity login ==="
  verify_login "post-bootstrap"

  log "=== Step 5/6: disable bootstrap mode ==="
  set_render_var "CONTROL_PLANE_BOOTSTRAP_ENABLED" "false"
  DEPLOY_ID="$(trigger_deploy)"
  wait_for_live "$DEPLOY_ID" 1200
  wait_for_health 420

  log "=== Step 6/6: final login verification on steady-state deployment ==="
  verify_login "final"

  deploy_state
  log "Reconcile complete — smoke identity aligned with GitHub secrets; ready to re-run the production release."
}

# -----------------------------------------------------------------------------

case "$MODE" in
  diagnose)  diagnose ;;
  reconcile) reconcile ;;
  *) fail "usage: scp-smoke-identity-reconcile.sh [diagnose|reconcile]" ;;
esac
