#!/usr/bin/env bash
set -Eeuo pipefail

: "${PRODUCTION_BASE_URL:?required}"
: "${PROD_ADMIN_EMAIL:?required}"
: "${PROD_ADMIN_PASSWORD:?required}"
: "${PROD_TENANT_ID:?required}"
: "${PROD_QA_TENANT_ID:?required}"
: "${PROD_QA_ADMIN_EMAIL:?required}"
: "${PROD_QA_ADMIN_PASSWORD:?required}"
: "${EXPECTED_MAIN_SHA:?required}"
: "${LIVE_BACKEND_SHA:?required}"

BASE_URL="${PRODUCTION_BASE_URL%/}"
WORK_DIR="$(mktemp -d)"
CHECKS="$WORK_DIR/checks.jsonl"
EVIDENCE="${PROD_EVIDENCE_FILE:-workflow-production-3user-journey.json}"
: > "$CHECKS"
trap 'rm -rf "$WORK_DIR"' EXIT

for secret in "$PROD_ADMIN_EMAIL" "$PROD_ADMIN_PASSWORD" "$PROD_TENANT_ID" "$PROD_QA_TENANT_ID" "$PROD_QA_ADMIN_EMAIL" "$PROD_QA_ADMIN_PASSWORD"; do
  echo "::add-mask::$secret"
done

record() { jq -cn --arg stage "$1" --arg status "$2" --arg detail "${3:-}" '{stage:$stage,status:$status,detail:(if $detail=="" then null else $detail end)}' >> "$CHECKS"; }
write_evidence() {
  local result="$1" stage="${2:-}"
  local checks='[]'; [ -s "$CHECKS" ] && checks="$(jq -s '.' "$CHECKS")"
  jq -n --arg schema 'snad.workflow.production-3user-journey.v1' --arg result "$result" --arg failureStage "$stage" \
    --arg mainSha "$EXPECTED_MAIN_SHA" --arg backendSha "$LIVE_BACKEND_SHA" --argjson checks "$checks" \
    '{schema:$schema,result:$result,failureStage:(if $failureStage=="" then null else $failureStage end),mainSha:$mainSha,backendSha:$backendSha,checks:$checks}' > "$EVIDENCE"
}
fail() { echo "::error::$2"; record "$1" FAIL "$2"; write_evidence FAIL "$1"; exit 1; }

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
  local status rc; set +e; status="$(curl "${args[@]}" "$BASE_URL$path")"; rc=$?; set -e
  [ "$rc" -eq 0 ] && printf '%s' "$status" || printf '000'
}
expect() { [ "$1" = "$2" ] || fail "$3" "$3 returned HTTP $1; expected $2"; record "$3" PASS "HTTP $1"; }

# 1) Exact Vercel current-main identity.
status="$(request GET '/api/system/release' "$WORK_DIR/release.json")"; expect "$status" 200 vercelReleaseIdentity
jq -e --arg sha "$EXPECTED_MAIN_SHA" '.commitSha==$sha and (.environment=="production" or .environment=="prod")' "$WORK_DIR/release.json" >/dev/null \
  || fail vercelReleaseIdentity "Vercel production is not exact current main SHA"

# 2) Production control-plane login through Vercel BFF.
login_payload="$(jq -cn --arg email "$PROD_ADMIN_EMAIL" --arg password "$PROD_ADMIN_PASSWORD" --arg tenantId "$PROD_TENANT_ID" '{email:$email,password:$password,tenantId:$tenantId}')"
status="$(request POST '/api/platform/api/v1/auth/login' "$WORK_DIR/login-cp.json" none "$login_payload")"; expect "$status" 200 controlPlaneLogin
CP_TOKEN="$(jq -r '.accessToken // empty' "$WORK_DIR/login-cp.json")"; [ -n "$CP_TOKEN" ] || fail controlPlaneLogin 'No access token'; echo "::add-mask::$CP_TOKEN"
status="$(request GET '/api/platform/api/v1/executive/access-check/v2' "$WORK_DIR/cp-access.json" cp)"; expect "$status" 200 controlPlaneAccessCheck
jq -e '.authenticated==true' "$WORK_DIR/cp-access.json" >/dev/null || fail controlPlaneAccessCheck 'Control Plane access-check is not authenticated'
record controlPlaneOperatorBoundary PASS 'Control Plane token confined to Executive APIs'
[ "$PROD_QA_TENANT_ID" != "$PROD_TENANT_ID" ] || fail qaTenantIsolation 'QA tenant must differ from control-plane tenant'
record qaTenantIsolation PASS 'Dedicated QA tenant differs from control-plane tenant'

