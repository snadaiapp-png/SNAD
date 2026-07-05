#!/usr/bin/env bash
set -euo pipefail

: "${PRODUCTION_BASE_URL:?PRODUCTION_BASE_URL is required}"
: "${CONTROL_PLANE_ADMIN_EMAIL:?CONTROL_PLANE_ADMIN_EMAIL is required}"
: "${CONTROL_PLANE_ADMIN_PASSWORD:?CONTROL_PLANE_ADMIN_PASSWORD is required}"
: "${CONTROL_PLANE_TENANT_ID:?CONTROL_PLANE_TENANT_ID is required}"
: "${CONTROL_PLANE_NON_ADMIN_EMAIL:?CONTROL_PLANE_NON_ADMIN_EMAIL is required}"
: "${CONTROL_PLANE_NON_ADMIN_PASSWORD:?CONTROL_PLANE_NON_ADMIN_PASSWORD is required}"
: "${CONTROL_PLANE_NON_ADMIN_TENANT_ID:?CONTROL_PLANE_NON_ADMIN_TENANT_ID is required}"

BASE_URL="${PRODUCTION_BASE_URL%/}"
EVIDENCE_FILE="${CONTROL_PLANE_SMOKE_EVIDENCE_FILE:-control-plane-authenticated-smoke.json}"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

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
  echo "::error::CONTROL_PLANE_TENANT_ID is not a valid UUID."
  exit 1
}
[[ "$CONTROL_PLANE_NON_ADMIN_TENANT_ID" =~ $uuid_regex ]] || {
  echo "::error::CONTROL_PLANE_NON_ADMIN_TENANT_ID is not a valid UUID."
  exit 1
}
[ "$CONTROL_PLANE_TENANT_ID" != "$CONTROL_PLANE_NON_ADMIN_TENANT_ID" ] || {
  echo "::error::The non-admin smoke identity must belong to a different tenant."
  exit 1
}

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

expect_status() {
  local actual="$1"
  local expected="$2"
  local label="$3"
  [ "$actual" = "$expected" ] || {
    echo "::error::$label returned HTTP ${actual:-000}; expected $expected."
    exit 1
  }
  echo "$label: PASS"
}

expect_denied() {
  local actual="$1"
  local label="$2"
  case "$actual" in
    401|403) echo "$label: PASS" ;;
    *)
      echo "::error::$label returned HTTP ${actual:-000}; expected 401 or 403."
      exit 1
      ;;
  esac
}

admin_login_payload="$(jq -n \
  --arg email "$CONTROL_PLANE_ADMIN_EMAIL" \
  --arg password "$CONTROL_PLANE_ADMIN_PASSWORD" \
  --arg tenantId "$CONTROL_PLANE_TENANT_ID" \
  '{email:$email,password:$password,tenantId:$tenantId}')"

admin_login_status="$(request POST "$BASE_URL/api/v1/auth/login" "$WORK_DIR/admin-login.json" \
  --cookie-jar "$WORK_DIR/admin.cookies" \
  --header 'Content-Type: application/json' \
  --data "$admin_login_payload")"
expect_status "$admin_login_status" "200" "Admin login"
ADMIN_TOKEN="$(jq -r '.accessToken // empty' "$WORK_DIR/admin-login.json")"
test -n "$ADMIN_TOKEN" || { echo "::error::Admin login did not return an access token."; exit 1; }
echo "::add-mask::$ADMIN_TOKEN"

admin_me_status="$(request GET "$BASE_URL/api/v1/auth/me" "$WORK_DIR/admin-me.json" \
  --header "Authorization: Bearer $ADMIN_TOKEN")"
expect_status "$admin_me_status" "200" "Secure admin session identity"
admin_identity_valid="$(jq -r --arg tenant "$CONTROL_PLANE_TENANT_ID" '
  .tenantId == $tenant and
  .status == "ACTIVE" and
  any(.roleGrants[]?; .status == "ACTIVE" and (.roleCode == "ADMIN" or .roleCode == "SUPER_ADMIN"))
' "$WORK_DIR/admin-me.json")"
[ "$admin_identity_valid" = "true" ] || {
  echo "::error::The authenticated admin identity is not an active Control Plane administrator in the expected tenant."
  exit 1
}

dashboard_status="$(request GET "$BASE_URL/api/v1/control-plane/dashboard" "$WORK_DIR/admin-dashboard.json" \
  --header "Authorization: Bearer $ADMIN_TOKEN")"
expect_status "$dashboard_status" "200" "Control Plane dashboard"

