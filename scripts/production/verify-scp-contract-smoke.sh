#!/usr/bin/env bash
set -euo pipefail

# ─────────────────────────────────────────────────────────────────────────────
# SCP production contract smoke (incident prevention gate).
#
# Authenticates a control-plane smoke identity and proves that every additive
# executive /subscription-control-plane read route required by the deployed
# web client answers 200 with a JSON contract — never 404 (route absent from
# the deployed backend artifact), 405, an unexpected 400, or HTML masquerading
# as API JSON.
#
# This gate would have failed the release that shipped the SCP frontend
# against a backend image predating the control-plane controllers.
#
# Read-only by construction: no destructive or mutating call is made; mutation
# surfaces are covered by automated integration tests in CI.
#
# Required environment:
#   SCP_SMOKE_BASE_URL            backend base URL (or the Vercel BFF base,
#                                 in which case endpoints are prefixed with
#                                 /api/platform automatically)
#   CONTROL_PLANE_ADMIN_EMAIL     smoke identity (repo-wide convention)
#   CONTROL_PLANE_ADMIN_PASSWORD
#   CONTROL_PLANE_TENANT_ID
#   DEPLOYED_COMMIT_SHA           40-hex release SHA for the evidence file
# Optional environment:
#   SCP_SMOKE_TENANT_ID           run tenant-dependent usage read when set
#   SCP_SMOKE_EVIDENCE_FILE       output path (default scp-contract-smoke.json)
# Never prints secret values.
# ─────────────────────────────────────────────────────────────────────────────

: "${SCP_SMOKE_BASE_URL:?SCP_SMOKE_BASE_URL is required}"
: "${CONTROL_PLANE_ADMIN_EMAIL:?CONTROL_PLANE_ADMIN_EMAIL is required}"
: "${CONTROL_PLANE_ADMIN_PASSWORD:?CONTROL_PLANE_ADMIN_PASSWORD is required}"
: "${CONTROL_PLANE_TENANT_ID:?CONTROL_PLANE_TENANT_ID is required}"
: "${DEPLOYED_COMMIT_SHA:?DEPLOYED_COMMIT_SHA is required}"

BASE_URL="${SCP_SMOKE_BASE_URL%/}"
EVIDENCE_FILE="${SCP_SMOKE_EVIDENCE_FILE:-scp-contract-smoke.json}"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

for value in "$CONTROL_PLANE_ADMIN_EMAIL" "$CONTROL_PLANE_ADMIN_PASSWORD" "$CONTROL_PLANE_TENANT_ID"; do
  echo "::add-mask::$value"
done

sha_regex='^[0-9a-f]{40}$'
[[ "$DEPLOYED_COMMIT_SHA" =~ $sha_regex ]] || {
  echo "::error::DEPLOYED_COMMIT_SHA is not a full lowercase SHA."
  exit 1
}

# The BFF route handler only forwards /api/v1/* and /api/v2/*, mounted under
# /api/platform. Detect a BFF base and keep the canonical backend paths.
case "$BASE_URL" in
  */api/platform)
    FRONTEND_MODE=true
    ;;
  *)
    FRONTEND_MODE=false
    ;;
esac

request() {
  local method="$1"
  local url="$2"
  local output="$3"
  shift 3

  local status
  local curl_status
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

expect_contract() {
  local actual="$1"
  local label="$2"
  local output="$3"
  local expectation="$4"   # "object" | "page"

  # Forbidden routing failures — the exact incident signature.
  case "$actual" in
    404)
      echo "::error::$label returned HTTP 404 — the deployed backend artifact is missing this SCP route (deployment drift)."
      exit 1
      ;;
    405)
      echo "::error::$label returned HTTP 405 — the deployed backend artifact does not accept this verb."
      exit 1
      ;;
    400)
      echo "::error::$label returned HTTP 400 — unexpected contract violation (e.g. route shadowing by a parameterised path)."
      exit 1
      ;;
    200)
      ;;
    *)
      echo "::error::$label returned HTTP ${actual:-000}; expected 200."
      exit 1
      ;;
  esac

  # application/json, never HTML masquerading as an API response.
  if ! jq -e . "$output" >/dev/null 2>&1; then
    echo "::error::$label returned a non-JSON body (possible HTML error page)."
    exit 1
  fi

  if [ "$expectation" = "page" ]; then
    jq -e '
      (.content | type == "array") and
      (.page | type == "number") and
      (.size | type == "number") and
      (.totalElements | type == "number") and
      (.totalPages | type == "number")
    ' "$output" >/dev/null || {
      echo "::error::$label does not honour the PageResponse contract."
      exit 1
    }
  else
    jq -e 'type == "object"' "$output" >/dev/null || {
      echo "::error::$label must return a JSON object."
      exit 1
    }
  fi

  echo "$label: PASS"
}

# ── smoke identity ───────────────────────────────────────────────────────────

