#!/usr/bin/env bash
set -euo pipefail

: "${WEB_PRODUCTION_BASE_URL:?WEB_PRODUCTION_BASE_URL is required}"
: "${CONTROL_PLANE_ADMIN_EMAIL:?CONTROL_PLANE_ADMIN_EMAIL is required}"
: "${CONTROL_PLANE_ADMIN_PASSWORD:?CONTROL_PLANE_ADMIN_PASSWORD is required}"
: "${CONTROL_PLANE_TENANT_ID:?CONTROL_PLANE_TENANT_ID is required}"
: "${CONTROL_PLANE_NON_ADMIN_EMAIL:?CONTROL_PLANE_NON_ADMIN_EMAIL is required}"
: "${CONTROL_PLANE_NON_ADMIN_PASSWORD:?CONTROL_PLANE_NON_ADMIN_PASSWORD is required}"
: "${CONTROL_PLANE_NON_ADMIN_TENANT_ID:?CONTROL_PLANE_NON_ADMIN_TENANT_ID is required}"

WEB_BASE="${WEB_PRODUCTION_BASE_URL%/}"
API_BASE="${CONTROL_PLANE_API_BASE_URL:-$WEB_BASE/api/platform}"
API_BASE="${API_BASE%/}"
ORIGIN="${CONTROL_PLANE_ORIGIN:-$WEB_BASE}"
AUTH_EVIDENCE_FILE="${AUTHENTICATED_SMOKE_EVIDENCE_FILE:-evidence/authenticated-smoke-evidence.json}"
ISOLATION_EVIDENCE_FILE="${TENANT_ISOLATION_EVIDENCE_FILE:-evidence/tenant-isolation-evidence.json}"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT
mkdir -p "$(dirname "$AUTH_EVIDENCE_FILE")" "$(dirname "$ISOLATION_EVIDENCE_FILE")"

for secret in \
  "$CONTROL_PLANE_ADMIN_EMAIL" \
  "$CONTROL_PLANE_ADMIN_PASSWORD" \
  "$CONTROL_PLANE_TENANT_ID" \
  "$CONTROL_PLANE_NON_ADMIN_EMAIL" \
  "$CONTROL_PLANE_NON_ADMIN_PASSWORD" \
  "$CONTROL_PLANE_NON_ADMIN_TENANT_ID"
do
  echo "::add-mask::$secret"
done

uuid_regex='^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$'
[[ "$CONTROL_PLANE_TENANT_ID" =~ $uuid_regex ]] || {
  echo "::error::CONTROL_PLANE_TENANT_ID is not a UUID"; exit 1; }
[[ "$CONTROL_PLANE_NON_ADMIN_TENANT_ID" =~ $uuid_regex ]] || {
  echo "::error::CONTROL_PLANE_NON_ADMIN_TENANT_ID is not a UUID"; exit 1; }
[ "$CONTROL_PLANE_TENANT_ID" != "$CONTROL_PLANE_NON_ADMIN_TENANT_ID" ] || {
  echo "::error::Identity B must belong to a different tenant"; exit 1; }

request() {
  local method="$1"
  local url="$2"
  local output="$3"
  shift 3
  local status
  set +e
  status="$(curl --silent --show-error --location \
    --request "$method" \
    --connect-timeout 15 \
    --max-time 60 \
    --output "$output" \
    --write-out '%{http_code}' \
    "$@" \
    "$url")"
  local curl_code=$?
  set -e
  if [ "$curl_code" -ne 0 ]; then
    echo "000"
    return 0
  fi
  echo "$status"
}

expect_status() {
  local actual="$1"
  local expected="$2"
  local label="$3"
  [ "$actual" = "$expected" ] || {
    echo "::error::$label returned HTTP ${actual:-000}; expected $expected"; exit 1; }
  echo "$label: PASS"
}

expect_denied() {
  local actual="$1"
  local label="$2"
  case "$actual" in
    401|403) echo "$label: PASS" ;;
    *) echo "::error::$label returned HTTP ${actual:-000}; expected 401 or 403"; exit 1 ;;
  esac
}

admin_login_payload="$(jq -n \
  --arg email "$CONTROL_PLANE_ADMIN_EMAIL" \
  --arg password "$CONTROL_PLANE_ADMIN_PASSWORD" \
  --arg tenantId "$CONTROL_PLANE_TENANT_ID" \
  '{email:$email,password:$password,tenantId:$tenantId}')"

admin_login_status="$(request POST "$API_BASE/api/v1/auth/login" "$WORK_DIR/admin-login.json" \
  --cookie-jar "$WORK_DIR/admin.cookies" \
  --header 'Content-Type: application/json' \
  --header "Origin: $ORIGIN" \
  --data "$admin_login_payload")"
