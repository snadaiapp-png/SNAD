#!/bin/bash
# ============================================================================
# SNAD — Local Verification Script
# Runs all locally-available checks in a CI-equivalent order.
#
# Usage:
#   scripts/dev/verify-local.sh --fast   # Quick checks (no Docker/Maven heavy ops)
#   scripts/dev/verify-local.sh --full   # Full checks including Docker + Flyway
# ============================================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
MODE="${1:---fast}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

PASS=0
FAIL=0
SKIP=0
RESULTS=()

record() {
  local name="$1"
  local status="$2"
  local detail="${3:-}"
  case "$status" in
    PASS) PASS=$((PASS+1)); RESULTS+=("${GREEN}PASS${NC} | $name | $detail") ;;
    FAIL) FAIL=$((FAIL+1)); RESULTS+=("${RED}FAIL${NC} | $name | $detail") ;;
    SKIP) SKIP=$((SKIP+1)); RESULTS+=("${YELLOW}SKIP${NC} | $name | $detail") ;;
  esac
}

echo "=============================================="
echo "  SNAD Local Verification — $MODE"
echo "  Repository: $REPO_ROOT"
echo "  Date: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
echo "=============================================="
echo ""

# --- 1. Git clean check ---
echo ">>> 1. Git clean check"
if [ -z "$(cd "$REPO_ROOT" && git status --porcelain)" ]; then
  record "Git clean check" PASS "Working tree is clean"
else
  record "Git clean check" FAIL "Working tree has uncommitted changes"
fi

# --- 2. Tool versions ---
echo ">>> 2. Tool versions"
JAVA_VER=$(java -version 2>&1 | head -1 | sed 's/.*"\([^"]*\)".*/\1/')
record "Java" PASS "version=$JAVA_VER"

NODE_VER=$(node --version 2>&1 || echo "NOT_FOUND")
record "Node.js" PASS "version=$NODE_VER"

PYTHON_VER=$(python3 --version 2>&1 || echo "NOT_FOUND")
record "Python" PASS "version=$PYTHON_VER"

# --- 3. Frontend checks ---
echo ">>> 3. Frontend checks"
cd "$REPO_ROOT/apps/web"

if npm ci > /dev/null 2>&1; then
  record "Frontend npm ci" PASS
else
  record "Frontend npm ci" FAIL
fi

if npm run lint > /dev/null 2>&1; then
  record "Frontend lint" PASS
else
  record "Frontend lint" FAIL
fi

if npm run brand:check > /dev/null 2>&1; then
  record "Frontend brand:check" PASS
else
  record "Frontend brand:check" FAIL
fi

TEST_OUTPUT=$(npm test 2>&1)
if echo "$TEST_OUTPUT" | grep -q "passed"; then
  TEST_COUNT=$(echo "$TEST_OUTPUT" | grep -oP '\d+ passed' | head -1)
  record "Frontend tests" PASS "$TEST_COUNT"
else
  record "Frontend tests" FAIL
fi

if npm run build > /dev/null 2>&1; then
  record "Frontend build" PASS
else
  record "Frontend build" FAIL
fi

# --- 4. Python tests ---
echo ">>> 4. Python tests"
cd "$REPO_ROOT"
if python3 -m pytest tests/ -q > /dev/null 2>&1; then
  record "Python tests" PASS
else
  record "Python tests" FAIL
fi

# --- 5. Backend compile (via Maven Wrapper) ---
echo ">>> 5. Backend compile"
cd "$REPO_ROOT/apps/sanad-platform"
if [ -f ./mvnw ]; then
  if ./mvnw -B -q -DskipTests compile > /dev/null 2>&1; then
    record "Backend compile" PASS
  else
    record "Backend compile" FAIL
  fi
else
  record "Backend compile" SKIP "No Maven Wrapper found"
fi

# --- 6. Secret scan ---
echo ">>> 6. Secret scan"
cd "$REPO_ROOT"
if command -v gitleaks > /dev/null 2>&1 || [ -x /tmp/my-project/tools/bin/gitleaks-8.24.3 ]; then
  GITLEAKS_BIN=$(command -v gitleaks 2>/dev/null || echo "/tmp/my-project/tools/bin/gitleaks-8.24.3")
  if "$GITLEAKS_BIN" detect --source . --no-git --config .gitleaks.toml --redact > /dev/null 2>&1; then
    record "Secret scan" PASS "0 findings"
  else
    record "Secret scan" FAIL "Findings detected"
  fi
else
  record "Secret scan" SKIP "Gitleaks not available"
fi

# --- 7. Docker parity (full mode only) ---
if [ "$MODE" = "--full" ]; then
  echo ">>> 7. Docker parity"
  if bash "$REPO_ROOT/scripts/dev/check-docker-parity.sh" > /tmp/docker-parity.log 2>&1; then
    record "Docker parity" PASS
  else
    DOCKER_RESULT=$(grep "^RESULT:" /tmp/docker-parity.log 2>/dev/null | head -1 || echo "UNKNOWN")
    if echo "$DOCKER_RESULT" | grep -q "SKIPPED"; then
      record "Docker parity" SKIP "Docker not available"
    else
      record "Docker parity" FAIL
    fi
  fi

  # --- 8. Flyway/PostgreSQL parity (full mode only) ---
  echo ">>> 8. Flyway/PostgreSQL parity"
  if bash "$REPO_ROOT/scripts/dev/check-flyway-postgres.sh" > /tmp/flyway-parity.log 2>&1; then
    record "Flyway/PostgreSQL" PASS
  else
    FLYWAY_RESULT=$(grep "^RESULT:" /tmp/flyway-parity.log 2>/dev/null | head -1 || echo "UNKNOWN")
    if echo "$FLYWAY_RESULT" | grep -q "SKIPPED"; then
      record "Flyway/PostgreSQL" SKIP "Docker not available"
    else
      record "Flyway/PostgreSQL" FAIL
    fi
  fi
else
  record "Docker parity" SKIP "Not run in --fast mode"
  record "Flyway/PostgreSQL" SKIP "Not run in --fast mode"
fi

# --- Summary ---
echo ""
echo "=============================================="
echo "  SUMMARY"
echo "=============================================="
for r in "${RESULTS[@]}"; do
  echo "  $r"
done
echo ""
echo "  PASS: $PASS  FAIL: $FAIL  SKIP: $SKIP"
echo "=============================================="

if [ "$FAIL" -gt 0 ]; then
  echo "FINAL: FAIL"
  exit 1
else
  echo "FINAL: PASS"
  exit 0
fi
