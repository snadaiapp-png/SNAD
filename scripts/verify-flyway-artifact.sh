#!/usr/bin/env bash
# ============================================================
# SANAD Platform — Flyway Artifact Verification Script
# Stage 05A.2.9.1 §4
#
# Verifies that the built JAR artifact contains a consistent,
# conflict-free set of Flyway migrations. Fails (exit 1) on any
# of the following violations:
#
#   1. V15 is missing from the source tree.
#   2. V15 is missing from the built JAR.
#   3. More than one migration carries version 15 (duplicate V15).
#   4. A versioned migration exists in BOTH db/migration and
#      db/migration-pg-only (cross-directory duplicate).
#   5. The JAR is missing application-prod.yml.
#   6. The JAR's application-prod.yml does not contain the
#      required Flyway configuration:
#        flyway.enabled = true
#        flyway.locations includes both db/migration and
#          db/migration-pg-only
#        flyway.validate-on-migrate = true
#        flyway.clean-disabled = true
#
# Usage:
#   scripts/verify-flyway-artifact.sh [JAR_PATH]
#
# If JAR_PATH is not provided, the script auto-detects the JAR
# in apps/sanad-platform/target/.
#
# This script is intended to run:
#   - Locally after `mvn clean package`
#   - In CI as a mandatory gate before deployment
#   - In the Docker build as a verification step
# ============================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP_DIR="${REPO_ROOT}/apps/sanad-platform"
SRC_MIGRATION_DIR="${APP_DIR}/src/main/resources/db/migration"
SRC_PG_ONLY_DIR="${APP_DIR}/src/main/resources/db/migration-pg-only"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

fail() {
    echo -e "${RED}FAIL: $1${NC}"
    exit 1
}

info() {
    echo -e "${GREEN}INFO: $1${NC}"
}

warn() {
    echo -e "${YELLOW}WARN: $1${NC}"
}

# --- 0. Find the JAR ---
JAR_PATH="${1:-}"
if [ -z "$JAR_PATH" ]; then
    JAR_PATH="$(find "${APP_DIR}/target" -maxdepth 1 -name 'sanad-platform-*.jar' ! -name '*.original' 2>/dev/null | head -n 1)"
fi
if [ -z "$JAR_PATH" ] || [ ! -f "$JAR_PATH" ]; then
    fail "JAR not found. Build the project first or provide JAR_PATH as argument."
fi
info "Verifying JAR: ${JAR_PATH}"

# Use unzip (available in most environments including Alpine)
list_jar() {
    unzip -l "$JAR_PATH" 2>/dev/null | awk '{print $4}'
}

# --- 1. V15 must exist in source ---
info "Checking V15 in source tree..."

# V15 can be SQL (.sql) or Java (.java)
V15_SQL_SOURCE="$(find "${SRC_MIGRATION_DIR}" "${SRC_PG_ONLY_DIR}" -name 'V15__*.sql' 2>/dev/null || true)"
V15_JAVA_SOURCE="$(find "${APP_DIR}/src" -name 'V15__*.java' 2>/dev/null || true)"

if [ -z "$V15_SQL_SOURCE" ] && [ -z "$V15_JAVA_SOURCE" ]; then
    fail "V15 migration not found in source tree (neither SQL nor Java)."
fi
info "V15 source files: ${V15_SQL_SOURCE:-none-sql} ${V15_JAVA_SOURCE:-none-java}"

# --- 2. V15 must exist in JAR ---
info "Checking V15 in JAR..."
V15_IN_JAR_SQL="$(list_jar | grep -E 'db/migration.*/V15__.*\.sql' || true)"
V15_IN_JAR_JAVA="$(list_jar | grep -E 'V15__.*\.class' || true)"

if [ -z "$V15_IN_JAR_SQL" ] && [ -z "$V15_IN_JAR_JAVA" ]; then
    fail "V15 migration not found in JAR (neither SQL nor Java class)."
fi
info "V15 in JAR: ${V15_IN_JAR_SQL:-none-sql} ${V15_IN_JAR_JAVA:-none-java}"

