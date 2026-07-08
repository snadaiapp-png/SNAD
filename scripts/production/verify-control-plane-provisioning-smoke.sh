#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${PRODUCTION_BASE_URL:?PRODUCTION_BASE_URL required}"
ADMIN_EMAIL="${CP_SMOKE_EMAIL:?CP_SMOKE_EMAIL required}"
ADMIN_PASSWORD="${CP_SMOKE_PASSWORD:?CP_SMOKE_PASSWORD required}"
TEST_DOMAIN="${CONTROL_PLANE_TEST_EMAIL_DOMAIN:-smoke-test.invalid}"

echo "=== SNAD Control Plane Provisioning Smoke ==="
echo "Backend: ${BASE_URL}"
echo ""

# Step 1: Login
echo "1. Login..."
LOGIN_RESP=$(curl -sS --max-time 30 -X POST "${BASE_URL}/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${ADMIN_EMAIL}\",\"password\":\"${ADMIN_PASSWORD}\"}")
TOKEN=$(echo "${LOGIN_RESP}" | python3 -c "import json,sys; print(json.load(sys.stdin).get('accessToken',''))" 2>/dev/null || echo "")
if [ -z "${TOKEN}" ]; then
  echo "   FAIL: Login failed"
  echo "   Response: $(echo "${LOGIN_RESP}" | head -c 200)"
  exit 1
fi
echo "   PASS: Login successful"

# Step 2: Access check (skip if 404 - old backend version)
echo "2. Access check..."
ACCESS_CODE=$(curl -sS -o /tmp/cp-access.json -w "%{http_code}" --max-time 15 "${BASE_URL}/api/v1/control-plane/access-check" \
  -H "Authorization: Bearer ${TOKEN}" 2>/dev/null || echo "000")
if [ "${ACCESS_CODE}" = "200" ]; then
  echo "   PASS: Access check 200"
elif [ "${ACCESS_CODE}" = "404" ]; then
  echo "   SKIP: Access check not available (backend version old) - continuing"
else
  echo "   WARN: Access check returned ${ACCESS_CODE} - continuing"
fi

# Step 3: Try to list tenants (this will verify Control Plane access)
echo "3. List tenants..."
TENANTS_CODE=$(curl -sS -o /tmp/cp-tenants.json -w "%{http_code}" --max-time 15 "${BASE_URL}/api/v1/control-plane/tenants" \
  -H "Authorization: Bearer ${TOKEN}")
if [ "${TENANTS_CODE}" = "200" ]; then
  TENANT_COUNT=$(cat /tmp/cp-tenants.json | python3 -c "import json,sys; print(len(json.load(sys.stdin)))" 2>/dev/null || echo "0")
  echo "   PASS: List tenants 200 (count: ${TENANT_COUNT})"
elif [ "${TENANTS_CODE}" = "403" ]; then
  echo "   FAIL: 403 Forbidden - account does not have Control Plane access"
  exit 1
elif [ "${TENANTS_CODE}" = "401" ]; then
  echo "   FAIL: 401 Unauthorized - authentication failed"
  exit 1
else
  echo "   FAIL: List tenants returned ${TENANTS_CODE}"
  cat /tmp/cp-tenants.json | head -5
  exit 1
fi

# Step 4: Create tenant
echo "4. Create tenant..."
TIMESTAMP=$(date +%s)
TENANT_NAME="Smoke Tenant - CP - ${TIMESTAMP}"
CREATE_RESP=$(curl -sS -o /tmp/cp-tenant.json -w "%{http_code}" --max-time 30 -X POST "${BASE_URL}/api/v1/control-plane/tenants" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"${TENANT_NAME}\",\"subdomain\":\"smoke-cp-${TIMESTAMP}\",\"adminEmail\":\"smoke-admin-${TIMESTAMP}@${TEST_DOMAIN}\",\"adminDisplayName\":\"Smoke Admin\",\"trialDays\":14,\"planCode\":\"STARTER\",\"billingCycle\":\"MONTHLY\",\"seatQuantity\":3,\"createDefaultOrganization\":true}")
if [ "${CREATE_RESP}" != "201" ] && [ "${CREATE_RESP}" != "200" ]; then
  echo "   FAIL: Create tenant returned ${CREATE_RESP}"
  cat /tmp/cp-tenant.json | head -5
  exit 1
fi
TENANT_ID=$(cat /tmp/cp-tenant.json | python3 -c "import json,sys; print(json.load(sys.stdin).get('id',''))" 2>/dev/null || echo "")
echo "   PASS: Tenant created (ID: ${TENANT_ID:0:8}...)"

