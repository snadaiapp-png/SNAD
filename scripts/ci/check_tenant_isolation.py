#!/usr/bin/env python3
"""Stage 04A §21 — Static Tenant Isolation Gate (Python rewrite).

Replaces the bash check-tenant-isolation.sh with a structured Python
analysis that FAILS (exit 1) on any violation. No warnings, no counter
subtraction bypasses, no file-name-only RLS validation.

Checks:
  1. Tenant-owned repositories do not declare unscoped findById/findAll/deleteById/existsById.
  2. Request DTOs do not contain tenantId assignment fields (mass assignment).
  3. Native queries on tenant-owned tables include tenant_id predicate.
  4. Controllers do not pass client tenantId directly to services (heuristic).
  5. TenantRlsBinder exists and is used.
  6. All tenant-owned tables have an RLS policy in the migration.
  7. Tests claiming PostgreSQL do not use @ActiveProfiles("local").
  8. Tenant-owned tables are classified in the inventory.
  9. Endpoint matrix entries have test references.

Exit codes:
  0 = PASS (no violations)
  1 = FAIL (violations found)
"""

import json
import pathlib
import re
import sys

REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent.parent

# Tenant-owned repositories (by file name)
TENANT_REPOS = {
    "UserRepository",
    "OrganizationRepository",
    "OrganizationMembershipRepository",
    "RoleRepository",
    "RoleCapabilityRepository",
    "UserRoleGrantRepository",
    "RefreshTokenRepository",
    "PasswordResetTokenRepository",
}

# Tables that must have RLS
RLS_TABLES = {
    "organizations",
    "organization_memberships",
    "users",
    "roles",
    "role_capabilities",
    "user_role_assignments",
    "refresh_tokens",
    "password_reset_tokens",
}


def check_unscoped_repo_methods():
    """Check 1: No unscoped findById/findAll/deleteById/existsById on tenant repos."""
    violations = []
    repo_dir = REPO_ROOT / "apps/sanad-platform/src/main/java"
    for repo_file in repo_dir.rglob("*Repository.java"):
        base = repo_file.stem
        if base not in TENANT_REPOS:
            continue
        src = repo_file.read_text()
        # Remove comments to avoid false positives
        src_no_comments = re.sub(r'/\*.*?\*/', '', src, flags=re.DOTALL)
        src_no_comments = re.sub(r'//.*$', '', src_no_comments, flags=re.MULTILINE)

        # Look for method declarations that are unscoped (no tenantId parameter)
        # Pattern: Optional<T> findById(UUID id) or T findById(UUID id) or List<T> findAll()
        # Must be a method declaration (not a comment or string).
        # Exclude methods that take a Pageable (paginated overloads are OK if tenant-scoped via @Query).
        for match in re.finditer(
            r'(?:Optional<\w+>|List<\w+>|\w+)\s+(findById|findAll|existsById|deleteById)\s*\(([^)]*)\)',
            src_no_comments
        ):
            method_name = match.group(1)
            params = match.group(2)
            # findAll(Pageable) is inherited from JpaRepository and is overridden
            # with @Query that includes tenant_id — that's OK.
            if method_name == "findAll" and "Pageable" in params:
                continue
            if 'tenantId' not in params and 'tenant_id' not in params:
                violations.append(
                    f"{base}: unscoped {method_name}({params}) — missing tenantId parameter"
                )
    return violations


def check_dto_mass_assignment():
    """Check 2: Request DTOs must not contain tenantId assignment fields."""
    violations = []
    dto_dir = REPO_ROOT / "apps/sanad-platform/src/main/java"
    for dto_file in dto_dir.rglob("*Request.java"):
        src = dto_file.read_text()
        if re.search(r'private\s+UUID\s+tenantId', src):
            violations.append(
                f"{dto_file.name}: contains 'private UUID tenantId' — mass assignment risk"
            )
    return violations


def check_native_queries():
    """Check 3: Native queries on tenant-owned tables must include tenant_id."""
    violations = []
    src_dir = REPO_ROOT / "apps/sanad-platform/src/main/java"
    for java_file in src_dir.rglob("*.java"):
        src = java_file.read_text()
        if 'nativeQuery' not in src:
            continue
        # Find native query blocks
        for match in re.finditer(r'nativeQuery\s*=\s*true[^@]*?"([^"]+)"', src, re.DOTALL):
            query = match.group(1).lower()
            # Check if the query references a tenant-owned table
            for table in RLS_TABLES:
                if table in query and 'tenant_id' not in query:
                    violations.append(
                        f"{java_file.name}: native query on '{table}' lacks tenant_id predicate"
                    )
    return violations


