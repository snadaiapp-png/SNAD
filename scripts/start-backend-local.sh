#!/usr/bin/env bash
# SNAD Backend Local Runner
# Usage: bash scripts/start-backend-local.sh
#
# Starts the Spring Boot backend in local profile (H2 in-memory DB)
# and keeps it alive in the current shell session.
#
# Backend stays alive ONLY as long as this script's shell session runs.
# When the shell exits, the backend is terminated.

set -e

export PATH="/home/z/.local/bin:/home/z/.npm-global/bin:$PATH"
export JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"

REPO_ROOT="/home/z/my-project/SNAD"
BACKEND_DIR="$REPO_ROOT/apps/sanad-platform"
LOG_DIR="$REPO_ROOT/logs"
BACKEND_LOG="$LOG_DIR/backend-runtime.log"

mkdir -p "$LOG_DIR"

cd "$BACKEND_DIR"

echo "=== SNAD Backend Local Runner ==="
echo "Working directory: $(pwd)"
echo "Profile: local (H2 in-memory database)"
echo "Port: 8080"
echo "Log file: $BACKEND_LOG"
echo ""

# Check if Maven is available
if ! command -v mvn &>/dev/null; then
    echo "ERROR: Maven is not installed or not in PATH"
    echo "Install Maven: see /home/z/.local/share/apache-maven-3.9.9/"
    exit 1
fi

# Check if Java is available
if ! command -v java &>/dev/null; then
    echo "ERROR: Java is not installed"
    exit 1
fi

echo "Maven version: $(mvn --version | head -1)"
echo "Java version:  $(java --version | head -1)"
echo ""

# Start backend
echo "=== Starting Spring Boot backend (profile=local) ==="
mvn spring-boot:run \
    -Dspring-boot.run.profiles=local \
    -Dspring-boot.run.jvmArguments="-Xmx768m" \
    -Dspring-boot.run.fork=false \
    -B --no-transfer-progress \
    > "$BACKEND_LOG" 2>&1 &
MAVEN_PID=$!
echo "$MAVEN_PID" > "$LOG_DIR/backend.pid"
echo "Maven PID: $MAVEN_PID"
echo ""

# Wait for health
echo "=== Waiting for startup (max 60s) ==="
START_TIME=$(date +%s)
while true; do
    ELAPSED=$(( $(date +%s) - START_TIME ))
    if ! kill -0 $MAVEN_PID 2>/dev/null; then
        echo "[$ELAPSED s] ERROR: Process died during startup"
        echo "--- Last 30 log lines ---"
        tail -30 "$BACKEND_LOG"
        exit 1
    fi
    HEALTH=$(curl -sS --max-time 3 -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health 2>/dev/null)
    if [ "$HEALTH" = "200" ]; then
        echo "[$ELAPSED s] Backend is UP (HTTP 200 from /actuator/health)"
        break
    fi
    if [ $ELAPSED -ge 60 ]; then
        echo "[$ELAPSED s] TIMEOUT: Backend did not start within 60s"
        echo "--- Last 30 log lines ---"
        tail -30 "$BACKEND_LOG"
        exit 1
    fi
    sleep 5
done

echo ""
echo "=== Backend Status ==="
curl -sS http://localhost:8080/actuator/health | python3 -m json.tool 2>/dev/null || curl -sS http://localhost:8080/actuator/health

echo ""
echo "=== Available endpoints ==="
echo "  Health:        http://localhost:8080/actuator/health"
echo "  Info:          http://localhost:8080/actuator/info"
echo "  Beans:         http://localhost:8080/actuator/beans"
echo "  H2 Console:    http://localhost:8080/h2-console"
echo "  Swagger UI:    http://localhost:8080/swagger-ui.html"
echo "  OpenAPI docs:  http://localhost:8080/v3/api-docs"
echo ""
echo "  Auth API:      http://localhost:8080/api/v1/auth/*"
echo "  Users API:     http://localhost:8080/api/v1/users"
echo "  CRM API:       http://localhost:8080/api/v1/crm/*"
echo "  Organizations: http://localhost:8080/api/v1/organizations"
echo ""
echo "=== Backend is running. Press Ctrl+C to stop. ==="
echo "=== Log file: tail -f $BACKEND_LOG ==="
echo ""

# Save status
cat > "$LOG_DIR/backend-status.json" << EOF
{
  "status": "RUNNING",
  "maven_pid": $MAVEN_PID,
  "port": 8080,
  "health_endpoint": "http://localhost:8080/actuator/health",
  "h2_console": "http://localhost:8080/h2-console",
  "swagger_ui": "http://localhost:8080/swagger-ui.html",
  "openapi_docs": "http://localhost:8080/v3/api-docs",
  "started_at": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "profile": "local",
  "database": "H2 in-memory (jdbc:h2:mem:sanad)",
  "log_file": "$BACKEND_LOG"
}
EOF

# Keep the process alive — wait for it to exit
wait $MAVEN_PID
EXIT_CODE=$?
echo ""
echo "Backend exited with code: $EXIT_CODE"
exit $EXIT_CODE