# 3) Ensure the dedicated QA tenant has exactly one ACTIVE subscription, using audited Executive APIs only.
status="$(request GET "/api/platform/api/v1/executive/subscriptions?tenantId=$PROD_QA_TENANT_ID" "$WORK_DIR/qa-subscriptions.json" cp)"; expect "$status" 200 qaTenantSubscriptions
ACTIVE_COUNT="$(jq '[.[]|select(.status=="ACTIVE")]|length' "$WORK_DIR/qa-subscriptions.json")"
TOTAL_COUNT="$(jq 'length' "$WORK_DIR/qa-subscriptions.json")"
[ "$ACTIVE_COUNT" -le 1 ] || fail qaTenantSubscriptionGuard "Expected at most one ACTIVE QA subscription; found $ACTIVE_COUNT"

if [ "$ACTIVE_COUNT" = 0 ]; then
  [ "$TOTAL_COUNT" = 0 ] || fail qaTenantSubscriptionGuard 'QA tenant has subscription history but no ACTIVE subscription; refusing lifecycle bypass'

  status="$(request GET '/api/platform/api/v1/executive/plans' "$WORK_DIR/plans.json" cp)"; expect "$status" 200 plansRead
  QA_PLAN_CODE="${PROD_QA_PLAN_CODE:-WORKFLOW_PROD_QA}"
  PLAN_MATCH_COUNT="$(jq --arg code "$QA_PLAN_CODE" '[.[]|select(.code==$code and .status=="ACTIVE")]|length' "$WORK_DIR/plans.json")"
  [ "$PLAN_MATCH_COUNT" = 1 ] || fail qaPlanSelection "Expected exactly one existing ACTIVE plan code $QA_PLAN_CODE; found $PLAN_MATCH_COUNT. Catalog creation is forbidden."
  PLAN_ID="$(jq -r --arg code "$QA_PLAN_CODE" '[.[]|select(.code==$code and .status=="ACTIVE")][0].id' "$WORK_DIR/plans.json")"
  [[ "$PLAN_ID" =~ ^[0-9a-fA-F-]{36}$ ]] || fail qaPlanSelection 'Invalid existing plan id'

  status="$(request GET "/api/platform/api/v1/executive/plans/$PLAN_ID/versions" "$WORK_DIR/plan-versions.json" cp)"; expect "$status" 200 qaPlanVersions
  ACTIVE_VERSION_COUNT="$(jq '[.[]|select(.status=="ACTIVE")]|length' "$WORK_DIR/plan-versions.json")"
  [ "$ACTIVE_VERSION_COUNT" = 1 ] || fail qaPlanVersionGuard "Existing QA plan must have exactly one ACTIVE version; found $ACTIVE_VERSION_COUNT"
  record qaPlanSelection PASS "Existing ACTIVE plan reused: $QA_PLAN_CODE; no catalog/version creation"

  subscription_payload="$(jq -cn --arg tenantId "$PROD_QA_TENANT_ID" --arg planId "$PLAN_ID" '{tenantId:$tenantId,planId:$planId,billingCycle:"MONTHLY",seatQuantity:10,trialDays:0}')"
  status="$(request POST '/api/platform/api/v1/executive/subscriptions' "$WORK_DIR/subscription-create.json" cp "$subscription_payload")"; expect "$status" 200 qaSubscriptionCreate
  jq -e '.status=="ACTIVE"' "$WORK_DIR/subscription-create.json" >/dev/null || fail qaSubscriptionCreate 'QA subscription was not created ACTIVE'
  SUB_ID="$(jq -r '.id // empty' "$WORK_DIR/subscription-create.json")"
  record qaSubscriptionProvisioning PASS 'ACTIVE QA subscription created through Executive API using existing plan'
