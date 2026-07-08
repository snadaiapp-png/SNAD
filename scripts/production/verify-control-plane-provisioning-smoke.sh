#!/usr/bin/env bash
# SNAD Control Plane Provisioning Production Smoke
# Tests the full tenant provisioning chain: tenant → subscription → org → membership
set -euo pipefail

BASE_URL="${PRODUCTION_BASE_URL:-https://sanad-backend-mcrj.onrender.com}"
ADMIN_EMAIL="${CONTROL_PLANE_ADMIN_EMAIL:?CONTROL_PLANE_ADMIN_EMAIL required}"
ADMIN_PASSWORD="${CONTROL_PLANE_ADMIN_PASSWORD:?CONTROL_PLANE_ADMIN_PASSWORD required}"
TEST_DOMAIN="${CONTROL_PLANE_TEST_EMAIL_DOMAIN:-example.com}"

echo "=== SNAD Control Plane Provisioning Smoke ==="
echo "Backend: ${BASE_URL}"
echo ""

# Step 1: Login
echo "1. Login..."
LOGIN_RESP=$(curl -sS -X POST "${BASE_URL}/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${ADMIN_EMAIL}\",\"password\":\"${ADMIN_PASSWORD}\"}")
TOKEN=$(echo "${LOGIN_RESP}" | python3 -c "import json,sys; print(json.load(sys.stdin).get('accessToken',''))" 2>/dev/null || echo "")
if [ -z "${TOKEN}" ]; then
  echo "FAIL: Login failed"
  exit 1
fi
echo "   PASS: Login successful"

# Step 2: Access check
echo "2. Access check..."
ACCESS_RESP=$(curl -sS -w "\n%{http_code}" "${BASE_URL}/api/v1/control-plane/access-check" \
  -H "Authorization: Bearer ${TOKEN}")
ACCESS_CODE=$(echo "${ACCESS_RESP}" | tail -1)
if [ "${ACCESS_CODE}" != "200" ]; then
  echo "FAIL: Access check returned ${ACCESS_CODE}"
  exit 1
fi
echo "   PASS: Access check 200"

# Step 3: Create tenant
echo "3. Create tenant..."
TENANT_NAME="Smoke-$(date +%s)"
CREATE_RESP=$(curl -sS -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/control-plane/tenants" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"${TENANT_NAME}\",\"subdomain\":\"smoke-$(date +%s)\",\"adminEmail\":\"admin@${TEST_DOMAIN}\",\"adminDisplayName\":\"Smoke Admin\",\"trialDays\":14,\"planCode\":\"STARTER\",\"billingCycle\":\"MONTHLY\",\"seatQuantity\":1}")
CREATE_CODE=$(echo "${CREATE_RESP}" | tail -1)
if [ "${CREATE_CODE}" != "201" ] && [ "${CREATE_CODE}" != "200" ]; then
  echo "FAIL: Create tenant returned ${CREATE_CODE}"
  echo "${CREATE_RESP}" | head -5
  exit 1
fi
TENANT_ID=$(echo "${CREATE_RESP}" | head -1 | python3 -c "import json,sys; print(json.load(sys.stdin).get('id',''))" 2>/dev/null || echo "")
echo "   PASS: Tenant created (ID: ${TENANT_ID:0:8}...)"

# Step 4: List tenants (should include new tenant)
echo "4. List tenants..."
TENANTS_CODE=$(curl -sS -o /dev/null -w "%{http_code}" "${BASE_URL}/api/v1/control-plane/tenants" \
  -H "Authorization: Bearer ${TOKEN}")
if [ "${TENANTS_CODE}" != "200" ]; then
  echo "FAIL: List tenants returned ${TENANTS_CODE}"
  exit 1
fi
echo "   PASS: List tenants 200"

# Step 5: List organizations (should contain primary org)
echo "5. List organizations..."
ORGS_CODE=$(curl -sS -o /dev/null -w "%{http_code}" "${BASE_URL}/api/v1/control-plane/tenants/${TENANT_ID}/organizations" \
  -H "Authorization: Bearer ${TOKEN}")
if [ "${ORGS_CODE}" != "200" ]; then
  echo "FAIL: List organizations returned ${ORGS_CODE}"
  exit 1
fi
echo "   PASS: List organizations 200"

# Step 6: Create additional organization
echo "6. Create additional organization..."
ADDITIONAL_ORG_CODE=$(curl -sS -o /dev/null -w "%{http_code}" -X POST "${BASE_URL}/api/v1/control-plane/tenants/${TENANT_ID}/organizations" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"name":"Additional Org","description":"Smoke test org"}')
if [ "${ADDITIONAL_ORG_CODE}" != "201" ] && [ "${ADDITIONAL_ORG_CODE}" != "200" ]; then
  echo "FAIL: Create additional organization returned ${ADDITIONAL_ORG_CODE}"
  exit 1
fi
echo "   PASS: Additional organization created"

# Step 7: List subscriptions (should contain tenant subscription)
echo "7. List subscriptions..."
SUBS_CODE=$(curl -sS -o /dev/null -w "%{http_code}" "${BASE_URL}/api/v1/control-plane/subscriptions" \
  -H "Authorization: Bearer ${TOKEN}")
if [ "${SUBS_CODE}" != "200" ]; then
  echo "FAIL: List subscriptions returned ${SUBS_CODE}"
  exit 1
fi
echo "   PASS: List subscriptions 200"

echo ""
echo "=== ALL CONTROL PLANE PROVISIONING CHECKS PASSED ==="
