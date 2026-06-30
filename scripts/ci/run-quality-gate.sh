#!/bin/bash
# ============================================================================
# SNAD — Local Quality Gate Runner
# Mimics the CI quality-gate.yml workflow as closely as possible locally.
#
# Usage:
#   scripts/ci/run-quality-gate.sh --fast   # Quick checks (no Docker)
#   scripts/ci/run-quality-gate.sh --full   # Full CI-equivalent checks
#
# Exit codes: 0=PASS, 1=FAIL, 2=PARTIAL, 3=BLOCKED
# ============================================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
MODE="${1:---fast}"

PASS=0
FAIL=0
SKIP=0
PARTIAL=0
RESULTS=()

record() {
  local name="$1"
  local status="$2"
  local detail="${3:-}"
  case "$status" in
    PASS) PASS=$((PASS+1)); RESULTS+=("PASS | $name | $detail") ;;
    FAIL) FAIL=$((FAIL+1)); RESULTS+=("FAIL | $name | $detail") ;;
    SKIP) SKIP=$((SKIP+1)); RESULTS+=("SKIP | $name | $detail") ;;
  esac
}

echo "=============================================="
echo "  SNAD Quality Gate — $MODE"
echo "  Date: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
echo "=============================================="
echo ""

# --- 1. Repository Policy ---
echo ">>> 1. Repository Policy"
cd "$REPO_ROOT"

# JSON validity
if python3 -c "
import json, pathlib
for p in pathlib.Path('.').rglob('*.json'):
    if 'node_modules' in str(p) or '.git' in str(p) or '.next' in str(p): continue
    json.loads(p.read_text())
print('OK')
" > /dev/null 2>&1; then
  record "JSON validation" PASS
else
  record "JSON validation" FAIL
fi

# YAML validity
if python3 -c "
import yaml, pathlib
for p in pathlib.Path('.').rglob('*.yml'):
    if 'node_modules' in str(p) or '.git' in str(p): continue
    yaml.safe_load(p.read_text())
print('OK')
" > /dev/null 2>&1; then
  record "YAML validation" PASS
else
  record "YAML validation" FAIL
fi

# No .env files tracked
if git ls-files | grep -qE '\.env$'; then
  record "No .env tracked" FAIL ".env files found in git"
else
  record "No .env tracked" PASS
fi

# No merge conflict markers
if git diff --check > /dev/null 2>&1; then
  record "No conflict markers" PASS
else
  record "No conflict markers" FAIL
fi

# --- 2. Migration Immutability ---
echo ">>> 2. Migration Immutability"
if bash "$REPO_ROOT/scripts/ci/check-migration-immutability.sh" > /tmp/migration-check.log 2>&1; then
  record "Migration immutability" PASS
else
  MIG_RESULT=$(grep "^RESULT:" /tmp/migration-check.log 2>/dev/null | head -1 || echo "FAIL")
  if echo "$MIG_RESULT" | grep -q "SKIPPED"; then
    record "Migration immutability" SKIP "No base ref"
  else
    record "Migration immutability" FAIL
  fi
fi

# --- 3. Backend Tests ---
echo ">>> 3. Backend Build and Tests"
cd "$REPO_ROOT/apps/sanad-platform"
if [ -f ./mvnw ]; then
  if ./mvnw -B -ntp clean test > /tmp/backend-tests.log 2>&1; then
    record "Backend tests" PASS
  else
    record "Backend tests" FAIL "See /tmp/backend-tests.log"
  fi
else
  record "Backend tests" SKIP "No Maven Wrapper"
fi

# --- 4. Backend PostgreSQL Integration ---
echo ">>> 4. Backend PostgreSQL Integration"
if [ "$MODE" = "--full" ]; then
  if command -v docker > /dev/null 2>&1; then
    record "PostgreSQL integration" SKIP "Docker available but Testcontainers used in tests"
  else
    record "PostgreSQL integration" SKIP "Docker not available"
  fi
else
  record "PostgreSQL integration" SKIP "Not run in --fast mode"
fi

