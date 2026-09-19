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
  [ "$auth" = auth ] && args+=(--header "Authorization: Bearer $TOKEN")
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
TOKEN="$CP_TOKEN"
status="$(request GET '/api/platform/api/v1/auth/me' "$WORK_DIR/me-cp.json" auth)"; expect "$status" 200 controlPlaneAuthMe
for cap in EXECUTIVE_VIEW EXECUTIVE_MANAGE EXECUTIVE_BILLING; do
  jq -e --arg c "$cap" '.capabilities | index($c) != null' "$WORK_DIR/me-cp.json" >/dev/null || fail controlPlaneCapabilityGuard "Missing capability $cap"
done
record controlPlaneCapabilityGuard PASS 'Required executive capabilities present'
[ "$PROD_QA_TENANT_ID" != "$PROD_TENANT_ID" ] || fail qaTenantIsolation 'QA tenant must differ from control-plane tenant'
record qaTenantIsolation PASS 'Dedicated QA tenant differs from control-plane tenant'

# 3) Ensure the dedicated QA tenant has exactly one ACTIVE subscription, using audited Executive APIs only.
status="$(request GET "/api/platform/api/v1/executive/subscriptions?tenantId=$PROD_QA_TENANT_ID" "$WORK_DIR/qa-subscriptions.json" auth)"; expect "$status" 200 qaTenantSubscriptions
ACTIVE_COUNT="$(jq '[.[]|select(.status=="ACTIVE")]|length' "$WORK_DIR/qa-subscriptions.json")"
TOTAL_COUNT="$(jq 'length' "$WORK_DIR/qa-subscriptions.json")"
[ "$ACTIVE_COUNT" -le 1 ] || fail qaTenantSubscriptionGuard "Expected at most one ACTIVE QA subscription; found $ACTIVE_COUNT"

