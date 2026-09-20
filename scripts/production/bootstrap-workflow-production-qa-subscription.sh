#!/usr/bin/env bash
set -Eeuo pipefail

: "${PRODUCTION_BASE_URL:?required}"
: "${PROD_ADMIN_EMAIL:?required}"
: "${PROD_ADMIN_PASSWORD:?required}"
: "${PROD_TENANT_ID:?required}"
: "${PROD_QA_TENANT_ID:?required}"
: "${EXPECTED_MAIN_SHA:?required}"

BASE_URL="${PRODUCTION_BASE_URL%/}"
WORK_DIR="$(mktemp -d)"
EVIDENCE="${BOOTSTRAP_EVIDENCE_FILE:-workflow-production-qa-subscription-bootstrap.json}"
trap 'rm -rf "$WORK_DIR"' EXIT

for secret in "$PROD_ADMIN_EMAIL" "$PROD_ADMIN_PASSWORD" "$PROD_TENANT_ID" "$PROD_QA_TENANT_ID"; do
  echo "::add-mask::$secret"
done

fail() {
  local stage="$1" detail="$2"
  echo "::error::$detail"
  jq -n --arg schema 'snad.workflow.production-qa-subscription-bootstrap.v2' \
    --arg result FAIL --arg failureStage "$stage" --arg mainSha "$EXPECTED_MAIN_SHA" --arg detail "$detail" \
    '{schema:$schema,result:$result,failureStage:$failureStage,mainSha:$mainSha,detail:$detail}' > "$EVIDENCE"
  exit 1
}

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

login_payload="$(jq -cn --arg email "$PROD_ADMIN_EMAIL" --arg password "$PROD_ADMIN_PASSWORD" --arg tenantId "$PROD_TENANT_ID" '{email:$email,password:$password,tenantId:$tenantId}')"
status="$(request POST '/api/platform/api/v1/auth/login' "$WORK_DIR/login.json" none "$login_payload")"; expect "$status" 200 controlPlaneLogin
CP_TOKEN="$(jq -r '.accessToken // empty' "$WORK_DIR/login.json")"
[ -n "$CP_TOKEN" ] || fail controlPlaneLogin 'No Control Plane access token'
echo "::add-mask::$CP_TOKEN"

status="$(request GET '/api/platform/api/v1/executive/access-check/v2' "$WORK_DIR/access.json" cp)"; expect "$status" 200 controlPlaneAccessCheck
jq -e '.authenticated==true' "$WORK_DIR/access.json" >/dev/null || fail controlPlaneAccessCheck 'Control Plane access-check is not authenticated'

status="$(request GET "/api/platform/api/v1/executive/tenants/$PROD_QA_TENANT_ID" "$WORK_DIR/tenant.json" cp)"; expect "$status" 200 qaTenantRead
jq -e '.status=="ACTIVE"' "$WORK_DIR/tenant.json" >/dev/null || fail qaTenantRead 'Tenant B is not ACTIVE'

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
    jq -e '.status=="TRIALING" and .trialEndsAt!=null' "$WORK_DIR/create.json" >/dev/null || fail qaSubscriptionCreate 'Expected TRIALING subscription with non-zero trial'
    SUB_ID="$(jq -r '.id // empty' "$WORK_DIR/create.json")"
    CURRENT_STATUS="TRIALING"
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

jq -n --arg schema 'snad.workflow.production-qa-subscription-bootstrap.v2' \
  --arg result PASS --arg mainSha "$EXPECTED_MAIN_SHA" --arg subscriptionStatus ACTIVE --arg planCode STARTER \
  '{schema:$schema,result:$result,failureStage:null,mainSha:$mainSha,subscriptionStatus:$subscriptionStatus,planCode:$planCode,directDatabaseWrites:"NONE",catalogMutations:"NONE",planMutations:"NONE",paidInvoicePath:"NOT_USED"}' > "$EVIDENCE"

echo 'Workflow production QA subscription bootstrap: PASSED'