else
  PLAN_ID="$(jq -r '[.[]|select(.status=="ACTIVE")][0].planId' "$WORK_DIR/qa-subscriptions.json")"
  SUB_ID="$(jq -r '[.[]|select(.status=="ACTIVE")][0].id' "$WORK_DIR/qa-subscriptions.json")"
  record qaSubscriptionProvisioning PASS 'Existing ACTIVE QA subscription and plan reused'
fi
[[ "$PLAN_ID" =~ ^[0-9a-fA-F-]{36}$ ]] || fail qaTenantSubscriptionGuard 'Invalid planId'
[[ "$SUB_ID" =~ ^[0-9a-fA-F-]{36}$ ]] || fail qaTenantSubscriptionGuard 'Invalid subscriptionId'

# Snapshot ACTIVE use of the selected existing plan before any entitlement mutation.
: > "$WORK_DIR/all-active.jsonl"; page=0
while :; do
  status="$(request GET "/api/platform/api/v1/executive/subscriptions/v2?status=ACTIVE&page=$page&size=100" "$WORK_DIR/page.json" cp)"; expect "$status" 200 "activeSubscriptionsPage${page}"
  jq -c '.content[]?' "$WORK_DIR/page.json" >> "$WORK_DIR/all-active.jsonl"
  totalPages="$(jq -r '.totalPages' "$WORK_DIR/page.json")"; [ "$page" -ge $((totalPages-1)) ] && break
  page=$((page+1)); [ "$page" -lt 100 ] || fail planUsageSafety 'Pagination safety limit exceeded'
done
jq -s '.' "$WORK_DIR/all-active.jsonl" > "$WORK_DIR/all-active.json"
PLAN_ACTIVE_COUNT="$(jq --arg plan "$PLAN_ID" '[.[]|select(.planId==$plan)]|length' "$WORK_DIR/all-active.json")"
PLAN_TENANT="$(jq -r --arg plan "$PLAN_ID" '[.[]|select(.planId==$plan)][0].tenantId // empty' "$WORK_DIR/all-active.json")"
record planUsageSnapshot PASS "Selected existing plan has $PLAN_ACTIVE_COUNT ACTIVE subscription(s)"

# 4) Read effective Workflow entitlement; mutate the plan only when it is exclusive to Tenant B.
status="$(request GET "/api/platform/api/v1/executive/tenants/$PROD_QA_TENANT_ID/entitlements" "$WORK_DIR/ent-before.json" cp)"; expect "$status" 200 entitlementsBefore
ENABLED_BEFORE="$(jq -r '[.[]|select(.moduleCode=="WORKFLOW")][0].moduleEnabled // false' "$WORK_DIR/ent-before.json")"
if [ "$ENABLED_BEFORE" != true ]; then
  [ "$PLAN_ACTIVE_COUNT" = 1 ] && [ "$PLAN_TENANT" = "$PROD_QA_TENANT_ID" ] || fail planMutationSafety "Refusing to mutate shared existing plan: activeSubscriptions=$PLAN_ACTIVE_COUNT tenant=$PLAN_TENANT"
  status="$(request GET '/api/platform/api/v1/executive/modules/WORKFLOW' "$WORK_DIR/module.json" cp)"; expect "$status" 200 workflowModule
  MODULE_ID="$(jq -r '.id // empty' "$WORK_DIR/module.json")"; [[ "$MODULE_ID" =~ ^[0-9a-fA-F-]{36}$ ]] || fail workflowModule 'Invalid WORKFLOW module id'
  entitlement_payload="$(jq -cn --arg moduleId "$MODULE_ID" '{moduleId:$moduleId,moduleEnabled:true,capabilityCode:null,capabilityValue:null,limitValue:null,quotaValue:null,quotaPeriod:null}')"
  status="$(request PUT "/api/platform/api/v1/executive/plans/$PLAN_ID/modules/WORKFLOW" "$WORK_DIR/enable.json" cp "$entitlement_payload")"; expect "$status" 200 enableWorkflowEntitlement
  status="$(request POST "/api/platform/api/v1/executive/tenants/$PROD_QA_TENANT_ID/entitlements/recalculate" "$WORK_DIR/recalc.json" cp '{}')"; expect "$status" 200 recalculateEntitlements
  record entitlementMutation PASS 'Enabled through audited Executive API on Tenant-B-exclusive existing plan'