if [ "$ACTIVE_COUNT" = 0 ]; then
  [ "$TOTAL_COUNT" = 0 ] || fail qaTenantSubscriptionGuard 'QA tenant has subscription history but no ACTIVE subscription; refusing lifecycle bypass'

  status="$(request GET '/api/platform/api/v1/executive/plans' "$WORK_DIR/plans.json" auth)"; expect "$status" 200 plansRead
  PLAN_ID="$(jq -r '[.[]|select(.code=="WORKFLOW_PROD_QA")][0].id // empty' "$WORK_DIR/plans.json")"
  if [ -z "$PLAN_ID" ]; then
    plan_payload='{"code":"WORKFLOW_PROD_QA","name":"Workflow Production QA","description":"Dedicated zero-price production certification plan","currencyCode":"SAR","monthlyPriceMinor":0,"annualPriceMinor":0,"trialDays":0,"maxUsers":20,"maxOrganizations":5,"storageMb":0,"entitlements":[]}'
    status="$(request POST '/api/platform/api/v1/executive/plans' "$WORK_DIR/plan-create.json" auth "$plan_payload")"; expect "$status" 200 qaPlanCreate
    PLAN_ID="$(jq -r '.id // empty' "$WORK_DIR/plan-create.json")"
    [[ "$PLAN_ID" =~ ^[0-9a-fA-F-]{36}$ ]] || fail qaPlanCreate 'Invalid plan id'
    record qaPlanProvisioning PASS 'Dedicated QA plan created through Executive API'
  else
    jq -e '[.[]|select(.code=="WORKFLOW_PROD_QA" and .status=="ACTIVE")]|length==1' "$WORK_DIR/plans.json" >/dev/null || fail qaPlanProvisioning 'Dedicated QA plan exists but is not ACTIVE'
    record qaPlanProvisioning PASS 'Dedicated QA plan reused'
  fi

  status="$(request GET "/api/platform/api/v1/executive/plans/$PLAN_ID/versions" "$WORK_DIR/plan-versions.json" auth)"; expect "$status" 200 qaPlanVersions
  ACTIVE_VERSION_COUNT="$(jq '[.[]|select(.status=="ACTIVE")]|length' "$WORK_DIR/plan-versions.json")"
  [ "$ACTIVE_VERSION_COUNT" -le 1 ] || fail qaPlanVersionGuard "Expected at most one ACTIVE plan version; found $ACTIVE_VERSION_COUNT"
  if [ "$ACTIVE_VERSION_COUNT" = 0 ]; then
    version_payload='{"currencyCode":"SAR","monthlyPriceMinor":0,"annualPriceMinor":0,"trialDays":0,"maxUsers":20,"maxOrganizations":5,"storageMb":0}'
    status="$(request POST "/api/platform/api/v1/executive/plans/$PLAN_ID/versions" "$WORK_DIR/version-create.json" auth "$version_payload")"; expect "$status" 200 qaPlanVersionCreate
    VERSION_ID="$(jq -r '.id // empty' "$WORK_DIR/version-create.json")"
    [[ "$VERSION_ID" =~ ^[0-9a-fA-F-]{36}$ ]] || fail qaPlanVersionCreate 'Invalid version id'
    status="$(request POST "/api/platform/api/v1/executive/plans/$PLAN_ID/versions/$VERSION_ID/activate" "$WORK_DIR/version-activate.json" auth '{}')"; expect "$status" 200 qaPlanVersionActivate
    jq -e '.status=="ACTIVE"' "$WORK_DIR/version-activate.json" >/dev/null || fail qaPlanVersionActivate 'Plan version did not become ACTIVE'
    record qaPlanVersionProvisioning PASS 'Dedicated QA plan version activated'
  else
    record qaPlanVersionProvisioning PASS 'Dedicated QA ACTIVE plan version reused'
  fi

  subscription_payload="$(jq -cn --arg tenantId "$PROD_QA_TENANT_ID" --arg planId "$PLAN_ID" '{tenantId:$tenantId,planId:$planId,billingCycle:"MONTHLY",seatQuantity:10,trialDays:0}')"
  status="$(request POST '/api/platform/api/v1/executive/subscriptions' "$WORK_DIR/subscription-create.json" auth "$subscription_payload")"; expect "$status" 200 qaSubscriptionCreate
  jq -e '.status=="ACTIVE"' "$WORK_DIR/subscription-create.json" >/dev/null || fail qaSubscriptionCreate 'QA subscription was not created ACTIVE'
  SUB_ID="$(jq -r '.id // empty' "$WORK_DIR/subscription-create.json")"
  record qaSubscriptionProvisioning PASS 'ACTIVE QA subscription created through Executive API'
else
  PLAN_ID="$(jq -r '[.[]|select(.status=="ACTIVE")][0].planId' "$WORK_DIR/qa-subscriptions.json")"
  SUB_ID="$(jq -r '[.[]|select(.status=="ACTIVE")][0].id' "$WORK_DIR/qa-subscriptions.json")"
  record qaSubscriptionProvisioning PASS 'Existing ACTIVE QA subscription reused'
fi
[[ "$PLAN_ID" =~ ^[0-9a-fA-F-]{36}$ ]] || fail qaTenantSubscriptionGuard 'Invalid planId'
[[ "$SUB_ID" =~ ^[0-9a-fA-F-]{36}$ ]] || fail qaTenantSubscriptionGuard 'Invalid subscriptionId'

# Prove the QA plan is exclusive among ACTIVE subscriptions before any entitlement mutation.
: > "$WORK_DIR/all-active.jsonl"; page=0
while :; do
  status="$(request GET "/api/platform/api/v1/executive/subscriptions/v2?status=ACTIVE&page=$page&size=100" "$WORK_DIR/page.json" auth)"; expect "$status" 200 "activeSubscriptionsPage${page}"
  jq -c '.content[]?' "$WORK_DIR/page.json" >> "$WORK_DIR/all-active.jsonl"
  totalPages="$(jq -r '.totalPages' "$WORK_DIR/page.json")"; [ "$page" -ge $((totalPages-1)) ] && break
  page=$((page+1)); [ "$page" -lt 100 ] || fail planExclusivity 'Pagination safety limit exceeded'