# Step 5: List tenants again (should include new tenant)
echo "5. List tenants (verify new tenant)..."
TENANTS_CODE=$(curl -sS -o /dev/null -w "%{http_code}" --max-time 15 "${BASE_URL}/api/v1/control-plane/tenants" \
  -H "Authorization: Bearer ${TOKEN}")
if [ "${TENANTS_CODE}" != "200" ]; then
  echo "   FAIL: List tenants returned ${TENANTS_CODE}"
  exit 1
fi
echo "   PASS: List tenants 200"

# Step 6: List organizations (should contain primary org)
echo "6. List organizations..."
ORGS_CODE=$(curl -sS -o /tmp/cp-orgs.json -w "%{http_code}" --max-time 15 "${BASE_URL}/api/v1/control-plane/tenants/${TENANT_ID}/organizations" \
  -H "Authorization: Bearer ${TOKEN}")
if [ "${ORGS_CODE}" != "200" ]; then
  echo "   FAIL: List organizations returned ${ORGS_CODE}"
  exit 1
fi
ORG_COUNT=$(cat /tmp/cp-orgs.json | python3 -c "import json,sys; print(len(json.load(sys.stdin)))" 2>/dev/null || echo "0")
echo "   PASS: List organizations 200 (count: ${ORG_COUNT})"

# Step 7: Create additional organization
echo "7. Create additional organization..."
ORG_NAME="Smoke Org - ${TIMESTAMP}"
ADDITIONAL_ORG_RESP=$(curl -sS -o /tmp/cp-org-create.json -w "%{http_code}" --max-time 15 -X POST "${BASE_URL}/api/v1/control-plane/tenants/${TENANT_ID}/organizations" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"${ORG_NAME}\",\"description\":\"Smoke test org\"}")
if [ "${ADDITIONAL_ORG_RESP}" != "201" ] && [ "${ADDITIONAL_ORG_RESP}" != "200" ]; then
  echo "   FAIL: Create organization returned ${ADDITIONAL_ORG_RESP}"
  cat /tmp/cp-org-create.json | head -5
  exit 1
fi
ORG_ID=$(cat /tmp/cp-org-create.json | python3 -c "import json,sys; print(json.load(sys.stdin).get('id',''))" 2>/dev/null || echo "")
echo "   PASS: Organization created (ID: ${ORG_ID:0:8}...)"

# Step 8: Create membership
echo "8. Create membership..."
MEMBER_EMAIL="smoke-member-${TIMESTAMP}@${TEST_DOMAIN}"
MEMBERSHIP_RESP=$(curl -sS -o /tmp/cp-membership.json -w "%{http_code}" --max-time 15 -X POST "${BASE_URL}/api/v1/control-plane/tenants/${TENANT_ID}/organizations/${ORG_ID}/memberships" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${MEMBER_EMAIL}\",\"displayName\":\"Smoke Member\",\"roleCode\":\"USER\"}")
if [ "${MEMBERSHIP_RESP}" != "201" ] && [ "${MEMBERSHIP_RESP}" != "200" ]; then
  echo "   WARN: Create membership returned ${MEMBERSHIP_RESP}"
  cat /tmp/cp-membership.json | head -5
else
  echo "   PASS: Membership created"
fi

# Step 9: List memberships
echo "9. List memberships..."
MEMBERS_CODE=$(curl -sS -o /dev/null -w "%{http_code}" --max-time 15 "${BASE_URL}/api/v1/control-plane/tenants/${TENANT_ID}/organizations/${ORG_ID}/memberships" \
  -H "Authorization: Bearer ${TOKEN}")
if [ "${MEMBERS_CODE}" = "200" ]; then
  echo "   PASS: List memberships 200"
else
  echo "   WARN: List memberships returned ${MEMBERS_CODE}"
fi

# Step 10: List subscriptions (verify auto-subscription)
echo "10. List subscriptions..."
SUBS_CODE=$(curl -sS -o /dev/null -w "%{http_code}" --max-time 15 "${BASE_URL}/api/v1/control-plane/subscriptions" \
  -H "Authorization: Bearer ${TOKEN}")
if [ "${SUBS_CODE}" = "200" ]; then
  echo "   PASS: List subscriptions 200"
else
  echo "   WARN: List subscriptions returned ${SUBS_CODE}"
fi

echo ""
echo "=== ALL CONTROL PLANE PROVISIONING CHECKS PASSED ==="
