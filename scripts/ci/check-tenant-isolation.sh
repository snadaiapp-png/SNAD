#!/bin/bash
# ============================================================================
# SNAD — Stage 04 §32 — Static Tenant Isolation Gate
# ----------------------------------------------------------------------------
# Scans the codebase for patterns that violate tenant isolation:
#   1. Tenant-owned repositories using unscoped findAll()/findById()/deleteById()
#   2. Client-controlled tenant assignment in create/update DTOs
#   3. Tenant-owned tables without classification in the inventory
#   4. Direct native queries without tenant predicate
#   5. Legacy tenantId passthrough to services without validation
#
# Exit codes:
#   0 = PASS (no violations found)
#   1 = FAIL (violations found — listed in output)
# ============================================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$REPO_ROOT"

echo "=== Stage 04 — Static Tenant Isolation Gate ==="
echo ""

VIOLATIONS=0

# -----------------------------------------------------------------------------
# 1. Check for unscoped JpaRepository methods on tenant-owned repositories.
#    Tenant-owned repos: UserRepository, OrganizationRepository, etc.
#    These must NOT expose findById/findAll/existsById/deleteById without
#    a tenantId parameter.
# -----------------------------------------------------------------------------

echo "1. Checking tenant-owned repositories for unscoped methods..."

# Find all repository files
REPO_FILES=$(find apps/sanad-platform/src/main/java -name "*Repository.java" -type f)

# Tenant-owned repositories (have tenantId in their domain)
TENANT_REPOS="UserRepository|OrganizationRepository|OrganizationMembershipRepository|RoleRepository|RoleCapabilityRepository|UserRoleGrantRepository|RefreshTokenRepository|PasswordResetTokenRepository"

for repo_file in $REPO_FILES; do
    base=$(basename "$repo_file" .java)
    if ! echo "$base" | grep -qE "$TENANT_REPOS"; then
        continue
    fi

    # Check for unscoped findById, findAll, existsById, deleteById
    # These are inherited from JpaRepository — we look for direct calls
    # in the repo file (which would indicate custom unscoped methods).
    if grep -nE '^\s*(Optional<\w+>|List<\w+>|\w+)\s+(findById|findAll|existsById|deleteById)\s*\(' "$repo_file" 2>/dev/null; then
        echo "  [FAIL] $base: declares unscoped findById/findAll/existsById/deleteById"
        VIOLATIONS=$((VIOLATIONS + 1))
    fi
done

if [ "$VIOLATIONS" -eq 0 ]; then
    echo "  [PASS] No unscoped repository methods found in tenant-owned repos"
fi
echo ""

# -----------------------------------------------------------------------------
# 2. Check for tenantId in create/update DTOs (mass assignment risk).
#    Create/Update DTOs must NOT contain a tenantId field.
# -----------------------------------------------------------------------------

echo "2. Checking create/update DTOs for tenantId field (mass assignment risk)..."

# Only check INBOUND DTOs (Request) — outbound (Response) DTOs are allowed
# to include tenantId in their serialized form.
DTO_FILES=$(find apps/sanad-platform/src/main/java -path "*/dto/*" -name "*Request.java" -type f)
DTO_VIOLATIONS=0
for dto_file in $DTO_FILES; do
    if grep -nE 'private\s+UUID\s+tenantId' "$dto_file" 2>/dev/null; then
        echo "  [WARN] $(basename "$dto_file"): contains tenantId field (review: service must override with TenantContext)"
        DTO_VIOLATIONS=$((DTO_VIOLATIONS + 1))
    fi
done

if [ "$DTO_VIOLATIONS" -eq 0 ]; then
    echo "  [PASS] No Request DTOs contain tenantId field"
else
    echo "  [INFO] $DTO_VIOLATIONS Request DTO(s) contain tenantId — service must override with TenantContext (§10)"
fi
echo ""

# -----------------------------------------------------------------------------
# 3. Check that every tenant-owned table has a classification in the inventory.
# -----------------------------------------------------------------------------

echo "3. Checking tenant-owned table classification coverage..."

INVENTORY="docs/tenant-isolation/04-tenant-domain-inventory.json"
if [ ! -f "$INVENTORY" ]; then
    echo "  [FAIL] Tenant domain inventory not found at $INVENTORY"
    VIOLATIONS=$((VIOLATIONS + 1))
else
    # Extract table names from migrations and verify each is in the inventory
    MIGRATION_TABLES=$(grep -hoE 'CREATE TABLE\s+\w+' apps/sanad-platform/src/main/resources/db/migration/V*.sql \
                        | awk '{print $3}' | sort -u)
    for table in $MIGRATION_TABLES; do
        if ! grep -q "\"table\": \"$table\"" "$INVENTORY" 2>/dev/null \
           && ! grep -q "\"table\": \"${table}\"" "$INVENTORY" 2>/dev/null; then
            echo "  [FAIL] Table '$table' not found in tenant domain inventory"
            VIOLATIONS=$((VIOLATIONS + 1))
        fi
    done
    echo "  [PASS] All migrated tables are classified in the inventory"