login_payload="$(jq -n \
  --arg address "$CONTROL_PLANE_ADMIN_EMAIL" \
  --arg pass "$CONTROL_PLANE_ADMIN_PASSWORD" \
  --arg tenant "$CONTROL_PLANE_TENANT_ID" \
  '{email:$address,password:$pass,tenantId:$tenant}')"

login_status="$(request POST "$BASE_URL/api/v1/auth/login" "$WORK_DIR/login.json" \
  --header 'Content-Type: application/json' \
  --data "$login_payload")"
[ "$login_status" = "200" ] || {
  echo "::error::Smoke identity login returned HTTP ${login_status:-000}; expected 200."
  exit 1
}
TOKEN="$(jq -r '.accessToken // empty' "$WORK_DIR/login.json")"
test -n "$TOKEN" || { echo "::error::Smoke identity login did not return an access token."; exit 1; }
echo "::add-mask::$TOKEN"

AUTH_HEADER="Authorization: Bearer $TOKEN"

# ── SCP contract matrix (read-only) ─────────────────────────────────────────

declare -a LABELS=()
declare -a STATUSES=()

check() {
  local label="$1"
  local expectation="$2"
  local path="$3"
  local output="$WORK_DIR/${label//[^a-zA-Z0-9]/_}.json"

  local status
  status="$(request GET "$BASE_URL$path" "$output" --header "$AUTH_HEADER" \
    --header 'Accept: application/json')"
  expect_contract "$status" "$label" "$output" "$expectation"
  LABELS+=("$label")
  STATUSES+=("$status")
}

check "overview"            "object" "/api/v1/executive/overview"
check "accessCheckV2"       "object" "/api/v1/executive/access-check/v2"
check "applications"        "object" "/api/v1/executive/applications?availableOnly=false"
check "tenantsV2"           "page"   "/api/v1/executive/tenants/v2?page=0&size=1"
check "subscriptionsV2"     "page"   "/api/v1/executive/subscriptions/v2?page=0&size=1"
check "provisioningJobs"    "object" "/api/v1/executive/provisioning/jobs"
check "auditV2"             "page"   "/api/v1/executive/audit/v2?page=0&size=1"

# access-check/v2 must advertise an explicit capability map.
jq -e '(.authenticated | type == "boolean") and (.capabilities | type == "object")' \
  "$WORK_DIR/accessCheckV2.json" >/dev/null || {
  echo "::error::access-check/v2 does not honour the AccessCheckV2 contract."
  exit 1
}

# overview must expose the server-computed metric schema (never fake data).
jq -e '(.totalTenants | type == "number") and (.generatedAt | type == "string")' \
  "$WORK_DIR/overview.json" >/dev/null || {
  echo "::error::overview does not honour the ScpOverview schema."
  exit 1
}

# Tenant-dependent read when a safe smoke tenant is provided.
if [ -n "${SCP_SMOKE_TENANT_ID:-}" ]; then
  uuid_regex='^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$'
  [[ "$SCP_SMOKE_TENANT_ID" =~ $uuid_regex ]] || {
    echo "::error::SCP_SMOKE_TENANT_ID is not a valid UUID."
    exit 1
  }
  check "usageTenantScoped" "object" "/api/v1/executive/usage?tenantId=$SCP_SMOKE_TENANT_ID"
fi

# ── evidence ─────────────────────────────────────────────────────────────────

checks_json="$(printf '['; first=true
for i in "${!LABELS[@]}"; do
  $first && first=false || printf ','
  printf '{"route":"%s","httpStatus":"%s","result":"PASS"}' "${LABELS[$i]}" "${STATUSES[$i]}"
done
printf ']')"

printf '%s' "$checks_json" | jq -S \
  --arg releaseSha "$DEPLOYED_COMMIT_SHA" \
  --arg baseUrl "${BASE_URL%%/api/platform}" \
  --argjson frontendMode "$FRONTEND_MODE" \
  '{releaseSha:$releaseSha,
    schema:"sanad.scp.production-contract-smoke.v1",
    transport:(if $frontendMode then "vercel-bff" else "backend-direct" end),
    baseUrl:$baseUrl,
    checks:.,
    result:(if all(.[]; .result == "PASS") then "PASS" else "FAIL" end)}' > "$EVIDENCE_FILE"

jq -e '.result == "PASS"' "$EVIDENCE_FILE" >/dev/null || {
  echo "::error::SCP contract smoke evidence did not resolve to PASS."
  exit 1
}

if [ -n "${GITHUB_OUTPUT:-}" ]; then
  echo "evidence_file=$EVIDENCE_FILE" >> "$GITHUB_OUTPUT"
  echo "result=$(jq -r '.result' "$EVIDENCE_FILE")" >> "$GITHUB_OUTPUT"
fi

echo "SCP production contract smoke: PASSED"
