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
  jq -n --arg schema 'snad.workflow.production-qa-subscription-bootstrap.v1'     --arg result FAIL --arg failureStage "$stage" --arg mainSha "$EXPECTED_MAIN_SHA"     --arg detail "$detail"     '{schema:$schema,result:$result,failureStage:$failureStage,mainSha:$mainSha,detail:$detail}' > "$EVIDENCE"
  exit 1
}

request() {
  local method="$1" path="$2" out="$3" auth="${4:-none}" payload="${5:-}"
  local -a args=(--silent --show-error --location --connect-timeout 12 --max-time 120 --request "$method" --output "$out" --write-out '%{http_code}' --header 'Accept: application/json')
  [ "$auth" = auth ] && args+=(--header "Authorization: Bearer $TOKEN")
  [ -n "$payload" ] && args+=(--header 'Content-Type: application/json' --data "$payload")
  local status rc
  set +e; status="$(curl "${args[@]}" "$BASE_URL$path")"; rc=$?; set -e
  [ "$rc" -eq 0 ] && printf '%s' "$status" || printf '000'
}

expect() {
  [ "$1" = "$2" ] || fail "$3" "$3 returned HTTP $1; expected $2"
}

login_payload="$(jq -cn --arg email "$PROD_ADMIN_EMAIL" --arg password "$PROD_ADMIN_PASSWORD" --arg tenantId "$PROD_TENANT_ID" '{email:$email,password:$password,tenantId:$tenantId}')"
status="$(request POST '/api/platform/api/v1/auth/login' "$WORK_DIR/login.json" none "$login_payload")"; expect "$status" 200 controlPlaneLogin
TOKEN="$(jq -r '.accessToken // empty' "$WORK_DIR/login.json")"; [ -n "$TOKEN" ] || fail controlPlaneLogin 'No control-plane access token'; echo "::add-mask::$TOKEN"

status="$(request GET '/api/platform/api/v1/auth/me' "$WORK_DIR/me.json" auth)"; expect "$status" 200 controlPlaneAuthMe
for cap in EXECUTIVE_VIEW EXECUTIVE_MANAGE; do
  jq -e --arg c "$cap" '.capabilities | index($c) != null' "$WORK_DIR/me.json" >/dev/null || fail capabilityGuard "Missing capability $cap"
done

status="$(request GET "/api/platform/api/v1/executive/tenants/$PROD_QA_TENANT_ID" "$WORK_DIR/tenant.json" auth)"; expect "$status" 200 qaTenantRead
jq -e '.status=="ACTIVE"' "$WORK_DIR/tenant.json" >/dev/null || fail qaTenantRead 'Dedicated QA tenant is not ACTIVE'

status="$(request GET '/api/platform/api/v1/executive/plans' "$WORK_DIR/plans.json" auth)"; expect "$status" 200 plansRead
STARTER_COUNT="$(jq '[.[]|select(.code=="STARTER" and .status=="ACTIVE" and (.trialDays // 0)>0 and (.maxUsers // 0)>=4 and ([.entitlements[]? | select(.featureCode=="WORKFLOW" and .enabled==true)]|length)>=1)]|length' "$WORK_DIR/plans.json")"
[ "$STARTER_COUNT" = 1 ] || fail starterPlanGuard "Expected exactly one eligible STARTER plan; found $STARTER_COUNT"
PLAN_ID="$(jq -r '[.[]|select(.code=="STARTER" and .status=="ACTIVE")][0].id' "$WORK_DIR/plans.json")"
TRIAL_DAYS="$(jq -r '[.[]|select(.code=="STARTER" and .status=="ACTIVE")][0].trialDays' "$WORK_DIR/plans.json")"
MAX_USERS="$(jq -r '[.[]|select(.code=="STARTER" and .status=="ACTIVE")][0].maxUsers' "$WORK_DIR/plans.json")"

read_all_subscriptions() {
  local target="$1"
  : > "$WORK_DIR/subscriptions.jsonl"
  local page=0
  while :; do
    local code
    code="$(request GET "/api/platform/api/v1/executive/subscriptions/v2?page=$page&size=100" "$WORK_DIR/page.json" auth)"
    expect "$code" 200 "subscriptionsPage${page}"
    jq -c '.content[]?' "$WORK_DIR/page.json" >> "$WORK_DIR/subscriptions.jsonl"
    local pages
    pages="$(jq -r '.totalPages' "$WORK_DIR/page.json")"
    [ "$page" -ge $((pages-1)) ] && break
    page=$((page+1)); [ "$page" -lt 100 ] || fail subscriptionRead 'Subscription pagination safety limit exceeded'
  done
  jq -s '.' "$WORK_DIR/subscriptions.jsonl" > "$target"
}

read_all_subscriptions "$WORK_DIR/before.json"
QA_COUNT="$(jq --arg tenant "$PROD_QA_TENANT_ID" '[.[]|select(.tenantId==$tenant)]|length' "$WORK_DIR/before.json")"
STARTER_ACTIVE_COUNT="$(jq --arg plan "$PLAN_ID" '[.[]|select(.planId==$plan and .status=="ACTIVE")]|length' "$WORK_DIR/before.json")"
STARTER_ACTIVE_TENANT="$(jq -r --arg plan "$PLAN_ID" '[.[]|select(.planId==$plan and .status=="ACTIVE")][0].tenantId // empty' "$WORK_DIR/before.json")"