else
  record entitlementMutation PASS 'WORKFLOW already enabled; existing plan left unchanged'
fi
status="$(request GET "/api/platform/api/v1/executive/tenants/$PROD_QA_TENANT_ID/entitlements" "$WORK_DIR/ent-after.json" cp)"; expect "$status" 200 entitlementsAfter
jq -e '[.[]|select(.moduleCode=="WORKFLOW" and .moduleEnabled==true)]|length==1' "$WORK_DIR/ent-after.json" >/dev/null || fail entitlementsAfter 'WORKFLOW entitlement not active'

# Switch to the dedicated QA tenant identity for all tenant-scoped Workflow operations.
qa_login_payload="$(jq -cn --arg email "$PROD_QA_ADMIN_EMAIL" --arg password "$PROD_QA_ADMIN_PASSWORD" --arg tenantId "$PROD_QA_TENANT_ID" '{email:$email,password:$password,tenantId:$tenantId}')"
status="$(request POST '/api/platform/api/v1/auth/login' "$WORK_DIR/login-qa.json" none "$qa_login_payload")"; expect "$status" 200 qaAdminLogin
QA_TOKEN="$(jq -r '.accessToken // empty' "$WORK_DIR/login-qa.json")"; [ -n "$QA_TOKEN" ] || fail qaAdminLogin 'No QA access token'; echo "::add-mask::$QA_TOKEN"
status="$(request GET '/api/platform/api/v1/auth/me' "$WORK_DIR/me-qa.json" qa)"; expect "$status" 200 qaAuthMe
jq -e --arg tenant "$PROD_QA_TENANT_ID" '.tenantId==$tenant' "$WORK_DIR/me-qa.json" >/dev/null || fail qaAuthMe 'QA login tenant binding mismatch'
for cap in USER.CREATE USER.READ USER.WRITE WORKFLOW.WRITE WORKFLOW.DESIGN WORKFLOW.VALIDATE WORKFLOW.PUBLISH; do
  jq -e --arg c "$cap" '.capabilities | index($c) != null' "$WORK_DIR/me-qa.json" >/dev/null || fail capabilityGuard "Missing capability $cap"
done
record capabilityGuard PASS 'Required QA tenant user and workflow capabilities present'

# 5) Verify previously failing validate boundary is now open.
status="$(request GET '/api/platform/api/v1/workflows/definitions?limit=50' "$WORK_DIR/defs.json" qa)"; expect "$status" 200 definitionsRead
EXISTING_DEF="$(jq -r '[.[]|select(.publicationState=="PUBLISHED")][0].id // .[0].id // empty' "$WORK_DIR/defs.json")"
[ -n "$EXISTING_DEF" ] || fail validateBoundary 'No definition exists for boundary check'
status="$(request POST "/api/platform/api/v1/workflows/definitions/$EXISTING_DEF/validate" "$WORK_DIR/boundary-validate.json" qa '{}')"; expect "$status" 200 validateBoundary