tenants_status="$(request GET "$BASE_URL/api/v1/control-plane/tenants" "$WORK_DIR/admin-tenants.json" \
  --header "Authorization: Bearer $ADMIN_TOKEN")"
expect_status "$tenants_status" "200" "Control Plane tenant directory"

tenant_directory_valid="$(jq -r --arg tenant "$CONTROL_PLANE_TENANT_ID" '
  any(.[]?; .id == $tenant and .status == "ACTIVE")
' "$WORK_DIR/admin-tenants.json")"
[ "$tenant_directory_valid" = "true" ] || {
  echo "::error::The Control Plane tenant was not returned as ACTIVE by the tenant directory."
  exit 1
}

systems_status="$(request GET "$BASE_URL/api/v1/control-plane/systems" "$WORK_DIR/admin-systems.json" \
  --header "Authorization: Bearer $ADMIN_TOKEN")"
expect_status "$systems_status" "200" "Control Plane systems"

audit_status="$(request GET "$BASE_URL/api/v1/control-plane/audit" "$WORK_DIR/admin-audit.json" \
  --header "Authorization: Bearer $ADMIN_TOKEN")"
expect_status "$audit_status" "200" "Control Plane audit"

refresh_status="$(request POST "$BASE_URL/api/v1/auth/refresh" "$WORK_DIR/admin-refresh.json" \
  --cookie "$WORK_DIR/admin.cookies" \
  --cookie-jar "$WORK_DIR/admin.cookies" \
  --header 'Content-Type: application/json' \
  --data '{}')"
expect_status "$refresh_status" "200" "Refresh flow"
REFRESHED_ADMIN_TOKEN="$(jq -r '.accessToken // empty' "$WORK_DIR/admin-refresh.json")"
test -n "$REFRESHED_ADMIN_TOKEN" || { echo "::error::Refresh did not return an access token."; exit 1; }
echo "::add-mask::$REFRESHED_ADMIN_TOKEN"

non_admin_payload="$(jq -n \
  --arg email "$CONTROL_PLANE_NON_ADMIN_EMAIL" \
  --arg password "$CONTROL_PLANE_NON_ADMIN_PASSWORD" \
  --arg tenantId "$CONTROL_PLANE_NON_ADMIN_TENANT_ID" \
  '{email:$email,password:$password,tenantId:$tenantId}')"

non_admin_login_status="$(request POST "$BASE_URL/api/v1/auth/login" "$WORK_DIR/non-admin-login.json" \
  --cookie-jar "$WORK_DIR/non-admin.cookies" \
  --header 'Content-Type: application/json' \
  --data "$non_admin_payload")"
expect_status "$non_admin_login_status" "200" "Non-admin login"
NON_ADMIN_TOKEN="$(jq -r '.accessToken // empty' "$WORK_DIR/non-admin-login.json")"
test -n "$NON_ADMIN_TOKEN" || { echo "::error::Non-admin login did not return an access token."; exit 1; }
echo "::add-mask::$NON_ADMIN_TOKEN"

non_admin_me_status="$(request GET "$BASE_URL/api/v1/auth/me" "$WORK_DIR/non-admin-me.json" \
  --header "Authorization: Bearer $NON_ADMIN_TOKEN")"
expect_status "$non_admin_me_status" "200" "Secure non-admin session identity"
non_admin_identity_valid="$(jq -r --arg tenant "$CONTROL_PLANE_NON_ADMIN_TENANT_ID" '
  .tenantId == $tenant and
  .status == "ACTIVE" and
  (any(.roleGrants[]?; .status == "ACTIVE" and (.roleCode == "ADMIN" or .roleCode == "SUPER_ADMIN")) | not)
' "$WORK_DIR/non-admin-me.json")"
[ "$non_admin_identity_valid" = "true" ] || {
  echo "::error::Identity B is inactive, belongs to the wrong tenant, or has an administrative role."
  exit 1
}

rbac_status="$(request GET "$BASE_URL/api/v1/control-plane/tenants" "$WORK_DIR/rbac-denial.json" \
  --header "Authorization: Bearer $NON_ADMIN_TOKEN")"
expect_denied "$rbac_status" "Control Plane RBAC denial"

isolation_status="$(request GET "$BASE_URL/api/v1/control-plane/tenants/$CONTROL_PLANE_TENANT_ID/organizations" \
  "$WORK_DIR/tenant-isolation.json" \
  --header "Authorization: Bearer $NON_ADMIN_TOKEN")"
expect_denied "$isolation_status" "Cross-tenant denial"

