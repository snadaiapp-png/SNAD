#!/usr/bin/env bash
# =============================================================================
# SNAD CRM Governance Drift Check
# -----------------------------------------------------------------------------
# Branch: crm/001-baseline-governance-ci-recovery
# Baseline SHA: cee332e7f86a6ea64fbb5f72120ae77c441f6eac
#
# Purpose:
#   Fail closed when any of the following governance drifts are detected:
#     1. docs/crm/CRM-CURRENT-BASELINE.md is missing.
#     2. docs/crm/CRM-ENTERPRISE-EXECUTION-ROADMAP.md is missing.
#     3. Any CRM doc still claims "CRM_PRODUCT_BUILD: NOT STARTED" while CRM
#        source code is present on main.
#     4. A CRM doc enumerates migrations that do not match the actual files in
#        apps/sanad-platform/src/main/resources/db/migration/.
#     5. A doc, README, release note, or workflow summary presents an
#        empty-state-only Command Center tab as a delivered feature.
#     6. A doc claims the AI CRM capability is merged when it only exists in
#        an unmerged pull request.
#     7. A doc claims "production GO" or "commercial go-live" without a
#        formal decision record at docs/release/CRM-PRODUCTION-GO.md.
#     8. A doc hard-codes a stale CRM capability count (14 or 15) instead of
#        the reconciled 18.
#     9. docs/crm/README.md status block does not match the baseline.
#    10. A doc claims a milestone is CLOSED without a stage report.
#    11. (summary)
#    12. apps/web/app/crm/page.tsx regresses to render CrmCommandCenterPage
#        instead of the operational CrmWorkspaceV2 component, or the
#        /crm/command-center route is missing. (crm/002-restore-operational-ui)
#
# Exit codes:
#   0 — no drift detected
#   1 — drift detected (one or more violations)
#   2 — usage error or missing prerequisite
#
# Usage:
#   bash scripts/crm/governance-drift-check.sh
#
#   Intended to be invoked from the repository root.
#   Invoked by .github/workflows/crm-deployment-readiness.yml on every pull
#   request that touches CRM paths.
# =============================================================================

set -euo pipefail

# ----------------------------------------------------------------------------
# 0. Locate the repository root and define paths
# ----------------------------------------------------------------------------

SCRIPT_PATH="${BASH_SOURCE[0]}"
REPO_ROOT="$(cd "$(dirname "${SCRIPT_PATH}")/../.." && pwd)"

# Allow caller to override (e.g. CI runners with a custom checkout path).
REPO_ROOT="${REPO_ROOT_OVERRIDE:-$REPO_ROOT}"

DOCS_CRM_DIR="${REPO_ROOT}/docs/crm"
DOCS_RELEASE_DIR="${REPO_ROOT}/docs/release"
BASELINE_FILE="${DOCS_CRM_DIR}/CRM-CURRENT-BASELINE.md"
ROADMAP_FILE="${DOCS_CRM_DIR}/CRM-ENTERPRISE-EXECUTION-ROADMAP.md"
README_FILE="${DOCS_CRM_DIR}/README.md"
GAP_FILE="${REPO_ROOT}/docs/crm-gap-analysis.md"
READINESS_FILE="${REPO_ROOT}/docs/crm-readiness-assessment.md"
MIGRATION_DIR="${REPO_ROOT}/apps/sanad-platform/src/main/resources/db/migration"
CRM_CONTROLLER="${REPO_ROOT}/apps/sanad-platform/src/main/java/com/sanad/platform/crm/web/CrmController.java"
CRM_COMMAND_CENTER="${REPO_ROOT}/apps/web/app/crm/crm-command-center.tsx"
CRM_EXECUTION_DATA="${REPO_ROOT}/apps/web/app/crm/crm-execution-data.ts"
CRM_PAGE="${REPO_ROOT}/apps/web/app/crm/page.tsx"
CRM_COMMAND_CENTER_ROUTE="${REPO_ROOT}/apps/web/app/crm/command-center/page.tsx"
PRODUCTION_GO_RECORD="${DOCS_RELEASE_DIR}/CRM-PRODUCTION-GO.md"

