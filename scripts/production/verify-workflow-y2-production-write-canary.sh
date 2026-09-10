#!/usr/bin/env bash
set -Eeuo pipefail

: "${Y2_CANARY_BASE_URL:?Y2_CANARY_BASE_URL is required}"
: "${Y2_CANARY_ADMIN_EMAIL:?Y2_CANARY_ADMIN_EMAIL is required}"
: "${Y2_CANARY_ADMIN_PASSWORD:?Y2_CANARY_ADMIN_PASSWORD is required}"
: "${DEPLOYED_COMMIT_SHA:?DEPLOYED_COMMIT_SHA is required}"

BASE_URL="${Y2_CANARY_BASE_URL%/}"
EVIDENCE_FILE="${Y2_CANARY_EVIDENCE_FILE:-workflow-y2-production-write-canary.json}"
WORK_DIR="$(mktemp -d)"
CHECKS_FILE="$WORK_DIR/checks.jsonl"
: > "$CHECKS_FILE"
trap 'rm -rf "$WORK_DIR"' EXIT

STAGE="initialization"
DEF_ID=""
FAMILY_ID=""
DEF_VERSION=""
PUB_STATE=""
ENGINE_GENERATION=""
INSTANCE_ID=""
INSTANCE_STATUS=""
INSTANCE_WORKFLOW_VERSION=""
INSTANCE_DEFINITION_VERSION_ID=""
IMMUTABILITY_STEP_STATUS=""
IMMUTABILITY_TRANSITION_STATUS=""
WRITING_EVIDENCE=false

for secret in "$Y2_CANARY_ADMIN_EMAIL" "$Y2_CANARY_ADMIN_PASSWORD"; do
  echo "::add-mask::$secret"
done

record_check() {
  local label="$1" status="$2"
  jq -cn --arg route "$label" --arg status "$status" \
    '{route:$route,httpStatus:($status|tonumber)}' >> "$CHECKS_FILE"
}

write_evidence() {
  local result="$1" failure_stage="${2:-}"
  "$WRITING_EVIDENCE" && return 0
  WRITING_EVIDENCE=true
  local checks='[]'
  if [ -s "$CHECKS_FILE" ]; then
    checks="$(jq -s '.' "$CHECKS_FILE")"
  fi
  jq -n \
    --arg schema "snad.workflow-y2.production-write-canary.v1" \
    --arg result "$result" \
    --arg failureStage "$failure_stage" \
    --arg releaseSha "$DEPLOYED_COMMIT_SHA" \
    --arg definitionId "$DEF_ID" \
    --arg definitionFamilyId "$FAMILY_ID" \
    --arg definitionVersion "$DEF_VERSION" \
    --arg publicationState "$PUB_STATE" \
    --arg engineGeneration "$ENGINE_GENERATION" \
    --arg instanceId "$INSTANCE_ID" \
    --arg instanceStatus "$INSTANCE_STATUS" \
    --arg instanceWorkflowVersion "$INSTANCE_WORKFLOW_VERSION" \
    --arg instanceDefinitionVersionId "$INSTANCE_DEFINITION_VERSION_ID" \
    --arg immutabilityStep "$IMMUTABILITY_STEP_STATUS" \
    --arg immutabilityTransition "$IMMUTABILITY_TRANSITION_STATUS" \
    --argjson checks "$checks" '
      {
        schema:$schema,
        releaseSha:$releaseSha,
        result:$result,
        failureStage:(if $failureStage == "" then null else $failureStage end),
        definition:{
          id:(if $definitionId == "" then null else $definitionId end),
          definitionFamilyId:(if $definitionFamilyId == "" then null else $definitionFamilyId end),
          version:(if $definitionVersion == "" then null else ($definitionVersion|tonumber) end),
          publicationState:(if $publicationState == "" then null else $publicationState end),
          engineGeneration:(if $engineGeneration == "" then null else $engineGeneration end)
        },
        instance:{
          id:(if $instanceId == "" then null else $instanceId end),
          status:(if $instanceStatus == "" then null else $instanceStatus end),
          workflowVersion:(if $instanceWorkflowVersion == "" then null else ($instanceWorkflowVersion|tonumber) end),
          definitionVersionId:(if $instanceDefinitionVersionId == "" then null else $instanceDefinitionVersionId end)
        },
        immutability:{
          addStepHttpStatus:(if $immutabilityStep == "" then null else ($immutabilityStep|tonumber) end),
          addTransitionHttpStatus:(if $immutabilityTransition == "" then null else ($immutabilityTransition|tonumber) end)
        },
        checks:$checks
      }
    ' > "$EVIDENCE_FILE"
  WRITING_EVIDENCE=false
}

