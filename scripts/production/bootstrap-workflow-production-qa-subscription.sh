#!/usr/bin/env bash
set -Eeuo pipefail

: "${PRODUCTION_BASE_URL:?required}"
: "${PROD_ADMIN_EMAIL:?required}"
: "${PROD_ADMIN_PASSWORD:?required}"
: "${PROD_QA_ADMIN_EMAIL:?required}"
: "${PROD_QA_ADMIN_PASSWORD:?required}"
: "${PROD_QA_TENANT_CODE:?required}"
: "${PROD_QA_TENANT_NAME:?required}"
: "${EXPECTED_MAIN_SHA:?required}"
: "${RENDER_API_KEY:?required}"
: "${RENDER_SERVICE_ID:?required}"
: "${IMAGE_REF:?required}"

BASE_URL="${PRODUCTION_BASE_URL%/}"
RENDER_API="https://api.render.com/v1/services/${RENDER_SERVICE_ID}"
AUTH_HEADERS=(-H "Authorization: Bearer ${RENDER_API_KEY}" -H "Accept: application/json")
WORK_DIR="$(mktemp -d)"
EVIDENCE="${BOOTSTRAP_EVIDENCE_FILE:-workflow-production-qa-subscription-bootstrap.json}"
BOOTSTRAP_ARMED=false
CLEANUP_RUNNING=false

for secret in "$PROD_ADMIN_EMAIL" "$PROD_ADMIN_PASSWORD" "$PROD_QA_ADMIN_EMAIL" "$PROD_QA_ADMIN_PASSWORD"; do
  echo "::add-mask::$secret"
done

log() { echo "[qa-reconcile] $*" >&2; }
pass() { echo "[qa-reconcile] PASS: $*" >&2; }

write_failure_evidence() {
  local stage="$1" detail="$2"
  jq -n --arg schema 'snad.workflow.production-qa-reconcile.v3' \
    --arg result FAIL --arg failureStage "$stage" --arg mainSha "$EXPECTED_MAIN_SHA" --arg detail "$detail" \
    '{schema:$schema,result:$result,failureStage:$failureStage,mainSha:$mainSha,detail:$detail,directDatabaseWrites:"NONE",catalogMutations:"NONE",planMutations:"NONE"}' > "$EVIDENCE"
}

fail() {
  local stage="$1" detail="$2"
  echo "::error::$detail" >&2
  write_failure_evidence "$stage" "$detail"
  exit 1
}

set_render_var() {
  local key="$1" value="$2" type="${3:-raw}" payload status rc
  if [ "$type" = secret ]; then
    payload="$(python3 -c 'import json,sys; print(json.dumps({"value":sys.argv[1],"type":"SECRET"}))' "$value")"
  else
    payload="$(python3 -c 'import json,sys; print(json.dumps({"value":sys.argv[1]}))' "$value")"
  fi
  set +e
  status="$(curl --silent --show-error -o "$WORK_DIR/setvar.json" -w '%{http_code}' -X PUT \
    "${AUTH_HEADERS[@]}" -H 'Content-Type: application/json' --data "$payload" "${RENDER_API}/env-vars/${key}")"
  rc=$?
  set -e
  [ "$rc" -eq 0 ] || return 1
  case "$status" in 200|201) return 0 ;; *) return 1 ;; esac
}

trigger_deploy() {
  local payload status rc deploy_id
  payload="$(jq -n --arg image "$IMAGE_REF" '{imageUrl:$image,clearCache:"do_not_clear"}')"
  set +e
  status="$(curl --silent --show-error -o "$WORK_DIR/trigger.json" -w '%{http_code}' \
    -X POST "${AUTH_HEADERS[@]}" -H 'Content-Type: application/json' \
    --data "$payload" "${RENDER_API}/deploys")"
  rc=$?
  set -e
  [ "$rc" -eq 0 ] || return 1
  [ "$status" = 201 ] || return 1
  deploy_id="$(jq -r '(.deploy // .).id // empty' "$WORK_DIR/trigger.json")"
  [ -n "$deploy_id" ] || return 1
  printf '%s' "$deploy_id"
}

wait_for_live() {
  local deploy_id="$1" waited=0 status
  sleep 10
  while [ "$waited" -lt 1200 ]; do
    status="$(curl --fail-with-body --silent --show-error "${AUTH_HEADERS[@]}" "${RENDER_API}/deploys/${deploy_id}" \
      | jq -r '(.deploy // .).status // "unknown"')" || return 1
    case "$status" in
      live) return 0 ;;
      build_failed|update_failed|canceled|deactivated) return 1 ;;
    esac
    sleep 15
    waited=$((waited+15))
  done
  return 1
}