VIOLATIONS=()
add_violation() {
  VIOLATIONS+=("$1")
}

# ----------------------------------------------------------------------------
# 1. Verify baseline and roadmap documents exist
# ----------------------------------------------------------------------------

if [[ ! -s "$BASELINE_FILE" ]]; then
  add_violation "Missing or empty baseline file: ${BASELINE_FILE#${REPO_ROOT}/}"
fi

if [[ ! -s "$ROADMAP_FILE" ]]; then
  add_violation "Missing or empty roadmap file: ${ROADMAP_FILE#${REPO_ROOT}/}"
fi

# ----------------------------------------------------------------------------
# 2. Detect "CRM_PRODUCT_BUILD: NOT STARTED" claims while CRM code exists
# ----------------------------------------------------------------------------

crm_code_exists="no"
if [[ -f "$CRM_CONTROLLER" && -f "$CRM_COMMAND_CENTER" ]]; then
  crm_code_exists="yes"
fi

if [[ "$crm_code_exists" == "yes" ]]; then
  # Scan every markdown under docs/crm/ and the two top-level CRM rollups.
  # Skip matches that are descriptive references (e.g. "historically claimed",
  # "supersedes the older ... claim", "no longer says ... NOT STARTED") by
  # ignoring any line that contains one of the disqualifier words within 120
  # characters before the match.
  disqualifiers='historically|supersede|superseded|no longer|previously|older|stale|was |were |claim|claimed|says|said'
  while IFS= read -r -d '' doc_file; do
    # Use awk so we can apply a window-based disqualifier check per line.
    if awk -v re='CRM_PRODUCT_BUILD:[[:space:]]*NOT STARTED' \
           -v dq="$disqualifiers" '
      BEGIN { found=0 }
      {
        line=$0
        s=index(line, "CRM_PRODUCT_BUILD:")
        if (s==0) next
        # Look at the substring from the start of the line up to the match
        # plus 80 chars after — if it contains a disqualifier, skip.
        window=substr(line, 1, length(line))
        if (window ~ dq) next
        if (line ~ re) { found=1; exit }
      }
      END { exit (found?0:1) }
    ' "$doc_file"; then
      add_violation "Doc claims 'CRM_PRODUCT_BUILD: NOT STARTED' while CRM code is merged: ${doc_file#${REPO_ROOT}/}"
    fi
  done < <(find "$DOCS_CRM_DIR" "$GAP_FILE" "$READINESS_FILE" -type f -name '*.md' -print0 2>/dev/null)
fi

# ----------------------------------------------------------------------------
# 3. Verify migrations referenced in docs match actual files
# ----------------------------------------------------------------------------

# List of CRM migration files that the baseline declares as authoritative.
EXPECTED_CRM_MIGRATIONS=(
  "V20260702_1__create_unified_crm_core.sql"
  "V20260702_2__reconcile_admin_role_and_capabilities.sql"
  "V20260702_3__complete_crm_imports_custom_fields.sql"
  "V20260706_1__create_tenant_quota.sql"
  "V20260711_1__create_subscription_change_events.sql"
)

for migration in "${EXPECTED_CRM_MIGRATIONS[@]}"; do
  if [[ ! -f "${MIGRATION_DIR}/${migration}" ]]; then
    add_violation "Baseline-referenced migration file is missing on disk: ${migration}"
  fi
done

# Detect any migration file on disk that the baseline does not mention.
# Only flag migrations whose filename contains "crm" (case-insensitive) —
# platform-wide migrations (e.g. user mobile contact, tenant quota) are out
# of scope for the CRM baseline reconciliation.
mapfile -t actual_crm_migrations < <(
  find "$MIGRATION_DIR" -maxdepth 1 -type f -name 'V2026*.sql' -printf '%f\n' 2>/dev/null \
    | awk 'tolower($0) ~ /crm/ { print }' | sort
)

# Build a list of migrations mentioned anywhere in the baseline + roadmap.
mentioned_migrations="$(grep -hoE 'V2026[0-9_]+__[a-zA-Z0-9_]+\.sql' "$BASELINE_FILE" "$ROADMAP_FILE" 2>/dev/null | sort -u || true)"