fail() {
  local stage="$1" message="$2"
  echo "::error::$message"
  write_evidence "FAIL" "$stage"
  exit 1
}

on_err() {
  local rc=$?
  trap - ERR
  if [ "$WRITING_EVIDENCE" != "true" ]; then
    write_evidence "FAIL" "${STAGE:-unexpected-error}" || true
  fi
  exit "$rc"
}
trap on_err ERR

sha_regex='^[0-9a-f]{40}$'
[[ "$DEPLOYED_COMMIT_SHA" =~ $sha_regex ]] || fail "input-validation" "DEPLOYED_COMMIT_SHA must be a full lowercase 40-hex SHA"
case "$BASE_URL" in
  https://*) ;;
  http://*) [ "${Y2_CANARY_ALLOW_HTTP:-false}" = "true" ] || fail "input-validation" "HTTP is forbidden for production canary" ;;
  *) fail "input-validation" "Y2_CANARY_BASE_URL must use HTTPS" ;;
esac

request() {
  local method="$1" path="$2" output="$3" auth_mode="$4" payload="${5:-}"
  local -a args=(--silent --show-error --location --connect-timeout 10 --max-time 60 --request "$method" --output "$output" --write-out '%{http_code}' --header 'Accept: application/json')
  if [ "$auth_mode" = "auth" ]; then
    args+=(--header "Authorization: Bearer $TOKEN")
  fi
  if [ -n "$payload" ]; then
    args+=(--header 'Content-Type: application/json' --data "$payload")
  fi
  local status curl_rc
  set +e
  status="$(curl "${args[@]}" "$BASE_URL$path")"
  curl_rc=$?
  set -e
  if [ "$curl_rc" -ne 0 ]; then printf '000'; else printf '%s' "$status"; fi
}

expect_status() {
  local actual="$1" expected="$2" label="$3"
  record_check "$label" "$actual"
  [ "$actual" = "$expected" ] || fail "$STAGE" "$label returned HTTP ${actual:-000}; expected $expected"
}

STAGE="health"
health_ok=false
for attempt in $(seq 1 18); do
  status="$(request GET '/actuator/health' "$WORK_DIR/health.json" none)"
  if [ "$status" = "200" ] && jq -e '.status == "UP"' "$WORK_DIR/health.json" >/dev/null 2>&1; then
    record_check "health" "$status"
    health_ok=true
    break
  fi
  sleep 5
done
[ "$health_ok" = "true" ] || fail "$STAGE" "Production health did not become UP"

STAGE="login"
login_payload="$(jq -cn --arg email "$Y2_CANARY_ADMIN_EMAIL" --arg password "$Y2_CANARY_ADMIN_PASSWORD" '{email:$email,password:$password}')"
status="$(request POST '/api/v1/auth/login' "$WORK_DIR/login.json" none "$login_payload")"
expect_status "$status" 200 "login"
TOKEN="$(jq -r '.accessToken // empty' "$WORK_DIR/login.json")"
[ -n "$TOKEN" ] || fail "$STAGE" "Login returned no access token"
echo "::add-mask::$TOKEN"

CANARY_CODE="Y2-PROD-CANARY-${DEPLOYED_COMMIT_SHA:0:12}"

STAGE="workflow-access"
status="$(request GET '/api/v1/workflows/definitions?limit=200' "$WORK_DIR/access.json" auth)"
expect_status "$status" 200 "workflowDefinitionsRead"
jq -e 'type == "array"' "$WORK_DIR/access.json" >/dev/null || fail "$STAGE" "Workflow definitions read did not return an array"
DEFINITION_COUNT="$(jq 'length' "$WORK_DIR/access.json")"
[ "$DEFINITION_COUNT" -lt 200 ] || fail "preexisting-canary-scan-incomplete" "Cannot prove canary uniqueness because the workflow definition list reached the 200-item API limit"
if jq -e --arg code "$CANARY_CODE" 'any(.[]; .code == $code)' "$WORK_DIR/access.json" >/dev/null; then
  fail "preexisting-canary" "A Workflow Y2 production canary already exists for release ${DEPLOYED_COMMIT_SHA:0:12}"