# 6) Create three production QA users (real tenant user records; no credential bypass).
RUN_KEY="${GITHUB_RUN_ID:-manual}-${GITHUB_RUN_ATTEMPT:-1}-${EXPECTED_MAIN_SHA:0:8}"
USERS='[]'
for n in 1 2 3; do
  email="workflow-prod-qa${n}-${RUN_KEY}@example.invalid"; display="Workflow Production QA $n"
  payload="$(jq -cn --arg email "$email" --arg display "$display" '{email:$email,displayName:$display,status:"ACTIVE"}')"
  status="$(request POST "/api/platform/api/v1/users?tenantId=$PROD_QA_TENANT_ID" "$WORK_DIR/user$n.json" qa "$payload")"; expect "$status" 201 "createQaUser${n}"
  uid="$(jq -r '.id // empty' "$WORK_DIR/user$n.json")"; [[ "$uid" =~ ^[0-9a-fA-F-]{36}$ ]] || fail "createQaUser${n}" 'Invalid user id'
  USERS="$(jq -cn --argjson a "$USERS" --arg id "$uid" --arg email "$email" --arg display "$display" '$a + [{id:$id,email:$email,displayName:$display}]')"
done
record threeQaUsers PASS '3 production QA user records created'

# 7) Full Workflow lifecycle: DRAFT -> graph -> validate -> simulate -> publish(Y2).
WF_CODE="PROD-3USER-${EXPECTED_MAIN_SHA:0:8}-${GITHUB_RUN_ID:-manual}-${GITHUB_RUN_ATTEMPT:-1}"
create_payload="$(jq -cn --arg code "$WF_CODE" '{code:$code,name:"Production 3-User Journey",description:"Controlled production closure evidence",module:"GENERAL",triggerType:"MANUAL"}')"
status="$(request POST '/api/platform/api/v1/workflows/definitions' "$WORK_DIR/definition.json" qa "$create_payload")"; expect "$status" 200 definitionCreate
DEF_ID="$(jq -r '.id // empty' "$WORK_DIR/definition.json")"; VERSION_LOCK="$(jq -r '.versionLock // empty' "$WORK_DIR/definition.json")"
[[ "$DEF_ID" =~ ^[0-9a-fA-F-]{36}$ ]] || fail definitionCreate 'Invalid definition id'
[ "$(jq -r '.publicationState' "$WORK_DIR/definition.json")" = DRAFT ] || fail definitionCreate 'Definition not DRAFT'

start='{"stepKey":"start","name":"Start","stepType":"START","sequenceOrder":1,"configuration":"{}"}'
status="$(request POST "/api/platform/api/v1/workflows/definitions/$DEF_ID/steps" "$WORK_DIR/start.json" qa "$start")"; expect "$status" 200 startStep
START_ID="$(jq -r '.id' "$WORK_DIR/start.json")"
end='{"stepKey":"end","name":"End","stepType":"END","sequenceOrder":2,"configuration":"{}"}'
status="$(request POST "/api/platform/api/v1/workflows/definitions/$DEF_ID/steps" "$WORK_DIR/end.json" qa "$end")"; expect "$status" 200 endStep
END_ID="$(jq -r '.id' "$WORK_DIR/end.json")"
trans="$(jq -cn --arg from "$START_ID" --arg to "$END_ID" '{fromStepId:$from,toStepId:$to,transitionKey:"complete",outcome:"SUCCESS",priority:10}')"
status="$(request POST "/api/platform/api/v1/workflows/definitions/$DEF_ID/transitions" "$WORK_DIR/trans.json" qa "$trans")"; expect "$status" 200 transitionCreate
status="$(request POST "/api/platform/api/v1/workflows/definitions/$DEF_ID/validate" "$WORK_DIR/validate.json" qa '{}')"; expect "$status" 200 definitionValidate
jq -e '.valid==true and (.errors|length)==0' "$WORK_DIR/validate.json" >/dev/null || fail definitionValidate 'Validation not clean'
status="$(request POST "/api/platform/api/v1/workflows/definitions/$DEF_ID/simulate" "$WORK_DIR/simulate.json" qa '{}')"; expect "$status" 200 definitionSimulate
jq -e '.valid==true and .simulated==true' "$WORK_DIR/simulate.json" >/dev/null || fail definitionSimulate 'Simulation failed'
pub="$(jq -cn --argjson expected "$VERSION_LOCK" '{expectedVersion:$expected}')"
status="$(request POST "/api/platform/api/v1/workflows/definitions/$DEF_ID/publish" "$WORK_DIR/publish.json" qa "$pub")"; expect "$status" 200 definitionPublish
jq -e '.publicationState=="PUBLISHED" and .engineGeneration=="Y2"' "$WORK_DIR/publish.json" >/dev/null || fail definitionPublish 'Not PUBLISHED Y2'

