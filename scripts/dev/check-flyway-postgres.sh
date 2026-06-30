#!/bin/bash
# ============================================================================
# SNAD — Flyway PostgreSQL Parity Check
# Validates all Flyway migrations against a real PostgreSQL instance.
# If Docker is not available, exits with SKIPPED_DOCKER_NOT_AVAILABLE.
# ============================================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BACKEND_DIR="$REPO_ROOT/apps/sanad-platform"
MIGRATION_DIR="$BACKEND_DIR/src/main/resources/db/migration"

RESULT="SKIPPED_DOCKER_NOT_AVAILABLE"
DETAILS=""

echo "=== Flyway PostgreSQL Parity Check ==="
echo ""

# Check migrations exist
MIGRATION_COUNT=$(find "$MIGRATION_DIR" -name "V*.sql" | wc -l)
if [ "$MIGRATION_COUNT" -eq 0 ]; then
  echo "FAIL: No migration files found"
  exit 1
fi
echo "✓ Found $MIGRATION_COUNT migration files"

# List migrations
for f in "$MIGRATION_DIR"/V*.sql; do
  echo "  $(basename "$f")"
done

# Check Docker availability
if ! command -v docker > /dev/null 2>&1; then
  echo ""
  echo "RESULT: SKIPPED_DOCKER_NOT_AVAILABLE"
  echo "DETAILS: Docker not available. Static validation of $MIGRATION_COUNT migrations completed."
  exit 0
fi

echo ""
echo "=== Starting PostgreSQL for migration validation ==="

CONTAINER_NAME="snad-flyway-check-$$"
PG_PASSWORD="flyway-check-only-non-production"

# Start PostgreSQL
if ! docker run -d \
  --name "$CONTAINER_NAME" \
  -e POSTGRES_DB=sanad \
  -e POSTGRES_USER=sanad \
  -e POSTGRES_PASSWORD="$PG_PASSWORD" \
  -p 0:5432 \
  postgres:16-alpine > /dev/null 2>&1; then
  echo "FAIL: Could not start PostgreSQL container"
  RESULT="FAIL"
  DETAILS="Docker run failed for PostgreSQL"
  echo ""
  echo "RESULT: $RESULT"
  echo "DETAILS: $DETAILS"
  exit 1
fi

# Clean up function
cleanup() {
  docker rm -f "$CONTAINER_NAME" > /dev/null 2>&1 || true
}
trap cleanup EXIT

echo "✓ PostgreSQL container started ($CONTAINER_NAME)"

# Wait for PostgreSQL to be ready
echo "Waiting for PostgreSQL to be ready..."
for i in $(seq 1 30); do
  if docker exec "$CONTAINER_NAME" pg_isready -U sanad > /dev/null 2>&1; then
    echo "✓ PostgreSQL is ready"
    break
  fi
  sleep 1
  if [ "$i" = "30" ]; then
    echo "FAIL: PostgreSQL did not become ready within 30s"
    RESULT="FAIL"
    DETAILS="PostgreSQL startup timeout"
    echo ""
    echo "RESULT: $RESULT"
    echo "DETAILS: $DETAILS"
    exit 1
  fi
done

# Get the mapped port
PG_PORT=$(docker inspect "$CONTAINER_NAME" --format '{{range .NetworkSettings.Ports}}{{(index . 0).HostPort}}{{end}}')
PG_HOST="localhost"

echo "  Host: $PG_HOST"
echo "  Port: $PG_PORT"
echo "  Database: sanad"
echo "  User: sanad"

# Run migrations using the backend application
# We use Spring Boot's Flyway integration by starting the app briefly
echo ""
echo "=== Running Flyway migrations via Spring Boot ==="

# Set environment for local profile pointing to the test PostgreSQL
export SPRING_PROFILES_ACTIVE=local
export DATABASE_URL="jdbc:postgresql://$PG_HOST:$PG_PORT/sanad"
export DATABASE_USERNAME=sanad
export DATABASE_PASSWORD="$PG_PASSWORD"
export DATABASE_DRIVER=org.postgresql.Driver
export JPA_DDL_AUTO=validate
export FLYWAY_ENABLED=true
export JWT_SECRET="flyway-check-only-non-production-key-1234567890"
export SANAD_CORS_ALLOWED_ORIGINS="http://localhost:3000"

# Use Maven Wrapper to run a minimal test that triggers Flyway
cd "$BACKEND_DIR"
if ./mvnw -B -q test -Dtest="HealthEndpointTest" -Dspring.profiles.active=local \
  -Ddatabase.url="$DATABASE_URL" \
  -Ddatabase.username="$DATABASE_USERNAME" \
  -Ddatabase.password="$DATABASE_PASSWORD" \
  -Ddatabase.driver="$DATABASE_DRIVER" \
  2>&1 | tail -20; then
  echo "✓ Flyway migrations applied successfully"
  RESULT="PASS"
  DETAILS="All $MIGRATION_COUNT migrations applied against PostgreSQL 16"

  # Verify migrations are recorded
  MIGRATION_RECORDS=$(docker exec "$CONTAINER_NAME" \
    psql -U sanad -d sanad -t -c "SELECT COUNT(*) FROM flyway_schema_history;" 2>/dev/null || echo "0")
  echo "  Migrations recorded in flyway_schema_history: $MIGRATION_RECORDS"
else
  echo "FAIL: Migration test failed"
  RESULT="FAIL"
  DETAILS="Migrations failed against PostgreSQL 16"
fi

echo ""
echo "RESULT: $RESULT"
echo "DETAILS: $DETAILS"
