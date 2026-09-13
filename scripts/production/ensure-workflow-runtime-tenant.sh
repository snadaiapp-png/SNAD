#!/usr/bin/env bash
set -euo pipefail

# Idempotent production provisioning for the dedicated Workflow certification
# identity. This script deliberately refuses to resurrect or mutate terminal
# subscription history. It may create exactly one first subscription for a
# history-less synthetic runtime tenant, then proves Workflow entitlement via
# the Vercel BFF using that tenant's own credentials.

: "${WEB_PRODUCTION_BASE_URL:?WEB_PRODUCTION_BASE_URL is required}"
: "${CONTROL_PLANE_ADMIN_EMAIL:?CONTROL_PLANE_ADMIN_EMAIL is required}"
: "${CONTROL_PLANE_ADMIN_PASSWORD:?CONTROL_PLANE_ADMIN_PASSWORD is required}"
: "${CONTROL_PLANE_TENANT_ID:?CONTROL_PLANE_TENANT_ID is required}"
: "${WORKFLOW_RUNTIME_ADMIN_EMAIL:?WORKFLOW_RUNTIME_ADMIN_EMAIL is required}"
: "${WORKFLOW_RUNTIME_ADMIN_PASSWORD:?WORKFLOW_RUNTIME_ADMIN_PASSWORD is required}"
: "${WORKFLOW_RUNTIME_TENANT_ID:?WORKFLOW_RUNTIME_TENANT_ID is required}"

BASE_URL="${WEB_PRODUCTION_BASE_URL%/}"
PLAN_CODE="${WORKFLOW_RUNTIME_PLAN_CODE:-STARTER}"
EVIDENCE_FILE="${WORKFLOW_RUNTIME_PROVISION_EVIDENCE_FILE:-workflow-runtime-tenant-provisioning.json}"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

for value in \
  "$CONTROL_PLANE_ADMIN_EMAIL" \
  "$CONTROL_PLANE_ADMIN_PASSWORD" \
  "$CONTROL_PLANE_TENANT_ID" \
  "$WORKFLOW_RUNTIME_ADMIN_EMAIL" \
  "$WORKFLOW_RUNTIME_ADMIN_PASSWORD" \
  "$WORKFLOW_RUNTIME_TENANT_ID"; do
  echo "::add-mask::$value"
done

uuid_regex='^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
[[ "$CONTROL_PLANE_TENANT_ID" =~ $uuid_regex ]] || {
  echo "::error::CONTROL_PLANE_TENANT_ID is not a valid UUID"
  exit 1
}
[[ "$WORKFLOW_RUNTIME_TENANT_ID" =~ $uuid_regex ]] || {
  echo "::error::WORKFLOW_RUNTIME_TENANT_ID is not a valid UUID"
  exit 1
}
[[ "$PLAN_CODE" =~ ^[A-Z0-9_-]+$ ]] || {
  echo "::error::WORKFLOW_RUNTIME_PLAN_CODE must be a normalized plan code"
  exit 1
}