fi

STAGE="definition-create"
create_payload="$(jq -cn --arg code "$CANARY_CODE" '{code:$code,name:"Y2 Production Write Canary",description:"Isolated permanent production canary evidence",module:"GENERAL",triggerType:"MANUAL"}')"
status="$(request POST '/api/v1/workflows/definitions' "$WORK_DIR/definition.json" auth "$create_payload")"
expect_status "$status" 200 "definitionCreate"
DEF_ID="$(jq -r '.id // empty' "$WORK_DIR/definition.json")"
FAMILY_ID="$(jq -r '.definitionFamilyId // empty' "$WORK_DIR/definition.json")"
DEF_VERSION="$(jq -r '.version // empty' "$WORK_DIR/definition.json")"
VERSION_LOCK="$(jq -r '.versionLock // empty' "$WORK_DIR/definition.json")"
PUB_STATE="$(jq -r '.publicationState // empty' "$WORK_DIR/definition.json")"
ENGINE_GENERATION="$(jq -r '.engineGeneration // empty' "$WORK_DIR/definition.json")"
[[ "$DEF_ID" =~ ^[0-9a-fA-F-]{36}$ ]] || fail "$STAGE" "Definition id is missing or invalid"
[ "$DEF_VERSION" = "1" ] || fail "$STAGE" "Canary definition must start at version 1"
[ "$VERSION_LOCK" = "0" ] || fail "$STAGE" "Canary definition must start at versionLock 0"
[ "$PUB_STATE" = "DRAFT" ] || fail "$STAGE" "Canary definition must start DRAFT"
[ "$ENGINE_GENERATION" = "LEGACY" ] || fail "$STAGE" "Draft must start LEGACY before Y2 publish"

STAGE="graph-build"
start_payload='{"stepKey":"start","name":"Start","stepType":"START","sequenceOrder":1,"configuration":"{}"}'
status="$(request POST "/api/v1/workflows/definitions/$DEF_ID/steps" "$WORK_DIR/start-step.json" auth "$start_payload")"
expect_status "$status" 200 "startStepCreate"
START_ID="$(jq -r '.id // empty' "$WORK_DIR/start-step.json")"
end_payload='{"stepKey":"end","name":"End","stepType":"END","sequenceOrder":2,"configuration":"{}"}'
status="$(request POST "/api/v1/workflows/definitions/$DEF_ID/steps" "$WORK_DIR/end-step.json" auth "$end_payload")"
expect_status "$status" 200 "endStepCreate"
END_ID="$(jq -r '.id // empty' "$WORK_DIR/end-step.json")"
transition_payload="$(jq -cn --arg from "$START_ID" --arg to "$END_ID" '{fromStepId:$from,toStepId:$to,transitionKey:"finish",outcome:"SUCCESS",priority:10}')"
status="$(request POST "/api/v1/workflows/definitions/$DEF_ID/transitions" "$WORK_DIR/transition.json" auth "$transition_payload")"
expect_status "$status" 200 "transitionCreate"

STAGE="validate"
status="$(request POST "/api/v1/workflows/definitions/$DEF_ID/validate" "$WORK_DIR/validate.json" auth '{}')"
expect_status "$status" 200 "definitionValidate"
jq -e '.valid == true and (.errors|type == "array") and (.errors|length == 0)' "$WORK_DIR/validate.json" >/dev/null || fail "$STAGE" "Definition validation did not pass cleanly"

STAGE="simulate"
status="$(request POST "/api/v1/workflows/definitions/$DEF_ID/simulate" "$WORK_DIR/simulate.json" auth '{}')"
expect_status "$status" 200 "definitionSimulate"
jq -e '.valid == true and .simulated == true' "$WORK_DIR/simulate.json" >/dev/null || fail "$STAGE" "Definition simulation did not pass"