# 8) Link one full production instance to each QA user and require 3/3 COMPLETED.
INSTANCES='[]'
for n in 1 2 3; do
  uid="$(jq -r --argjson n "$n" '.[$n-1].id' <<<"$USERS")"
  corr="$(python3 -c 'import uuid; print(uuid.uuid4())')"
  payload="$(jq -cn --arg def "$DEF_ID" --arg uid "$uid" --arg corr "$corr" '{workflowDefinitionId:$def,businessEntityType:"WORKFLOW_QA_USER",businessEntityId:$uid,correlationId:$corr}')"
  status="$(request POST '/api/platform/api/v1/workflows/instances' "$WORK_DIR/instance$n.json" qa "$payload")"; expect "$status" 200 "instanceStart${n}"
  jq -e '.status=="COMPLETED" and .engineGeneration=="Y2"' "$WORK_DIR/instance$n.json" >/dev/null || fail "instanceStart${n}" "Instance $n did not reach COMPLETED"
  iid="$(jq -r '.id' "$WORK_DIR/instance$n.json")"
  INSTANCES="$(jq -cn --argjson a "$INSTANCES" --arg userId "$uid" --arg instanceId "$iid" '$a + [{userId:$userId,instanceId:$instanceId,status:"COMPLETED"}]')"
done
record threeInstances PASS '3/3 QA-user-linked instances reached COMPLETED'

# 9) Published graph immutability.
extra='{"stepKey":"forbidden","name":"Forbidden","stepType":"ACTION","sequenceOrder":99,"configuration":"{}"}'
status="$(request POST "/api/platform/api/v1/workflows/definitions/$DEF_ID/steps" "$WORK_DIR/immutable.json" qa "$extra")"
[ "$status" = 409 ] || fail publishedImmutability "Expected 409, got $status"; record publishedImmutability PASS 'HTTP 409'

jq -s '.' "$CHECKS" > "$WORK_DIR/checks.json"
jq -n --arg schema 'snad.workflow.production-3user-journey.v1' --arg result PASS --arg mainSha "$EXPECTED_MAIN_SHA" --arg backendSha "$LIVE_BACKEND_SHA" \
  --arg subscriptionId "$SUB_ID" --arg planId "$PLAN_ID" --arg definitionId "$DEF_ID" --arg workflowCode "$WF_CODE" \
  --argjson users "$USERS" --argjson instances "$INSTANCES" --slurpfile checks "$WORK_DIR/checks.json" \
  '{schema:$schema,result:$result,failureStage:null,mainSha:$mainSha,backendSha:$backendSha,subscriptionId:$subscriptionId,planId:$planId,workflow:{definitionId:$definitionId,code:$workflowCode,state:"PUBLISHED",engineGeneration:"Y2"},users:$users,instances:$instances,checks:$checks[0]}' > "$EVIDENCE"

{
  echo "WORKFLOW_DEFINITION_ID=$DEF_ID"
  echo "WORKFLOW_DEFINITION_CODE=$WF_CODE"
} >> "$GITHUB_ENV"

echo 'Production Workflow 3-user journey: PASSED'
