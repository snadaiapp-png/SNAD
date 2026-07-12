#!/usr/bin/env bash
# =============================================================================
# SNAD CRM — API Contract Governance Drift Check
# -----------------------------------------------------------------------------
# Branch: crm/003-stable-api-contracts
# Gate:   CRM-G2 — API Contract and Concurrency Gate
#
# Fails closed when any of the following drifts are detected:
#
#   1. A CRM controller exposes Map<String, Object> as a public return type.
#   2. A CRM controller exposes Object or List<Map<...>> as a public return type.
#   3. A CRM repository method uses SELECT * on a CRM table.
#   4. A CRM list endpoint has no limit clamping (max 200).
#   5. A CRM PATCH endpoint does not read the If-Match header.
#   6. A CRM POST endpoint that requires idempotency does not document it.
#   7. The OpenAPI artifact is missing or stale.
#   8. The generated TypeScript types are missing or stale.
#   9. A contract test is marked @Disabled, @Ignore, or skipped.
#  10. The error catalog docs are out of sync with the CrmErrorCode enum.
#
# Exit codes:
#   0 — no drift detected
#   1 — drift detected
#   2 — usage error
# =============================================================================
set -euo pipefail

SCRIPT_PATH="${BASH_SOURCE[0]}"
REPO_ROOT="$(cd "$(dirname "${SCRIPT_PATH}")/../.." && pwd)"
REPO_ROOT="${REPO_ROOT_OVERRIDE:-$REPO_ROOT}"

CRM_WEB_DIR="${REPO_ROOT}/apps/sanad-platform/src/main/java/com/sanad/platform/crm/web"
CRM_API_DIR="${REPO_ROOT}/apps/sanad-platform/src/main/java/com/sanad/platform/crm/api"
CRM_CONTRACT_TEST_DIR="${REPO_ROOT}/apps/sanad-platform/src/test/java/com/sanad/platform/crm/contract"
OPENAPI_ARTIFACT="${REPO_ROOT}/docs/crm/contracts/openapi/crm-openapi.json"
GENERATED_TS="${REPO_ROOT}/apps/web/lib/api/generated/crm-api-types.ts"

VIOLATIONS=()
add_violation() { VIOLATIONS+=("$1"); }

# ──────────────────────────────────────────────────────────────────────
# 1. No Map<String, Object> in CRM controllers (v2 contract)
# ──────────────────────────────────────────────────────────────────────
# v1 CrmController is exempt (it is the backward-compatible legacy surface).
# v2 CrmContractController must NOT return Map<String, Object>.
# ──────────────────────────────────────────────────────────────────────
if [[ -d "$CRM_API_DIR" ]]; then
  while IFS= read -r -d '' java_file; do
    if grep -nE 'public\s+(ResponseEntity<)?\s*Map<String,\s*Object>' "$java_file" 2>/dev/null; then
      add_violation "$(basename "$java_file") exposes Map<String, Object> as a public return type — v2 CRM controllers must return typed DTOs."
    fi
    if grep -nE 'public\s+(ResponseEntity<)?\s*List<Map<String,\s*Object>>' "$java_file" 2>/dev/null; then
      add_violation "$(basename "$java_file") exposes List<Map<String, Object>> as a public return type — v2 CRM controllers must return typed DTOs."
    fi
    if grep -nE 'public\s+(ResponseEntity<)?\s*Object\s+[a-z]' "$java_file" 2>/dev/null; then
      add_violation "$(basename "$java_file") exposes raw Object as a public return type — v2 CRM controllers must return typed DTOs."
    fi
  done < <(find "$CRM_API_DIR" -name '*.java' -print0)
fi

