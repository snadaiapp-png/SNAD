#!/usr/bin/env bash
set -Eeuo pipefail

: "${VERCEL_BASE_URL:?VERCEL_BASE_URL is required}"
: "${WORKFLOW_RUNTIME_ADMIN_EMAIL:?WORKFLOW_RUNTIME_ADMIN_EMAIL is required}"
: "${WORKFLOW_RUNTIME_ADMIN_PASSWORD:?WORKFLOW_RUNTIME_ADMIN_PASSWORD is required}"
: "${WORKFLOW_RUNTIME_TENANT_ID:?WORKFLOW_RUNTIME_TENANT_ID is required}"

BASE_URL="${VERCEL_BASE_URL%/}"
EVIDENCE_FILE="${WORKFLOW_ENTITLEMENT_REMEDIATION_EVIDENCE_FILE:-workflow-production-entitlement-remediation.json}"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

for secret in "$WORKFLOW_RUNTIME_ADMIN_EMAIL" "$WORKFLOW_RUNTIME_ADMIN_PASSWORD" "$WORKFLOW_RUNTIME_TENANT_ID"; do
  echo "::add-mask::$secret"
done

fail() {
  local stage="$1" message="$2"
  echo "::error::$message"
  jq -n \
    --arg schema "snad.workflow.production-entitlement-remediation.v1" \
    --arg result "FAIL" \
    --arg failureStage "$stage" \
    --arg baseUrl "$BASE_URL" \
    '{schema:$schema,result:$result,failureStage:$failureStage,transport:"vercel-bff",baseUrl:$baseUrl,mutationPerformed:false}' \
    > "$EVIDENCE_FILE" || true
  exit 1
}

request() {
  local method="$1" url="$2" output="$3"
  shift 3
  curl --silent --show-error --location \
    --request "$method" \
    --connect-timeout 15 \
    --max-time 140 \
    --output "$output" \
    --write-out '%{http_code}' \
    "$@" \
    "$url"
}

expect_200() {
  local status="$1" stage="$2"
  [ "$status" = "200" ] || fail "$stage" "$stage returned HTTP ${status:-000}; expected 200"
}

case "$BASE_URL" in
  https://*) ;;
  http://127.0.0.1:*|http://localhost:*)
    [ "${WORKFLOW_ENTITLEMENT_ALLOW_HTTP:-false}" = "true" ] || fail "input-validation" "HTTP is allowed only for explicit local tests"
    ;;
  *) fail "input-validation" "VERCEL_BASE_URL must use HTTPS" ;;
esac
[[ "$WORKFLOW_RUNTIME_TENANT_ID" =~ ^[0-9a-fA-F-]{36}$ ]] || fail "input-validation" "WORKFLOW_RUNTIME_TENANT_ID must be a UUID"

login_payload="$(jq -cn \
  --arg email "$WORKFLOW_RUNTIME_ADMIN_EMAIL" \
  --arg password "$WORKFLOW_RUNTIME_ADMIN_PASSWORD" \
  --arg tenantId "$WORKFLOW_RUNTIME_TENANT_ID" \
  '{email:$email,password:$password,tenantId:$tenantId}')"
status="$(request POST "$BASE_URL/api/platform/api/v1/auth/login" "$WORK_DIR/login.json" \
  --header 'Accept: application/json' --header 'Content-Type: application/json' --data "$login_payload")"
expect_200 "$status" "login"
TOKEN="$(jq -r '.accessToken // empty' "$WORK_DIR/login.json")"
[ -n "$TOKEN" ] || fail "login" "Vercel BFF login returned no access token"
echo "::add-mask::$TOKEN"
AUTH_HEADER="Authorization: Bearer $TOKEN"

status="$(request GET "$BASE_URL/api/platform/api/v1/auth/me" "$WORK_DIR/me.json" \
  --header 'Accept: application/json' --header "$AUTH_HEADER")"
expect_200 "$status" "auth-me"
jq -e --arg tenant "$WORKFLOW_RUNTIME_TENANT_ID" '
  .tenantId == $tenant
  and (.capabilities | index("EXECUTIVE_VIEW") != null)
  and (.capabilities | index("EXECUTIVE_MANAGE") != null)
  and (.capabilities | index("WORKFLOW.VIEW") != null)
' "$WORK_DIR/me.json" >/dev/null || fail "authorization" "Production admin identity lacks exact tenant binding or required control-plane capabilities"

status="$(request GET "$BASE_URL/api/platform/api/v1/executive/modules" "$WORK_DIR/modules.json" \
  --header 'Accept: application/json' --header "$AUTH_HEADER")"
expect_200 "$status" "module-registry"
WORKFLOW_MODULE_ID="$(jq -r '[.[] | select(.code == "WORKFLOW" and .enabled == true)][0].id // empty' "$WORK_DIR/modules.json")"
[[ "$WORKFLOW_MODULE_ID" =~ ^[0-9a-fA-F-]{36}$ ]] || fail "module-registry" "Enabled WORKFLOW module is absent from the production registry"

status="$(request GET "$BASE_URL/api/platform/api/v1/executive/subscriptions/v2?tenantId=$WORKFLOW_RUNTIME_TENANT_ID&status=ACTIVE&page=0&size=2" "$WORK_DIR/subscriptions.json" \
  --header 'Accept: application/json' --header "$AUTH_HEADER")"