done
jq -s '.' "$WORK_DIR/all-active.jsonl" > "$WORK_DIR/all-active.json"
PLAN_ACTIVE_COUNT="$(jq --arg plan "$PLAN_ID" '[.[]|select(.planId==$plan)]|length' "$WORK_DIR/all-active.json")"
PLAN_TENANT="$(jq -r --arg plan "$PLAN_ID" '[.[]|select(.planId==$plan)][0].tenantId // empty' "$WORK_DIR/all-active.json")"
[ "$PLAN_ACTIVE_COUNT" = 1 ] && [ "$PLAN_TENANT" = "$PROD_QA_TENANT_ID" ] || fail planExclusivity "Dedicated QA plan is shared by $PLAN_ACTIVE_COUNT ACTIVE subscriptions"
record planExclusivity PASS 'Dedicated QA plan is exclusive to the QA tenant'

# 4) Enable Workflow only through audited executive entitlement APIs.
status="$(request GET '/api/platform/api/v1/executive/modules/WORKFLOW' "$WORK_DIR/module.json" auth)"; expect "$status" 200 workflowModule
MODULE_ID="$(jq -r '.id // empty' "$WORK_DIR/module.json")"; [[ "$MODULE_ID" =~ ^[0-9a-fA-F-]{36}$ ]] || fail workflowModule 'Invalid WORKFLOW module id'
status="$(request GET "/api/platform/api/v1/executive/tenants/$PROD_QA_TENANT_ID/entitlements" "$WORK_DIR/ent-before.json" auth)"; expect "$status" 200 entitlementsBefore
ENABLED_BEFORE="$(jq -r '[.[]|select(.moduleCode=="WORKFLOW")][0].moduleEnabled // false' "$WORK_DIR/ent-before.json")"
if [ "$ENABLED_BEFORE" != true ]; then
  entitlement_payload="$(jq -cn --arg moduleId "$MODULE_ID" '{moduleId:$moduleId,moduleEnabled:true,capabilityCode:null,capabilityValue:null,limitValue:null,quotaValue:null,quotaPeriod:null}')"
  status="$(request PUT "/api/platform/api/v1/executive/plans/$PLAN_ID/modules/WORKFLOW" "$WORK_DIR/enable.json" auth "$entitlement_payload")"; expect "$status" 200 enableWorkflowEntitlement
  status="$(request POST "/api/platform/api/v1/executive/tenants/$PROD_QA_TENANT_ID/entitlements/recalculate" "$WORK_DIR/recalc.json" auth '{}')"; expect "$status" 200 recalculateEntitlements
  record entitlementMutation PASS 'Enabled through audited executive API on dedicated QA plan'
else
  record entitlementMutation PASS 'Already enabled; no mutation required'
fi
status="$(request GET "/api/platform/api/v1/executive/tenants/$PROD_QA_TENANT_ID/entitlements" "$WORK_DIR/ent-after.json" auth)"; expect "$status" 200 entitlementsAfter
jq -e '[.[]|select(.moduleCode=="WORKFLOW" and .moduleEnabled==true)]|length==1' "$WORK_DIR/ent-after.json" >/dev/null || fail entitlementsAfter 'WORKFLOW entitlement not active'

# Switch to the dedicated QA tenant identity for all tenant-scoped Workflow operations.
qa_login_payload="$(jq -cn --arg email "$PROD_QA_ADMIN_EMAIL" --arg password "$PROD_QA_ADMIN_PASSWORD" --arg tenantId "$PROD_QA_TENANT_ID" '{email:$email,password:$password,tenantId:$tenantId}')"
status="$(request POST '/api/platform/api/v1/auth/login' "$WORK_DIR/login-qa.json" none "$qa_login_payload")"; expect "$status" 200 qaAdminLogin
QA_TOKEN="$(jq -r '.accessToken // empty' "$WORK_DIR/login-qa.json")"; [ -n "$QA_TOKEN" ] || fail qaAdminLogin 'No QA access token'; echo "::add-mask::$QA_TOKEN"
TOKEN="$QA_TOKEN"
status="$(request GET '/api/platform/api/v1/auth/me' "$WORK_DIR/me-qa.json" auth)"; expect "$status" 200 qaAuthMe
jq -e --arg tenant "$PROD_QA_TENANT_ID" '.tenantId==$tenant' "$WORK_DIR/me-qa.json" >/dev/null || fail qaAuthMe 'QA login tenant binding mismatch'
for cap in USER.CREATE USER.READ USER.WRITE WORKFLOW.WRITE WORKFLOW.DESIGN WORKFLOW.VALIDATE WORKFLOW.SIMULATE WORKFLOW.PUBLISH; do
  jq -e --arg c "$cap" '.capabilities | index($c) != null' "$WORK_DIR/me-qa.json" >/dev/null || fail capabilityGuard "Missing capability $cap"