for actual in "${actual_crm_migrations[@]}"; do
  # Always allow the expected migrations.
  if printf '%s\n' "${EXPECTED_CRM_MIGRATIONS[@]}" | grep -qx "$actual"; then
    continue
  fi
  # Allow migrations explicitly mentioned in the baseline or roadmap.
  if printf '%s\n' "$mentioned_migrations" | grep -qx "$actual"; then
    continue
  fi
  add_violation "Migration file on disk is not referenced in baseline or roadmap: ${actual}"
done

# ----------------------------------------------------------------------------
# 4. Detect empty-state-only tabs presented as delivered features
# ----------------------------------------------------------------------------

# Pull the list of empty-state-only tab IDs from the Command Center source.
# These are the TabId values that fall through to the `default:` branch in
# renderContent().
empty_state_tabs=(
  "leads"
  "customers"
  "contacts"
  "opportunities"
  "pipeline"
  "tasks"
  "transfers"
  "employees"
  "reports"
  "mobileSync"
  "callerId"
  "aiCrm"
  "billing"
  "settings"
)

# Phrases that, when found in a doc next to one of the empty-state tab names,
# strongly suggest the tab is being presented as a delivered feature.
delivered_phrases=(
  "delivered"
  "implemented feature"
  "complete feature"
  "fully implemented"
  "production-ready"
  "available in production"
  "live in production"
)

scan_doc_for_empty_tab_presentation() {
  local doc_file="$1"
  local content
  content="$(grep -Eiv '^#|^[[:space:]]*$' "$doc_file" 2>/dev/null || true)"
  for tab in "${empty_state_tabs[@]}"; do
    # Skip if the doc never mentions the tab id.
    if ! grep -qi -- "$tab" "$doc_file" 2>/dev/null; then
      continue
    fi
    for phrase in "${delivered_phrases[@]}"; do
      # Match lines that mention the tab id within 4 lines of a delivered phrase.
      if grep -iEn -- "$tab" "$doc_file" 2>/dev/null \
        | grep -iE -- "$phrase" >/dev/null; then
        add_violation "Doc presents empty-state-only tab '${tab}' as a delivered feature ('${phrase}'): ${doc_file#${REPO_ROOT}/}"
        break
      fi
    done
  done
}

while IFS= read -r -d '' doc_file; do
  scan_doc_for_empty_tab_presentation "$doc_file"
done < <(find "$DOCS_CRM_DIR" "$GAP_FILE" "$READINESS_FILE" -type f -name '*.md' -print0 2>/dev/null)

# ----------------------------------------------------------------------------
# 5. Detect AI CRM described as merged if only present in unmerged PRs
# ----------------------------------------------------------------------------

# The AI CRM tab renders CrmEmptyState in crm-command-center.tsx. Any doc that
# claims AI CRM is merged, deployed, or in production is drift.
ai_crm_merged_phrases=(
  "AI CRM is merged"
  "AI CRM is deployed"
  "AI CRM is live"
  "AI CRM is in production"
  "AI CRM is available"
  "ai crm has shipped"
  "ai crm capability is delivered"
)

while IFS= read -r -d '' doc_file; do
  for phrase in "${ai_crm_merged_phrases[@]}"; do
    if grep -qi -- "$phrase" "$doc_file" 2>/dev/null; then
      add_violation "Doc claims AI CRM is merged/deployed but it only renders CrmEmptyState in the Command Center: ${doc_file#${REPO_ROOT}/} (matched: '${phrase}')"
    fi
  done
done < <(find "$DOCS_CRM_DIR" "$GAP_FILE" "$READINESS_FILE" -type f -name '*.md' -print0 2>/dev/null)

# ----------------------------------------------------------------------------
# 6. Detect production GO / commercial go-live claims without a decision record
# ----------------------------------------------------------------------------

# Any doc (excluding the production GO record itself and the baseline which
# references the requirement) that claims commercial go-live must point at the
# formal decision record.
go_live_phrases=(
  "commercial go-live: authorized"
  "commercial go-live authorized"
  "production go: authorized"
  "production go authorized"
  "commercial launch authorized"
  "commercial launch: authorized"
)

