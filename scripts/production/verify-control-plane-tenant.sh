#!/usr/bin/env bash
set -euo pipefail

: "${PRODUCTION_DATABASE_URL:?PRODUCTION_DATABASE_URL is required}"
: "${DATABASE_USERNAME:?DATABASE_USERNAME is required}"
: "${RENDER_API_KEY:?RENDER_API_KEY is required}"
: "${RENDER_SERVICE_ID:?RENDER_SERVICE_ID is required}"
: "${CONTROL_PLANE_TENANT_ID:?CONTROL_PLANE_TENANT_ID is required}"

[[ "$CONTROL_PLANE_TENANT_ID" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$ ]] || {
  echo "::error::CONTROL_PLANE_TENANT_ID must be a valid UUID."
  exit 1
}

# Get DATABASE_PASSWORD from Render env vars
RENDER_ENV=$(curl --silent --show-error \
  --header "Authorization: Bearer $RENDER_API_KEY" \
  --header "Accept: application/json" \
  "https://api.render.com/v1/services/$RENDER_SERVICE_ID/env-vars?limit=100")

DATABASE_PASSWORD=$(echo "$RENDER_ENV" | jq -r '[.[]? | (.envVar // .)] | .[] | select(.key == "DATABASE_PASSWORD") | .value // empty')

test -n "$DATABASE_PASSWORD" || {
  echo "::error::DATABASE_PASSWORD not found in Render env vars"
  exit 1
}

# Parse PRODUCTION_DATABASE_URL
RAW_URL="$PRODUCTION_DATABASE_URL"
RAW_URL="${RAW_URL#jdbc:}"
RAW_URL="${RAW_URL#https://}"
RAW_URL="${RAW_URL#postgresql://}"
HOST_PORT="${RAW_URL%%/*}"
DB_PART="${RAW_URL#*/}"
DB_NAME="${DB_PART%%\?*}"
PGHOST="${HOST_PORT%%:*}"
PGPORT="${HOST_PORT#*:}"
PGPORT="${PGPORT:-5432}"

echo "Verifying Control Plane tenant in production database..."

TENANT_EXISTS=$(PGPASSWORD="$DATABASE_PASSWORD" psql \
  -h "$PGHOST" -p "$PGPORT" -U "$DATABASE_USERNAME" -d "$DB_NAME" \
  --no-psqlrc --set=ON_ERROR_STOP=1 --tuples-only --no-align \
  --command="SELECT COUNT(*) FROM tenants WHERE id = '${CONTROL_PLANE_TENANT_ID}' AND status = 'ACTIVE';")

[ "$TENANT_EXISTS" -ge 1 ] || {
  echo "::error::Control Plane tenant not found or not ACTIVE: $CONTROL_PLANE_TENANT_ID"
  exit 1
}

ADMIN_EXISTS=$(PGPASSWORD="$DATABASE_PASSWORD" psql \
  -h "$PGHOST" -p "$PGPORT" -U "$DATABASE_USERNAME" -d "$DB_NAME" \
  --no-psqlrc --set=ON_ERROR_STOP=1 --tuples-only --no-align \
  --command="SELECT COUNT(*) FROM roles WHERE tenant_id = '${CONTROL_PLANE_TENANT_ID}' AND code = 'ADMIN' AND status = 'ACTIVE';")

[ "$ADMIN_EXISTS" -ge 1 ] || {
  echo "::error::No ACTIVE ADMIN role for Control Plane tenant"
  exit 1
}

echo "Control Plane tenant verified: ACTIVE tenant with ADMIN role exists."
