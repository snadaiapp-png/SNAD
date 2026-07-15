#!/usr/bin/env bash
# ============================================================
# SNAD Platform — Fly.io Deployment Script
# ============================================================
# Prerequisites:
#   1. source /home/z/.env.fly-deploy (with all credentials filled in)
#   2. flyctl installed (already at /home/z/.fly/bin/flyctl)
#
# Usage:
#   source /home/z/.env.fly-deploy
#   bash scripts/deploy-fly.sh
# ============================================================

set -euo pipefail

export PATH="/home/z/.fly/bin:/home/z/.local/bin:$PATH"

REPO_ROOT="/home/z/my-project/SNAD"
APP_NAME="snad-backend"
REGION="fra"

echo "=== SNAD Platform — Fly.io Deployment ==="
echo ""

# ──────────────────────────────────────────────────────────────────────
# 0. Verify prerequisites
# ──────────────────────────────────────────────────────────────────────
echo "[0/6] Verifying prerequisites..."

if [ -z "${FLY_API_TOKEN:-}" ]; then
    echo "ERROR: FLY_API_TOKEN is not set. Source /home/z/.env.fly-deploy first."
    exit 1
fi
if [ -z "${DATABASE_URL:-}" ]; then
    echo "ERROR: DATABASE_URL is not set."
    exit 1
fi
if [ -z "${JWT_SECRET:-}" ]; then
    echo "ERROR: JWT_SECRET is not set."
    exit 1
fi
if [ -z "${SANAD_CONTROL_PLANE_TENANT_ID:-}" ]; then
    echo "ERROR: SANAD_CONTROL_PLANE_TENANT_ID is not set."
    exit 1
fi

echo "  ✅ All required credentials present"
echo ""

# ──────────────────────────────────────────────────────────────────────
# 1. Authenticate with Fly.io
# ──────────────────────────────────────────────────────────────────────
echo "[1/6] Authenticating with Fly.io..."
export FLY_API_TOKEN
flyctl auth whoami 2>&1 | head -3
echo "  ✅ Authenticated"
echo ""

# ──────────────────────────────────────────────────────────────────────
# 2. Create Fly app (if it doesn't exist)
# ──────────────────────────────────────────────────────────────────────
echo "[2/6] Checking if app '$APP_NAME' exists..."
if flyctl apps list 2>&1 | grep -q "^$APP_NAME"; then
    echo "  ✅ App already exists"
else
    echo "  Creating app '$APP_NAME' in region '$REGION'..."
    flyctl apps create "$APP_NAME" --org "$(flyctl orgs list 2>&1 | tail -1 | awk '{print $1}')" 2>&1 | tail -5
    echo "  ✅ App created"
fi
echo ""

# ──────────────────────────────────────────────────────────────────────
# 3. Allocate static IP
# ──────────────────────────────────────────────────────────────────────
echo "[3/6] Ensuring static IP exists..."
if flyctl ips list --app "$APP_NAME" 2>&1 | grep -q "IPv4"; then
    echo "  ✅ IP already allocated"
else
    flyctl ips allocate-v4 --app "$APP_NAME" 2>&1 | tail -3
    echo "  ✅ IP allocated"
fi
echo ""

# ──────────────────────────────────────────────────────────────────────
# 4. Set secrets
# ──────────────────────────────────────────────────────────────────────
echo "[4/6] Setting secrets..."
flyctl secrets set --app "$APP_NAME" \
    SPRING_PROFILES_ACTIVE="$SPRING_PROFILES_ACTIVE" \
    DATABASE_URL="$DATABASE_URL" \
    DATABASE_USERNAME="$DATABASE_USERNAME" \
    DATABASE_PASSWORD="$DATABASE_PASSWORD" \
    JWT_SECRET="$JWT_SECRET" \
    SANAD_CORS_ALLOWED_ORIGINS="$SANAD_CORS_ALLOWED_ORIGINS" \
    SANAD_CONTROL_PLANE_TENANT_ID="$SANAD_CONTROL_PLANE_TENANT_ID" \
    ${SECURITY_NOTIFICATION_PROVIDER:+SECURITY_NOTIFICATION_PROVIDER="$SECURITY_NOTIFICATION_PROVIDER"} \
    ${SECURITY_NOTIFICATION_ENDPOINT:+SECURITY_NOTIFICATION_ENDPOINT="$SECURITY_NOTIFICATION_ENDPOINT"} \
    ${SECURITY_NOTIFICATION_BEARER_TOKEN:+SECURITY_NOTIFICATION_BEARER_TOKEN="$SECURITY_NOTIFICATION_BEARER_TOKEN"} \
    ${SECURITY_NOTIFICATION_FROM:+SECURITY_NOTIFICATION_FROM="$SECURITY_NOTIFICATION_FROM"} \
    2>&1 | tail -3
echo "  ✅ Secrets set"
echo ""

# ──────────────────────────────────────────────────────────────────────
# 5. Deploy
# ──────────────────────────────────────────────────────────────────────
echo "[5/6] Deploying..."
cd "$REPO_ROOT"
flyctl deploy --app "$APP_NAME" --dockerfile apps/sanad-platform/Dockerfile --strategy rolling 2>&1 | tail -20
echo "  ✅ Deployed"
echo ""

# ──────────────────────────────────────────────────────────────────────
# 6. Verify health
# ──────────────────────────────────────────────────────────────────────
echo "[6/6] Verifying health..."
sleep 10
HEALTH_URL="https://$APP_NAME.fly.dev/actuator/health"
echo "  Probing: $HEALTH_URL"
HEALTH_RESPONSE=$(curl -sS --max-time 15 "$HEALTH_URL" 2>&1)
echo "  Response: $HEALTH_RESPONSE"

if echo "$HEALTH_RESPONSE" | grep -q '"status":"UP"'; then
    echo ""
    echo "🎉 DEPLOYMENT SUCCESSFUL!"
    echo ""
    echo "Backend URL: https://$APP_NAME.fly.dev"
    echo "Health: $HEALTH_URL"
    echo ""
    echo "Next steps:"
    echo "  1. Set Vercel env vars (production):"
    echo "     NEXT_PUBLIC_API_BASE_URL=https://$APP_NAME.fly.dev"
    echo "     BACKEND_API_BASE_URL=https://$APP_NAME.fly.dev"
    echo "  2. Trigger Vercel redeploy"
    echo "  3. Test end-to-end: https://snad-app.vercel.app"
else
    echo ""
    echo "⚠️  Health check failed. Check logs:"
    echo "  flyctl logs --app $APP_NAME"
    exit 1
fi
