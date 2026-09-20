#!/usr/bin/env bash
set -Eeuo pipefail

: "${PRODUCTION_BASE_URL:?required}"
: "${PROD_ADMIN_EMAIL:?required}"
: "${PROD_ADMIN_PASSWORD:?required}"
: "${PROD_TENANT_ID:?required}"
: "${PROD_QA_TENANT_CODE:?required}"
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

for secret in "$PROD_ADMIN_EMAIL" "$PROD_ADMIN_PASSWORD" "$PROD_TENANT_ID" "$PROD_QA_ADMIN_EMAIL" "$PROD_QA_ADMIN_PASSWORD"; do
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

# Resolve Tenant B from the canonical production directory. The historical
# Historical static Tenant B identifiers are deliberately not trusted here.
status="$(request GET "/api/platform/api/v1/executive/tenants/v2?search=$PROD_QA_TENANT_CODE&status=ACTIVE&page=0&size=100&sort=code&direction=ASC" "$WORK_DIR/qa-tenant-directory.json" cp)"; expect "$status" 200 qaTenantDirectory
QA_TENANT_MATCH_COUNT="$(jq --arg code "$PROD_QA_TENANT_CODE" '[.content[]? | select(.code==$code and .status=="ACTIVE")]|length' "$WORK_DIR/qa-tenant-directory.json")"
[ "$QA_TENANT_MATCH_COUNT" = 1 ] || fail qaTenantResolution "Expected exactly one ACTIVE Tenant B code $PROD_QA_TENANT_CODE; found $QA_TENANT_MATCH_COUNT"
PROD_QA_TENANT_ID="$(jq -r --arg code "$PROD_QA_TENANT_CODE" '[.content[]? | select(.code==$code and .status=="ACTIVE")][0].id // empty' "$WORK_DIR/qa-tenant-directory.json")"
[[ "$PROD_QA_TENANT_ID" =~ ^[0-9a-fA-F-]{36}$ ]] || fail qaTenantResolution 'Resolved Tenant B id is invalid'
echo "::add-mask::$PROD_QA_TENANT_ID"
export PROD_QA_TENANT_ID
if [ -n "${GITHUB_ENV:-}" ]; then echo "PROD_QA_TENANT_ID=$PROD_QA_TENANT_ID" >> "$GITHUB_ENV"; fi
record qaTenantResolution PASS 'Tenant B resolved dynamically from Executive tenant directory'
[ "$PROD_QA_TENANT_ID" != "$PROD_TENANT_ID" ] || fail qaTenantIsolation 'QA tenant must differ from control-plane tenant'
record qaTenantIsolation PASS 'Dedicated QA tenant differs from control-plane tenant'

# 3) Resolve the dedicated QA tenant subscription through the global control-plane grid.
# Never send a foreign tenantId query parameter: JwtAuthenticationFilter intentionally rejects it.
status="$(request GET '/api/platform/api/v1/executive/plans' "$WORK_DIR/plans.json" cp)"; expect "$status" 200 plansRead
STARTER_COUNT="$(jq '[.[]|select(.code=="STARTER" and .status=="ACTIVE" and (.trialDays // 0)>0 and (.maxUsers // 0)>=4 and ([.entitlements[]? | select(.featureCode=="WORKFLOW" and .enabled==true)]|length)>=1)]|length' "$WORK_DIR/plans.json")"
[ "$STARTER_COUNT" = 1 ] || fail starterPlanGuard "Expected exactly one ACTIVE STARTER plan with non-zero trial, >=4 users, and WORKFLOW entitlement; found $STARTER_COUNT"
PLAN_ID="$(jq -r '[.[]|select(.code=="STARTER" and .status=="ACTIVE")][0].id' "$WORK_DIR/plans.json")"
STARTER_MAX_USERS="$(jq -r '[.[]|select(.code=="STARTER" and .status=="ACTIVE")][0].maxUsers' "$WORK_DIR/plans.json")"
[[ "$PLAN_ID" =~ ^[0-9a-fA-F-]{36}$ ]] || fail starterPlanGuard 'Invalid STARTER plan id'
record starterPlanGuard PASS "Existing STARTER plan selected; maxUsers=$STARTER_MAX_USERS"

: > "$WORK_DIR/all-subscriptions.jsonl"; page=0
while :; do
  status="$(request GET "/api/platform/api/v1/executive/subscriptions/v2?page=$page&size=100" "$WORK_DIR/sub-page.json" cp)"; expect "$status" 200 "subscriptionsPage${page}"
  jq -c '.content[]?' "$WORK_DIR/sub-page.json" >> "$WORK_DIR/all-subscriptions.jsonl"
  totalPages="$(jq -r '.totalPages' "$WORK_DIR/sub-page.json")"
  [ "$page" -ge $((totalPages-1)) ] && break
  page=$((page+1)); [ "$page" -lt 100 ] || fail qaTenantSubscriptionGuard 'Subscription pagination safety limit exceeded'