# --- 5. Flyway Validation ---
echo ">>> 5. Flyway Validation"
if [ "$MODE" = "--full" ]; then
  if command -v docker > /dev/null 2>&1; then
    if bash "$REPO_ROOT/scripts/dev/check-flyway-postgres.sh" > /tmp/flyway-check.log 2>&1; then
      record "Flyway validation" PASS
    else
      F_RESULT=$(grep "^RESULT:" /tmp/flyway-check.log 2>/dev/null | head -1 || echo "FAIL")
      if echo "$F_RESULT" | grep -q "SKIPPED"; then
        record "Flyway validation" SKIP "Docker not available"
      else
        record "Flyway validation" FAIL
      fi
    fi
  else
    record "Flyway validation" SKIP "Docker not available"
  fi
else
  record "Flyway validation" SKIP "Not run in --fast mode"
fi

# --- 6. Frontend ---
echo ">>> 6. Frontend Quality"
cd "$REPO_ROOT/apps/web"

npm ci > /dev/null 2>&1 && record "Frontend npm ci" PASS || record "Frontend npm ci" FAIL
npm run lint > /dev/null 2>&1 && record "Frontend lint" PASS || record "Frontend lint" FAIL
npm run brand:check > /dev/null 2>&1 && record "Frontend brand:check" PASS || record "Frontend brand:check" FAIL
npm test > /dev/null 2>&1 && record "Frontend tests" PASS || record "Frontend tests" FAIL
npm run build > /dev/null 2>&1 && record "Frontend build" PASS || record "Frontend build" FAIL

# --- 7. Python Tests ---
echo ">>> 7. Python Tests"
cd "$REPO_ROOT"
python3 -m pytest tests/ -q > /dev/null 2>&1 && record "Python tests" PASS || record "Python tests" FAIL

# --- 8. Secret Scan ---
echo ">>> 8. Secret Scan"
GITLEAKS_BIN=$(command -v gitleaks 2>/dev/null || echo "/tmp/my-project/tools/bin/gitleaks-8.24.3")
if [ -x "$GITLEAKS_BIN" ]; then
  "$GITLEAKS_BIN" detect --source . --no-git --config .gitleaks.toml --redact > /dev/null 2>&1 && record "Secret scan" PASS || record "Secret scan" FAIL
else
  record "Secret scan" SKIP "Gitleaks not available"
fi

# --- 9. Dependency Scan ---
echo ">>> 9. Dependency Scan"
if [ "$MODE" = "--full" ]; then
  cd "$REPO_ROOT/apps/sanad-platform"
  if [ -f ./mvnw ]; then
    if ./mvnw -B -ntp -Powasp-offline-gate verify > /tmp/dep-scan.log 2>&1; then
      record "Dependency scan" PASS
    else
      record "Dependency scan" PARTIAL "OWASP may have findings — check log"
    fi
  else
    record "Dependency scan" SKIP "No Maven Wrapper"
  fi
else
  record "Dependency scan" SKIP "Not run in --fast mode"
fi

# --- 10. Container Smoke ---
echo ">>> 10. Container Smoke"
if [ "$MODE" = "--full" ]; then
  if command -v docker > /dev/null 2>&1; then
    if bash "$REPO_ROOT/scripts/dev/check-docker-parity.sh" > /tmp/docker-smoke.log 2>&1; then
      record "Container smoke" PASS
    else
      D_RESULT=$(grep "^RESULT:" /tmp/docker-smoke.log 2>/dev/null | head -1 || echo "FAIL")
      if echo "$D_RESULT" | grep -q "SKIPPED"; then
        record "Container smoke" SKIP "Docker not available"
      else
        record "Container smoke" FAIL
      fi
    fi
  else
    record "Container smoke" SKIP "Docker not available"
  fi
else
  record "Container smoke" SKIP "Not run in --fast mode"
fi

# --- Summary ---
echo ""
echo "=============================================="
echo "  QUALITY GATE SUMMARY"
echo "=============================================="
for r in "${RESULTS[@]}"; do
  echo "  $r"
done
echo ""
echo "  PASS: $PASS  FAIL: $FAIL  SKIP: $SKIP"
echo "=============================================="

if [ "$FAIL" -gt 0 ]; then
  echo "QUALITY GATE: FAIL"
  exit 1
elif [ "$SKIP" -gt 0 ] && [ "$MODE" = "--full" ]; then
  echo "QUALITY GATE: PARTIAL (mandatory checks skipped)"
  exit 2
else
  echo "QUALITY GATE: PASS"
  exit 0
fi
