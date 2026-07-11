#!/bin/bash
# ============================================================
# SANAD Backend — Local Server Startup Script (Production-Safe)
# ============================================================
# This script starts the SANAD Spring Boot backend locally.
# It reads ALL secrets from a .env file — NO secrets are stored in this script.
#
# Prerequisites:
#   - Java 21+ installed
#   - Maven installed (or use the bundled mvn)
#   - A .env file at /srv/sanad/config/.env (see .env.example)
#
# Usage:
#   chmod +x scripts/start-local-backend.sh
#   ./scripts/start-local-backend.sh [--env-file /path/to/.env]
#
# The backend will start on http://localhost:8080
# ============================================================

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKEND_DIR="$PROJECT_DIR/apps/sanad-platform"
JAR_PATH="$BACKEND_DIR/target/sanad-platform-0.1.0-SNAPSHOT.jar"
LOG_FILE="${SANAD_LOG_FILE:-/var/log/sanad/backend.log}"
PID_FILE="${SANAD_PID_FILE:-/var/run/sanad-backend.pid}"

# Load .env file (secrets are NOT stored in this script)
ENV_FILE="${1:-${SANAD_ENV_FILE:-/srv/sanad/config/.env}}"
if [ ! -f "$ENV_FILE" ]; then
  echo "ERROR: Environment file not found: $ENV_FILE"
  echo "Create it from scripts/.env.example"
  exit 1
fi

# Source the .env file (contains all secrets)
set -a
source "$ENV_FILE"
set +a

# Validate required environment variables
REQUIRED_VARS=(
  DATABASE_URL
  DATABASE_USERNAME
  DATABASE_PASSWORD
  JWT_SECRET
  SANAD_CONTROL_PLANE_TENANT_ID
  SANAD_CORS_ALLOWED_ORIGINS
)
for var in "${REQUIRED_VARS[@]}"; do
  if [ -z "${!var:-}" ]; then
    echo "ERROR: Required variable $var is not set in $ENV_FILE"
    exit 1
  fi
done

# Set defaults for optional variables
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-prod}"
export SERVER_PORT="${SERVER_PORT:-8080}"
export DATABASE_DRIVER="${DATABASE_DRIVER:-org.postgresql.Driver}"
export JPA_DDL_AUTO="${JPA_DDL_AUTO:-validate}"
export FLYWAY_ENABLED="${FLYWAY_ENABLED:-true}"
export LOG_LEVEL_ROOT="${LOG_LEVEL_ROOT:-WARN}"
export LOG_LEVEL_SANAD="${LOG_LEVEL_SANAD:-INFO}"
export MANAGEMENT_ENDPOINTS="${MANAGEMENT_ENDPOINTS:-health}"
export LAZY_INIT="${LAZY_INIT:-false}"
export CONTROL_PLANE_BOOTSTRAP_ENABLED="${CONTROL_PLANE_BOOTSTRAP_ENABLED:-false}"
export BOOTSTRAP_ENABLED="${BOOTSTRAP_ENABLED:-false}"
export DATABASE_POOL_MAX="${DATABASE_POOL_MAX:-5}"
export DATABASE_POOL_MIN="${DATABASE_POOL_MIN:-1}"
export DATABASE_POOL_TIMEOUT="${DATABASE_POOL_TIMEOUT:-30000}"
export SHUTDOWN_TIMEOUT="${SHUTDOWN_TIMEOUT:-30s}"
export APPLICATION_BASE_URL="${APPLICATION_BASE_URL:-https://snad-app.vercel.app}"

echo "============================================================"
echo "SANAD Backend — Local Server Startup"
echo "============================================================"
echo "Backend dir:  $BACKEND_DIR"
echo "Env file:     $ENV_FILE"
echo "Log file:     $LOG_FILE"
echo "Port:         $SERVER_PORT"
echo "Profile:      $SPRING_PROFILES_ACTIVE"
echo "Flyway:       $FLYWAY_ENABLED"
echo "CORS origins: $SANAD_CORS_ALLOWED_ORIGINS"
echo "============================================================"
echo ""

# Ensure log directory exists
mkdir -p "$(dirname "$LOG_FILE")"

# Step 1: Kill any existing backend
if [ -f "$PID_FILE" ]; then
  OLD_PID=$(cat "$PID_FILE")
  if kill -0 "$OLD_PID" 2>/dev/null; then
    echo "Stopping existing backend (PID: $OLD_PID)..."
    kill "$OLD_PID" 2>/dev/null || true
    sleep 3
  fi
fi
pkill -f "sanad-platform" 2>/dev/null || true
sleep 2

# Step 2: Build if JAR doesn't exist or is stale
if [ ! -f "$JAR_PATH" ] || [ "$BACKEND_DIR/src/main" -nt "$JAR_PATH" ]; then
  echo "Building backend JAR..."
  cd "$BACKEND_DIR"
  if command -v mvn &>/dev/null; then
    mvn clean package -DskipTests -q
  elif [ -x "$PROJECT_DIR/tools/apache-maven-3.9.9/bin/mvn" ]; then
    "$PROJECT_DIR/tools/apache-maven-3.9.9/bin/mvn" clean package -DskipTests -q
  else
    echo "ERROR: Maven not found. Install Maven or set MAVEN_HOME."
    exit 1
  fi
  echo "✓ Build complete"
fi

# Step 3: Start the backend
echo ""
echo "Starting backend..."
cd "$BACKEND_DIR"
nohup java -Xmx512m -XX:MaxRAMPercentage=75 -Dfile.encoding=UTF-8 \
  -jar "$JAR_PATH" \
  > "$LOG_FILE" 2>&1 &

BACKEND_PID=$!
echo "$BACKEND_PID" > "$PID_FILE"
echo "Backend PID: $BACKEND_PID"

# Step 4: Wait for startup
echo ""
echo "Waiting for backend to start..."
for i in $(seq 1 40); do
  HEALTH=$(curl -sS -o /dev/null -w "%{http_code}" "http://localhost:${SERVER_PORT}/actuator/health" --max-time 3 2>/dev/null || echo "000")
  if [ "$HEALTH" = "200" ]; then
    echo "✓ Backend started successfully! (attempt $i)"
    echo ""
    echo "============================================================"
    echo "✅ SANAD Backend is running on http://localhost:${SERVER_PORT}"
    echo "============================================================"
    echo ""
    echo "Health: $(curl -sS "http://localhost:${SERVER_PORT}/actuator/health" --max-time 5)"
    echo ""
    echo "To stop: kill \$(cat $PID_FILE)"
    echo "To view logs: tail -f $LOG_FILE"
    echo ""
    exit 0
  fi
  echo "  [$i] Waiting... (health: $HEALTH)"
  sleep 3
done

echo ""
echo "❌ Backend failed to start within 120 seconds"
echo "Check logs: tail -50 $LOG_FILE"
exit 1