expect_status "$admin_login_status" "200" "Admin login"
ADMIN_TOKEN="$(jq -r '.accessToken // empty' "$WORK_DIR/admin-login.json")"
test -n "$ADMIN_TOKEN" || { echo "::error::Admin login returned no access token"; exit 1; }
echo "::add-mask::$ADMIN_TOKEN"

admin_me_status="$(request GET "$API_BASE/api/v1/auth/me" "$WORK_DIR/admin-me.json" \
  --header "Authorization: Bearer $ADMIN_TOKEN" \
  --header "Origin: $ORIGIN")"
expect_status "$admin_me_status" "200" "Admin /auth/me"
jq -e --arg tenant "$CONTROL_PLANE_TENANT_ID" '
  .tenantId == $tenant and
  .status == "ACTIVE" and
  any(.roleGrants[]?; .status == "ACTIVE" and (.roleCode == "ADMIN" or .roleCode == "SUPER_ADMIN"))
' "$WORK_DIR/admin-me.json" >/dev/null || {
  echo "::error::Admin identity is not ACTIVE with ADMIN/SUPER_ADMIN in the Control Plane tenant"; exit 1; }

declare -A admin_codes
for spec in \
  'dashboard:/api/v1/control-plane/dashboard' \
  'tenants:/api/v1/control-plane/tenants' \
  'systems:/api/v1/control-plane/systems' \
  'audit:/api/v1/control-plane/audit'
do
  label="${spec%%:*}"
  path="${spec#*:}"
  status="$(request GET "$API_BASE$path" "$WORK_DIR/admin-$label.json" \
    --header "Authorization: Bearer $ADMIN_TOKEN" \
    --header "Origin: $ORIGIN")"
  expect_status "$status" "200" "Control Plane $label"
  admin_codes["$label"]="$status"
done

jq -e --arg tenant "$CONTROL_PLANE_TENANT_ID" \
  'any(.[]?; .id == $tenant and .status == "ACTIVE")' \
  "$WORK_DIR/admin-tenants.json" >/dev/null || {
  echo "::error::Control Plane tenant missing or inactive in tenant directory"; exit 1; }

refresh_status="$(request POST "$API_BASE/api/v1/auth/refresh" "$WORK_DIR/admin-refresh.json" \
  --cookie "$WORK_DIR/admin.cookies" \
  --cookie-jar "$WORK_DIR/admin.cookies" \
  --header 'Content-Type: application/json' \
  --header "Origin: $ORIGIN" \
  --data '{}')"
expect_status "$refresh_status" "200" "Refresh flow"
REFRESHED_ADMIN_TOKEN="$(jq -r '.accessToken // empty' "$WORK_DIR/admin-refresh.json")"
test -n "$REFRESHED_ADMIN_TOKEN" || { echo "::error::Refresh returned no access token"; exit 1; }
echo "::add-mask::$REFRESHED_ADMIN_TOKEN"

non_admin_payload="$(jq -n \
  --arg email "$CONTROL_PLANE_NON_ADMIN_EMAIL" \
  --arg password "$CONTROL_PLANE_NON_ADMIN_PASSWORD" \
  --arg tenantId "$CONTROL_PLANE_NON_ADMIN_TENANT_ID" \
  '{email:$email,password:$password,tenantId:$tenantId}')"

non_admin_login_status="$(request POST "$API_BASE/api/v1/auth/login" "$WORK_DIR/non-admin-login.json" \
  --cookie-jar "$WORK_DIR/non-admin.cookies" \
  --header 'Content-Type: application/json' \
  --header "Origin: $ORIGIN" \
  --data "$non_admin_payload")"
expect_status "$non_admin_login_status" "200" "Identity B login"
NON_ADMIN_TOKEN="$(jq -r '.accessToken // empty' "$WORK_DIR/non-admin-login.json")"
test -n "$NON_ADMIN_TOKEN" || { echo "::error::Identity B login returned no access token"; exit 1; }
echo "::add-mask::$NON_ADMIN_TOKEN"

non_admin_me_status="$(request GET "$API_BASE/api/v1/auth/me" "$WORK_DIR/non-admin-me.json" \
  --header "Authorization: Bearer $NON_ADMIN_TOKEN" \
  --header "Origin: $ORIGIN")"
expect_status "$non_admin_me_status" "200" "Identity B /auth/me"
jq -e --arg tenant "$CONTROL_PLANE_NON_ADMIN_TENANT_ID" '
  .tenantId == $tenant and
  .status == "ACTIVE" and
  ([.roleGrants[]? | select(.status == "ACTIVE" and (.roleCode == "ADMIN" or .roleCode == "SUPER_ADMIN"))] | length == 0)
' "$WORK_DIR/non-admin-me.json" >/dev/null || {
  echo "::error::Identity B is inactive, in the wrong tenant, or has an administrative role"; exit 1; }