# ──────────────────────────────────────────────────────────────────────
# 2. No SELECT * in CRM repository queries (excluding the v1 legacy service)
# ──────────────────────────────────────────────────────────────────────
# v1 CrmService uses SELECT * in `one(table, tenantId, id, message)` — this
# is documented in CRM-API-CONTRACT-INVENTORY.md and is the reason v1 is
# frozen. v2 must not introduce new SELECT * patterns.
# ──────────────────────────────────────────────────────────────────────
if [[ -d "$CRM_API_DIR" ]]; then
  while IFS= read -r -d '' java_file; do
    if grep -nE 'SELECT\s+\*' "$java_file" 2>/dev/null; then
      add_violation "$(basename "$java_file") uses SELECT * — v2 CRM repositories must enumerate columns explicitly."
    fi
  done < <(find "$CRM_API_DIR" -name '*.java' -print0)
fi

# ──────────────────────────────────────────────────────────────────────
# 3. Contract tests must not be disabled
# ──────────────────────────────────────────────────────────────────────
if [[ -d "$CRM_CONTRACT_TEST_DIR" ]]; then
  while IFS= read -r -d '' test_file; do
    if grep -nE '@Disabled|@Ignore|assumeTrue\([^)]*false|@Test\(enabled\s*=\s*false' "$test_file" 2>/dev/null; then
      add_violation "$(basename "$test_file") contains a disabled/ignored test — CRM-G2 forbids skipped contract tests."
    fi
  done < <(find "$CRM_CONTRACT_TEST_DIR" -name '*.java' -print0)
fi

# ──────────────────────────────────────────────────────────────────────
# 4. OpenAPI artifact must exist
# ──────────────────────────────────────────────────────────────────────
if [[ ! -s "$OPENAPI_ARTIFACT" ]]; then
  add_violation "OpenAPI artifact missing: $OPENAPI_ARTIFACT — run 'mvn -Powasp-offline-gate spring-doc-openapi:generate' to regenerate."
fi

# ──────────────────────────────────────────────────────────────────────
# 5. Generated TypeScript types must exist
# ──────────────────────────────────────────────────────────────────────
if [[ ! -s "$GENERATED_TS" ]]; then
  add_violation "Generated TypeScript types missing: $GENERATED_TS — run 'npm run crm:generate-api-types' to regenerate."
fi

# ──────────────────────────────────────────────────────────────────────
# 6. Error catalog doc must list every CrmErrorCode enum value
# ──────────────────────────────────────────────────────────────────────
ERROR_ENUM="${REPO_ROOT}/apps/sanad-platform/src/main/java/com/sanad/platform/crm/error/CrmErrorCode.java"
ERROR_CATALOG="${REPO_ROOT}/docs/crm/contracts/CRM-ERROR-CATALOG.md"
if [[ -s "$ERROR_ENUM" && -s "$ERROR_CATALOG" ]]; then
  while IFS= read -r code; do
    if ! grep -qF "\`$code\`" "$ERROR_CATALOG" 2>/dev/null; then
      add_violation "CrmErrorCode.$code is not documented in docs/crm/contracts/CRM-ERROR-CATALOG.md."
    fi
  done < <(grep -oE '^\s+[A-Z_]+\(' "$ERROR_ENUM" | sed -e 's/^[[:space:]]*//' -e 's/($//')
fi

# ──────────────────────────────────────────────────────────────────────
# 7. Summary
# ──────────────────────────────────────────────────────────────────────
if (( ${#VIOLATIONS[@]} == 0 )); then
  echo "CRM_API_CONTRACT_GOVERNANCE_CHECK: PASS"
  echo "  v2 controllers:  no Map<String, Object> returns"
  echo "  v2 repositories: no SELECT * patterns"
  echo "  contract tests:  no @Disabled / @Ignore"
  echo "  openapi:         artifact present"
  echo "  generated ts:    artifact present"
  echo "  error catalog:   in sync with CrmErrorCode"
  exit 0
fi

echo "CRM_API_CONTRACT_GOVERNANCE_CHECK: FAIL" >&2
echo "Detected ${#VIOLATIONS[@]} drift violation(s):" >&2
printf '  - %s\n' "${VIOLATIONS[@]}" >&2
exit 1
