#!/usr/bin/env bash
set -euo pipefail

: "${PRODUCTION_DATABASE_URL:?PRODUCTION_DATABASE_URL is required}"
: "${DATABASE_USERNAME:?DATABASE_USERNAME is required}"
: "${DATABASE_PASSWORD:?DATABASE_PASSWORD is required}"
: "${CONTROL_PLANE_TENANT_ID:?CONTROL_PLANE_TENANT_ID is required}"

[[ "$CONTROL_PLANE_TENANT_ID" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$ ]] || {
  echo "::error::CONTROL_PLANE_TENANT_ID must be a valid UUID."
  exit 1
}

echo "::add-mask::$CONTROL_PLANE_TENANT_ID"

RAW_URL="${PRODUCTION_DATABASE_URL#jdbc:}"
RAW_URL="${RAW_URL#postgresql://}"
HOST_PORT="${RAW_URL%%/*}"
DB_PART="${RAW_URL#*/}"
DB_NAME="${DB_PART%%\?*}"
PGHOST="${HOST_PORT%%:*}"
if [[ "$HOST_PORT" == *:* ]]; then
  PGPORT="${HOST_PORT##*:}"
else
  PGPORT="5432"
fi

for value in "$PGHOST" "$PGPORT" "$DB_NAME"; do
  test -n "$value" || {
    echo "::error::Unable to parse the protected production database URL."
    exit 1
  }
done

VALID_COUNT="$(
  PGPASSWORD="$DATABASE_PASSWORD" psql \
    -h "$PGHOST" \
    -p "$PGPORT" \
    -U "$DATABASE_USERNAME" \
    -d "$DB_NAME" \
    --no-psqlrc \
    --set=ON_ERROR_STOP=1 \
    --tuples-only \
    --no-align \
    --command="
      SELECT COUNT(*)
      FROM tenants tenant
      WHERE tenant.id = '$CONTROL_PLANE_TENANT_ID'::uuid
        AND tenant.status = 'ACTIVE'
        AND EXISTS (
          SELECT 1
          FROM users app_user
          WHERE app_user.tenant_id = tenant.id
            AND app_user.status = 'ACTIVE'
        )
        AND EXISTS (
          SELECT 1
          FROM user_role_assignments assignment
          JOIN roles role
            ON role.id = assignment.role_id
           AND role.tenant_id = assignment.tenant_id
          JOIN users app_user
            ON app_user.id = assignment.user_id
           AND app_user.tenant_id = assignment.tenant_id
          WHERE assignment.tenant_id = tenant.id
            AND assignment.status = 'ACTIVE'
            AND role.status = 'ACTIVE'
            AND role.code IN ('ADMIN', 'SUPER_ADMIN')
            AND app_user.status = 'ACTIVE'
        );
    " | tr -d '[:space:]'
)"

[ "$VALID_COUNT" = "1" ] || {
  echo "::error::The configured Control Plane tenant is missing, inactive, or lacks an active administrative user grant."
  exit 1
}

echo "Control Plane tenant database validation: PASSED"