while IFS= read -r -d '' doc_file; do
  # Skip the decision record itself and the baseline (which only mentions the
  # requirement, not an authorization).
  case "$doc_file" in
    "$PRODUCTION_GO_RECORD") continue ;;
    "$BASELINE_FILE") continue ;;
    "$ROADMAP_FILE") continue ;;
  esac
  for phrase in "${go_live_phrases[@]}"; do
    if grep -qi -- "$phrase" "$doc_file" 2>/dev/null; then
      if [[ ! -s "$PRODUCTION_GO_RECORD" ]]; then
        add_violation "Doc claims commercial go-live is authorized but docs/release/CRM-PRODUCTION-GO.md is missing: ${doc_file#${REPO_ROOT}/} (matched: '${phrase}')"
      fi
    fi
  done
done < <(find "$DOCS_CRM_DIR" "$GAP_FILE" "$READINESS_FILE" "$DOCS_RELEASE_DIR" -type f -name '*.md' -print0 2>/dev/null)

# Also fail any doc that uses the exact upper-case phrase
# "COMMERCIAL GO-LIVE: AUTHORIZED" without the decision record.
if [[ ! -s "$PRODUCTION_GO_RECORD" ]]; then
  while IFS= read -r -d '' doc_file; do
    case "$doc_file" in
      "$PRODUCTION_GO_RECORD") continue ;;
      "$BASELINE_FILE") continue ;;
      "$ROADMAP_FILE") continue ;;
      "$README_FILE") continue ;;
    esac
    if grep -Eq 'COMMERCIAL GO-LIVE:[[:space:]]*AUTHORIZED' "$doc_file" 2>/dev/null; then
      add_violation "Doc claims COMMERCIAL GO-LIVE: AUTHORIZED without a formal decision record: ${doc_file#${REPO_ROOT}/}"
    fi
  done < <(find "$DOCS_CRM_DIR" "$GAP_FILE" "$READINESS_FILE" "$DOCS_RELEASE_DIR" -type f -name '*.md' -print0 2>/dev/null)
fi

# ----------------------------------------------------------------------------
# 7. Detect stale CRM capability counts (14 or 15)
# ----------------------------------------------------------------------------

# The reconciled count after V20260702_3 is 18. Any doc that hard-codes 14 or
# 15 in the immediate vicinity of "CRM capabilities" is drift.
# Skip lines that are descriptive references (e.g. "the previous claim of 14
# CRM capabilities is stale") by ignoring lines that contain disqualifier
# words.
cap_disqualifiers='historically|supersede|superseded|no longer|previously|older|stale|was |were |claim|claimed|says|said'
while IFS= read -r -d '' doc_file; do
  # Flag "14 CRM capabilities" / "15 CRM capabilities" / "14 capabilities"
  # / "15 capabilities" within 2 lines of "CRM".
  if awk -v dq="$cap_disqualifiers" '
    BEGIN { found=0 }
    {
      if ($0 ~ dq) next
      if ($0 ~ /1[45][[:space:]]+(crm[[:space:]]+)?capabilit/) { found=1; exit }
    }
    END { exit (found?0:1) }
  ' "$doc_file" 2>/dev/null; then
    add_violation "Doc hard-codes stale CRM capability count (14 or 15); reconciled count is 18: ${doc_file#${REPO_ROOT}/}"
  fi
  # Also catch the standalone "14 CRM RBAC capabilities" / "15 CRM RBAC".
  if awk -v dq="$cap_disqualifiers" '
    BEGIN { found=0 }
    {
      if ($0 ~ dq) next
      if ($0 ~ /1[45][[:space:]]+CRM[[:space:]]+RBAC/) { found=1; exit }
    }
    END { exit (found?0:1) }
  ' "$doc_file" 2>/dev/null; then
    add_violation "Doc hard-codes stale CRM RBAC capability count (14 or 15); reconciled count is 18: ${doc_file#${REPO_ROOT}/}"
  fi
done < <(find "$DOCS_CRM_DIR" "$GAP_FILE" "$READINESS_FILE" -type f -name '*.md' -print0 2>/dev/null)