case "$QA_COUNT" in
  0)
    [ "$STARTER_ACTIVE_COUNT" = 0 ] || fail planExclusivity 'STARTER already has an ACTIVE subscription; refusing to share the certification plan'
    payload="$(jq -cn --arg tenantId "$PROD_QA_TENANT_ID" --arg planId "$PLAN_ID" --argjson seats "$MAX_USERS" --argjson trialDays "$TRIAL_DAYS" '{tenantId:$tenantId,planId:$planId,billingCycle:"MONTHLY",seatQuantity:$seats,trialDays:$trialDays}')"
    status="$(request POST '/api/platform/api/v1/executive/subscriptions' "$WORK_DIR/create.json" auth "$payload")"; expect "$status" 200 qaSubscriptionCreate
    jq -e '.status=="TRIALING" and .trialEndsAt!=null' "$WORK_DIR/create.json" >/dev/null || fail qaSubscriptionCreate 'Expected TRIALING subscription with non-zero trial'
    SUB_ID="$(jq -r '.id // empty' "$WORK_DIR/create.json")"
    ;;
  1)
    SUB_ID="$(jq -r --arg tenant "$PROD_QA_TENANT_ID" '[.[]|select(.tenantId==$tenant)][0].id' "$WORK_DIR/before.json")"
    EXISTING_STATUS="$(jq -r --arg tenant "$PROD_QA_TENANT_ID" '[.[]|select(.tenantId==$tenant)][0].status' "$WORK_DIR/before.json")"
    EXISTING_PLAN="$(jq -r --arg tenant "$PROD_QA_TENANT_ID" '[.[]|select(.tenantId==$tenant)][0].planId' "$WORK_DIR/before.json")"
    [ "$EXISTING_PLAN" = "$PLAN_ID" ] || fail qaSubscriptionGuard 'Existing QA subscription is not on STARTER'
    case "$EXISTING_STATUS" in
      ACTIVE) ;;
      TRIALING) ;;
      *) fail qaSubscriptionGuard "Existing QA subscription status $EXISTING_STATUS is not safely continuable" ;;
    esac
    ;;
  *)
    fail qaSubscriptionGuard "Expected at most one QA subscription row; found $QA_COUNT"
    ;;
esac

[[ "$SUB_ID" =~ ^[0-9a-fA-F-]{36}$ ]] || fail qaSubscriptionGuard 'Invalid QA subscription id'

CURRENT_STATUS="$(jq -r --arg tenant "$PROD_QA_TENANT_ID" '[.[]|select(.tenantId==$tenant)][0].status // empty' "$WORK_DIR/before.json")"
if [ "$CURRENT_STATUS" != ACTIVE ]; then
  status="$(request POST "/api/platform/api/v1/executive/subscriptions/$SUB_ID/provision" "$WORK_DIR/provision.json" auth '{}')"; expect "$status" 200 qaSubscriptionProvision
  jq -e '.status=="SUCCEEDED"' "$WORK_DIR/provision.json" >/dev/null || fail qaSubscriptionProvision 'Provisioning job did not SUCCEED'
fi

read_all_subscriptions "$WORK_DIR/after.json"
QA_ACTIVE_COUNT="$(jq --arg tenant "$PROD_QA_TENANT_ID" --arg sid "$SUB_ID" --arg plan "$PLAN_ID" '[.[]|select(.tenantId==$tenant and .status=="ACTIVE" and .id==$sid and .planId==$plan)]|length' "$WORK_DIR/after.json")"
STARTER_ACTIVE_COUNT="$(jq --arg plan "$PLAN_ID" '[.[]|select(.planId==$plan and .status=="ACTIVE")]|length' "$WORK_DIR/after.json")"
STARTER_ACTIVE_TENANT="$(jq -r --arg plan "$PLAN_ID" '[.[]|select(.planId==$plan and .status=="ACTIVE")][0].tenantId // empty' "$WORK_DIR/after.json")"
[ "$QA_ACTIVE_COUNT" = 1 ] || fail qaSubscriptionGuard 'QA subscription did not converge to ACTIVE STARTER'
[ "$STARTER_ACTIVE_COUNT" = 1 ] && [ "$STARTER_ACTIVE_TENANT" = "$PROD_QA_TENANT_ID" ] || fail planExclusivity 'STARTER is not exclusive to the QA tenant after bootstrap'

status="$(request POST "/api/platform/api/v1/executive/tenants/$PROD_QA_TENANT_ID/entitlements/recalculate" "$WORK_DIR/recalc.json" auth '{}')"; expect "$status" 200 recalculateEntitlements
status="$(request GET "/api/platform/api/v1/executive/tenants/$PROD_QA_TENANT_ID/entitlements" "$WORK_DIR/entitlements.json" auth)"; expect "$status" 200 entitlementsRead
jq -e '[.[]|select(.moduleCode=="WORKFLOW" and .moduleEnabled==true)]|length==1' "$WORK_DIR/entitlements.json" >/dev/null || fail entitlementsRead 'WORKFLOW entitlement is not active after recalculation'

jq -n --arg schema 'snad.workflow.production-qa-subscription-bootstrap.v1'   --arg result PASS --arg mainSha "$EXPECTED_MAIN_SHA"   --arg subscriptionStatus ACTIVE --arg planCode STARTER   '{schema:$schema,result:$result,failureStage:null,mainSha:$mainSha,subscriptionStatus:$subscriptionStatus,planCode:$planCode,directDatabaseWrites:"NONE",catalogMutations:"NONE",paidInvoicePath:"NOT_USED"}' > "$EVIDENCE"

echo 'Workflow production QA subscription bootstrap: PASSED'
