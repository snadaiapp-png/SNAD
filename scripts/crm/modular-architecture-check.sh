#!/usr/bin/env bash
set -euo pipefail

SCRIPT_PATH="${BASH_SOURCE[0]}"
REPO_ROOT="$(cd "$(dirname "${SCRIPT_PATH}")/../.." && pwd)"
CRM_BASE="${REPO_ROOT}/apps/sanad-platform/src/main/java/com/sanad/platform/crm"

echo "=== CRM Modular Architecture Check ==="

MODULES=("party" "lead" "opportunity" "activity" "task" "configuration" "query" "integration")
MISSING=0
for mod in "${MODULES[@]}"; do
  dir="${CRM_BASE}/${mod}"
  if [ ! -d "$dir" ]; then
    echo "  MISSING: crm.${mod}"
    MISSING=$((MISSING + 1))
  else
    file_count=$(find "$dir" -name "*.java" | wc -l)
    if [ "$file_count" -eq 0 ]; then
      echo "  EMPTY: crm.${mod}"
      MISSING=$((MISSING + 1))
    else
      echo "  OK: crm.${mod} ($file_count files)"
    fi
  fi
done
if [ "$MISSING" -gt 0 ]; then
  echo "FAIL: $MISSING module(s) missing or empty"
  exit 1
fi

echo ""
echo "Checking domain layer isolation..."
DOMAIN_DIRS=$(find "${CRM_BASE}" -type d -name "domain" 2>/dev/null)
VIOLATIONS=0
for d in $DOMAIN_DIRS; do
  hits=$(grep -rl "import org.springframework.web\|import org.springframework.jdbc\|import jakarta.persistence\|import javax.persistence" "$d" 2>/dev/null || true)
  count=$(echo "$hits" | grep -c "." || true)
  VIOLATIONS=$((VIOLATIONS + count))
done
if [ "$VIOLATIONS" -gt 0 ]; then
  echo "FAIL: $VIOLATIONS domain file(s) depend on forbidden frameworks"
  exit 1
fi
echo "Domain isolation: PASS"

echo ""
echo "Checking domain ports for Map<String,Object> leakage..."
MAP_VIOLATIONS=0
for d in $DOMAIN_DIRS; do
  hits=$(grep -rn "Map<String, Object>" "$d" 2>/dev/null || true)
  count=$(echo "$hits" | grep -c "." || true)
  MAP_VIOLATIONS=$((MAP_VIOLATIONS + count))
done
if [ "$MAP_VIOLATIONS" -gt 0 ]; then
  echo "FAIL: $MAP_VIOLATIONS Map<String,Object> usage in domain ports"
  exit 1
fi
echo "Domain port typing: PASS"

echo ""
echo "Checking for implementation placeholders..."
# The previous raw-word scan rejected legitimate Javadocs that discussed
# placeholder prevention. This semantic gate detects actual placeholder
# artifacts and executable non-implementations instead of prose.
PLACEHOLDER_FILES=$(find "${CRM_BASE}" -type f -name "*Placeholder*.java" -print 2>/dev/null || true)
PLACEHOLDER_DECLARATIONS=$(grep -RInE \
  '(^|[[:space:]])(class|interface|enum|record)[[:space:]]+[A-Za-z0-9_]*Placeholder[A-Za-z0-9_]*' \
  "${CRM_BASE}" 2>/dev/null || true)
PLACEHOLDER_MARKERS=$(grep -RInE \
  'IMPLEMENTATION_PLACEHOLDER|TODO[[:space:]]*:[[:space:]]*(implement|placeholder)|throw[[:space:]]+new[[:space:]]+UnsupportedOperationException' \
  "${CRM_BASE}" 2>/dev/null || true)

PH_COUNT=0
for result in "$PLACEHOLDER_FILES" "$PLACEHOLDER_DECLARATIONS" "$PLACEHOLDER_MARKERS"; do
  if [ -n "$result" ]; then
    echo "$result"
    count=$(printf '%s\n' "$result" | grep -c "." || true)
    PH_COUNT=$((PH_COUNT + count))
  fi
done
if [ "$PH_COUNT" -gt 0 ]; then
  echo "FAIL: $PH_COUNT implementation placeholder marker(s) found"
  exit 1
fi
echo "Implementation placeholder check: PASS"

echo ""
echo "CRM Modular Architecture Check: PASS"