if [[ "$BASE_URL" != https://* ]]; then
  if [ "${WORKFLOW_PROVISION_ALLOW_LOCALHOST:-false}" != "true" ] || \
     [[ "$BASE_URL" != http://127.0.0.1:* && "$BASE_URL" != http://localhost:* ]]; then
    echo "::error::WEB_PRODUCTION_BASE_URL must use HTTPS"
    exit 1
  fi
fi

request() {
  local method="$1" url="$2" output="$3"
  shift 3
  local status curl_status
  set +e
  status="$(curl --silent --show-error --location \
    --request "$method" \
    --connect-timeout 15 \
    --max-time 60 \
    --output "$output" \
    --write-out '%{http_code}' \
    "$@" \
    "$url")"
  curl_status=$?
  set -e
  if [ "$curl_status" -ne 0 ]; then
    printf '000'
    return 0
  fi
  printf '%s' "$status"
}

expect_json_200() {
  local status="$1" label="$2" output="$3"
  [ "$status" = "200" ] || {
    echo "::error::$label returned HTTP ${status:-000}; expected 200"
    if jq -e . "$output" >/dev/null 2>&1; then
      jq '{status,error,message,path}' "$output" || true
    fi
    exit 1
  }
  jq -e . "$output" >/dev/null 2>&1 || {
    echo "::error::$label returned a non-JSON body"
    exit 1
  }
}

login() {
  local email="$1" password="$2" tenant_id="$3" output="$4"
  local payload status
  payload="$(jq -n \
    --arg email "$email" \
    --arg password "$password" \
    --arg tenantId "$tenant_id" \
    '{email:$email,password:$password,tenantId:$tenantId}')"
  status="$(request POST "$BASE_URL/api/platform/api/v1/auth/login" "$output" \
    --header 'Content-Type: application/json' \
    --header 'Accept: application/json' \
    --data "$payload")"
  expect_json_200 "$status" "login" "$output"
  jq -r '.accessToken // empty' "$output"
}

admin_token="$(login \
  "$CONTROL_PLANE_ADMIN_EMAIL" \
  "$CONTROL_PLANE_ADMIN_PASSWORD" \
  "$CONTROL_PLANE_TENANT_ID" \
  "$WORK_DIR/admin-login.json")"
test -n "$admin_token" || { echo "::error::Control Plane login returned no access token"; exit 1; }
echo "::add-mask::$admin_token"
admin_auth="Authorization: Bearer $admin_token"

plans_status="$(request GET "$BASE_URL/api/platform/api/v1/executive/plans" "$WORK_DIR/plans.json" \
  --header "$admin_auth" --header 'Accept: application/json')"
expect_json_200 "$plans_status" "executivePlans" "$WORK_DIR/plans.json"
jq -e 'type == "array"' "$WORK_DIR/plans.json" >/dev/null || {
  echo "::error::executivePlans must return an array"
  exit 1
}
plan_id="$(jq -r --arg code "$PLAN_CODE" \
  '[.[] | select(.code == $code and .status == "ACTIVE") | .id] | first // empty' \
  "$WORK_DIR/plans.json")"
[[ "$plan_id" =~ $uuid_regex ]] || {
  echo "::error::No ACTIVE $PLAN_CODE plan was returned by the Control Plane"
  exit 1
}
echo "::add-mask::$plan_id"

modules_status="$(request GET \
  "$BASE_URL/api/platform/api/v1/executive/plans/$plan_id/modules" \
  "$WORK_DIR/plan-modules.json" \
  --header "$admin_auth" --header 'Accept: application/json')"
expect_json_200 "$modules_status" "planModuleEntitlements" "$WORK_DIR/plan-modules.json"
jq -e 'type == "array"' "$WORK_DIR/plan-modules.json" >/dev/null || {
  echo "::error::planModuleEntitlements must return an array"
  exit 1
}
jq -e 'any(.[]; .moduleCode == "WORKFLOW" and .moduleEnabled == true)' \
  "$WORK_DIR/plan-modules.json" >/dev/null || {
  echo "::error::$PLAN_CODE does not explicitly enable WORKFLOW; provisioning refuses to mutate plan-wide entitlements"
  exit 1
}

subscriptions_status="$(request GET \
  "$BASE_URL/api/platform/api/v1/executive/subscriptions?tenantId=$WORKFLOW_RUNTIME_TENANT_ID" \
  "$WORK_DIR/subscriptions-before.json" \
  --header "$admin_auth" --header 'Accept: application/json')"
expect_json_200 "$subscriptions_status" "runtimeTenantSubscriptions" "$WORK_DIR/subscriptions-before.json"
jq -e 'type == "array"' "$WORK_DIR/subscriptions-before.json" >/dev/null || {
  echo "::error::runtimeTenantSubscriptions must return an array"
  exit 1
}

history_count="$(jq 'length' "$WORK_DIR/subscriptions-before.json")"
effective_count="$(jq '[.[] | select(.status != "CANCELLED" and .status != "EXPIRED" and .status != "TERMINATED")] | length' \
  "$WORK_DIR/subscriptions-before.json")"
active_count="$(jq '[.[] | select(.status == "ACTIVE")] | length' "$WORK_DIR/subscriptions-before.json")"
action="UNCHANGED"

if [ "$effective_count" -gt 1 ]; then
  echo "::error::Runtime tenant has more than one effective subscription"
  exit 1
fi

if [ "$effective_count" -eq 1 ]; then
  [ "$active_count" -eq 1 ] || {
    echo "::error::Runtime tenant has a non-terminal subscription that is not ACTIVE"
    exit 1
  }
  jq -e --arg code "$PLAN_CODE" 'any(.[]; .status == "ACTIVE" and .planCode == $code)' \
    "$WORK_DIR/subscriptions-before.json" >/dev/null || {
    echo "::error::Runtime tenant ACTIVE subscription is not on the required $PLAN_CODE plan"
    exit 1
  }
elif [ "$history_count" -eq 0 ]; then
  create_payload="$(jq -n \
    --arg tenantId "$WORKFLOW_RUNTIME_TENANT_ID" \
    --arg planId "$plan_id" \
    '{tenantId:$tenantId,planId:$planId,billingCycle:"MONTHLY",seatQuantity:1,trialDays:0}')"
  create_status="$(request POST \
    "$BASE_URL/api/platform/api/v1/executive/subscriptions" \
    "$WORK_DIR/subscription-created.json" \
    --header "$admin_auth" \
    --header 'Content-Type: application/json' \
    --header 'Accept: application/json' \
    --data "$create_payload")"
  expect_json_200 "$create_status" "createRuntimeTenantSubscription" "$WORK_DIR/subscription-created.json"
  jq -e --arg code "$PLAN_CODE" '.status == "ACTIVE" and .planCode == $code' \
    "$WORK_DIR/subscription-created.json" >/dev/null || {
    echo "::error::Created runtime subscription did not resolve to ACTIVE $PLAN_CODE"
    exit 1
  }
  action="CREATED"
else
  echo "::error::Runtime tenant has terminal subscription history but no effective subscription; automatic resurrection is forbidden"
  exit 1
fi

subscriptions_after_status="$(request GET \
  "$BASE_URL/api/platform/api/v1/executive/subscriptions?tenantId=$WORKFLOW_RUNTIME_TENANT_ID" \
  "$WORK_DIR/subscriptions-after.json" \
  --header "$admin_auth" --header 'Accept: application/json')"
expect_json_200 "$subscriptions_after_status" "runtimeTenantSubscriptionsAfter" "$WORK_DIR/subscriptions-after.json"
jq -e --arg code "$PLAN_CODE" \
  '([.[] | select(.status == "ACTIVE" and .planCode == $code)] | length) == 1' \
  "$WORK_DIR/subscriptions-after.json" >/dev/null || {
  echo "::error::Runtime tenant does not have exactly one ACTIVE $PLAN_CODE subscription after provisioning"
  exit 1
}

runtime_token="$(login \
  "$WORKFLOW_RUNTIME_ADMIN_EMAIL" \
  "$WORKFLOW_RUNTIME_ADMIN_PASSWORD" \
  "$WORKFLOW_RUNTIME_TENANT_ID" \
  "$WORK_DIR/runtime-login.json")"
test -n "$runtime_token" || { echo "::error::Workflow runtime login returned no access token"; exit 1; }
echo "::add-mask::$runtime_token"
runtime_auth="Authorization: Bearer $runtime_token"

catalog_status="$(request GET \
  "$BASE_URL/api/platform/api/v1/workflows/catalog/modules" \
  "$WORK_DIR/workflow-catalog.json" \
  --header "$runtime_auth" --header 'Accept: application/json')"
expect_json_200 "$catalog_status" "workflowModuleCatalogPreflight" "$WORK_DIR/workflow-catalog.json"
jq -e 'type == "object" and (.modules | type == "array")' "$WORK_DIR/workflow-catalog.json" >/dev/null || {
  echo "::error::workflowModuleCatalogPreflight must return an object with a modules array"
  exit 1
}

jq -n -S \
  --arg schema "sanad.workflow.runtime-tenant-provisioning.v1" \
  --arg planCode "$PLAN_CODE" \
  --arg action "$action" \
  --argjson initialHistoryCount "$history_count" \
  --arg catalogHttpStatus "$catalog_status" \
  '{schema:$schema,
    identity:"IDENTITY_B",
    planCode:$planCode,
    workflowEntitlement:"EXPLICIT_OPT_IN_VERIFIED",
    initialHistoryCount:$initialHistoryCount,
    subscriptionAction:$action,
    finalSubscriptionStatus:"ACTIVE",
    catalogHttpStatus:$catalogHttpStatus,
    result:"PASS"}' > "$EVIDENCE_FILE"

echo "Dedicated Workflow runtime tenant: PASS ($action)"