expect_200 "$status" "active-subscription"
jq -e '.content | type == "array"' "$WORK_DIR/subscriptions.json" >/dev/null || fail "active-subscription" "Subscription response is not paginated content"
ACTIVE_COUNT="$(jq -r '.totalElements // (.content | length)' "$WORK_DIR/subscriptions.json")"
[ "$ACTIVE_COUNT" = "1" ] || fail "active-subscription" "Expected exactly one ACTIVE subscription for the control-plane tenant"
PLAN_ID="$(jq -r '.content[0].planId // empty' "$WORK_DIR/subscriptions.json")"
[[ "$PLAN_ID" =~ ^[0-9a-fA-F-]{36}$ ]] || fail "active-subscription" "ACTIVE subscription has no valid planId"

status="$(request GET "$BASE_URL/api/platform/api/v1/executive/tenants/$WORKFLOW_RUNTIME_TENANT_ID/entitlements" "$WORK_DIR/before-entitlements.json" \
  --header 'Accept: application/json' --header "$AUTH_HEADER")"
expect_200 "$status" "entitlement-read-before"
CURRENT_ENABLED="$(jq -r '[.[] | select(.moduleCode == "WORKFLOW")][0].moduleEnabled // false' "$WORK_DIR/before-entitlements.json")"
MUTATION_PERFORMED=false

if [ "$CURRENT_ENABLED" != "true" ]; then
  status="$(request GET "$BASE_URL/api/platform/api/v1/executive/plans/$PLAN_ID/modules" "$WORK_DIR/plan-modules.json" \
    --header 'Accept: application/json' --header "$AUTH_HEADER")"
  expect_200 "$status" "plan-entitlement-read"

  existing="$(jq -c '[.[] | select(.moduleCode == "WORKFLOW")][0] // empty' "$WORK_DIR/plan-modules.json")"
  if [ -n "$existing" ]; then
    payload="$(jq -cn \
      --arg moduleId "$WORKFLOW_MODULE_ID" \
      --argjson existing "$existing" \
      '{moduleId:$moduleId,moduleEnabled:true,capabilityCode:$existing.capabilityCode,capabilityValue:$existing.capabilityValue,limitValue:$existing.limitValue,quotaValue:$existing.quotaValue,quotaPeriod:$existing.quotaPeriod}')"
  else
    payload="$(jq -cn --arg moduleId "$WORKFLOW_MODULE_ID" \
      '{moduleId:$moduleId,moduleEnabled:true,capabilityCode:null,capabilityValue:null,limitValue:null,quotaValue:null,quotaPeriod:null}')"
  fi

  status="$(request PUT "$BASE_URL/api/platform/api/v1/executive/plans/$PLAN_ID/modules/WORKFLOW" "$WORK_DIR/mutation.json" \
    --header 'Accept: application/json' --header 'Content-Type: application/json' --header "$AUTH_HEADER" --data "$payload")"
  expect_200 "$status" "workflow-entitlement-mutation"
  jq -e --arg moduleId "$WORKFLOW_MODULE_ID" '.moduleId == $moduleId and .moduleCode == "WORKFLOW" and .moduleEnabled == true' "$WORK_DIR/mutation.json" >/dev/null \
    || fail "workflow-entitlement-mutation" "Mutation response did not confirm enabled WORKFLOW entitlement"
  MUTATION_PERFORMED=true
fi

status="$(request GET "$BASE_URL/api/platform/api/v1/executive/tenants/$WORKFLOW_RUNTIME_TENANT_ID/entitlements" "$WORK_DIR/after-entitlements.json" \
  --header 'Accept: application/json' --header "$AUTH_HEADER")"
expect_200 "$status" "entitlement-read-after"
jq -e '[.[] | select(.moduleCode == "WORKFLOW" and .moduleEnabled == true)] | length == 1' "$WORK_DIR/after-entitlements.json" >/dev/null \
  || fail "entitlement-read-after" "WORKFLOW entitlement is still not effective after remediation"

status="$(request GET "$BASE_URL/api/platform/api/v1/workflows/catalog/modules" "$WORK_DIR/catalog.json" \
  --header 'Accept: application/json' --header "$AUTH_HEADER")"
expect_200 "$status" "workflow-module-catalog"
jq -e 'type == "object"' "$WORK_DIR/catalog.json" >/dev/null || fail "workflow-module-catalog" "Workflow module catalog contract is invalid after remediation"

jq -n \
  --arg schema "snad.workflow.production-entitlement-remediation.v1" \
  --arg result "PASS" \
  --arg baseUrl "$BASE_URL" \
  --arg planId "$PLAN_ID" \
  --argjson mutationPerformed "$MUTATION_PERFORMED" \
  '{schema:$schema,result:$result,failureStage:null,transport:"vercel-bff",baseUrl:$baseUrl,planId:$planId,moduleCode:"WORKFLOW",moduleEnabled:true,mutationPerformed:$mutationPerformed}' \
  > "$EVIDENCE_FILE"

echo "Workflow production entitlement remediation: PASSED (mutationPerformed=$MUTATION_PERFORMED)"