resolve_backend_url() {
  curl --fail-with-body --silent --show-error "${AUTH_HEADERS[@]}" "${RENDER_API}" > "$WORK_DIR/service.json"
  BACKEND_BASE_URL="$(jq -r '(.service // .).serviceDetails.url // (.service // .).url // empty' "$WORK_DIR/service.json")"
  [ -n "$BACKEND_BASE_URL" ] || fail backendUrl 'Render backend URL unavailable'
  BACKEND_BASE_URL="${BACKEND_BASE_URL%/}"
}

wait_for_health() {
  local waited=0 state
  while [ "$waited" -lt 420 ]; do
    state="$(curl --silent --show-error --max-time 15 "${BACKEND_BASE_URL}/actuator/health" 2>/dev/null | jq -r '.status // "unreachable"' || echo unreachable)"
    [ "$state" = UP ] && return 0
    sleep 10
    waited=$((waited+10))
  done
  return 1
}

emergency_disable() {
  [ "$BOOTSTRAP_ARMED" = true ] || return 0
  [ "$CLEANUP_RUNNING" = false ] || return 0
  CLEANUP_RUNNING=true
  log "Emergency cleanup: disabling startup bootstrap."
  set_render_var SANAD_SECURITY_BOOTSTRAP_ENABLED false || true
  set_render_var SANAD_SECURITY_BOOTSTRAP_FORCE_RESET false || true
  set_render_var SANAD_SECURITY_BOOTSTRAP_ADMIN_PASSWORD "" secret || true
  local cleanup_deploy=""
  cleanup_deploy="$(trigger_deploy 2>/dev/null || true)"
  if [ -n "$cleanup_deploy" ]; then
    wait_for_live "$cleanup_deploy" || true
    wait_for_health || true
  fi
  BOOTSTRAP_ARMED=false
}

cleanup() {
  local rc=$?
  emergency_disable || true
  rm -rf "$WORK_DIR"
  exit "$rc"
}
trap cleanup EXIT