# --- 3. No duplicate V15 (more than one migration with version 15) ---
info "Checking for duplicate V15..."
V15_COUNT=0
[ -n "$V15_SQL_SOURCE" ] && V15_COUNT=$((V15_COUNT + 1))
[ -n "$V15_JAVA_SOURCE" ] && V15_COUNT=$((V15_COUNT + 1))
if [ "$V15_COUNT" -gt 1 ]; then
    fail "Duplicate V15: found both SQL and Java V15 migrations. Only one V15 is allowed."
fi
info "V15 count: ${V15_COUNT} (OK)"

# --- 4. No cross-directory duplicate versioned migrations ---
info "Checking for cross-directory duplicate versions..."
SQL_VERSIONS_FILE="$(mktemp)"
PG_ONLY_VERSIONS_FILE="$(mktemp)"
ls -1 "${SRC_MIGRATION_DIR}" 2>/dev/null | sed -n 's/^V\([0-9]*\)__.*/\1/p' | grep -v '^$' | sort -n > "$SQL_VERSIONS_FILE"
ls -1 "${SRC_PG_ONLY_DIR}" 2>/dev/null | sed -n 's/^V\([0-9]*\)__.*/\1/p' | grep -v '^$' | sort -n > "$PG_ONLY_VERSIONS_FILE"
DUPLICATES="$(comm -12 "$SQL_VERSIONS_FILE" "$PG_ONLY_VERSIONS_FILE" 2>/dev/null || true)"
rm -f "$SQL_VERSIONS_FILE" "$PG_ONLY_VERSIONS_FILE"
if [ -n "$DUPLICATES" ]; then
    fail "Cross-directory duplicate migration versions found: ${DUPLICATES}"
fi
info "No cross-directory duplicate versions (OK)"

# --- 5. application-prod.yml must be in JAR ---
info "Checking application-prod.yml in JAR..."
if ! list_jar | grep -q 'BOOT-INF/classes/application-prod.yml'; then
    fail "application-prod.yml not found in JAR."
fi
info "application-prod.yml present in JAR (OK)"

# --- 6. Verify Flyway config in application-prod.yml inside JAR ---
info "Checking Flyway config in application-prod.yml..."
PROD_YML_CONTENT="$(unzip -p "$JAR_PATH" "BOOT-INF/classes/application-prod.yml" 2>/dev/null || true)"
if [ -z "$PROD_YML_CONTENT" ]; then
    fail "Could not extract application-prod.yml from JAR."
fi

check_contains() {
    if ! echo "$PROD_YML_CONTENT" | grep -q "$1"; then
        fail "application-prod.yml missing required setting: $1"
    fi
}

check_contains "flyway:"
check_contains "enabled: \${FLYWAY_ENABLED:true}"
check_contains "locations: classpath:db/migration,classpath:db/migration-pg-only"
check_contains "validate-on-migrate: true"
check_contains "clean-disabled: true"
info "Flyway config in application-prod.yml verified (OK)"

# --- 7. Verify both migration directories are in JAR ---
info "Checking migration directories in JAR..."
if ! list_jar | grep -q 'BOOT-INF/classes/db/migration/'; then
    fail "db/migration/ directory not found in JAR."
fi
if ! list_jar | grep -q 'BOOT-INF/classes/db/migration-pg-only/'; then
    fail "db/migration-pg-only/ directory not found in JAR."
fi
info "Both migration directories present in JAR (OK)"

# --- 8. Verify reconciler migration V20260702_1 is in JAR ---
info "Checking V20260702_1 reconciler in JAR..."
if ! list_jar | grep -q 'V20260702_1__reconcile_admin_role_and_capabilities.sql'; then
    fail "V20260702_1 reconciler migration not found in JAR."
fi
info "V20260702_1 reconciler present (OK)"

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Flyway artifact verification: ALL CHECKS PASS${NC}"
echo -e "${GREEN}========================================${NC}"
exit 0
