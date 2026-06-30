#!/bin/bash
# ============================================================================
# SNAD — Migration Immutability Check
# Verifies that existing Flyway migrations are not modified, deleted, or renamed.
# Only new migrations are allowed.
# ============================================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
MIGRATION_DIR="$REPO_ROOT/apps/sanad-platform/src/main/resources/db/migration"

echo "=== Migration Immutability Check ==="
echo ""

# Determine the base (main branch) to compare against
BASE_REF="origin/main"
if ! git -C "$REPO_ROOT" rev-parse --verify "$BASE_REF" > /dev/null 2>&1; then
  echo "WARNING: $BASE_REF not found — skipping immutability check (first run or no remote)"
  echo "RESULT: SKIPPED_NO_BASE_REF"
  exit 0
fi

# Get list of migration files on main
MAIN_MIGRATIONS=$(git -C "$REPO_ROOT" ls-tree --name-only "$BASE_REF" -- "$MIGRATION_DIR" 2>/dev/null | grep "\.sql$" | xargs -I{} basename {} || true)

if [ -z "$MAIN_MIGRATIONS" ]; then
  echo "No migrations found on $BASE_REF — all migrations are new"
  echo "RESULT: PASS"
  exit 0
fi

VIOLATIONS=0

# Check each migration that exists on main
for migration in $MAIN_MIGRATIONS; do
  FILE_PATH="$MIGRATION_DIR/$migration"

  # Check if file still exists
  if [ ! -f "$FILE_PATH" ]; then
    echo "VIOLATION: Migration deleted: $migration"
    VIOLATIONS=$((VIOLATIONS + 1))
    continue
  fi

  # Check if file content has changed
  BASE_CONTENT=$(git -C "$REPO_ROOT" show "$BASE_REF:$MIGRATION_DIR/$migration" 2>/dev/null || echo "")
  CURRENT_CONTENT=$(cat "$FILE_PATH" 2>/dev/null || echo "")

  if [ "$BASE_CONTENT" != "$CURRENT_CONTENT" ]; then
    echo "VIOLATION: Migration modified: $migration"
    VIOLATIONS=$((VIOLATIONS + 1))
  fi
done

# Check for duplicate version numbers
VERSIONS=$(ls "$MIGRATION_DIR"/V*.sql 2>/dev/null | xargs -I{} basename {} | sed 's/__.*//' | sort)
DUPLICATES=$(echo "$VERSIONS" | uniq -d)
if [ -n "$DUPLICATES" ]; then
  echo "VIOLATION: Duplicate migration versions:"
  echo "$DUPLICATES"
  VIOLATIONS=$((VIOLATIONS + 1))
fi

# Check naming convention (V<n>__<description>.sql or V<date>__<description>.sql)
for f in "$MIGRATION_DIR"/V*.sql; do
  BASENAME=$(basename "$f")
  if ! echo "$BASENAME" | grep -qE "^V[0-9]+(__[a-zA-Z0-9_]+)?\.sql$"; then
    echo "VIOLATION: Invalid migration naming: $BASENAME"
    VIOLATIONS=$((VIOLATIONS + 1))
  fi
done

echo ""
if [ "$VIOLATIONS" -gt 0 ]; then
  echo "RESULT: FAIL ($VIOLATIONS violations)"
  exit 1
else
  echo "RESULT: PASS (all migrations immutable, no duplicates, valid naming)"
  exit 0
fi