request() {
  local method="$1" path="$2" out="$3" auth="${4:-none}" payload="${5:-}"
  local -a args=(--silent --show-error --location --connect-timeout 12 --max-time 120 --request "$method" --output "$out" --write-out '%{http_code}' --header 'Accept: application/json')
  case "$auth" in
    none) ;;
    cp)
      [[ "$path" == /api/platform/api/v1/executive/* ]] || fail tokenBoundary "Control Plane token attempted non-Executive API: $path"
      [ -n "${CP_TOKEN:-}" ] || fail tokenBoundary 'Control Plane token unavailable'
      args+=(--header "Authorization: Bearer $CP_TOKEN")
      ;;
    qa)
      [[ "$path" != /api/platform/api/v1/executive/* ]] || fail tokenBoundary "Tenant B token attempted Executive API: $path"
      [ -n "${QA_TOKEN:-}" ] || fail tokenBoundary 'Tenant B token unavailable'
      args+=(--header "Authorization: Bearer $QA_TOKEN")
      ;;
    *) fail tokenBoundary "Unknown auth boundary: $auth" ;;
  esac
  [ -n "$payload" ] && args+=(--header 'Content-Type: application/json' --data "$payload")
  local status rc
  set +e
  status="$(curl "${args[@]}" "$BASE_URL$path")"
  rc=$?
  set -e
  [ "$rc" -eq 0 ] && printf '%s' "$status" || printf '000'
}

expect() {
  [ "$1" = "$2" ] || fail "$3" "$3 returned HTTP $1; expected $2"
}

get_render_var() {
  local key="$1" status
  status="$(curl --silent --show-error -o "$WORK_DIR/var-${key}.json" -w '%{http_code}' "${AUTH_HEADERS[@]}" "${RENDER_API}/env-vars/${key}" || true)"
  [ "$status" = 200 ] || { printf ''; return 0; }
  jq -r '(.envVar // .).value // empty' "$WORK_DIR/var-${key}.json"
}

resolve_backend_url
[[ "$IMAGE_REF" =~ ^ghcr\.io/snadaiapp-png/snad-backend:[0-9a-f]{40}$ ]] || fail immutableImage 'IMAGE_REF is not an immutable SNAD backend image'

PROD_TENANT_ID="$(get_render_var SANAD_CONTROL_PLANE_TENANT_ID)"
[[ "$PROD_TENANT_ID" =~ ^[0-9a-fA-F-]{36}$ ]] || fail controlPlaneTenant 'SANAD_CONTROL_PLANE_TENANT_ID missing or invalid'
echo "::add-mask::$PROD_TENANT_ID"
export PROD_TENANT_ID

BOOTSTRAP_TEMP_CREDENTIAL="$(python3 - <<'PY'
import hashlib, os
seed=(os.environ["PROD_QA_ADMIN_PASSWORD"]+os.environ["EXPECTED_MAIN_SHA"]).encode()
print("Tmp!" + hashlib.sha256(seed).hexdigest()[:28])
PY
)"
echo "::add-mask::$BOOTSTRAP_TEMP_CREDENTIAL"

log "Arming application-native Tenant B startup bootstrap."
set_render_var SANAD_SECURITY_BOOTSTRAP_FORCE_RESET true || fail renderEnv 'Failed to set bootstrap force-reset'
set_render_var SANAD_SECURITY_BOOTSTRAP_CREDENTIAL_ONLY false || fail renderEnv 'Failed to set bootstrap credential-only'
set_render_var SANAD_SECURITY_BOOTSTRAP_TENANT_ID "" || fail renderEnv 'Failed to clear bootstrap tenant-id'
set_render_var SANAD_SECURITY_BOOTSTRAP_TENANT_NAME "$PROD_QA_TENANT_NAME" || fail renderEnv 'Failed to set bootstrap tenant name'
set_render_var SANAD_SECURITY_BOOTSTRAP_TENANT_SUBDOMAIN "$PROD_QA_TENANT_CODE" || fail renderEnv 'Failed to set bootstrap tenant code'
set_render_var SANAD_SECURITY_BOOTSTRAP_ADMIN_EMAIL "$PROD_QA_ADMIN_EMAIL" secret || fail renderEnv 'Failed to set bootstrap admin email'
set_render_var SANAD_SECURITY_BOOTSTRAP_ADMIN_PASSWORD "$BOOTSTRAP_TEMP_CREDENTIAL" secret || fail renderEnv 'Failed to set bootstrap temporary password'
set_render_var SANAD_SECURITY_BOOTSTRAP_ADMIN_DISPLAY_NAME "Workflow Production QA Admin" || fail renderEnv 'Failed to set bootstrap display name'
set_render_var SANAD_SECURITY_BOOTSTRAP_AUDIT_ACTOR "workflow-production-qa-reconcile" || fail renderEnv 'Failed to set bootstrap audit actor'
set_render_var SANAD_SECURITY_BOOTSTRAP_ENABLED true || fail renderEnv 'Failed to enable bootstrap'
BOOTSTRAP_ARMED=true

deploy_id="$(trigger_deploy)" || fail bootstrapDeploy 'Failed to trigger bootstrap deploy'
wait_for_live "$deploy_id" || fail bootstrapDeploy 'Bootstrap deploy did not become live'
wait_for_health || fail bootstrapDeploy 'Backend health did not recover after bootstrap deploy'
pass 'application-native Tenant B bootstrap deploy is live'

cp_login_payload="$(jq -cn --arg email "$PROD_ADMIN_EMAIL" --arg password "$PROD_ADMIN_PASSWORD" --arg tenantId "$PROD_TENANT_ID" '{email:$email,password:$password,tenantId:$tenantId}')"
status="$(request POST '/api/platform/api/v1/auth/login' "$WORK_DIR/login-cp.json" none "$cp_login_payload")"; expect "$status" 200 controlPlaneLogin
CP_TOKEN="$(jq -r '.accessToken // empty' "$WORK_DIR/login-cp.json")"
[ -n "$CP_TOKEN" ] || fail controlPlaneLogin 'No Control Plane access token'
echo "::add-mask::$CP_TOKEN"

status="$(request GET "/api/platform/api/v1/executive/tenants/v2?search=$PROD_QA_TENANT_CODE&status=ACTIVE&page=0&size=100&sort=code&direction=ASC" "$WORK_DIR/qa-tenant.json" cp)"; expect "$status" 200 qaTenantDirectory
QA_TENANT_COUNT="$(jq --arg code "$PROD_QA_TENANT_CODE" '[.content[]? | select(.code==$code and .status=="ACTIVE")]|length' "$WORK_DIR/qa-tenant.json")"
[ "$QA_TENANT_COUNT" = 1 ] || fail qaTenantResolution "Expected exactly one ACTIVE Tenant B code $PROD_QA_TENANT_CODE; found $QA_TENANT_COUNT"
PROD_QA_TENANT_ID="$(jq -r --arg code "$PROD_QA_TENANT_CODE" '[.content[]? | select(.code==$code and .status=="ACTIVE")][0].id // empty' "$WORK_DIR/qa-tenant.json")"
[[ "$PROD_QA_TENANT_ID" =~ ^[0-9a-fA-F-]{36}$ ]] || fail qaTenantResolution 'Resolved Tenant B id is invalid'
echo "::add-mask::$PROD_QA_TENANT_ID"
export PROD_QA_TENANT_ID
[ "$PROD_QA_TENANT_ID" != "$PROD_TENANT_ID" ] || fail qaTenantIsolation 'Tenant B resolved to Control Plane tenant'
pass 'Tenant B resolved dynamically from Executive directory'

qa_temp_login_payload="$(jq -cn --arg email "$PROD_QA_ADMIN_EMAIL" --arg password "$BOOTSTRAP_TEMP_CREDENTIAL" --arg tenantId "$PROD_QA_TENANT_ID" '{email:$email,password:$password,tenantId:$tenantId}')"
status="$(request POST '/api/platform/api/v1/auth/login' "$WORK_DIR/login-qa-temp.json" none "$qa_temp_login_payload")"; expect "$status" 200 qaBootstrapLogin
QA_TOKEN="$(jq -r '.accessToken // empty' "$WORK_DIR/login-qa-temp.json")"
[ -n "$QA_TOKEN" ] || fail qaBootstrapLogin 'No Tenant B bootstrap token'
echo "::add-mask::$QA_TOKEN"
jq -e '.credentialRotationRequired==true' "$WORK_DIR/login-qa-temp.json" >/dev/null || fail qaBootstrapLogin 'Tenant B bootstrap did not arm credential rotation'

change_payload="$(jq -cn --arg current "$BOOTSTRAP_TEMP_CREDENTIAL" --arg next "$PROD_QA_ADMIN_PASSWORD" '{currentCredential:$current,newCredential:$next}')"
status="$(request POST '/api/platform/api/v1/auth/change-credential' "$WORK_DIR/change-credential.json" qa "$change_payload")"; expect "$status" 204 qaCredentialRotation
unset QA_TOKEN
pass 'Tenant B credential rotated from one-time bootstrap credential'

log "Disabling startup bootstrap before steady-state deploy."
set_render_var SANAD_SECURITY_BOOTSTRAP_ENABLED false || fail renderEnvDisable 'Failed to disable bootstrap'
set_render_var SANAD_SECURITY_BOOTSTRAP_FORCE_RESET false || fail renderEnvDisable 'Failed to disable force-reset'
set_render_var SANAD_SECURITY_BOOTSTRAP_ADMIN_PASSWORD "" secret || fail renderEnvDisable 'Failed to clear bootstrap password'
BOOTSTRAP_ARMED=false

deploy_id="$(trigger_deploy)" || fail steadyStateDeploy 'Failed to trigger steady-state deploy'
wait_for_live "$deploy_id" || fail steadyStateDeploy 'Steady-state deploy did not become live'
wait_for_health || fail steadyStateDeploy 'Backend health did not recover after steady-state deploy'
pass 'bootstrap disabled and steady-state backend is live'

qa_login_payload="$(jq -cn --arg email "$PROD_QA_ADMIN_EMAIL" --arg password "$PROD_QA_ADMIN_PASSWORD" --arg tenantId "$PROD_QA_TENANT_ID" '{email:$email,password:$password,tenantId:$tenantId}')"
status="$(request POST '/api/platform/api/v1/auth/login' "$WORK_DIR/login-qa-final.json" none "$qa_login_payload")"; expect "$status" 200 qaSteadyStateLogin
jq -e '.credentialRotationRequired==false' "$WORK_DIR/login-qa-final.json" >/dev/null || fail qaSteadyStateLogin 'Tenant B remains blocked by credential rotation'
QA_TOKEN="$(jq -r '.accessToken // empty' "$WORK_DIR/login-qa-final.json")"
[ -n "$QA_TOKEN" ] || fail qaSteadyStateLogin 'No Tenant B steady-state token'
echo "::add-mask::$QA_TOKEN"
status="$(request GET '/api/platform/api/v1/auth/me' "$WORK_DIR/me-qa.json" qa)"; expect "$status" 200 qaAuthMe
jq -e --arg tenant "$PROD_QA_TENANT_ID" '.tenantId==$tenant and .status=="ACTIVE"' "$WORK_DIR/me-qa.json" >/dev/null || fail qaAuthMe 'Tenant B identity binding/status mismatch'
for cap in USER.CREATE USER.READ USER.WRITE WORKFLOW.WRITE WORKFLOW.DESIGN WORKFLOW.VALIDATE WORKFLOW.PUBLISH; do
  jq -e --arg c "$cap" '.capabilities | index($c) != null' "$WORK_DIR/me-qa.json" >/dev/null || fail qaCapabilityGuard "Missing Tenant B capability $cap"
done
pass 'Tenant B steady-state login, membership and capabilities verified'

status="$(request POST '/api/platform/api/v1/auth/login' "$WORK_DIR/login-cp-final.json" none "$cp_login_payload")"; expect "$status" 200 controlPlaneRelogin
CP_TOKEN="$(jq -r '.accessToken // empty' "$WORK_DIR/login-cp-final.json")"
[ -n "$CP_TOKEN" ] || fail controlPlaneRelogin 'No Control Plane token after steady-state deploy'
echo "::add-mask::$CP_TOKEN"

status="$(request GET '/api/platform/api/v1/executive/plans' "$WORK_DIR/plans.json" cp)"; expect "$status" 200 plansRead
STARTER_COUNT="$(jq '[.[]|select(.code=="STARTER" and .status=="ACTIVE" and (.trialDays // 0)>0 and (.maxUsers // 0)>=4 and ([.entitlements[]? | select(.featureCode=="WORKFLOW" and .enabled==true)]|length)>=1)]|length' "$WORK_DIR/plans.json")"
[ "$STARTER_COUNT" = 1 ] || fail starterPlanGuard "Expected exactly one eligible existing STARTER plan; found $STARTER_COUNT"
PLAN_ID="$(jq -r '[.[]|select(.code=="STARTER" and .status=="ACTIVE")][0].id' "$WORK_DIR/plans.json")"
TRIAL_DAYS="$(jq -r '[.[]|select(.code=="STARTER" and .status=="ACTIVE")][0].trialDays' "$WORK_DIR/plans.json")"
MAX_USERS="$(jq -r '[.[]|select(.code=="STARTER" and .status=="ACTIVE")][0].maxUsers' "$WORK_DIR/plans.json")"
[[ "$PLAN_ID" =~ ^[0-9a-fA-F-]{36}$ ]] || fail starterPlanGuard 'Invalid STARTER plan id'

read_all_subscriptions() {
  local target="$1"
  : > "$WORK_DIR/subscriptions.jsonl"
  local page=0
  while :; do
    local code pages
    code="$(request GET "/api/platform/api/v1/executive/subscriptions/v2?page=$page&size=100" "$WORK_DIR/page.json" cp)"
    expect "$code" 200 "subscriptionsPage${page}"
    jq -c '.content[]?' "$WORK_DIR/page.json" >> "$WORK_DIR/subscriptions.jsonl"
    pages="$(jq -r '.totalPages // 0' "$WORK_DIR/page.json")"
    [[ "$pages" =~ ^[0-9]+$ ]] || fail subscriptionRead 'Invalid subscription pagination metadata'
    [ "$page" -ge $((pages-1)) ] && break
    page=$((page+1))
    [ "$page" -lt 100 ] || fail subscriptionRead 'Subscription pagination safety limit exceeded'
  done
  jq -s '.' "$WORK_DIR/subscriptions.jsonl" > "$target"
}

read_all_subscriptions "$WORK_DIR/before.json"
QA_ROWS="$(jq --arg tenant "$PROD_QA_TENANT_ID" '[.[]|select(.tenantId==$tenant)]|length' "$WORK_DIR/before.json")"
case "$QA_ROWS" in
  0)
    payload="$(jq -cn --arg tenantId "$PROD_QA_TENANT_ID" --arg planId "$PLAN_ID" --argjson seats "$MAX_USERS" --argjson trialDays "$TRIAL_DAYS" '{tenantId:$tenantId,planId:$planId,billingCycle:"MONTHLY",seatQuantity:$seats,trialDays:$trialDays}')"
    status="$(request POST '/api/platform/api/v1/executive/subscriptions' "$WORK_DIR/create.json" cp "$payload")"; expect "$status" 200 qaSubscriptionCreate
    SUB_ID="$(jq -r '.id // empty' "$WORK_DIR/create.json")"
    CURRENT_STATUS="$(jq -r '.status // empty' "$WORK_DIR/create.json")"
    case "$CURRENT_STATUS" in ACTIVE|TRIALING) ;; *) fail qaSubscriptionCreate "Unexpected created subscription status $CURRENT_STATUS" ;; esac
    ;;
  1)
    SUB_ID="$(jq -r --arg tenant "$PROD_QA_TENANT_ID" '[.[]|select(.tenantId==$tenant)][0].id' "$WORK_DIR/before.json")"
    CURRENT_STATUS="$(jq -r --arg tenant "$PROD_QA_TENANT_ID" '[.[]|select(.tenantId==$tenant)][0].status' "$WORK_DIR/before.json")"
    EXISTING_PLAN="$(jq -r --arg tenant "$PROD_QA_TENANT_ID" '[.[]|select(.tenantId==$tenant)][0].planId' "$WORK_DIR/before.json")"
    [ "$EXISTING_PLAN" = "$PLAN_ID" ] || fail qaSubscriptionGuard 'Existing Tenant B subscription is not on STARTER'
    case "$CURRENT_STATUS" in ACTIVE|TRIALING) ;; *) fail qaSubscriptionGuard "Existing Tenant B subscription status $CURRENT_STATUS is not safely continuable" ;; esac
    ;;
  *)
    fail qaSubscriptionGuard "Expected at most one Tenant B subscription row; found $QA_ROWS"
    ;;
esac
[[ "$SUB_ID" =~ ^[0-9a-fA-F-]{36}$ ]] || fail qaSubscriptionGuard 'Invalid Tenant B subscription id'

if [ "$CURRENT_STATUS" != ACTIVE ]; then
  status="$(request POST "/api/platform/api/v1/executive/subscriptions/$SUB_ID/provision" "$WORK_DIR/provision.json" cp '{}')"; expect "$status" 200 qaSubscriptionProvision
  jq -e '.status=="SUCCEEDED"' "$WORK_DIR/provision.json" >/dev/null || fail qaSubscriptionProvision 'Provisioning job did not SUCCEED'
fi

read_all_subscriptions "$WORK_DIR/after.json"
QA_ACTIVE_COUNT="$(jq --arg tenant "$PROD_QA_TENANT_ID" --arg sid "$SUB_ID" --arg plan "$PLAN_ID" '[.[]|select(.tenantId==$tenant and .status=="ACTIVE" and .id==$sid and .planId==$plan)]|length' "$WORK_DIR/after.json")"
[ "$QA_ACTIVE_COUNT" = 1 ] || fail qaSubscriptionGuard 'Tenant B subscription did not converge to exactly one ACTIVE STARTER subscription'

status="$(request POST "/api/platform/api/v1/executive/tenants/$PROD_QA_TENANT_ID/entitlements/recalculate" "$WORK_DIR/recalc.json" cp '{}')"; expect "$status" 200 recalculateEntitlements
status="$(request GET "/api/platform/api/v1/executive/tenants/$PROD_QA_TENANT_ID/entitlements" "$WORK_DIR/entitlements.json" cp)"; expect "$status" 200 entitlementsRead
jq -e '[.[]|select(.moduleCode=="WORKFLOW" and .moduleEnabled==true)]|length==1' "$WORK_DIR/entitlements.json" >/dev/null || fail entitlementsRead 'WORKFLOW entitlement is not active after recalculation'

if [ -n "${GITHUB_ENV:-}" ]; then
  echo "PROD_QA_TENANT_ID=$PROD_QA_TENANT_ID" >> "$GITHUB_ENV"
fi

jq -n --arg schema 'snad.workflow.production-qa-reconcile.v3' \
  --arg result PASS --arg mainSha "$EXPECTED_MAIN_SHA" --arg tenantCode "$PROD_QA_TENANT_CODE" \
  --arg subscriptionStatus ACTIVE --arg planCode STARTER \
  '{schema:$schema,result:$result,failureStage:null,mainSha:$mainSha,tenantCode:$tenantCode,identityBootstrap:"APPLICATION_NATIVE",credentialRotation:"COMPLETE",bootstrapEnabledFinal:false,subscriptionStatus:$subscriptionStatus,planCode:$planCode,directDatabaseWrites:"NONE",catalogMutations:"NONE",planMutations:"NONE",paidInvoicePath:"NOT_USED"}' > "$EVIDENCE"

pass 'Tenant B identity + STARTER subscription reconcile complete'
echo 'WORKFLOW_QA_RECONCILE=PASS'
