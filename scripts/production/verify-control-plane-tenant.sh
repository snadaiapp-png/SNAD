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

# Get ALL env vars from Render (DATABASE_URL, DATABASE_PASSWORD, etc.)
echo "Fetching env vars from Render..."
RENDER_ENV=$(curl --silent --show-error \
  --header "Authorization: Bearer $RENDER_API_KEY" \
  --header "Accept: application/json" \
  "https://api.render.com/v1/services/$RENDER_SERVICE_ID/env-vars?limit=100")

DATABASE_URL=$(echo "$RENDER_ENV" | jq -r '[.[]? | (.envVar // .)] | .[] | select(.key == "DATABASE_URL") | .value // empty')
DATABASE_PASSWORD=$(echo "$RENDER_ENV" | jq -r '[.[]? | (.envVar // .)] | .[] | select(.key == "DATABASE_PASSWORD") | .value // empty')

test -n "$DATABASE_URL" || { echo "::error::DATABASE_URL not found in Render"; exit 1; }
test -n "$DATABASE_PASSWORD" || { echo "::error::DATABASE_PASSWORD not found in Render"; exit 1; }

# Parse DATABASE_URL (from Render — format: jdbc:postgresql://host:port/db?params)
# Also extract username from DATABASE_URL if present (Supabase pooler requires
# the username embedded in the connection URL, which may differ from the
# separate DATABASE_USERNAME env var).
RAW_URL="$DATABASE_URL"
RAW_URL="${RAW_URL#jdbc:}"
RAW_URL="${RAW_URL#postgresql://}"
RAW_URL="${RAW_URL#https://}"

# Extract username from URL if present (user=... or postgresql://user:pass@host)
URL_USER=""
if [[ "$RAW_URL" == *@* ]]; then
  URL_USER="${RAW_URL%%@*}"
  URL_USER="${URL_USER%%:*}"  # strip password if present
  RAW_URL="${RAW_URL#*@}"
fi

HOST_PORT="${RAW_URL%%/*}"
DB_PART="${RAW_URL#*/}"
DB_NAME="${DB_PART%%\?*}"
PGHOST="${HOST_PORT%%:*}"
PGPORT="${HOST_PORT#*:}"
PGPORT="${PGPORT:-5432}"

# Also check for user= query parameter
QUERY_PART="${DB_PART#*\?}"
if [[ "$QUERY_PART" == *"user="* ]]; then
  URL_USER=$(echo "$QUERY_PART" | sed -n 's/.*user=\([^&]*\).*/\1/p')
fi

# Debug: print sanitized URL structure for troubleshooting
echo "DATABASE_URL structure: scheme-stripped, host=$PGHOST, port=$PGPORT, dbname=$DB_NAME"
echo "URL contains @: $(echo "$RAW_URL" | grep -c '@' || true)"
echo "Query part contains user=: $(echo "$QUERY_PART" | grep -c 'user=' || true)"
echo "URL_USER extracted: $( [ -n "$URL_USER" ] && echo 'YES' || echo 'NO')"
echo "DATABASE_USERNAME from env: $( [ -n "$DATABASE_USERNAME" ] && echo 'present' || echo 'absent')"

# Use the username from DATABASE_URL if present, otherwise fall back to
# the DATABASE_USERNAME env var. The Supabase connection pooler requires
# the username embedded in the connection URL.
if [ -n "$URL_USER" ]; then
  PGUSER="$URL_USER"
  echo "::add-mask::$PGUSER"
else
  PGUSER="$DATABASE_USERNAME"
  echo "::add-mask::$PGUSER"
fi