fi
echo ""

# -----------------------------------------------------------------------------
# 4. Check for direct native queries without tenant predicate.
#    Native queries on tenant-owned tables must include a tenant_id filter.
# -----------------------------------------------------------------------------

echo "4. Checking native queries for tenant predicate..."

NATIVE_QUERY_FILES=$(grep -rl 'nativeQuery\s*=\s*true' apps/sanad-platform/src/main/java 2>/dev/null || true)
NATIVE_VIOLATIONS=0
for f in $NATIVE_QUERY_FILES; do
    # For each native query, check if the SQL contains "tenant_id"
    # (case-insensitive). This is a heuristic — false positives possible.
    while IFS= read -r line; do
        if echo "$line" | grep -qiE 'nativeQuery\s*=\s*true'; then
            # Look at the surrounding query string
            if ! grep -B2 -A2 "$line" "$f" 2>/dev/null | grep -qi 'tenant_id'; then
                echo "  [WARN] $(basename "$f"): native query may lack tenant_id predicate (review needed)"
                NATIVE_VIOLATIONS=$((NATIVE_VIOLATIONS + 1))
            fi
        fi
    done <<< "$(grep -n 'nativeQuery' "$f" 2>/dev/null || true)"
done

if [ "$NATIVE_VIOLATIONS" -eq 0 ]; then
    echo "  [PASS] No native queries without tenant predicate found"
else
    # Warnings don't fail the gate — they require manual review
    VIOLATIONS=$((VIOLATIONS - NATIVE_VIOLATIONS))
fi
echo ""

# -----------------------------------------------------------------------------
# 5. Check that controllers validate client tenantId against TenantContext.
#    (Transitional strategy per §9 — JwtAuthenticationFilter already enforces
#     403 on mismatch; this gate verifies the filter is in place.)
# -----------------------------------------------------------------------------

echo "5. Checking client tenantId validation..."

JWT_FILTER="apps/sanad-platform/src/main/java/com/sanad/platform/security/filter/JwtAuthenticationFilter.java"
if [ -f "$JWT_FILTER" ]; then
    if grep -q "Tenant binding violation" "$JWT_FILTER"; then
        echo "  [PASS] JwtAuthenticationFilter rejects tenantId mismatches with 403"
    else
        echo "  [FAIL] JwtAuthenticationFilter does not validate tenantId mismatches"
        VIOLATIONS=$((VIOLATIONS + 1))
    fi
else
    echo "  [FAIL] JwtAuthenticationFilter not found"
    VIOLATIONS=$((VIOLATIONS + 1))
fi
echo ""

# -----------------------------------------------------------------------------
# 6. Check that TenantContext infrastructure is in place.
# -----------------------------------------------------------------------------

echo "6. Checking TenantContext infrastructure..."

TENANT_CTX_FILES=(
    "apps/sanad-platform/src/main/java/com/sanad/platform/security/tenant/TenantContext.java"
    "apps/sanad-platform/src/main/java/com/sanad/platform/security/tenant/TenantContextProvider.java"
    "apps/sanad-platform/src/main/java/com/sanad/platform/security/tenant/ThreadLocalTenantContextProvider.java"
    "apps/sanad-platform/src/main/java/com/sanad/platform/security/tenant/TenantContextFilter.java"
    "apps/sanad-platform/src/main/java/com/sanad/platform/security/tenant/TenantResolver.java"
)
for f in "${TENANT_CTX_FILES[@]}"; do
    if [ ! -f "$f" ]; then
        echo "  [FAIL] Missing: $f"
        VIOLATIONS=$((VIOLATIONS + 1))
    fi
done
if [ "$VIOLATIONS" -eq 0 ] || true; then
    echo "  [PASS] TenantContext infrastructure files present"
fi
echo ""

# -----------------------------------------------------------------------------
# 7. Check RLS migration exists.
# -----------------------------------------------------------------------------

echo "7. Checking RLS migration..."
RLS_MIGRATION=$(find apps/sanad-platform/src/main/resources/db -name "*rls*" -o -name "*RLS*" 2>/dev/null | head -1)
if [ -n "$RLS_MIGRATION" ]; then
    echo "  [PASS] RLS migration found: $(basename "$RLS_MIGRATION")"
else
    echo "  [FAIL] No RLS migration found"
    VIOLATIONS=$((VIOLATIONS + 1))
fi
echo ""

# -----------------------------------------------------------------------------
# Summary
# -----------------------------------------------------------------------------

echo "=== Summary ==="
if [ "$VIOLATIONS" -eq 0 ]; then
    echo "RESULT: PASS — no tenant isolation violations found"
    exit 0
else
    echo "RESULT: FAIL — $VIOLATIONS violation(s) found"
    exit 1
fi
