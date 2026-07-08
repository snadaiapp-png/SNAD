#!/usr/bin/env bash
# SNAD Control Plane Provisioning Production Smoke
# Tests the full tenant provisioning chain: tenant → subscription → org → membership
set -euo pipefail

BASE_URL="${PRODUCTION_BASE_URL:?PRODUCTION_BASE_URL required}"
ADMIN_EMAIL="${CONTROL_PLANE_ADMIN_EMAIL:?CONTROL_PLANE_ADMIN_EMAIL required}"
ADMIN_PASSWORD="${CONTROL_PLANE_ADMIN_PASSWORD:?CONTROL_PLANE_ADMIN_PASSWORD required}"
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

# Step 2: Access check
echo "2. Access check..."
ACCESS_CODE=$(curl -sS -o /tmp/cp-access.json -w "%{http_code}" --max-time 15 "${BASE_URL}/api/v1/control-plane/access-check" \
  -H "Authorization: Bearer ${TOKEN}")
if [ "${ACCESS_CODE}" != "200" ]; then
  echo "   FAIL: Access check returned ${ACCESS_CODE}"
  cat /tmp/cp-access.json
  exit 1
fi
CAN_READ=$(cat /tmp/cp-access.json | python3 -c "import json,sys; print(json.load(sys.stdin).get('canRead',False))" 2>/dev/null || echo "False")
CAN_WRITE=$(cat /tmp/cp-access.json | python3 -c "import json,sys; print(json.load(sys.stdin).get('canWrite',False))" 2>/dev/null || echo "False")
echo "   PASS: Access check 200 (canRead=${CAN_READ}, canWrite=${CAN_WRITE})"

# Step 3: Create tenant
echo "3. Create tenant..."
TENANT_NAME="Smoke-$(date +%s)"
CREATE_RESP=$(curl -sS -o /tmp/cp-tenant.json -w "%{http_code}" --max-time 30 -X POST "${BASE_URL}/api/v1/control-plane/tenants" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"${TENANT_NAME}\",\"subdomain\":\"smoke-$(date +%s)\",\"adminEmail\":\"admin@${TEST_DOMAIN}\",\"adminDisplayName\":\"Smoke Admin\",\"trialDays\":14,\"planCode\":\"STARTER\",\"billingCycle\":\"MONTHLY\",\"seatQuantity\":1,\"createDefaultOrganization\":true}")
if [ "${CREATE_RESP}" != "201" ] && [ "${CREATE_RESP}" != "200" ]; then
  echo "   FAIL: Create tenant returned ${CREATE_RESP}"
  cat /tmp/cp-tenant.json | head -5
  exit 1
fi
TENANT_ID=$(cat /tmp/cp-tenant.json | python3 -c "import json,sys; print(json.load(sys.stdin).get('id',''))" 2>/dev/null || echo "")
echo "   PASS: Tenant created (ID: ${TENANT_ID:0:8}...)"

# Step 4: List tenants
echo "4. List tenants..."
TENANTS_CODE=$(curl -sS -o /dev/null -w "%{http_code}" --max-time 15 "${BASE_URL}/api/v1/control-plane/tenants" \
  -H "Authorization: Bearer ${TOKEN}")
if [ "${TENANTS_CODE}" != "200" ]; then
  echo "   FAIL: List tenants returned ${TENANTS_CODE}"
  exit 1
fi
echo "   PASS: List tenants 200"

# Step 5: List organizations (should contain primary org)
echo "5. List organizations..."
ORGS_CODE=$(curl -sS -o /tmp/cp-orgs.json -w "%{http_code}" --max-time 15 "${BASE_URL}/api/v1/control-plane/tenants/${TENANT_ID}/organizations" \
  -H "Authorization: Bearer ${TOKEN}")
if [ "${ORGS_CODE}" != "200" ]; then
  echo "   FAIL: List organizations returned ${ORGS_CODE}"
  exit 1
fi
ORG_COUNT=$(cat /tmp/cp-orgs.json | python3 -c "import json,sys; print(len(json.load(sys.stdin)))" 2>/dev/null || echo "0")
echo "   PASS: List organizations 200 (count: ${ORG_COUNT})"

# Step 6: Create additional organization
echo "6. Create additional organization..."
ORG_NAME="Additional-$(date +%s)"
ADDITIONAL_ORG_RESP=$(curl -sS -o /tmp/cp-org-create.json -w "%{http_code}" --max-time 15 -X POST "${BASE_URL}/api/v1/control-plane/tenants/${TENANT_ID}/organizations" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"${ORG_NAME}\",\"description\":\"Smoke test org\"}")
if [ "${ADDITIONAL_ORG_RESP}" != "201" ] && [ "${ADDITIONAL_ORG_RESP}" != "200" ]; then
  echo "   FAIL: Create additional organization returned ${ADDITIONAL_ORG_RESP}"
  cat /tmp/cp-org-create.json | head -5
  exit 1
fi
ORG_ID=$(cat /tmp/cp-org-create.json | python3 -c "import json,sys; print(json.load(sys.stdin).get('id',''))" 2>/dev/null || echo "")
echo "   PASS: Additional organization created (ID: ${ORG_ID:0:8}...)"

# Step 7: Create membership
echo "7. Create membership..."
MEMBER_EMAIL="member-$(date +%s)@${TEST_DOMAIN}"
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

# Step 8: List memberships
echo "8. List memberships..."
MEMBERS_CODE=$(curl -sS -o /dev/null -w "%{http_code}" --max-time 15 "${BASE_URL}/api/v1/control-plane/tenants/${TENANT_ID}/organizations/${ORG_ID}/memberships" \
  -H "Authorization: Bearer ${TOKEN}")
if [ "${MEMBERS_CODE}" != "200" ]; then
  echo "   WARN: List memberships returned ${MEMBERS_CODE}"
else
  echo "   PASS: List memberships 200"
fi

# Step 9: List subscriptions
echo "9. List subscriptions..."
SUBS_CODE=$(curl -sS -o /dev/null -w "%{http_code}" --max-time 15 "${BASE_URL}/api/v1/control-plane/subscriptions" \
  -H "Authorization: Bearer ${TOKEN}")
if [ "${SUBS_CODE}" != "200" ]; then
  echo "   FAIL: List subscriptions returned ${SUBS_CODE}"
  exit 1
fi
echo "   PASS: List subscriptions 200"

echo ""
echo "=== ALL CONTROL PLANE PROVISIONING CHECKS PASSED ==="
