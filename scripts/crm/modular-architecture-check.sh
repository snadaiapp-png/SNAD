#!/usr/bin/env bash
set -euo pipefail
SCRIPT_PATH="${BASH_SOURCE[0]}"
REPO_ROOT="$(cd "$(dirname "${SCRIPT_PATH}")/../.." && pwd)"

echo "=== CRM Modular Architecture Check ==="

# Check all 8 modules exist
MODULES=("party" "lead" "opportunity" "activity" "configuration" "dataquality" "query" "integration")
MISSING=0
for mod in "${MODULES[@]}"; do
  dir="${REPO_ROOT}/apps/sanad-platform/src/main/java/com/sanad/platform/crm/${mod}"
  if [ ! -d "$dir" ]; then
    echo "  MISSING: crm.${mod}"
    MISSING=$((MISSING + 1))
  else
    echo "  OK: crm.${mod}"
  fi
done

if [ "$MISSING" -gt 0 ]; then
  echo "FAIL: $MISSING module(s) missing"
  exit 1
fi

# Check domain packages don't depend on Spring Web/JDBC
echo ""
echo "Checking domain layer isolation..."
VIOLATIONS=$(grep -rl "import org.springframework.web\|import org.springframework.jdbc\|import jakarta.persistence\|import javax.persistence" \
  "${REPO_ROOT}/apps/sanad-platform/src/main/java/com/sanad/platform/crm"/*/domain/ 2>/dev/null | wc -l || echo 0)

if [ "$VIOLATIONS" -gt 0 ]; then
  echo "FAIL: $VIOLATIONS domain file(s) depend on forbidden frameworks"
  exit 1
fi

echo "Domain isolation: PASS"
echo ""
echo "CRM Modular Architecture Check: PASS"