# Supabase connection pooler (PgBouncer) requires username format:
# postgres.<project_ref>
# If connecting to a Supabase pooler host and the username doesn't contain
# a dot, prepend the Supabase project ref extracted from the hostname.
# Supabase pooler hostname pattern: aws-X-<region>.pooler.supabase.com
# The project ref is NOT in the pooler hostname — it's in the direct DB
# hostname (db.<project_ref>.supabase.co). For the pooler, the username
# must be postgres.<project_ref>.
# We construct it by appending the project ref from the DATABASE_USERNAME
# env var if it contains a dot, or by using the DATABASE_USERNAME as-is
# if it already contains a dot (already in pooler format).
if [[ "$PGHOST" == *"pooler.supabase.com"* ]] && [[ "$PGUSER" != *.* ]]; then
  # The username doesn't contain a dot — Supabase pooler needs postgres.<ref>
  # Try to extract the project ref from other Render env vars that might
  # contain the direct DB host (db.<ref>.supabase.co).
  SUPABASE_REF=""
  # Check PRODUCTION_DATABASE_JDBC_URL if it exists in Render env
  PROD_JDBC_URL=$(echo "$RENDER_ENV" | jq -r '[.[]? | (.envVar // .)] | .[] | select(.key == "PRODUCTION_DATABASE_JDBC_URL") | .value // empty')
  if [[ -n "$PROD_JDBC_URL" ]] && [[ "$PROD_JDBC_URL" == *"db."*".supabase.co"* ]]; then
    SUPABASE_REF=$(echo "$PROD_JDBC_URL" | sed -n 's/.*db\.\([^.]*\)\.supabase\.co.*/\1/p')
    echo "Extracted Supabase project ref from PRODUCTION_DATABASE_JDBC_URL"
  fi
  # Check PRODUCTION_DATABASE_URL if it exists in Render env
  if [ -z "$SUPABASE_REF" ]; then
    PROD_DB_URL=$(echo "$RENDER_ENV" | jq -r '[.[]? | (.envVar // .)] | .[] | select(.key == "PRODUCTION_DATABASE_URL") | .value // empty')
    if [[ -n "$PROD_DB_URL" ]] && [[ "$PROD_DB_URL" == *"db."*".supabase.co"* ]]; then
      SUPABASE_REF=$(echo "$PROD_DB_URL" | sed -n 's/.*db\.\([^.]*\)\.supabase\.co.*/\1/p')
      echo "Extracted Supabase project ref from PRODUCTION_DATABASE_URL"
    fi
  fi
  if [ -n "$SUPABASE_REF" ]; then
    PGUSER="postgres.$SUPABASE_REF"
    echo "::add-mask::$PGUSER"
    echo "Constructed Supabase pooler username from project ref"
  else
    echo "WARNING: Could not extract Supabase project ref for pooler username construction"
  fi
fi

echo "Connecting to: host=$PGHOST port=$PGPORT dbname=$DB_NAME (user masked)"

TENANT_EXISTS=$(PGPASSWORD="$DATABASE_PASSWORD" psql \
  -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$DB_NAME" \
  --no-psqlrc --set=ON_ERROR_STOP=1 --tuples-only --no-align \
  --command="SELECT COUNT(*) FROM tenants WHERE id = '${CONTROL_PLANE_TENANT_ID}' AND status = 'ACTIVE';")

[ "$TENANT_EXISTS" -ge 1 ] || {
  echo "::error::Control Plane tenant not found or not ACTIVE"
  exit 1
}

ADMIN_EXISTS=$(PGPASSWORD="$DATABASE_PASSWORD" psql \
  -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$DB_NAME" \
  --no-psqlrc --set=ON_ERROR_STOP=1 --tuples-only --no-align \
  --command="SELECT COUNT(*) FROM roles WHERE tenant_id = '${CONTROL_PLANE_TENANT_ID}' AND code = 'ADMIN' AND status = 'ACTIVE';")

[ "$ADMIN_EXISTS" -ge 1 ] || {
  echo "::error::No ACTIVE ADMIN role for Control Plane tenant"
  exit 1
}

echo "Control Plane tenant verified: ACTIVE tenant with ADMIN role exists."