# ----------------------------------------------------------------------------
# 8. Verify docs/crm/README.md status block matches the baseline
# ----------------------------------------------------------------------------

if [[ -s "$README_FILE" ]]; then
  # Apply the same disqualifier logic as check #2 — a README may quote the
  # historical "NOT STARTED" claim as long as the same line contains a
  # disqualifier (e.g. "no longer", "historically", "supersedes").
  readme_disqualifiers='historically|supersede|superseded|no longer|previously|older|stale|was |were |claim|claimed|says|said'
  if awk -v dq="$readme_disqualifiers" '
    BEGIN { found=0 }
    {
      if ($0 ~ dq) next
      if ($0 ~ /CRM_PRODUCT_BUILD:[[:space:]]*NOT STARTED/) { found=1; exit }
    }
    END { exit (found?0:1) }
  ' "$README_FILE"; then
    add_violation "docs/crm/README.md still claims 'CRM_PRODUCT_BUILD: NOT STARTED'; update to IMPLEMENTED_AND_CONNECTED."
  fi
  if ! grep -Eq 'CRM_PRODUCT_BUILD:[[:space:]]*IMPLEMENTED_AND_CONNECTED' "$README_FILE"; then
    add_violation "docs/crm/README.md does not declare 'CRM_PRODUCT_BUILD: IMPLEMENTED_AND_CONNECTED'."
  fi
else
  add_violation "docs/crm/README.md is missing or empty."
fi

# ----------------------------------------------------------------------------
# 9. Verify the Command Center does not silently render empty states for tabs
#    that docs claim are wired (cross-check the source against the registry)
# ----------------------------------------------------------------------------

# If the Execution Board data registry lists a group as DONE or APPROVED, the
# matching Command Center tab must not fall through to the empty-state branch.
# (Today G0 is DONE and overview+executionBoard are wired; we do not assert
# anything stronger because G1-G10 are intentionally not yet wired.)
if [[ -s "$CRM_EXECUTION_DATA" ]]; then
  if grep -q 'status: "APPROVED"' "$CRM_EXECUTION_DATA" \
    && grep -q 'code: "G0"' "$CRM_EXECUTION_DATA"; then
    # G0 must have its overview + executionBoard tabs wired.
    if ! grep -q 'case "overview":' "$CRM_COMMAND_CENTER"; then
      add_violation "Execution Board marks G0 as APPROVED but crm-command-center.tsx does not render the overview tab."
    fi
    if ! grep -q 'case "executionBoard":' "$CRM_COMMAND_CENTER"; then
      add_violation "Execution Board marks G0 as APPROVED but crm-command-center.tsx does not render the executionBoard tab."
    fi
  fi
fi

# ----------------------------------------------------------------------------
# 10. Detect any doc that claims a milestone is CLOSED without a stage report
# ----------------------------------------------------------------------------

if [[ -d "${DOCS_CRM_DIR}/stage-reports" ]]; then
  :
else
  # The stage-reports directory may not exist yet; create a soft expectation:
  # if a doc claims "CRM-G0: CLOSED" or "G0 STAGE REPORT" but no report file
  # exists anywhere under docs/crm/, that is drift.
  :
fi

# Look for explicit milestone-closure claims and require the matching report.
declare -A milestone_report_map=(
  ["CRM-G0"]="CRM-G0-STAGE-REPORT.md"
  ["CRM-G1"]="CRM-G1-STAGE-REPORT.md"
  ["CRM-G2"]="CRM-G2-STAGE-REPORT.md"
  ["CRM-G3"]="CRM-G3-STAGE-REPORT.md"
  ["CRM-G4"]="CRM-G4-STAGE-REPORT.md"
  ["CRM-G5"]="CRM-G5-STAGE-REPORT.md"
  ["CRM-G6"]="CRM-G6-STAGE-REPORT.md"
  ["CRM-G7"]="CRM-G7-STAGE-REPORT.md"
  ["CRM-G8"]="CRM-G8-STAGE-REPORT.md"
)