done
record capabilityGuard PASS 'Required QA tenant user and workflow capabilities present'

# 5) Verify previously failing validate boundary is now open.
status="$(request GET '/api/platform/api/v1/workflows/definitions?limit=50' "$WORK_DIR/defs.json" auth)"; expect "$status" 200 definitionsRead
EXISTING_DEF="$(jq -r '[.[]|select(.publicationState=="PUBLISHED")][0].id // .[0].id // empty' "$WORK_DIR/defs.json")"
[ -n "$EXISTING_DEF" ] || fail validateBoundary 'No definition exists for boundary check'
status="$(request POST "/api/platform/api/v1/workflows/definitions/$EXISTING_DEF/validate" "$WORK_DIR/boundary-validate.json" auth '{}')"; expect "$status" 200 validateBoundary

# 6) Create three production QA users (real tenant user records; no credential bypass).
RUN_KEY="${GITHUB_RUN_ID:-manual}-${GITHUB_RUN_ATTEMPT:-1}-${EXPECTED_MAIN_SHA:0:8}"
USERS='[]'
for n in 1 2 3; do
  email="workflow-prod-qa${n}-${RUN_KEY}@example.invalid"; display="Workflow Production QA $n"
  payload="$(jq -cn --arg email "$email" --arg display "$display" '{email:$email,displayName:$display,status:"ACTIVE"}')"
  status="$(request POST "/api/platform/api/v1/users?tenantId=$PROD_QA_TENANT_ID" "$WORK_DIR/user$n.json" auth "$payload")"; expect "$status" 201 "createQaUser${n}"
  uid="$(jq -r '.id // empty' "$WORK_DIR/user$n.json")"; [[ "$uid" =~ ^[0-9a-fA-F-]{36}$ ]] || fail "createQaUser${n}" 'Invalid user id'
  USERS="$(jq -cn --argjson a "$USERS" --arg id "$uid" --arg email "$email" --arg display "$display" '$a + [{id:$id,email:$email,displayName:$display}]')"
done
record threeQaUsers PASS '3 production QA user records created'

# 7) Full Workflow lifecycle: DRAFT -> graph -> validate -> simulate -> publish(Y2).
WF_CODE="PROD-3USER-${EXPECTED_MAIN_SHA:0:8}-${GITHUB_RUN_ID:-manual}-${GITHUB_RUN_ATTEMPT:-1}"
create_payload="$(jq -cn --arg code "$WF_CODE" '{code:$code,name:"Production 3-User Journey",description:"Controlled production closure evidence",module:"GENERAL",triggerType:"MANUAL"}')"
status="$(request POST '/api/platform/api/v1/workflows/definitions' "$WORK_DIR/definition.json" auth "$create_payload")"; expect "$status" 200 definitionCreate
DEF_ID="$(jq -r '.id // empty' "$WORK_DIR/definition.json")"; VERSION_LOCK="$(jq -r '.versionLock // empty' "$WORK_DIR/definition.json")"
[[ "$DEF_ID" =~ ^[0-9a-fA-F-]{36}$ ]] || fail definitionCreate 'Invalid definition id'
[ "$(jq -r '.publicationState' "$WORK_DIR/definition.json")" = DRAFT ] || fail definitionCreate 'Definition not DRAFT'

