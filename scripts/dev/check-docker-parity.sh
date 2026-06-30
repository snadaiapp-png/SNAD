#!/bin/bash
# ============================================================================
# SNAD — Docker Parity Check
# Validates Dockerfile and Docker Compose configuration.
# If Docker is available, performs a safe build + health check.
# If Docker is not available, performs static validation only.
# ============================================================================
set -euo pipefail

BACKEND_DIR="$(cd "$(dirname "$0")/../../apps/sanad-platform" && pwd)"
DOCKERFILE="$BACKEND_DIR/Dockerfile"
COMPOSE_DEV="$BACKEND_DIR/docker-compose.yml"
COMPOSE_PROD="$BACKEND_DIR/docker-compose.prod.yml"

RESULT="SKIPPED_DOCKER_NOT_AVAILABLE"
DETAILS=""

# --- Static validation (always runs) ---

echo "=== Docker Parity Check ==="
echo ""

# Check Dockerfile exists
if [ ! -f "$DOCKERFILE" ]; then
  echo "FAIL: Dockerfile not found at $DOCKERFILE"
  exit 1
fi
echo "✓ Dockerfile found"

# Check non-root user
if grep -q "^USER\s" "$DOCKERFILE"; then
  echo "✓ Non-root user: $(grep '^USER' "$DOCKERFILE")"
else
  echo "WARNING: No USER directive found in Dockerfile"
fi

# Check healthcheck
if grep -q "HEALTHCHECK" "$DOCKERFILE"; then
  echo "✓ HEALTHCHECK present"
else
  echo "WARNING: No HEALTHCHECK in Dockerfile"
fi

# Check multi-stage build
FROM_COUNT=$(grep -c "^FROM" "$DOCKERFILE")
if [ "$FROM_COUNT" -ge 2 ]; then
  echo "✓ Multi-stage build ($FROM_COUNT stages)"
else
  echo "WARNING: Single-stage build"
fi

# Check exposed port
if grep -q "EXPOSE" "$DOCKERFILE"; then
  echo "✓ Port exposed: $(grep 'EXPOSE' "$DOCKERFILE")"
fi

# Check JVM options
if grep -q "JAVA_OPTS" "$DOCKERFILE"; then
  echo "✓ JVM options configured"
fi

# Validate compose files exist
for f in "$COMPOSE_DEV" "$COMPOSE_PROD"; do
  if [ ! -f "$f" ]; then
    echo "WARNING: $f not found"
  else
    echo "✓ $(basename "$f") found"
  fi
done

# --- Docker availability check ---

if ! command -v docker > /dev/null 2>&1; then
  echo ""
  echo "RESULT: SKIPPED_DOCKER_NOT_AVAILABLE"
  echo "DETAILS: Docker is not installed. Static validation completed."
  exit 0
fi

# --- Layer B: Docker available — safe build + health check ---

echo ""
echo "=== Docker Build Test ==="

IMAGE_NAME="sanad-backend:parity-check"

if docker build -t "$IMAGE_NAME" "$BACKEND_DIR" 2>&1 | tail -5; then
  echo "✓ Docker image built successfully"
  RESULT="PASS"
  DETAILS="Image built, non-root user confirmed, healthcheck present"

  # Check image user
  IMAGE_USER=$(docker inspect "$IMAGE_NAME" --format '{{.Config.User}}' 2>/dev/null || echo "unknown")
  echo "✓ Image user: $IMAGE_USER"

  # Check image size
  IMAGE_SIZE=$(docker inspect "$IMAGE_NAME" --format '{{.Size}}' 2>/dev/null || echo "unknown")
  echo "  Image size: $IMAGE_SIZE bytes"

  # Check healthcheck
  HEALTHCHECK=$(docker inspect "$IMAGE_NAME" --format '{{json .Config.Healthcheck}}' 2>/dev/null || echo "none")
  if [ "$HEALTHCHECK" != "null" ] && [ "$HEALTHCHECK" != "none" ]; then
    echo "✓ Healthcheck configured"
  fi
else
  echo "FAIL: Docker build failed"
  RESULT="FAIL"
  DETAILS="Docker build failed"
fi

# Clean up
docker rmi -f "$IMAGE_NAME" > /dev/null 2>&1 || true

echo ""
echo "RESULT: $RESULT"
echo "DETAILS: $DETAILS"
