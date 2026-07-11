#!/bin/bash
# ============================================================
# Control Panel Full Verification Script
# ============================================================
# Tests all 3 control panel operations:
#   1. Create Tenant (إضافة مستأجر)
#   2. Create Organization (إضافة شركة)
#   3. Create Membership (إضافة عضوية)
#
# Usage:
#   ./verify-control-panel.sh [BASE_URL] [EMAIL] [PASSWORD]
#
# Defaults:
#   BASE_URL = http://localhost:8080
#   EMAIL    = cp-admin@sanad-control-plane.internal
#   PASSWORD = Senan@001985
# ============================================================

set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
EMAIL="${2:-cp-admin@sanad-control-plane.internal}"
PASSWORD="${3:-Senan@001985}"
TS=$(date +%s)

echo "============================================================"
echo "Control Panel Verification"
echo "Backend: $BASE_URL"
echo "Email:   $EMAIL"
echo "============================================================"
echo ""

# ─── Step 0: Health Check ─────────────────────────────────────
echo "Step 0: Health check..."
HEALTH=$(curl -sS -o /dev/null -w "%{http_code}" "$BASE_URL/actuator/health" --max-time 10 2>/dev/null || echo "000")
if [ "$HEALTH" != "200" ]; then
  echo "  ✗ Backend not healthy (HTTP $HEALTH)"
  exit 1
fi
echo "  ✓ Backend healthy (HTTP 200)"

# ─── Step 1: Login ────────────────────────────────────────────
echo ""
echo "Step 1: Login..."
LOGIN_RESP=$(curl -sS -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" \
  --max-time 30 2>&1)
TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import json,sys; print(json.load(sys.stdin).get('accessToken',''))" 2>/dev/null || echo "")
if [ -z "$TOKEN" ]; then
  echo "  ✗ Login failed"
  echo "  Response: $LOGIN_RESP"
  exit 1
fi
echo "  ✓ Login successful (token length: ${#TOKEN})"

# ─── Step 2: Create Tenant ────────────────────────────────────
echo ""
echo "Step 2: Create Tenant (إضافة مستأجر)..."
TENANT_RESP=$(curl -sS -w "\n[HTTP %{http_code}]" -X POST "$BASE_URL/api/v1/control-plane/tenants" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"name\": \"Test Tenant $TS\",
    \"subdomain\": \"test$TS\",
    \"adminEmail\": \"admin@test$TS.example\",
    \"adminDisplayName\": \"Test Admin\",
    \"countryCode\": \"SA\",
    \"currencyCode\": \"SAR\",
    \"trialDays\": 14
  }" \
  --max-time 60 2>&1)
TENANT_HTTP=$(echo "$TENANT_RESP" | grep -o '\[HTTP [0-9]*\]' | grep -o '[0-9]*')
TENANT_ID=$(echo "$TENANT_RESP" | python3 -c "import json,sys; d=json.load(sys.stdin.split('[HTTP')[0]); print(d.get('id',''))" 2>/dev/null || echo "")
if [ "$TENANT_HTTP" = "201" ] && [ -n "$TENANT_ID" ]; then
  echo "  ✓ Tenant created (HTTP 201, id: ${TENANT_ID:0:8}...)"
else
  echo "  ✗ Tenant creation failed (HTTP $TENANT_HTTP)"
  echo "  Response: $TENANT_RESP"
  exit 1
fi

# ─── Step 3: Create Organization ──────────────────────────────
echo ""
echo "Step 3: Create Organization (إضافة شركة)..."
ORG_RESP=$(curl -sS -w "\n[HTTP %{http_code}]" -X POST "$BASE_URL/api/v1/control-plane/tenants/$TENANT_ID/organizations" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"name\": \"Test Organization $TS\",
    \"description\": \"Created by verification script\"
  }" \
  --max-time 60 2>&1)
ORG_HTTP=$(echo "$ORG_RESP" | grep -o '\[HTTP [0-9]*\]' | grep -o '[0-9]*')
ORG_ID=$(echo "$ORG_RESP" | python3 -c "import json,sys; d=json.load(sys.stdin.split('[HTTP')[0]); print(d.get('id',''))" 2>/dev/null || echo "")
if [ "$ORG_HTTP" = "201" ] && [ -n "$ORG_ID" ]; then
  echo "  ✓ Organization created (HTTP 201, id: ${ORG_ID:0:8}...)"
else
  echo "  ✗ Organization creation failed (HTTP $ORG_HTTP)"
  echo "  Response: $ORG_RESP"
  exit 1
fi

# ─── Step 4: Create Membership ────────────────────────────────
echo ""
echo "Step 4: Create Membership (إضافة عضوية)..."
MEMBER_RESP=$(curl -sS -w "\n[HTTP %{http_code}]" -X POST "$BASE_URL/api/v1/control-plane/tenants/$TENANT_ID/organizations/$ORG_ID/memberships" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"email\": \"member$TS@test.example\",
    \"displayName\": \"Test Member\",
    \"roleCode\": \"ADMIN\"
  }" \
  --max-time 60 2>&1)
MEMBER_HTTP=$(echo "$MEMBER_RESP" | grep -o '\[HTTP [0-9]*\]' | grep -o '[0-9]*')
MEMBER_ID=$(echo "$MEMBER_RESP" | python3 -c "import json,sys; d=json.load(sys.stdin.split('[HTTP')[0]); print(d.get('id',''))" 2>/dev/null || echo "")
if [ "$MEMBER_HTTP" = "201" ] && [ -n "$MEMBER_ID" ]; then
  echo "  ✓ Membership created (HTTP 201, id: ${MEMBER_ID:0:8}...)"
else
  echo "  ✗ Membership creation failed (HTTP $MEMBER_HTTP)"
  echo "  Response: $MEMBER_RESP"
  exit 1
fi

# ─── Summary ──────────────────────────────────────────────────
echo ""
echo "============================================================"
echo "✅ ALL 3 OPERATIONS SUCCESSFUL"
echo "============================================================"
echo "Tenant:       $TENANT_ID (HTTP 201)"
echo "Organization: $ORG_ID (HTTP 201)"
echo "Membership:   $MEMBER_ID (HTTP 201)"
echo ""
echo "Control Panel is fully operational."
echo "============================================================"