rbac_status="$(request GET "$API_BASE/api/v1/control-plane/tenants" "$WORK_DIR/rbac-denial.json" \
  --header "Authorization: Bearer $NON_ADMIN_TOKEN" \
  --header "Origin: $ORIGIN")"
expect_denied "$rbac_status" "Control Plane RBAC denial"

isolation_status="$(request GET "$API_BASE/api/v1/control-plane/tenants/$CONTROL_PLANE_TENANT_ID/organizations" \
  "$WORK_DIR/tenant-isolation.json" \
  --header "Authorization: Bearer $NON_ADMIN_TOKEN" \
  --header "Origin: $ORIGIN")"
expect_denied "$isolation_status" "Cross-tenant denial"

logout_status="$(request POST "$API_BASE/api/v1/auth/logout" "$WORK_DIR/admin-logout.json" \
  --cookie "$WORK_DIR/admin.cookies" \
  --cookie-jar "$WORK_DIR/admin.cookies" \
  --header "Authorization: Bearer $REFRESHED_ADMIN_TOKEN" \
  --header 'Content-Type: application/json' \
  --header "Origin: $ORIGIN" \
  --data '{}')"
case "$logout_status" in
  200|204) echo "Logout: PASS" ;;
  *) echo "::error::Logout returned HTTP ${logout_status:-000}; expected 200 or 204"; exit 1 ;;
esac

post_logout_refresh_status="$(request POST "$API_BASE/api/v1/auth/refresh" "$WORK_DIR/post-logout-refresh.json" \
  --cookie "$WORK_DIR/admin.cookies" \
  --cookie-jar "$WORK_DIR/admin.cookies" \
  --header 'Content-Type: application/json' \
  --header "Origin: $ORIGIN" \
  --data '{}')"
expect_denied "$post_logout_refresh_status" "Post-logout refresh rejection"

jq -n \
  --arg testedSha "${DEPLOYED_COMMIT_SHA:-unknown}" \
  --arg runId "${GITHUB_RUN_ID:-local}" \
  --arg adminLogin "$admin_login_status" \
  --arg adminMe "$admin_me_status" \
  --arg dashboard "${admin_codes[dashboard]}" \
  --arg tenants "${admin_codes[tenants]}" \
  --arg systems "${admin_codes[systems]}" \
  --arg audit "${admin_codes[audit]}" \
  --arg refresh "$refresh_status" \
  --arg identityBLogin "$non_admin_login_status" \
  --arg identityBMe "$non_admin_me_status" \
  --arg logout "$logout_status" \
  --arg postLogoutRefresh "$post_logout_refresh_status" \
  '{
    schema:"sanad.control-plane.authenticated-smoke.v2",
    testedSha:$testedSha,
    runId:$runId,
    http:{
      adminLogin:($adminLogin|tonumber),
      adminMe:($adminMe|tonumber),
      dashboard:($dashboard|tonumber),
      tenantDirectory:($tenants|tonumber),
      systems:($systems|tonumber),
      audit:($audit|tonumber),
      refresh:($refresh|tonumber),
      identityBLogin:($identityBLogin|tonumber),
      identityBMe:($identityBMe|tonumber),
      logout:($logout|tonumber),
      postLogoutRefresh:($postLogoutRefresh|tonumber)
    },
    adminRoleVerified:true,
    controlPlaneTenantVerified:true,
    identityBNonAdminVerified:true,
    result:(
      if (
        $adminLogin == "200" and $adminMe == "200" and
        $dashboard == "200" and $tenants == "200" and
        $systems == "200" and $audit == "200" and
        $refresh == "200" and $identityBLogin == "200" and
        $identityBMe == "200" and
        ($logout == "200" or $logout == "204") and
        ($postLogoutRefresh == "401" or $postLogoutRefresh == "403")
      ) then "PASS" else "FAIL" end
    )
  }' > "$AUTH_EVIDENCE_FILE"

jq -n \
  --arg testedSha "${DEPLOYED_COMMIT_SHA:-unknown}" \
  --arg rbac "$rbac_status" \
  --arg isolation "$isolation_status" \
  '{
    schema:"sanad.control-plane.tenant-isolation.v2",
    testedSha:$testedSha,
    identityTenantsDistinct:true,
    rbacHttpStatus:($rbac|tonumber),
    crossTenantHttpStatus:($isolation|tonumber),
    result:(
      if (
        ($rbac == "401" or $rbac == "403") and
        ($isolation == "401" or $isolation == "403")
      ) then "PASS" else "FAIL" end
    )
  }' > "$ISOLATION_EVIDENCE_FILE"

jq -e '.result == "PASS"' "$AUTH_EVIDENCE_FILE" >/dev/null
jq -e '.result == "PASS"' "$ISOLATION_EVIDENCE_FILE" >/dev/null
echo "Authenticated Control Plane production smoke: PASSED"
