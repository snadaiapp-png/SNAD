#!/usr/bin/env bash
set -euo pipefail
SCRIPT_PATH="${BASH_SOURCE[0]}"
REPO_ROOT="$(cd "$(dirname "${SCRIPT_PATH}")/../.." && pwd)"
CRM_BASE="${REPO_ROOT}/apps/sanad-platform/src/main/java/com/sanad/platform/crm"

echo "=== CRM Modular Architecture Check ==="

MODULES=("party" "lead" "opportunity" "activity" "configuration" "query" "integration")
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
if [ "$MISSING" -gt 0 ]; then echo "FAIL: $MISSING module(s) missing or empty"; exit 1; fi

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
echo "Checking for placeholder classes..."
PLACEHOLDERS=$(find "${CRM_BASE}" -name "*.java" -exec grep -l "Placeholder\|placeholder" {} \; 2>/dev/null || true)
PH_COUNT=$(echo "$PLACEHOLDERS" | grep -c "." || true)
if [ "$PH_COUNT" -gt 0 ]; then
  echo "FAIL: $PH_COUNT file(s) contain placeholder markers"
  exit 1
fi
echo "Placeholder check: PASS"

echo ""
echo "CRM Modular Architecture Check: PASS"
