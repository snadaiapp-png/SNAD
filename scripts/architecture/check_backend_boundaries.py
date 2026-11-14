#!/usr/bin/env python3
"""
SANAD Architecture Protection — Backend Bounded Context Boundary Validator
=========================================================================

Enforces Architecture Protection Policy §4.2 (backend import rules) and §5.1
(layer dependency direction) on the Spring Boot backend
(`apps/sanad-platform/src/main/java/com/sanad/platform/`).

Validations:
  1. Cross-context imports between BUSINESS bounded contexts are forbidden:
     - executive.* MUST NOT import health.*, crm.*, erp.*, accounting.*, hrm.*, pos.*
     - health.*    MUST NOT import executive.*, crm.*, erp.*, accounting.*, hrm.*, pos.*
     - crm.*       MUST NOT import executive.*, health.*, erp.*, accounting.*, hrm.*, pos.*
     - (symmetric rule for all BUSINESS contexts per §2)
  2. Layer direction is Presentation -> Application -> Domain -> Infrastructure:
     - domain.*      MUST NOT import application.*, infrastructure.*, api.*, service.*
     - application.* MUST NOT import infrastructure.*
     - api.*         MUST NOT import infrastructure.* directly (must go via application)
  3. DTO leakage: api.* DTOs MUST NOT be imported by infrastructure.*

Note: Supporting modules (Admin, Organization, User, Tenant, Access, Core) are
NOT business bounded contexts per §2 — they MAY be imported by any business
context (cross-cutting concerns like audit, identity, tenant resolution).

Exit code: 0 if all checks pass, 1 if any violation is found.

Reference: docs/governance/ARCHITECTURE-PROTECTION-POLICY.md
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

# ----------------------------------------------------------------------------
# Configuration
# ----------------------------------------------------------------------------

REPO_ROOT = Path(__file__).resolve().parents[2]
BACKEND_ROOT = REPO_ROOT / "apps" / "sanad-platform" / "src" / "main" / "java" / "com" / "sanad" / "platform"
BASE_PACKAGE = "com.sanad.platform"

# ----------------------------------------------------------------------------
# Bounded contexts classification (per Architecture Protection Policy §2)
# ----------------------------------------------------------------------------
# BUSINESS bounded contexts: architecturally independent, MUST NOT import each
# other directly. Communication is allowed only through public APIs / events /
# message bus (§4.2).
#
# SUPPORTING modules: cross-cutting infrastructure that may be imported by any
# business context (audit, identity, organization, tenant resolution, etc.).
# These are NOT subject to the cross-context import ban but ARE subject to
# layering rules (§5.1).
# ----------------------------------------------------------------------------

BUSINESS_CONTEXTS = {
    "executive":       "Executive Management",
    "health":          "System Health",
    "crm":             "CRM",
    "erp":             "ERP",
    "accounting":      "Accounting",
    "hrm":             "HRM",
    "pos":             "POS",
    "businessprocess": "Workflow Engine",
    "ai":              "AI Platform",
}

# Supporting modules — NOT business bounded contexts per §2.
# These MAY be imported by business bounded contexts (cross-cutting concerns).
SUPPORTING_MODULES = {
    "admin":         "Admin (cross-cutting)",
    "organization":  "Organization",
    "user":          "User",
    "tenant":        "Tenant",
    "access":        "Identity / Access",
    "shared":        "Core Platform (shared)",
    "config":        "Core Platform (config)",
    "security":      "Core Platform (security)",
    "infrastructure": "Core Platform (infrastructure)",
    "internal":      "Internal utilities",
    "scale":         "Scaling infrastructure",
    "api":           "Shared API utilities",
    "application":   "Shared application utilities",
    "domain":        "Shared domain utilities",
}

# Backwards-compat alias used in error messages below
BOUNDED_CONTEXTS = {**BUSINESS_CONTEXTS, **SUPPORTING_MODULES}

# Layer sub-packages per bounded context (relative to BASE_PACKAGE.<context>)
LAYERS = ["api", "application", "domain", "infrastructure", "service"]

# Allowed cross-context imports: NONE between business contexts.
ALLOWED_CROSS_CONTEXT = set()

# Allowed base/infra packages that any context may import
ALLOWED_BASE_PACKAGES = {
    f"{BASE_PACKAGE}.shared",
    f"{BASE_PACKAGE}.config",
    f"{BASE_PACKAGE}.security",
    f"{BASE_PACKAGE}.tenant",  # tenant context holder (infrastructure)
    f"{BASE_PACKAGE}.infrastructure",  # shared infrastructure (DB config, etc.)
}

IMPORT_RE = re.compile(r"^\s*import\s+(static\s+)?([\w\.*]+)\s*;", re.MULTILINE)
PACKAGE_RE = re.compile(r"^\s*package\s+([\w\.]+)\s*;", re.MULTILINE)


def find_java_files(root: Path) -> list[Path]:
    return list(root.rglob("*.java"))


def parse_file(path: Path) -> tuple[str | None, list[str]]:
    text = path.read_text(encoding="utf-8", errors="replace")
    pkg_match = PACKAGE_RE.search(text)
    package = pkg_match.group(1) if pkg_match else None
    imports = IMPORT_RE.findall(text)
    imports = [m[1] for m in imports]
    return package, imports


def context_of(package: str) -> str | None:
    if not package.startswith(BASE_PACKAGE + "."):
        return None
    rest = package[len(BASE_PACKAGE) + 1:]
    parts = rest.split(".", 1)
    if not parts:
        return None
    candidate = parts[0]
    if candidate in BOUNDED_CONTEXTS:
        return candidate
    return None


def layer_of(package: str, ctx: str) -> str | None:
    prefix = f"{BASE_PACKAGE}.{ctx}."
    if not package.startswith(prefix):
        return None
    rest = package[len(prefix):]
    parts = rest.split(".", 1)
    if not parts or not parts[0]:
        return None
    layer = parts[0]
    if layer in LAYERS:
        return layer
    return None


def is_allowed_external(fqn: str) -> bool:
    if not fqn.startswith(BASE_PACKAGE):
        return True
    for allowed in ALLOWED_BASE_PACKAGES:
        if fqn == allowed or fqn.startswith(allowed + "."):
            return True
    return False


def check_cross_context_imports(files: list[Path]) -> list[str]:
    """
    Rule: business bounded contexts MUST NOT import each other directly.

    Per §4.2: Executive, System Health, CRM, ERP, HRM, Accounting, POS, Workflow,
    AI are architecturally independent business bounded contexts. They may NOT
    import each other's internal packages (api, service, application, domain,
    infrastructure). Communication is allowed only through public APIs / events
    / message bus.

    Supporting modules (Admin, Organization, User, Tenant, Access, Core) MAY be
    imported by business contexts — they provide cross-cutting concerns (audit,
    identity, tenant resolution, etc.).
    """
    violations = []
    for f in files:
        pkg, imports = parse_file(f)
        if pkg is None:
            continue
        src_ctx = context_of(pkg)
        if src_ctx is None:
            continue
        # Only enforce the rule for BUSINESS contexts (per §2)
        if src_ctx not in BUSINESS_CONTEXTS:
            continue
        for imp in imports:
            if is_allowed_external(imp):
                continue
            tgt_ctx = context_of(imp)
            if tgt_ctx is None:
                continue
            if tgt_ctx == src_ctx:
                continue  # same context — allowed
            # Only flag if BOTH source and target are BUSINESS contexts
            if tgt_ctx not in BUSINESS_CONTEXTS:
                continue  # importing a supporting module — allowed
            # Cross-context import between two BUSINESS contexts — forbidden
            key = (src_ctx, tgt_ctx)
            if key in ALLOWED_CROSS_CONTEXT:
                continue
            rel = f.relative_to(REPO_ROOT)
            violations.append(
                f"[CROSS-CONTEXT] {rel}\n"
                f"    {BUSINESS_CONTEXTS.get(src_ctx, src_ctx)} ({pkg}) imports "
                f"{BUSINESS_CONTEXTS.get(tgt_ctx, tgt_ctx)} ({imp})\n"
                f"    Policy: §4.2 — business bounded contexts communicate only through public APIs / events / message bus"
            )
    return violations


def check_layer_direction(files: list[Path]) -> list[str]:
    violations = []
    for f in files:
        pkg, imports = parse_file(f)
        if pkg is None:
            continue
        src_ctx = context_of(pkg)
        if src_ctx is None:
            continue
        src_layer = layer_of(pkg, src_ctx)
        if src_layer is None:
            continue
        for imp in imports:
            if not imp.startswith(BASE_PACKAGE + "."):
                continue
            tgt_ctx = context_of(imp)
            if tgt_ctx is None:
                if is_allowed_external(imp):
                    continue
                continue
            if tgt_ctx != src_ctx:
                continue
            tgt_layer = layer_of(imp, tgt_ctx)
            if tgt_layer is None:
                continue
            if src_layer == "domain" and tgt_layer in {"application", "infrastructure", "api", "service"}:
                rel = f.relative_to(REPO_ROOT)
                violations.append(
                    f"[LAYER-VIOLATION] {rel}\n"
                    f"    domain ({pkg}) imports {tgt_layer} ({imp})\n"
                    f"    Policy: §5.1 — Domain layer MUST be pure (depends on nothing)"
                )
            if src_layer == "application" and tgt_layer == "infrastructure":
                rel = f.relative_to(REPO_ROOT)
                violations.append(
                    f"[LAYER-VIOLATION] {rel}\n"
                    f"    application ({pkg}) imports infrastructure ({imp})\n"
                    f"    Policy: §5.1 — Application layer depends on domain ONLY"
                )
            if src_layer == "api" and tgt_layer == "infrastructure":
                rel = f.relative_to(REPO_ROOT)
                violations.append(
                    f"[LAYER-VIOLATION] {rel}\n"
                    f"    api ({pkg}) imports infrastructure ({imp})\n"
                    f"    Policy: §5.1 — Presentation layer must go through Application layer"
                )
    return violations


def check_dto_leakage(files: list[Path]) -> list[str]:
    violations = []
    for f in files:
        pkg, imports = parse_file(f)
        if pkg is None:
            continue
        src_ctx = context_of(pkg)
        if src_ctx is None:
            continue
        src_layer = layer_of(pkg, src_ctx)
        if src_layer != "infrastructure":
            continue
        for imp in imports:
            if not imp.startswith(BASE_PACKAGE + "."):
                continue
            tgt_ctx = context_of(imp)
            if tgt_ctx is None:
                continue
            tgt_layer = layer_of(imp, tgt_ctx)
            if tgt_layer == "api":
                rel = f.relative_to(REPO_ROOT)
                violations.append(
                    f"[DTO-LEAKAGE] {rel}\n"
                    f"    infrastructure ({pkg}) imports api ({imp})\n"
                    f"    Policy: §12 — DTO leakage forbidden"
                )
    return violations


def main() -> int:
    if not BACKEND_ROOT.exists():
        print(f"[SKIP] backend root not found at {BACKEND_ROOT}")
        return 0

    java_files = find_java_files(BACKEND_ROOT)
    print(f"[INFO] Scanning {len(java_files)} Java files under {BACKEND_ROOT}")
    print(f"[INFO] BUSINESS bounded contexts: {len(BUSINESS_CONTEXTS)}")
    print(f"[INFO] SUPPORTING modules:        {len(SUPPORTING_MODULES)}")

    all_violations: list[str] = []
    all_violations += check_cross_context_imports(java_files)
    all_violations += check_layer_direction(java_files)
    all_violations += check_dto_leakage(java_files)

    if not all_violations:
        print("[PASS] Backend architecture boundaries validated — 0 violations")
        return 0

    print(f"[FAIL] Backend architecture violations: {len(all_violations)}\n")
    for v in all_violations:
        print(v)
        print()
    return 1


if __name__ == "__main__":
    sys.exit(main())
