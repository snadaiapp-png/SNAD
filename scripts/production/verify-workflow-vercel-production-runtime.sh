#!/usr/bin/env bash
set -Eeuo pipefail

: "${VERCEL_BASE_URL:?VERCEL_BASE_URL is required}"
: "${VERCEL_EXPECTED_SHA:?VERCEL_EXPECTED_SHA is required}"
: "${WORKFLOW_RUNTIME_ADMIN_EMAIL:?WORKFLOW_RUNTIME_ADMIN_EMAIL is required}"
: "${WORKFLOW_RUNTIME_ADMIN_PASSWORD:?WORKFLOW_RUNTIME_ADMIN_PASSWORD is required}"
: "${WORKFLOW_RUNTIME_TENANT_ID:?WORKFLOW_RUNTIME_TENANT_ID is required}"

BASE_URL="${VERCEL_BASE_URL%/}"
VERCEL_RELEASE_ATTEMPTS="${WORKFLOW_VERCEL_RELEASE_ATTEMPTS:-40}"
VERCEL_RELEASE_DELAY_SECONDS="${WORKFLOW_VERCEL_RELEASE_DELAY_SECONDS:-20}"
BACKEND_STATUS_ATTEMPTS="${WORKFLOW_VERCEL_BACKEND_STATUS_ATTEMPTS:-4}"
BACKEND_STATUS_DELAY_SECONDS="${WORKFLOW_VERCEL_BACKEND_STATUS_DELAY_SECONDS:-10}"
EVIDENCE_FILE="${WORKFLOW_VERCEL_EVIDENCE_FILE:-workflow-vercel-production-runtime.json}"
WORK_DIR="$(mktemp -d)"
CHECKS_FILE="$WORK_DIR/checks.jsonl"
: > "$CHECKS_FILE"
trap 'rm -rf "$WORK_DIR"' EXIT

for secret in "$WORKFLOW_RUNTIME_ADMIN_EMAIL" "$WORKFLOW_RUNTIME_ADMIN_PASSWORD" "$WORKFLOW_RUNTIME_TENANT_ID"; do
  echo "::add-mask::$secret"
done

fail() {
  local stage="$1" message="$2"
  echo "::error::$message"
  jq -n \
    --arg schema "snad.workflow.vercel-production-runtime.v1" \
    --arg result "FAIL" \
    --arg failureStage "$stage" \
    --arg releaseSha "$VERCEL_EXPECTED_SHA" \
    --arg baseUrl "$BASE_URL" \
    --slurpfile checks "$CHECKS_FILE" \
    '{schema:$schema,result:$result,failureStage:$failureStage,releaseSha:$releaseSha,transport:"vercel-bff",baseUrl:$baseUrl,checks:$checks}' \
    > "$EVIDENCE_FILE" || true
  exit 1
}

record_check() {
  local label="$1" status="$2" result="${3:-PASS}"
  jq -cn --arg route "$label" --arg status "$status" --arg result "$result" \
    '{route:$route,httpStatus:($status|tonumber),result:$result}' >> "$CHECKS_FILE"
}

request() {
  local method="$1" url="$2" output="$3"
  shift 3
  local status rc
  set +e
  status="$(curl --silent --show-error --location \
    --request "$method" \
    --connect-timeout 15 \
    --max-time 140 \
    --output "$output" \
    --write-out '%{http_code}' \
    "$@" \
    "$url")"
  rc=$?
  set -e
  if [ "$rc" -ne 0 ]; then printf '000'; else printf '%s' "$status"; fi
}

expect_200() {
  local status="$1" label="$2"
  if [ "$status" = "200" ]; then
    record_check "$label" "$status" "PASS"
    return 0
  fi
  record_check "$label" "$status" "FAIL"
  fail "$label" "$label returned HTTP ${status:-000}; expected 200"
}

[[ "$VERCEL_EXPECTED_SHA" =~ ^[0-9a-f]{40}$ ]] || fail "input-validation" "VERCEL_EXPECTED_SHA must be a full lowercase SHA"
case "$BASE_URL" in
  https://*) ;;
  http://127.0.0.1:*|http://localhost:*)
    [ "${WORKFLOW_VERCEL_ALLOW_HTTP:-false}" = "true" ] || fail "input-validation" "HTTP is allowed only for explicit local tests"
    ;;
  *) fail "input-validation" "VERCEL_BASE_URL must use HTTPS" ;;
esac

