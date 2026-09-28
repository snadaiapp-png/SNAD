#!/usr/bin/env bash
set -euo pipefail

# Runs only after the exact candidate image is live and verified by the canonical
# production release. Credentials/RBAC are provisioned separately by the G2
# identity workflow; this step only resolves the existing G2 users and creates
# or reuses their canonical Person -> Employment -> Assignment graph.
for var in PRODUCTION_BASE_URL G2_TENANT_ID G2_ADMIN_EMAIL G2_ADMIN_PASSWORD \
           E2E_EMPLOYEE_EMAIL E2E_MANAGER_EMAIL E2E_HR_EMAIL; do
  test -n "${!var:-}" || { echo "::error::$var is required"; exit 1; }
done

BASE_URL="${PRODUCTION_BASE_URL%/}"
API="$BASE_URL/api/v1"
echo "::add-mask::$G2_ADMIN_EMAIL"
echo "::add-mask::$G2_ADMIN_PASSWORD"
for email in "$E2E_EMPLOYEE_EMAIL" "$E2E_MANAGER_EMAIL" "$E2E_HR_EMAIL"; do echo "::add-mask::$email"; done

response=$(curl --silent --show-error -X POST "$API/auth/login" \
  -H 'Content-Type: application/json' \
  -d "$(jq -n --arg e "$G2_ADMIN_EMAIL" --arg p "$G2_ADMIN_PASSWORD" --arg t "$G2_TENANT_ID" \
        '{email:$e,password:$p,tenantId:$t}')")
ACCESS_TOKEN=$(echo "$response" | jq -r '.accessToken // empty')
tenant=$(echo "$response" | jq -r '.user.tenantId // empty')
if [ -z "$ACCESS_TOKEN" ] || [ "$tenant" != "$G2_TENANT_ID" ]; then
  echo '::error::Governed tenant admin authentication/binding failed'
  exit 1
fi
echo "::add-mask::$ACCESS_TOKEN"

caps=$(curl --silent --show-error -H "Authorization: Bearer $ACCESS_TOKEN" "$API/auth/me" | jq -r '.capabilities[]? // empty')
for cap in ORGANIZATION.READ HRM.EMPLOYEE.VIEW HRM.EMPLOYEE.CREATE HRM.EMPLOYEE.UPDATE \
           HRM.USER_LINK.MANAGE HRM.ASSIGNMENT.VIEW HRM.ASSIGNMENT.MANAGE; do
  grep -Fxq "$cap" <<<"$caps" || { echo "::error::G2 admin missing required capability: $cap"; exit 1; }
done

resolve_user() {
  local label="$1" email="$2" body count
  body=$(curl --silent --show-error -H "Authorization: Bearer $ACCESS_TOKEN" \
    "$API/users?tenantId=$G2_TENANT_ID&email=$email")
  count=$(echo "$body" | jq --arg e "$email" '[.[] | select(.email == $e)] | length')
  if [ "$count" != "1" ]; then
    echo "::error::HR_CONTEXT_AMBIGUOUS: expected exactly one existing $label G2 user, found $count" >&2
    exit 1
  fi
  echo "$body" | jq -r --arg e "$email" '.[] | select(.email == $e) | .id'
}

EMP_USER_ID=$(resolve_user EMPLOYEE "$E2E_EMPLOYEE_EMAIL")
MGR_USER_ID=$(resolve_user MANAGER "$E2E_MANAGER_EMAIL")
HR_USER_ID=$(resolve_user HR "$E2E_HR_EMAIL")

export BASE_URL ACCESS_TOKEN G2_TENANT_ID EMP_USER_ID MGR_USER_ID HR_USER_ID
bash scripts/production/bootstrap-g2-canonical-hr.sh

echo 'G2_RELEASE_CANONICAL_HR_BOOTSTRAP=PASS'