start='{"stepKey":"start","name":"Start","stepType":"START","sequenceOrder":1,"configuration":"{}"}'
status="$(request POST "/api/platform/api/v1/workflows/definitions/$DEF_ID/steps" "$WORK_DIR/start.json" auth "$start")"; expect "$status" 200 startStep
START_ID="$(jq -r '.id' "$WORK_DIR/start.json")"
end='{"stepKey":"end","name":"End","stepType":"END","sequenceOrder":2,"configuration":"{}"}'
status="$(request POST "/api/platform/api/v1/workflows/definitions/$DEF_ID/steps" "$WORK_DIR/end.json" auth "$end")"; expect "$status" 200 endStep
END_ID="$(jq -r '.id' "$WORK_DIR/end.json")"
trans="$(jq -cn --arg from "$START_ID" --arg to "$END_ID" '{fromStepId:$from,toStepId:$to,transitionKey:"complete",outcome:"SUCCESS",priority:10}')"
status="$(request POST "/api/platform/api/v1/workflows/definitions/$DEF_ID/transitions" "$WORK_DIR/trans.json" auth "$trans")"; expect "$status" 200 transitionCreate
status="$(request POST "/api/platform/api/v1/workflows/definitions/$DEF_ID/validate" "$WORK_DIR/validate.json" auth '{}')"; expect "$status" 200 definitionValidate
jq -e '.valid==true and (.errors|length)==0' "$WORK_DIR/validate.json" >/dev/null || fail definitionValidate 'Validation not clean'
status="$(request POST "/api/platform/api/v1/workflows/definitions/$DEF_ID/simulate" "$WORK_DIR/simulate.json" auth '{}')"; expect "$status" 200 definitionSimulate
jq -e '.valid==true and .simulated==true' "$WORK_DIR/simulate.json" >/dev/null || fail definitionSimulate 'Simulation failed'
pub="$(jq -cn --argjson expected "$VERSION_LOCK" '{expectedVersion:$expected}')"
status="$(request POST "/api/platform/api/v1/workflows/definitions/$DEF_ID/publish" "$WORK_DIR/publish.json" auth "$pub")"; expect "$status" 200 definitionPublish
jq -e '.publicationState=="PUBLISHED" and .engineGeneration=="Y2"' "$WORK_DIR/publish.json" >/dev/null || fail definitionPublish 'Not PUBLISHED Y2'

# 8) Link one full production instance to each QA user and require 3/3 COMPLETED.
INSTANCES='[]'
for n in 1 2 3; do
  uid="$(jq -r --argjson n "$n" '.[$n-1].id' <<<"$USERS")"
  corr="$(python3 -c 'import uuid; print(uuid.uuid4())')"
  payload="$(jq -cn --arg def "$DEF_ID" --arg uid "$uid" --arg corr "$corr" '{workflowDefinitionId:$def,businessEntityType:"WORKFLOW_QA_USER",businessEntityId:$uid,correlationId:$corr}')"
  status="$(request POST '/api/platform/api/v1/workflows/instances' "$WORK_DIR/instance$n.json" auth "$payload")"; expect "$status" 200 "instanceStart${n}"
  jq -e '.status=="COMPLETED" and .engineGeneration=="Y2"' "$WORK_DIR/instance$n.json" >/dev/null || fail "instanceStart${n}" "Instance $n did not reach COMPLETED"
  iid="$(jq -r '.id' "$WORK_DIR/instance$n.json")"
  INSTANCES="$(jq -cn --argjson a "$INSTANCES" --arg userId "$uid" --arg instanceId "$iid" '$a + [{userId:$userId,instanceId:$instanceId,status:"COMPLETED"}]')"
done
record threeInstances PASS '3/3 QA-user-linked instances reached COMPLETED'

# 9) Published graph immutability.
extra='{"stepKey":"forbidden","name":"Forbidden","stepType":"ACTION","sequenceOrder":99,"configuration":"{}"}'
status="$(request POST "/api/platform/api/v1/workflows/definitions/$DEF_ID/steps" "$WORK_DIR/immutable.json" auth "$extra")"
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
