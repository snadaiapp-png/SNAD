#!/bin/bash
# ============================================================
# SANAD Backend — Local Server Startup Script
# ============================================================
# This script starts the SANAD Spring Boot backend locally,
# replacing Render completely.
#
# Prerequisites:
#   - Java 21+ installed
#   - Maven installed (or use the bundled mvn)
#   - The backend JAR built (or it will build automatically)
#
# Usage:
#   chmod +x scripts/start-local-backend.sh
#   ./scripts/start-local-backend.sh
#
# The backend will start on http://localhost:8080
# ============================================================

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKEND_DIR="$PROJECT_DIR/apps/sanad-platform"
JAR_PATH="$BACKEND_DIR/target/sanad-platform-0.1.0-SNAPSHOT.jar"
LOG_FILE="/tmp/sanad-backend.log"
PID_FILE="/tmp/sanad-backend.pid"

# Database configuration (Supabase pooler)
export DATABASE_URL="jdbc:postgresql://aws-0-eu-central-1.pooler.supabase.com:5432/postgres?sslmode=require"
export DATABASE_USERNAME="sanad_app.hxhvfqxzigrqoxxnnzje"
export DATABASE_PASSWORD="Senan@001985NewDB"
export DATABASE_DRIVER="org.postgresql.Driver"

# JWT secret (generate a new one each start)
export JWT_SECRET=$(python3 -c "import secrets; print(secrets.token_urlsafe(48))" 2>/dev/null || openssl rand -base64 48)

# Application configuration
export SPRING_PROFILES_ACTIVE="prod"
export SERVER_PORT="8080"
export JPA_DDL_AUTO="validate"
export FLYWAY_ENABLED="false"
export SANAD_CONTROL_PLANE_TENANT_ID="958bbb1c-eece-4839-bca8-a5bfa14e6ac1"
export SANAD_CORS_ALLOWED_ORIGINS="https://snad-app.vercel.app"
export LOG_LEVEL_ROOT="WARN"
export LOG_LEVEL_SANAD="INFO"
export MANAGEMENT_ENDPOINTS="health"
export LAZY_INIT="false"
export CONTROL_PLANE_BOOTSTRAP_ENABLED="false"
export BOOTSTRAP_ENABLED="false"
export DATABASE_POOL_MAX="5"
export DATABASE_POOL_MIN="1"
export DATABASE_POOL_TIMEOUT="30000"
export SHUTDOWN_TIMEOUT="30s"
export APPLICATION_BASE_URL="https://snad-app.vercel.app"

# Admin credentials (for login)
ADMIN_EMAIL="cp-admin@sanad-control-plane.internal"
ADMIN_PASSWORD="Senan@001985"

echo "============================================================"
echo "SANAD Backend — Local Server Startup"
echo "============================================================"
echo "Backend dir: $BACKEND_DIR"
echo "Log file:    $LOG_FILE"
echo "Port:        8080"
echo "Database:    Supabase (hxhvfqxzigrqoxxnnzje)"
echo "Admin:       $ADMIN_EMAIL"
echo "============================================================"
echo ""

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

# Step 2: Build if JAR doesn't exist
if [ ! -f "$JAR_PATH" ]; then
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
for i in $(seq 1 30); do
  HEALTH=$(curl -sS -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health --max-time 3 2>/dev/null || echo "000")
  if [ "$HEALTH" = "200" ]; then
    echo "✓ Backend started successfully! (attempt $i)"
    echo ""
    echo "============================================================"
    echo "✅ SANAD Backend is running on http://localhost:8080"
    echo "============================================================"
    echo ""
    echo "Health: $(curl -sS http://localhost:8080/actuator/health --max-time 5)"
    echo ""
    echo "Admin login:"
    echo "  Email:    $ADMIN_EMAIL"
    echo "  Password: $ADMIN_PASSWORD"
    echo ""
    echo "To stop: kill \$(cat $PID_FILE)"
    echo "To view logs: tail -f $LOG_FILE"
    echo ""
    
    # Step 5: Quick verification
    echo "Running quick verification..."
    LOGIN_RESP=$(curl -sS -X POST "http://localhost:8080/api/v1/auth/login" \
      -H "Content-Type: application/json" \
      -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" \
      --max-time 15 2>&1)
    TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import json,sys; print(json.load(sys.stdin).get('accessToken',''))" 2>/dev/null || echo "")
    if [ -n "$TOKEN" ]; then
      echo "  ✅ Login works (${#TOKEN} chars token)"
    else
      echo "  ❌ Login failed"
    fi
    
    exit 0
  fi
  echo "  [$i] Waiting... (health: $HEALTH)"
  sleep 3
done

echo ""
echo "❌ Backend failed to start within 90 seconds"
echo "Check logs: tail -50 $LOG_FILE"
exit 1
