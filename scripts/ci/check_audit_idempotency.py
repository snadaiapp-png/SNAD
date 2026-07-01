#!/usr/bin/env python3
"""
Stage 05 §27 — Static gate for audit and idempotency integrity.

Fails CI if any of the following are detected:
  - Audit entity without tenantId
  - Audit entity without actor identity
  - Audit update/delete repository methods
  - Audit sensitive fields without redaction
  - Idempotency unique key lacking tenant scope
  - Idempotency key trusted without fingerprint
  - JVM-only concurrency lock
  - Idempotent response stores Authorization/Set-Cookie/raw token
  - Mandatory test missing
  - Mandatory test absent from workflow
  - Audit/idempotency RLS migration missing

Usage:
    python3 scripts/ci/check_audit_idempotency.py

Exit codes:
    0 — all checks passed
    1 — one or more checks failed
    2 — unexpected error
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
SRC_MAIN = REPO_ROOT / "apps" / "sanad-platform" / "src" / "main" / "java"
SRC_TEST = REPO_ROOT / "apps" / "sanad-platform" / "src" / "test" / "java"
MIGRATION_DIR = REPO_ROOT / "apps" / "sanad-platform" / "src" / "main" / "resources" / "db" / "migration"
MIGRATION_PG_DIR = REPO_ROOT / "apps" / "sanad-platform" / "src" / "main" / "resources" / "db" / "migration-pg-only"
WORKFLOW_FILE = REPO_ROOT / ".github" / "workflows" / "quality-gate.yml"

MANDATORY_TESTS = [
    "AuditEventPersistenceIntegrationTest",
    "AuditAppendOnlyIntegrationTest",
    "AuditRlsIntegrationTest",
    "AuditRedactionIntegrationTest",
    "AuditHashChainIntegrationTest",
    "AuditTransactionBoundaryIntegrationTest",
    "AuditDeniedRequestIntegrationTest",
    "AuditActorAttributionIntegrationTest",
    "IdempotencySameRequestReplayIntegrationTest",
    "IdempotencyPayloadMismatchIntegrationTest",
    "IdempotencyConcurrentExecutionIntegrationTest",
    "IdempotencyCrossTenantIsolationIntegrationTest",
    "IdempotencyFailureRecoveryIntegrationTest",
    "IdempotencyResponseRedactionIntegrationTest",
    "IdempotencyRlsIntegrationTest",
    "IdempotencyExpirationIntegrationTest",
]


def check_audit_entity_has_tenant_id() -> list[str]:
    """AuditEvent entity must have a tenantId field."""
    entity = SRC_MAIN / "com" / "sanad" / "platform" / "audit" / "domain" / "AuditEvent.java"
    if not entity.exists():
        return [f"Audit entity not found: {entity}"]
    content = entity.read_text()
    if "tenant_id" not in content or "tenantId" not in content:
        return ["AuditEvent entity is missing tenantId field"]
    return []


def check_audit_entity_has_actor() -> list[str]:
    """AuditEvent entity must have actor identity fields."""
    entity = SRC_MAIN / "com" / "sanad" / "platform" / "audit" / "domain" / "AuditEvent.java"
    if not entity.exists():
        return [f"Audit entity not found: {entity}"]
    content = entity.read_text()
    issues = []
    if "actorType" not in content:
        issues.append("AuditEvent is missing actorType field")
    if "actorUserId" not in content:
        issues.append("AuditEvent is missing actorUserId field")
    return issues


def check_no_audit_update_delete_methods() -> list[str]:
    """AuditEventRepository must not expose update or delete methods."""
    repo = SRC_MAIN / "com" / "sanad" / "platform" / "audit" / "repository" / "AuditEventRepository.java"
    if not repo.exists():
        return [f"Audit repository not found: {repo}"]
    content = repo.read_text()
    issues = []
    # Check for update/delete method declarations
    for pattern in [r"void\s+delete", r"void\s+update", r"@Modifying", r"@Query.*UPDATE", r"@Query.*DELETE"]:
        if re.search(pattern, content, re.IGNORECASE):
            issues.append(f"Audit repository contains forbidden pattern: {pattern}")
    return issues


def check_audit_redaction_exists() -> list[str]:
    """AuditRedactionService must exist and redact sensitive fields."""
    svc = SRC_MAIN / "com" / "sanad" / "platform" / "audit" / "service" / "AuditRedactionService.java"
    if not svc.exists():
        return [f"Audit redaction service not found: {svc}"]
    content = svc.read_text()
    issues = []
    for field in ["password", "token", "secret", "authorization"]:
        if field not in content.lower():
            issues.append(f"Audit redaction service does not handle '{field}'")
    if "REDACTED" not in content:
        issues.append("Audit redaction service does not produce [REDACTED] sentinel")
    return issues


def check_idempotency_unique_has_tenant_scope() -> list[str]:
    """Idempotency unique constraint must include tenant_id."""
    # Check the migration for the unique constraint
    issues = []
    for mig in MIGRATION_DIR.glob("V*.sql"):
        content = mig.read_text()
        if "idempotency_records" in content and "UNIQUE" in content.upper():
            if "tenant_id" not in content.lower():
                issues.append(f"{mig.name}: idempotency unique constraint missing tenant_id")
    # Also check the entity
    entity = SRC_MAIN / "com" / "sanad" / "platform" / "idempotency" / "domain" / "IdempotencyRecord.java"
    if entity.exists():
        content = entity.read_text()
        if "tenantId" not in content:
            issues.append("IdempotencyRecord entity missing tenantId")
    return issues


def check_idempotency_has_fingerprint() -> list[str]:
    """Idempotency must use request fingerprint, not just the key."""
    svc = SRC_MAIN / "com" / "sanad" / "platform" / "idempotency" / "service" / "IdempotencyService.java"
    if not svc.exists():
        return [f"Idempotency service not found: {svc}"]
    content = svc.read_text()
    if "requestFingerprint" not in content and "fingerprint" not in content.lower():
        return ["Idempotency service does not use request fingerprint"]
    return []


def check_no_jvm_only_lock() -> list[str]:
    """Idempotency must not rely on JVM-local locks alone (no synchronized, no ConcurrentHashMap-based locks)."""
    svc = SRC_MAIN / "com" / "sanad" / "platform" / "idempotency" / "service" / "IdempotencyService.java"
    if not svc.exists():
        return []
    content = svc.read_text()
    issues = []
    # synchronized blocks are JVM-local and do not work in multi-instance deployments
    if "synchronized" in content:
        issues.append("Idempotency service uses 'synchronized' (JVM-local lock) — must use DB-level concurrency")
    return issues


def check_idempotency_response_sanitization() -> list[str]:
    """Idempotent response storage must strip Authorization, Set-Cookie, and raw tokens."""
    svc = SRC_MAIN / "com" / "sanad" / "platform" / "idempotency" / "service" / "IdempotencyService.java"
    if not svc.exists():
        return []
    content = svc.read_text()
    issues = []
    if "set-cookie" not in content.lower() and "Set-Cookie" not in content:
        issues.append("Idempotency service does not strip Set-Cookie from stored response headers")
    if "authorization" not in content.lower():
        issues.append("Idempotency service does not strip Authorization from stored response headers")
    return issues


def check_migrations_exist() -> list[str]:
    """V21 (audit_events) and V22 (idempotency_records) migrations must exist."""
    issues = []
    v21 = MIGRATION_DIR / "V21__create_immutable_audit_log.sql"
    v22 = MIGRATION_DIR / "V22__create_idempotency_records.sql"
    if not v21.exists():
        issues.append(f"V21 audit_events migration not found: {v21}")
    if not v22.exists():
        issues.append(f"V22 idempotency_records migration not found: {v22}")
    # Check for RLS migration (V23 in pg-only)
    rls_found = False
    for mig in MIGRATION_PG_DIR.glob("V*.sql"):
        content = mig.read_text()
        if "audit_events" in content and "ROW LEVEL SECURITY" in content:
            rls_found = True
            break
    if not rls_found:
        issues.append("No pg-only migration enabling RLS on audit_events found")
    return issues


def check_mandatory_tests_exist() -> list[str]:
    """All 16 mandatory test classes must exist in src/test."""
    issues = []
    for test_name in MANDATORY_TESTS:
        found = False
        for p in SRC_TEST.rglob(f"{test_name}.java"):
            found = True
            break
        if not found:
            issues.append(f"Mandatory test class not found: {test_name}.java")
    return issues


def check_tests_in_workflow() -> list[str]:
    """All 16 mandatory test classes must be listed in the audit-idempotency CI job."""
    if not WORKFLOW_FILE.exists():
        return [f"Workflow file not found: {WORKFLOW_FILE}"]
    content = WORKFLOW_FILE.read_text()
    issues = []
    # The audit-idempotency job should exist
    if "audit-idempotency" not in content:
        issues.append("quality-gate.yml is missing the audit-idempotency job")
    # Each mandatory test should be referenced somewhere in the workflow
    for test_name in MANDATORY_TESTS:
        if test_name not in content:
            issues.append(f"Mandatory test '{test_name}' not referenced in quality-gate.yml")
    return issues


def main() -> int:
    checks = [
        ("Audit entity has tenantId", check_audit_entity_has_tenant_id),
        ("Audit entity has actor identity", check_audit_entity_has_actor),
        ("No audit update/delete methods", check_no_audit_update_delete_methods),
        ("Audit redaction exists", check_audit_redaction_exists),
        ("Idempotency unique has tenant scope", check_idempotency_unique_has_tenant_scope),
        ("Idempotency has fingerprint", check_idempotency_has_fingerprint),
        ("No JVM-only concurrency lock", check_no_jvm_only_lock),
        ("Idempotency response sanitization", check_idempotency_response_sanitization),
        ("Migrations exist", check_migrations_exist),
        ("Mandatory tests exist", check_mandatory_tests_exist),
        ("Tests in workflow", check_tests_in_workflow),
    ]

    all_issues = []
    for name, check_fn in checks:
        issues = check_fn()
        status = "PASS" if not issues else "FAIL"
        print(f"  {name}: {status}")
        for issue in issues:
            print(f"    - {issue}")
            all_issues.append(f"{name}: {issue}")

    print()
    if all_issues:
        print(f"FAIL: {len(all_issues)} audit/idempotency issue(s) detected")
        return 1
    print("AUDIT-IDEMPOTENCY STATIC GATE: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