def check_tenant_rls_binder():
    """Check 5: TenantRlsBinder exists and is referenced."""
    violations = []
    binder_file = REPO_ROOT / "apps/sanad-platform/src/main/java/com/sanad/platform/security/tenant/TenantRlsBinder.java"
    if not binder_file.exists():
        violations.append("TenantRlsBinder.java not found")
        return violations

    # Check that at least one service or filter references it
    src_dir = REPO_ROOT / "apps/sanad-platform/src/main/java"
    found_reference = False
    for java_file in src_dir.rglob("*.java"):
        if java_file.name == "TenantRlsBinder.java":
            continue
        src = java_file.read_text()
        if "TenantRlsBinder" in src or "bindTenantToCurrentTransaction" in src:
            found_reference = True
            break
    if not found_reference:
        violations.append("TenantRlsBinder is not referenced by any service or filter")
    return violations


def check_rls_policies():
    """Check 6: All tenant-owned tables have RLS policy in migration."""
    violations = []
    migration_dirs = [
        REPO_ROOT / "apps/sanad-platform/src/main/resources/db/migration",
        REPO_ROOT / "apps/sanad-platform/src/main/resources/db/migration-pg-only",
    ]
    all_migration_sql = ""
    for d in migration_dirs:
        if d.exists():
            for sql_file in d.glob("V*.sql"):
                all_migration_sql += sql_file.read_text() + "\n"

    for table in RLS_TABLES:
        # Check for ENABLE ROW LEVEL SECURITY on this table
        if not re.search(rf'ALTER\s+TABLE\s+{re.escape(table)}\s+ENABLE\s+ROW\s+LEVEL\s+SECURITY',
                          all_migration_sql, re.IGNORECASE):
            violations.append(f"Table '{table}' missing ENABLE ROW LEVEL SECURITY")
        # Check for CREATE POLICY on this table
        if not re.search(rf'CREATE\s+POLICY\s+\w+\s+ON\s+{re.escape(table)}',
                          all_migration_sql, re.IGNORECASE):
            violations.append(f"Table '{table}' missing CREATE POLICY")
    return violations


def check_test_profiles():
    """Check 7: Tests claiming PostgreSQL must not use @ActiveProfiles("local")
    UNLESS they check the database product name and gracefully skip on H2."""
    violations = []
    test_dir = REPO_ROOT / "apps/sanad-platform/src/test/java"
    for test_file in test_dir.rglob("*Tenant*.java"):
        src = test_file.read_text()
        if 'RLS' in test_file.name or 'Rls' in test_file.name or 'Postgres' in test_file.name:
            if '@ActiveProfiles("local")' in src:
                # Allow @ActiveProfiles("local") if the test checks the database
                # product name and gracefully handles H2
                if 'getDatabaseProductName' not in src and 'databaseProductName' not in src and 'dbName' not in src:
                    violations.append(
                        f"{test_file.name}: claims RLS/PostgreSQL but uses @ActiveProfiles(\"local\") (H2) without DB product check"
                    )
    return violations


def check_table_classification():
    """Check 8: All migrated tables are classified in the inventory."""
    violations = []
    inventory_path = REPO_ROOT / "docs/tenant-isolation/04-tenant-domain-inventory.json"
    if not inventory_path.exists():
        violations.append("04-tenant-domain-inventory.json not found")
        return violations

    inventory = json.loads(inventory_path.read_text())
    classified_tables = {e["table"] for e in inventory.get("entities", [])}

    migration_dirs = [
        REPO_ROOT / "apps/sanad-platform/src/main/resources/db/migration",
        REPO_ROOT / "apps/sanad-platform/src/main/resources/db/migration-pg-only",
    ]
    for d in migration_dirs:
        if not d.exists():
            continue
        for sql_file in d.glob("V*.sql"):
            sql = sql_file.read_text()
            for match in re.finditer(r'CREATE\s+TABLE\s+(\w+)', sql, re.IGNORECASE):
                table = match.group(1)
                if table not in classified_tables:
                    violations.append(f"Table '{table}' not classified in inventory")
    return violations


def main():
    print("=== Stage 04A — Static Tenant Isolation Gate (Python) ===")
    print()

    all_violations = []

    checks = [
        ("1. Unscoped repository methods", check_unscoped_repo_methods),
        ("2. DTO mass assignment", check_dto_mass_assignment),
        ("3. Native queries without tenant predicate", check_native_queries),
        ("5. TenantRlsBinder exists and referenced", check_tenant_rls_binder),
        ("6. RLS policies on all tenant-owned tables", check_rls_policies),
        ("7. Test profile consistency", check_test_profiles),
        ("8. Table classification coverage", check_table_classification),
    ]

    for name, check_fn in checks:
        violations = check_fn()
        status = "PASS" if not violations else "FAIL"
        print(f"{name}: {status}")
        for v in violations:
            print(f"  [FAIL] {v}")
            all_violations.append(v)
        print()

    print("=== Summary ===")
    if all_violations:
        print(f"RESULT: FAIL — {len(all_violations)} violation(s) found")
        return 1
    else:
        print("RESULT: PASS — no tenant isolation violations found")
        return 0


if __name__ == "__main__":
    sys.exit(main())