done
jq -s '.' "$WORK_DIR/all-subscriptions.jsonl" > "$WORK_DIR/all-subscriptions.json"
QA_ACTIVE_COUNT="$(jq --arg tenant "$PROD_QA_TENANT_ID" '[.[]|select(.tenantId==$tenant and .status=="ACTIVE")]|length' "$WORK_DIR/all-subscriptions.json")"
[ "$QA_ACTIVE_COUNT" = 1 ] || fail qaTenantSubscriptionGuard "Dedicated QA tenant requires exactly one governed ACTIVE subscription before the final journey; found $QA_ACTIVE_COUNT"
SUB_ID="$(jq -r --arg tenant "$PROD_QA_TENANT_ID" '[.[]|select(.tenantId==$tenant and .status=="ACTIVE")][0].id' "$WORK_DIR/all-subscriptions.json")"
ACTIVE_PLAN_ID="$(jq -r --arg tenant "$PROD_QA_TENANT_ID" '[.[]|select(.tenantId==$tenant and .status=="ACTIVE")][0].planId' "$WORK_DIR/all-subscriptions.json")"
[ "$ACTIVE_PLAN_ID" = "$PLAN_ID" ] || fail qaTenantSubscriptionGuard 'Dedicated QA tenant ACTIVE subscription must use the existing STARTER plan'
record qaSubscriptionProvisioning PASS 'Existing governed ACTIVE STARTER subscription verified; final gate performs no subscription/catalog creation'
record starterPlanReuse PASS 'Existing STARTER catalog reused without mutation; plan sharing is allowed because the final gate performs no catalog writes'

# 4) Recalculate QA tenant entitlements only; never mutate the shared STARTER catalog.
status="$(request POST "/api/platform/api/v1/executive/tenants/$PROD_QA_TENANT_ID/entitlements/recalculate" "$WORK_DIR/recalc.json" cp '{}')"; expect "$status" 200 recalculateEntitlements
status="$(request GET "/api/platform/api/v1/executive/tenants/$PROD_QA_TENANT_ID/entitlements" "$WORK_DIR/ent-after.json" cp)"; expect "$status" 200 entitlementsAfter
jq -e '[.[]|select(.moduleCode=="WORKFLOW" and .moduleEnabled==true)]|length==1' "$WORK_DIR/ent-after.json" >/dev/null || fail entitlementsAfter 'STARTER did not resolve an active WORKFLOW entitlement for the QA tenant'
record entitlementGuard PASS 'WORKFLOW entitlement resolved from existing STARTER catalog; plan/catalog mutations NONE'

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
if [ -n "$EXISTING_DEF" ]; then
  status="$(request POST "/api/platform/api/v1/workflows/definitions/$EXISTING_DEF/validate" "$WORK_DIR/boundary-validate.json" qa '{}')"; expect "$status" 200 validateBoundary
else
  record validateBoundaryPrecondition PASS 'No pre-existing definition; boundary will be proven on final-gate draft'
fi

# 6) Ensure exactly three stable production QA user identities exist; create only missing identities.
status="$(request GET "/api/platform/api/v1/users?tenantId=$PROD_QA_TENANT_ID" "$WORK_DIR/users-before.json" qa)"; expect "$status" 200 qaUsersRead
EXISTING_USER_COUNT="$(jq 'length' "$WORK_DIR/users-before.json")"
MISSING_QA_USERS=0
for n in 1 2 3; do
  email="workflow-prod-qa${n}@qa.snad.invalid"
  count="$(jq --arg email "$email" '[.[]|select((.email|ascii_downcase)==($email|ascii_downcase))]|length' "$WORK_DIR/users-before.json")"
  [ "$count" -le 1 ] || fail threeQaUsers "Duplicate QA identity found for $email"
  [ "$count" = 1 ] || MISSING_QA_USERS=$((MISSING_QA_USERS+1))
done
[ $((EXISTING_USER_COUNT + MISSING_QA_USERS)) -le "$STARTER_MAX_USERS" ] || fail qaSeatGuard "STARTER seat capacity would be exceeded by the three QA identities"
record qaSeatGuard PASS "Existing users=$EXISTING_USER_COUNT missing fixed QA users=$MISSING_QA_USERS maxUsers=$STARTER_MAX_USERS"

USERS='[]'
for n in 1 2 3; do
  email="workflow-prod-qa${n}@qa.snad.invalid"; display="Workflow Production QA $n"
  count="$(jq --arg email "$email" '[.[]|select((.email|ascii_downcase)==($email|ascii_downcase))]|length' "$WORK_DIR/users-before.json")"
  if [ "$count" = 0 ]; then
    payload="$(jq -cn --arg email "$email" --arg display "$display" '{email:$email,displayName:$display,status:"ACTIVE"}')"
    status="$(request POST "/api/platform/api/v1/users?tenantId=$PROD_QA_TENANT_ID" "$WORK_DIR/user$n.json" qa "$payload")"; expect "$status" 201 "createQaUser${n}"
    uid="$(jq -r '.id // empty' "$WORK_DIR/user$n.json")"; user_status="$(jq -r '.status // empty' "$WORK_DIR/user$n.json")"
  else
    uid="$(jq -r --arg email "$email" '[.[]|select((.email|ascii_downcase)==($email|ascii_downcase))][0].id' "$WORK_DIR/users-before.json")"
    user_status="$(jq -r --arg email "$email" '[.[]|select((.email|ascii_downcase)==($email|ascii_downcase))][0].status' "$WORK_DIR/users-before.json")"
  fi
  [[ "$uid" =~ ^[0-9a-fA-F-]{36}$ ]] || fail "qaUser${n}" 'Invalid user id'
  [ "$user_status" = ACTIVE ] || fail "qaUser${n}" "QA user $n is not ACTIVE"
  USERS="$(jq -cn --argjson a "$USERS" --arg id "$uid" --arg email "$email" --arg display "$display" '$a + [{id:$id,email:$email,displayName:$display}]')"
done
jq -e 'map(.id)|unique|length==3' <<<"$USERS" >/dev/null || fail threeQaUsers 'QA user identities are not three distinct users'
record threeQaUsers PASS '3 stable isolated production QA user identities verified (created only when absent)'

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
record validateBoundary PASS 'Tenant B token accepted by WORKFLOW.VALIDATE on final-gate draft'
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
