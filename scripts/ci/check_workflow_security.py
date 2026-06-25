#!/usr/bin/env python3
"""
SANAD — Workflow Security Policy Scanner
==========================================
EXEC-PROMPT-010R8 Section 10.1: Scans .github/workflows/*.yml for
prohibited production credential-management patterns.

Usage:
    python3 scripts/ci/check_workflow_security.py

Exit codes:
    0 = all workflows pass security policy
    1 = one or more workflows violate security policy
"""

import os
import re
import sys
import glob

WORKFLOWS_DIR = os.path.join(os.path.dirname(__file__), "..", "..", ".github", "workflows")

# Patterns that indicate unsafe credential management in workflows
PROHIBITED_PATTERNS = [
    # Password inputs via workflow_dispatch
    (r"new_password", "workflow_dispatch password input — password accepted via workflow input"),
    (r"inputs.*password.*type:\s*string", "workflow_dispatch password input"),

    # Direct database mutations from workflows
    (r"UPDATE\s+users\s+SET\s+password_hash", "direct password_hash mutation in workflow"),
    (r"DELETE\s+FROM\s+refresh_tokens", "direct refresh token deletion in workflow"),

    # Direct production database access via psycopg2 in workflows
    (r"psycopg2\.connect.*PRODUCTION", "direct production PostgreSQL access in workflow"),
    (r"psycopg2\.connect.*DATABASE_URL", "direct database access via DATABASE_URL in workflow"),

    # Render environment retrieval for credential mutation
    (r"api\.render\.com.*env-vars.*password_hash", "Render env retrieval for credential mutation"),
    (r"api\.render\.com.*env-vars.*UPDATE users", "Render env retrieval + user mutation"),

    # Production user enumeration
    (r"SELECT\s+id.*email.*FROM\s+users.*LIMIT\s+\d+", "production user enumeration in workflow"),

    # Printing user/tenant identifiers from production
    (r"print.*user_id.*tenant_id", "printing user and tenant identifiers"),

    # Unpinned package installation with production secrets
    (r"pip\s+install.*psycopg2.*bcrypt", "unpinned package installation (psycopg2+bcrypt) in workflow with potential production access"),
]

# Patterns that are safe and should not be flagged
SAFE_CONTEXTS = [
    "target/surefire-reports",  # Test reports
    "target/failsafe-reports",  # Test reports
    "Testcontainers",           # Test containers
    "test_",                    # Test files
    "fixtures/",                # Test fixtures
]


def scan_workflow(filepath):
    """Scan a single workflow file for prohibited patterns."""
    violations = []

    try:
        with open(filepath, "r", encoding="utf-8") as f:
            content = f.read()
    except Exception as e:
        return [{"file": filepath, "error": f"Could not read file: {e}"}]

    # Skip if file doesn't look like a workflow
    if not content.strip():
        return []

    for pattern, description in PROHIBITED_PATTERNS:
        matches = re.finditer(pattern, content, re.IGNORECASE | re.MULTILINE)
        for match in matches:
            # Check if this is in a safe context
            line_start = content.rfind("\n", 0, match.start()) + 1
            line_end = content.find("\n", match.end())
            if line_end == -1:
                line_end = len(content)
            line = content[line_start:line_end]
            line_num = content[:match.start()].count("\n") + 1

            # Skip if in a safe context (test-related)
            is_safe = any(safe in line for safe in SAFE_CONTEXTS)
            if is_safe:
                continue

            violations.append({
                "file": filepath,
                "line": line_num,
                "pattern": description,
                "matched_text": match.group()[:80],
            })

    return violations


def main():
    if not os.path.isdir(WORKFLOWS_DIR):
        print(f"ERROR: Workflows directory not found: {WORKFLOWS_DIR}")
        return 1

    workflow_files = sorted(glob.glob(os.path.join(WORKFLOWS_DIR, "*.yml")) +
                           glob.glob(os.path.join(WORKFLOWS_DIR, "*.yaml")))

    if not workflow_files:
        print("No workflow files found.")
        return 0

    print(f"Scanning {len(workflow_files)} workflow files...")
    print()

    all_violations = []
    for wf in workflow_files:
        basename = os.path.basename(wf)
        violations = scan_workflow(wf)
        if violations:
            all_violations.extend(violations)
            print(f"  ❌ {basename}: {len(violations)} violation(s)")
            for v in violations:
                print(f"     Line {v['line']}: {v['pattern']}")
                print(f"       Matched: {v['matched_text']}")
        else:
            print(f"  ✅ {basename}: PASS")

    print()
    if all_violations:
        print(f"FAILED: {len(all_violations)} security violation(s) found in workflows.")
        print("Prohibited patterns include:")
        print("  - Password inputs via workflow_dispatch")
        print("  - Direct password_hash mutation")
        print("  - Direct refresh token deletion")
        print("  - Direct production PostgreSQL access")
        print("  - Production user enumeration")
        print("  - Printing user/tenant identifiers")
        return 1
    else:
        print(f"PASSED: All {len(workflow_files)} workflow files comply with security policy.")
        return 0


if __name__ == "__main__":
    sys.exit(main())