STAGE="vercel-release-identity"
release_ready=false
for attempt in $(seq 1 "$VERCEL_RELEASE_ATTEMPTS"); do
  status="$(request GET "$BASE_URL/api/system/release" "$WORK_DIR/release.json" --header 'Accept: application/json')"
  if [ "$status" = "200" ] && jq -e --arg sha "$VERCEL_EXPECTED_SHA" '
    .service == "SNAD Web"
    and .commitSha == $sha
    and (.environment == "production" or .environment == "prod")
  ' "$WORK_DIR/release.json" >/dev/null 2>&1; then
    record_check "vercelReleaseIdentity" "$status"
    release_ready=true
    break
  fi
  sleep "$VERCEL_RELEASE_DELAY_SECONDS"
done
[ "$release_ready" = "true" ] || fail "$STAGE" "Vercel production release identity did not converge to exact current main SHA"

STAGE="vercel-backend-status"
backend_ready=false
for attempt in $(seq 1 "$BACKEND_STATUS_ATTEMPTS"); do
  status="$(request GET "$BASE_URL/api/system/backend-status" "$WORK_DIR/backend-status.json" --header 'Accept: application/json')"
  if [ "$status" = "200" ] && jq -e '.configured == true and .reachable == true and .statusCode == 200' "$WORK_DIR/backend-status.json" >/dev/null 2>&1; then
    record_check "vercelBackendStatus" "$status" "PASS"
    backend_ready=true
    break
  fi
  if [ "$attempt" -lt "$BACKEND_STATUS_ATTEMPTS" ]; then
    sleep "$BACKEND_STATUS_DELAY_SECONDS"
  fi
done
if [ "$backend_ready" != "true" ]; then
  record_check "vercelBackendStatus" "${status:-000}" "FAIL"
  fail "$STAGE" "Vercel BFF cannot prove the production backend reachable after bounded convergence attempts"
fi

STAGE="workflow-route"
status="$(request GET "$BASE_URL/workflow" "$WORK_DIR/workflow.html" --header 'Accept: text/html')"
expect_200 "$status" "workflowRoute"

STAGE="login"
login_payload="$(jq -cn \
  --arg email "$WORKFLOW_RUNTIME_ADMIN_EMAIL" \
  --arg password "$WORKFLOW_RUNTIME_ADMIN_PASSWORD" \
  --arg tenantId "$WORKFLOW_RUNTIME_TENANT_ID" \
  '{email:$email,password:$password,tenantId:$tenantId}')"
status="$(request POST "$BASE_URL/api/platform/api/v1/auth/login" "$WORK_DIR/login.json" \
  --header 'Accept: application/json' \
  --header 'Content-Type: application/json' \
  --data "$login_payload")"
expect_200 "$status" "vercelBffLogin"
TOKEN="$(jq -r '.accessToken // empty' "$WORK_DIR/login.json")"
[ -n "$TOKEN" ] || fail "$STAGE" "Vercel BFF login returned no access token"
echo "::add-mask::$TOKEN"
AUTH_HEADER="Authorization: Bearer $TOKEN"

STAGE="auth-me"
status="$(request GET "$BASE_URL/api/platform/api/v1/auth/me" "$WORK_DIR/me.json" \
  --header 'Accept: application/json' --header "$AUTH_HEADER")"
expect_200 "$status" "authMe"
jq -e --arg email "$WORKFLOW_RUNTIME_ADMIN_EMAIL" '.email == $email and (.capabilities | type == "array")' "$WORK_DIR/me.json" >/dev/null \
  || fail "$STAGE" "Authenticated Vercel BFF identity contract is invalid"

STAGE="definitions"
status="$(request GET "$BASE_URL/api/platform/api/v1/workflows/definitions?limit=50" "$WORK_DIR/definitions.json" \
  --header 'Accept: application/json' --header "$AUTH_HEADER")"
expect_200 "$status" "workflowDefinitions"
jq -e 'type == "array"' "$WORK_DIR/definitions.json" >/dev/null || fail "$STAGE" "Workflow definitions must return an array"

STAGE="module-catalog"
status="$(request GET "$BASE_URL/api/platform/api/v1/workflows/catalog/modules" "$WORK_DIR/catalog.json" \
  --header 'Accept: application/json' --header "$AUTH_HEADER")"