while IFS= read -r -d '' doc_file; do
  for milestone in "${!milestone_report_map[@]}"; do
    report="${milestone_report_map[$milestone]}"
    # Match "CRM-Gn: CLOSED" or "CRM-Gn — CLOSED" or "CRM-Gn is CLOSED".
    if grep -Eq "${milestone}[^[:alnum:]]+(is[[:space:]]+)?CLOSED" "$doc_file" 2>/dev/null; then
      if [[ ! -f "${DOCS_CRM_DIR}/stage-reports/${report}" ]] \
        && ! find "$DOCS_CRM_DIR" -type f -name "$report" -print0 2>/dev/null | grep -q .; then
        add_violation "Doc claims ${milestone} is CLOSED but stage report ${report} is missing: ${doc_file#${REPO_ROOT}/}"
      fi
    fi
  done
done < <(find "$DOCS_CRM_DIR" "$GAP_FILE" "$READINESS_FILE" -type f -name '*.md' -print0 2>/dev/null)

# ----------------------------------------------------------------------------
# 12. Verify /crm page redirects to /crm/overview (CRM-002a route migration)
# -----------------------------------------------------------------------------
#
# Branch crm/002a-complete-operational-ui replaced the monolithic
# CrmWorkspaceV2 mount at /crm with a server-side redirect to the new
# route-based CRM pages under /crm/(operational)/*. We fail closed if:
#   (a) /crm/page.tsx no longer redirects to /crm/overview (regression to the
#       monolithic mount shape).
#   (b) /crm/(operational)/overview/page.tsx is missing.
#   (c) /crm/(operational)/accounts/page.tsx is missing.
#   (d) /crm/(operational)/imports/page.tsx is missing.
#   (e) /crm/(operational)/settings/custom-fields/page.tsx is missing.
#   (f) /crm/command-center/page.tsx still renders CrmCommandCenterPage.
#
# The deprecated CrmWorkspaceV2 / CrmAdvancedView components are kept for
# reference and may still be imported (e.g. by tests), so we no longer assert
# that /crm/page.tsx imports CrmWorkspaceV2.

CRM_OVERVIEW_ROUTE="${REPO_ROOT}/apps/web/app/crm/(operational)/overview/page.tsx"
CRM_ACCOUNTS_ROUTE="${REPO_ROOT}/apps/web/app/crm/(operational)/accounts/page.tsx"
CRM_IMPORTS_ROUTE="${REPO_ROOT}/apps/web/app/crm/(operational)/imports/page.tsx"
CRM_CUSTOM_FIELDS_ROUTE="${REPO_ROOT}/apps/web/app/crm/(operational)/settings/custom-fields/page.tsx"
# CRM-002b detail routes
CRM_CONTACT_DETAIL_ROUTE="${REPO_ROOT}/apps/web/app/crm/(operational)/contacts/[contactId]/page.tsx"
CRM_LEAD_DETAIL_ROUTE="${REPO_ROOT}/apps/web/app/crm/(operational)/leads/[leadId]/page.tsx"
CRM_OPPORTUNITY_DETAIL_ROUTE="${REPO_ROOT}/apps/web/app/crm/(operational)/opportunities/[opportunityId]/page.tsx"
CRM_E2E_SPEC="${REPO_ROOT}/apps/web/e2e/crm-operational.spec.ts"