logout_status="$(request POST "$BASE_URL/api/v1/auth/logout" "$WORK_DIR/admin-logout.json" \
  --cookie "$WORK_DIR/admin.cookies" \
  --cookie-jar "$WORK_DIR/admin.cookies" \
  --header "Authorization: Bearer $REFRESHED_ADMIN_TOKEN" \
  --header 'Content-Type: application/json' \
  --data '{}')"
case "$logout_status" in
  200|204) echo "Logout: PASS" ;;
  *) echo "::error::Logout returned HTTP ${logout_status:-000}."; exit 1 ;;
esac

post_logout_refresh_status="$(request POST "$BASE_URL/api/v1/auth/refresh" "$WORK_DIR/post-logout-refresh.json" \
  --cookie "$WORK_DIR/admin.cookies" \
  --cookie-jar "$WORK_DIR/admin.cookies" \
  --header 'Content-Type: application/json' \
  --data '{}')"
expect_denied "$post_logout_refresh_status" "Refresh revocation after logout"

jq -n \
  --arg testedSha "${DEPLOYED_COMMIT_SHA:-unknown}" \
  --arg runId "${GITHUB_RUN_ID:-local}" \
  --arg adminLogin "$admin_login_status" \
  --arg adminMe "$admin_me_status" \
  --argjson adminIdentityValid "$admin_identity_valid" \
  --arg dashboard "$dashboard_status" \
  --arg tenants "$tenants_status" \
  --argjson tenantDirectoryValid "$tenant_directory_valid" \
  --arg systems "$systems_status" \
  --arg audit "$audit_status" \
  --arg refresh "$refresh_status" \
  --arg identityBLogin "$non_admin_login_status" \
  --arg identityBMe "$non_admin_me_status" \
  --argjson identityBValid "$non_admin_identity_valid" \
  --arg rbac "$rbac_status" \
  --arg isolation "$isolation_status" \
  --arg logout "$logout_status" \
  --arg postLogoutRefresh "$post_logout_refresh_status" \
  '
  def ok200($s): $s == "200";
  def denied($s): $s == "401" or $s == "403";
  def logout_ok($s): $s == "200" or $s == "204";
  def verdict($condition): if $condition then "PASS" else "FAIL" end;
  (
    ok200($adminLogin) and ok200($adminMe) and $adminIdentityValid and
    ok200($dashboard) and ok200($tenants) and $tenantDirectoryValid and
    ok200($systems) and ok200($audit) and ok200($refresh) and
    ok200($identityBLogin) and ok200($identityBMe) and $identityBValid and
    denied($rbac) and denied($isolation) and logout_ok($logout) and
    denied($postLogoutRefresh)
  ) as $allPassed |
  {
    schema:"sanad.control-plane.production-smoke.v2",
    result:verdict($allPassed),
    testedSha:$testedSha,
    runId:$runId,
    checks:{
      adminLogin:{httpStatus:$adminLogin,result:verdict(ok200($adminLogin))},
      adminIdentity:{httpStatus:$adminMe,result:verdict(ok200($adminMe) and $adminIdentityValid)},
      dashboard:{httpStatus:$dashboard,result:verdict(ok200($dashboard))},
      tenantDirectory:{httpStatus:$tenants,result:verdict(ok200($tenants) and $tenantDirectoryValid)},
      systems:{httpStatus:$systems,result:verdict(ok200($systems))},
      audit:{httpStatus:$audit,result:verdict(ok200($audit))},
      refresh:{httpStatus:$refresh,result:verdict(ok200($refresh))},
      identityB:{loginHttpStatus:$identityBLogin,meHttpStatus:$identityBMe,result:verdict(ok200($identityBLogin) and ok200($identityBMe) and $identityBValid)},
      rbacDenial:{httpStatus:$rbac,result:verdict(denied($rbac))},
      tenantIsolation:{httpStatus:$isolation,result:verdict(denied($isolation))},
      logout:{httpStatus:$logout,result:verdict(logout_ok($logout))},
      postLogoutRefreshRejection:{httpStatus:$postLogoutRefresh,result:verdict(denied($postLogoutRefresh))}
    }
  }
  ' > "$EVIDENCE_FILE"

jq -e '.result == "PASS"' "$EVIDENCE_FILE" >/dev/null || {
  echo "::error::Authenticated Control Plane smoke evidence did not resolve to PASS."
  exit 1
}

if [ -n "${GITHUB_OUTPUT:-}" ]; then
  echo "evidence_file=$EVIDENCE_FILE" >> "$GITHUB_OUTPUT"
  echo "result=$(jq -r '.result' "$EVIDENCE_FILE")" >> "$GITHUB_OUTPUT"
fi

echo "Authenticated Control Plane production smoke: PASSED"