case "$status" in
  200)
    if jq -e 'type == "object" and (.modules | type == "array")' "$WORK_DIR/catalog.json" >/dev/null 2>&1; then
      record_check "workflowModuleCatalog" "$status" "PASS"
    else
      record_check "workflowModuleCatalog" "$status" "FAIL"
      fail "workflowModuleCatalog" "Workflow module catalog 200 response does not match the expected object contract"
    fi
    ;;
  403)
    if jq -e '
      .status == 403
      and .error == "Forbidden"
      and ((.message // "") | contains("WORKFLOW_MODULE_NOT_ENTITLED"))
    ' "$WORK_DIR/catalog.json" >/dev/null 2>&1; then
      record_check "workflowModuleCatalog" "$status" "PASS"
    else
      record_check "workflowModuleCatalog" "$status" "FAIL"
      fail "workflowModuleCatalog" "Workflow module catalog returned 403 without the explicit WORKFLOW_MODULE_NOT_ENTITLED contract"
    fi
    ;;
  *)
    record_check "workflowModuleCatalog" "${status:-000}" "FAIL"
    fail "workflowModuleCatalog" "Workflow module catalog returned HTTP ${status:-000}; expected 200 or the explicit entitlement-denied 403 contract"
    ;;
esac

STAGE="instances"
status="$(request GET "$BASE_URL/api/platform/api/v1/workflows/instances?limit=50" "$WORK_DIR/instances.json" \
  --header 'Accept: application/json' --header "$AUTH_HEADER")"
expect_200 "$status" "workflowInstances"
jq -e 'type == "array"' "$WORK_DIR/instances.json" >/dev/null || fail "$STAGE" "Workflow instances must return an array"

STAGE="monitoring"
status="$(request GET "$BASE_URL/api/platform/api/v1/workflows/monitoring/health" "$WORK_DIR/monitoring.json" \
  --header 'Accept: application/json' --header "$AUTH_HEADER")"
expect_200 "$status" "workflowMonitoringHealth"
jq -e 'type == "object"' "$WORK_DIR/monitoring.json" >/dev/null || fail "$STAGE" "Workflow monitoring health must return an object"

STAGE="notifications"
status="$(request GET "$BASE_URL/api/platform/api/v1/workflows/notifications" "$WORK_DIR/notifications.json" \
  --header 'Accept: application/json' --header "$AUTH_HEADER")"
expect_200 "$status" "workflowNotifications"
jq -e 'type == "array"' "$WORK_DIR/notifications.json" >/dev/null || fail "$STAGE" "Workflow notifications must return an array"

STAGE="side-effect-free-post"
DEF_ID="$(jq -r '[.[] | select(.publicationState == "PUBLISHED")][0].id // .[0].id // empty' "$WORK_DIR/definitions.json")"
if [ -n "$DEF_ID" ]; then
  status="$(request POST "$BASE_URL/api/platform/api/v1/workflows/definitions/$DEF_ID/validate" "$WORK_DIR/validate.json" \
    --header 'Accept: application/json' --header 'Content-Type: application/json' --header "$AUTH_HEADER" --data '{}')"
  expect_200 "$status" "workflowValidateViaVercel"
  jq -e '(.valid | type == "boolean") and (.errors | type == "array")' "$WORK_DIR/validate.json" >/dev/null \
    || fail "$STAGE" "Workflow validate response is invalid"

  status="$(request POST "$BASE_URL/api/platform/api/v1/workflows/definitions/$DEF_ID/simulate" "$WORK_DIR/simulate.json" \
    --header 'Accept: application/json' --header 'Content-Type: application/json' --header "$AUTH_HEADER" --data '{}')"
  expect_200 "$status" "workflowSimulateViaVercel"
  jq -e '(.valid | type == "boolean") and (.simulated | type == "boolean")' "$WORK_DIR/simulate.json" >/dev/null \
    || fail "$STAGE" "Workflow simulate response is invalid"
else
  fail "$STAGE" "No Workflow definition exists to prove the Vercel BFF POST path without mutating production state"
fi

jq -s '.' "$CHECKS_FILE" > "$WORK_DIR/checks.json"
jq -n \
  --arg schema "snad.workflow.vercel-production-runtime.v1" \
  --arg result "PASS" \
  --arg releaseSha "$VERCEL_EXPECTED_SHA" \
  --arg baseUrl "$BASE_URL" \
  --arg workflowDefinitionId "$DEF_ID" \
  --slurpfile checks "$WORK_DIR/checks.json" \
  '{schema:$schema,result:$result,failureStage:null,releaseSha:$releaseSha,transport:"vercel-bff",baseUrl:$baseUrl,workflowDefinitionId:$workflowDefinitionId,checks:$checks[0]}' \
  > "$EVIDENCE_FILE"

jq -e '.result == "PASS" and (.checks | length >= 9)' "$EVIDENCE_FILE" >/dev/null || fail "evidence" "Vercel Workflow runtime evidence did not resolve to PASS"

echo "Workflow Vercel production runtime certification: PASSED"