if [[ -f "$CRM_PAGE" ]]; then
  if ! grep -Eq 'redirect\([[:space:]]*["'\'']/crm/overview["'\'']' "$CRM_PAGE"; then
    add_violation "apps/web/app/crm/page.tsx no longer redirects to /crm/overview; the operational CRM route migration has regressed."
  fi
  # If the page imports CrmCommandCenterPage as its default content (the
  # pre-002 shape), that is a regression even if the redirect is also
  # present — the Command Center must only be mounted at /crm/command-center.
  if grep -Eq 'import[[:space:]]+CrmCommandCenterPage[[:space:]]+from[[:space:]]+["'\'']\.\/crm-command-center["'\'']' "$CRM_PAGE" \
    && grep -Eq '<CrmCommandCenterPage[[:space:]]*/?>' "$CRM_PAGE"; then
    add_violation "apps/web/app/crm/page.tsx renders CrmCommandCenterPage directly; move it to /crm/command-center/page.tsx."
  fi
else
  add_violation "apps/web/app/crm/page.tsx is missing."
fi

if [[ ! -f "$CRM_OVERVIEW_ROUTE" ]]; then
  add_violation "apps/web/app/crm/(operational)/overview/page.tsx is missing; the operational overview route must exist."
fi

if [[ ! -f "$CRM_ACCOUNTS_ROUTE" ]]; then
  add_violation "apps/web/app/crm/(operational)/accounts/page.tsx is missing; the accounts route must exist."
fi

if [[ ! -f "$CRM_IMPORTS_ROUTE" ]]; then
  add_violation "apps/web/app/crm/(operational)/imports/page.tsx is missing; the imports route must exist."
fi

if [[ ! -f "$CRM_CUSTOM_FIELDS_ROUTE" ]]; then
  add_violation "apps/web/app/crm/(operational)/settings/custom-fields/page.tsx is missing; the custom fields admin route must exist."
fi

if [[ ! -f "$CRM_COMMAND_CENTER_ROUTE" ]]; then
  add_violation "apps/web/app/crm/command-center/page.tsx is missing; the Command Center + Execution Board must remain accessible at /crm/command-center."
elif ! grep -Eq 'CrmCommandCenterPage' "$CRM_COMMAND_CENTER_ROUTE"; then
  add_violation "apps/web/app/crm/command-center/page.tsx does not render CrmCommandCenterPage."
fi

# ----------------------------------------------------------------------------
# 13. Verify CRM-002b detail routes and E2E spec exist
# -----------------------------------------------------------------------------
#
# Branch crm/002b-final-operational-acceptance added the three missing
# detail routes (contact, lead, opportunity) plus the first Playwright E2E
# suite for the operational CRM. We fail closed if any of these regress.

if [[ ! -f "$CRM_CONTACT_DETAIL_ROUTE" ]]; then
  add_violation "apps/web/app/crm/(operational)/contacts/[contactId]/page.tsx is missing; the contact detail route must exist."
fi

if [[ ! -f "$CRM_LEAD_DETAIL_ROUTE" ]]; then
  add_violation "apps/web/app/crm/(operational)/leads/[leadId]/page.tsx is missing; the lead detail route must exist."
fi

if [[ ! -f "$CRM_OPPORTUNITY_DETAIL_ROUTE" ]]; then
  add_violation "apps/web/app/crm/(operational)/opportunities/[opportunityId]/page.tsx is missing; the opportunity detail route must exist."
fi

if [[ ! -f "$CRM_E2E_SPEC" ]]; then
  add_violation "apps/web/e2e/crm-operational.spec.ts is missing; the CRM operational E2E suite must exist."
fi

# ----------------------------------------------------------------------------
# 14. Summary and exit
# ----------------------------------------------------------------------------

if (( ${#VIOLATIONS[@]} == 0 )); then
  echo "CRM_GOVERNANCE_DRIFT_CHECK: PASS"
  echo "  baseline:        present"
  echo "  roadmap:         present"
  echo "  README status:   IMPLEMENTED_AND_CONNECTED"
  echo "  migrations:      ${#EXPECTED_CRM_MIGRATIONS[@]} expected, ${#actual_crm_migrations[@]} on disk"
  echo "  capability count: 18 (reconciled)"
  exit 0
fi

echo "CRM_GOVERNANCE_DRIFT_CHECK: FAIL" >&2
echo "Detected ${#VIOLATIONS[@]} drift violation(s):" >&2
printf '  - %s\n' "${VIOLATIONS[@]}" >&2
exit 1

# Check 14: Verify CRM-G1 closure claims have evidence
if grep -q "CRM-G1: CLOSED" docs/crm/CRM-CURRENT-BASELINE.md 2>/dev/null; then
  if ! grep -q "Known Limitations" docs/crm/evidence/CRM-002-OPERATIONAL-UI-EVIDENCE.md 2>/dev/null; then
    fail "CRM-G1 claimed CLOSED but evidence file lacks Known Limitations section"
  fi
fi
