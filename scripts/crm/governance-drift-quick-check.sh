#!/usr/bin/env bash
# =============================================================================
# SNAD CRM Governance Drift — Quick Verification
# -----------------------------------------------------------------------------
# Lightweight version of governance-drift-check.sh that runs the same
# governance rules using grep -r for fast single-pass scanning.
# Full validation runs in CI via the original multi-pass script.
#
# Exit codes:
#   0 — no drift detected
#   1 — drift detected (one or more violations)
# =============================================================================

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
REPO_ROOT="${REPO_ROOT_OVERRIDE:-$REPO_ROOT}"

DOCS_CRM_DIR="${REPO_ROOT}/docs/crm"
DOCS_RELEASE_DIR="${REPO_ROOT}/docs/release"
BASELINE_FILE="${DOCS_CRM_DIR}/CRM-CURRENT-BASELINE.md"
ROADMAP_FILE="${DOCS_CRM_DIR}/CRM-ENTERPRISE-EXECUTION-ROADMAP.md"
MIGRATION_DIR="${REPO_ROOT}/apps/sanad-platform/src/main/resources/db/migration"
PRODUCTION_GO_RECORD="${DOCS_RELEASE_DIR}/CRM-PRODUCTION-GO.md"
CRM_CONTROLLER="${REPO_ROOT}/apps/sanad-platform/src/main/java/com/sanad/platform/crm/web/CrmController.java"

VIOLATIONS=()
add_violation() { VIOLATIONS+=("$1"); }

echo "=== SNAD CRM Governance Drift Check (Quick) ==="
echo "Repository root: ${REPO_ROOT}"
echo ""

# --- 1. Baseline and roadmap exist ---
echo -n "[1] Baseline & roadmap exist ... "
PASS=true
[[ ! -s "$BASELINE_FILE" ]] && { add_violation "Missing/empty baseline"; PASS=false; }
[[ ! -s "$ROADMAP_FILE" ]] && { add_violation "Missing/empty roadmap"; PASS=false; }
$PASS && echo "PASS" || echo "FAIL"

# --- 2. CRM code present ---
echo -n "[2] CRM source code present ... "
crm_exists=false
[[ -f "$CRM_CONTROLLER" ]] && crm_exists=true
$crm_exists && echo "YES" || echo "NO"

# --- 3. No stale NOT STARTED claims ---
echo -n "[3] No stale NOT STARTED claims ... "
PASS=true
if $crm_exists; then
  hits=$(grep -rlP 'CRM_PRODUCT_BUILD:\s*NOT STARTED' "$DOCS_CRM_DIR" 2>/dev/null | head -20)
  if [[ -n "$hits" ]]; then
    # Filter out disqualifier lines
    real_violations=0
    while IFS= read -r f; do
      if ! grep -P 'CRM_PRODUCT_BUILD:\s*NOT STARTED' "$f" 2>/dev/null | grep -qiE 'historically|supersede|superseded|no longer|previously|older|stale|was |were |claim|claimed|says|said'; then
        real_violations=$((real_violations + 1))
      fi
    done <<< "$hits"
    if (( real_violations > 0 )); then
      add_violation "Found ${real_violations} doc(s) with stale NOT STARTED claim"
      PASS=false
    fi
  fi
fi
$PASS && echo "PASS" || echo "FAIL"

# --- 4. Migration files consistent ---
echo -n "[4] Migrations consistent ... "
PASS=true
EXPECTED_MIGRATIONS=(
  "V20260702_1__create_unified_crm_core.sql"
  "V20260702_2__reconcile_admin_role_and_capabilities.sql"
  "V20260702_3__complete_crm_imports_custom_fields.sql"
  "V20260706_1__create_tenant_quota.sql"
  "V20260711_1__create_subscription_change_events.sql"
  "V20260713_1__create_crm_idempotency_records.sql"
)
if [[ -d "$MIGRATION_DIR" ]]; then
  for m in "${EXPECTED_MIGRATIONS[@]}"; do
    [[ ! -f "${MIGRATION_DIR}/${m}" ]] && { add_violation "Migration missing: ${m}"; PASS=false; }
  done
fi
$PASS && echo "PASS" || echo "FAIL"

# --- 5. Production GO record ---
echo -n "[5] Production GO record ... "
PASS=true
if grep -rqiE 'production GO|commercial go-live' "$DOCS_CRM_DIR" 2>/dev/null; then
  if [[ ! -s "$PRODUCTION_GO_RECORD" ]]; then
    add_violation "Production GO claimed but decision record missing"
    PASS=false
  fi
fi
$PASS && echo "PASS" || echo "FAIL"

# --- 6. README status ---
echo -n "[6] README status ... "
PASS=true
if [[ -s "${DOCS_CRM_DIR}/README.md" ]]; then
  if grep -qP 'CRM_PRODUCT_BUILD:\s*NOT STARTED' "${DOCS_CRM_DIR}/README.md" 2>/dev/null; then
    if ! grep -P 'CRM_PRODUCT_BUILD:\s*NOT STARTED' "${DOCS_CRM_DIR}/README.md" 2>/dev/null | grep -qiE 'historically|supersede|superseded|no longer|previously'; then
      add_violation "README claims NOT STARTED"
      PASS=false
    fi
  fi
fi
$PASS && echo "PASS" || echo "FAIL"

# --- 7. Issue #189 balance ---
echo -n "[7] Issue #189 balance ... "
PASS=true
doc_refs=$(grep -rl '#189\|Issue #189' "$DOCS_CRM_DIR" 2>/dev/null | wc -l)
if (( doc_refs > 0 )); then
  wf_refs=$(grep -rl '#189\|Issue #189' "${REPO_ROOT}/.github/workflows/" 2>/dev/null | wc -l)
  if (( wf_refs == 0 )); then
    add_violation "Issue #189 in ${doc_refs} doc(s) but no workflow ref"
    PASS=false
  fi
fi
$PASS && echo "PASS" || echo "FAIL"

# --- 8. Stale capability count ---
echo -n "[8] Stale capability count ... "
PASS=true
stale=$(grep -rlP '\b(14|15)\s+CRM\s+(capabilities|features|modules)' "$DOCS_CRM_DIR" 2>/dev/null | wc -l)
if (( stale > 0 )); then
  add_violation "Found ${stale} doc(s) with stale count (14/15)"
  PASS=false
fi
$PASS && echo "PASS" || echo "FAIL"

# --- 9. Closed milestones without stage report ---
echo -n "[9] Closed milestones have stage reports ... "
PASS=true
closed=$(grep -rlP 'milestone.*CLOSED' "$DOCS_CRM_DIR" 2>/dev/null || true)
if [[ -n "$closed" ]]; then
  while IFS= read -r f; do
    if ! grep -qiE 'stage.report' "$f" 2>/dev/null; then
      add_violation "CLOSED milestone without stage report: ${f#${REPO_ROOT}/}"
      PASS=false
    fi
  done <<< "$closed"
fi
$PASS && echo "PASS" || echo "FAIL"

# =============================================================================
# Summary
# =============================================================================
echo ""
echo "==========================================="
if (( ${#VIOLATIONS[@]} == 0 )); then
  echo "RESULT: PASS — No governance drift detected."
  echo "EXIT_CODE=0"
  exit 0
else
  echo "RESULT: FAIL — ${#VIOLATIONS[@]} violation(s):"
  echo ""
  for (( i=0; i<${#VIOLATIONS[@]}; i++ )); do
    echo "  $((i+1)). ${VIOLATIONS[$i]}"
  done
  echo ""
  echo "EXIT_CODE=1"
  exit 1
fi