STAGE="publish"
publish_payload="$(jq -cn --argjson expected "$VERSION_LOCK" '{expectedVersion:$expected}')"
status="$(request POST "/api/v1/workflows/definitions/$DEF_ID/publish" "$WORK_DIR/publish.json" auth "$publish_payload")"
expect_status "$status" 200 "definitionPublish"
PUB_STATE="$(jq -r '.publicationState // empty' "$WORK_DIR/publish.json")"
ENGINE_GENERATION="$(jq -r '.engineGeneration // empty' "$WORK_DIR/publish.json")"
DEF_VERSION="$(jq -r '.version // empty' "$WORK_DIR/publish.json")"
[ "$PUB_STATE" = "PUBLISHED" ] || fail "$STAGE" "Published definition did not report PUBLISHED"
[ "$ENGINE_GENERATION" = "Y2" ] || fail "$STAGE" "Published definition did not report Y2"
[ "$DEF_VERSION" = "1" ] || fail "$STAGE" "Published canary version changed unexpectedly"

STAGE="instance-start"
BUSINESS_ID="$(python3 -c 'import uuid; print(uuid.uuid4())')"
CORRELATION_ID="$(python3 -c 'import uuid; print(uuid.uuid4())')"
instance_payload="$(jq -cn --arg def "$DEF_ID" --arg business "$BUSINESS_ID" --arg correlation "$CORRELATION_ID" '{workflowDefinitionId:$def,businessEntityType:"PRODUCTION_CANARY",businessEntityId:$business,correlationId:$correlation}')"
status="$(request POST '/api/v1/workflows/instances' "$WORK_DIR/instance.json" auth "$instance_payload")"
expect_status "$status" 200 "instanceStart"
INSTANCE_ID="$(jq -r '.id // empty' "$WORK_DIR/instance.json")"
INSTANCE_STATUS="$(jq -r '.status // empty' "$WORK_DIR/instance.json")"
INSTANCE_WORKFLOW_VERSION="$(jq -r '.workflowVersion // empty' "$WORK_DIR/instance.json")"
INSTANCE_DEFINITION_VERSION_ID="$(jq -r '.definitionVersionId // empty' "$WORK_DIR/instance.json")"
[ "$INSTANCE_STATUS" = "COMPLETED" ] || fail "$STAGE" "START→END canary instance did not complete"
[ "$INSTANCE_WORKFLOW_VERSION" = "1" ] || fail "$STAGE" "Canary instance did not pin workflow version 1"
[ "$INSTANCE_DEFINITION_VERSION_ID" = "$DEF_ID" ] || fail "$STAGE" "Canary instance did not pin the published definition id"
jq -e --arg family "$FAMILY_ID" '.engineGeneration == "Y2" and .definitionFamilyId == $family' "$WORK_DIR/instance.json" >/dev/null || fail "$STAGE" "Canary instance Y2 family pin is invalid"

STAGE="published-immutability"
extra_step_payload='{"stepKey":"forbidden-extra","name":"Forbidden Extra","stepType":"ACTION","sequenceOrder":99,"configuration":"{}"}'
IMMUTABILITY_STEP_STATUS="$(request POST "/api/v1/workflows/definitions/$DEF_ID/steps" "$WORK_DIR/immutability-step.json" auth "$extra_step_payload")"
record_check "publishedAddStepRejected" "$IMMUTABILITY_STEP_STATUS"
[ "$IMMUTABILITY_STEP_STATUS" = "409" ] || fail "$STAGE" "Published definition addStep must fail closed with HTTP 409"
extra_transition_payload="$(jq -cn --arg from "$START_ID" --arg to "$END_ID" '{fromStepId:$from,toStepId:$to,transitionKey:"forbidden-extra",outcome:"SUCCESS",priority:1}')"
IMMUTABILITY_TRANSITION_STATUS="$(request POST "/api/v1/workflows/definitions/$DEF_ID/transitions" "$WORK_DIR/immutability-transition.json" auth "$extra_transition_payload")"
record_check "publishedAddTransitionRejected" "$IMMUTABILITY_TRANSITION_STATUS"
[ "$IMMUTABILITY_TRANSITION_STATUS" = "409" ] || fail "$STAGE" "Published definition addTransition must fail closed with HTTP 409"

STAGE="reload"
status="$(request GET "/api/v1/workflows/definitions/$DEF_ID" "$WORK_DIR/reload.json" auth)"
expect_status "$status" 200 "publishedDefinitionReload"
jq -e '.publicationState == "PUBLISHED" and .engineGeneration == "Y2" and .version == 1' "$WORK_DIR/reload.json" >/dev/null || fail "$STAGE" "Published definition changed after immutability probes"

STAGE="complete"
write_evidence "PASS" ""
echo "Workflow Y2 production write canary: PASSED"
