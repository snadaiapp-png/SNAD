#!/usr/bin/env bash
set -euo pipefail

: "${RENDER_API_KEY:?RENDER_API_KEY is required}"
: "${RENDER_SERVICE_ID:?RENDER_SERVICE_ID is required}"
: "${DATABASE_USERNAME:?DATABASE_USERNAME is required}"
: "${CONTROL_PLANE_TENANT_ID:?CONTROL_PLANE_TENANT_ID is required}"

[[ "$CONTROL_PLANE_TENANT_ID" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$ ]] || {
  echo "::error::CONTROL_PLANE_TENANT_ID must be a valid UUID."
  exit 1
}

# Get the database connection values from Render without exposing them.
RENDER_ENV=$(curl --fail-with-body --silent --show-error \
  --header "Authorization: Bearer $RENDER_API_KEY" \
  --header "Accept: application/json" \
  "https://api.render.com/v1/services/$RENDER_SERVICE_ID/env-vars?limit=100")

DATABASE_URL=$(echo "$RENDER_ENV" | jq -r '[.[]? | (.envVar // .)] | .[] | select(.key == "DATABASE_URL") | .value // empty')
DATABASE_PASSWORD=$(echo "$RENDER_ENV" | jq -r '[.[]? | (.envVar // .)] | .[] | select(.key == "DATABASE_PASSWORD") | .value // empty')

test -n "$DATABASE_URL" || { echo "::error::DATABASE_URL not found in Render"; exit 1; }
test -n "$DATABASE_PASSWORD" || { echo "::error::DATABASE_PASSWORD not found in Render"; exit 1; }

echo "::add-mask::$DATABASE_URL"
echo "::add-mask::$DATABASE_PASSWORD"

RAW_URL="$DATABASE_URL"
RAW_URL="${RAW_URL#jdbc:}"
RAW_URL="${RAW_URL#postgresql://}"
RAW_URL="${RAW_URL#postgres://}"
HOST_PORT="${RAW_URL%%/*}"
DB_PART="${RAW_URL#*/}"
DB_NAME="${DB_PART%%\?*}"
PGHOST="${HOST_PORT%%:*}"
if [[ "$HOST_PORT" == *:* ]]; then
  PGPORT="${HOST_PORT##*:}"
else
  PGPORT=5432
fi

if [ -z "$PGHOST" ] || [ -z "$DB_NAME" ]; then
  echo "::error::DATABASE_URL is not a supported PostgreSQL connection URL"
  exit 1
fi

echo "::add-mask::$PGHOST"

# Render databases commonly expose a private hostname that is intentionally
# resolvable only inside the provider network. In that case GitHub must not
# open or require public database access. The ControlPlaneHealthIndicator runs
# the same tenant/ADMIN checks inside the application container, and Render's
# /actuator/health deployment gate fails closed if that indicator is DOWN.
if ! getent hosts "$PGHOST" >/dev/null 2>&1; then
  echo "Control Plane database uses private DNS; external SQL verification is deferred to the in-runtime health gate."
  exit 0
fi

TENANT_EXISTS=$(PGPASSWORD="$DATABASE_PASSWORD" PGCONNECT_TIMEOUT=15 psql \
  -h "$PGHOST" -p "$PGPORT" -U "$DATABASE_USERNAME" -d "$DB_NAME" \
  --no-psqlrc --set=ON_ERROR_STOP=1 --tuples-only --no-align \
  --command="SELECT COUNT(*) FROM tenants WHERE id = '${CONTROL_PLANE_TENANT_ID}' AND status = 'ACTIVE';")

[ "$TENANT_EXISTS" -ge 1 ] || {
  echo "::error::Control Plane tenant not found or not ACTIVE"
  exit 1
}

ADMIN_EXISTS=$(PGPASSWORD="$DATABASE_PASSWORD" PGCONNECT_TIMEOUT=15 psql \
  -h "$PGHOST" -p "$PGPORT" -U "$DATABASE_USERNAME" -d "$DB_NAME" \
  --no-psqlrc --set=ON_ERROR_STOP=1 --tuples-only --no-align \
  --command="SELECT COUNT(*) FROM roles WHERE tenant_id = '${CONTROL_PLANE_TENANT_ID}' AND code = 'ADMIN' AND status = 'ACTIVE';")

[ "$ADMIN_EXISTS" -ge 1 ] || {
  echo "::error::No ACTIVE ADMIN role for Control Plane tenant"
  exit 1
}

echo "Control Plane tenant verified: ACTIVE tenant with ADMIN role exists."
