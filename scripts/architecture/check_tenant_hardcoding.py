#!/usr/bin/env python3
"""
SANAD Architecture Protection — Multi-Tenant Hardcoded Tenant ID Validator
==========================================================================

Enforces Architecture Protection Policy §8 (Multi-Tenant Rules):
  Hardcoded tenant IDs are FORBIDDEN.

Reference: docs/governance/ARCHITECTURE-PROTECTION-POLICY.md §8
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]

SCAN_ROOTS = [
    REPO_ROOT / "apps" / "sanad-platform" / "src" / "main",
    REPO_ROOT / "apps" / "web" / "app",
    REPO_ROOT / "apps" / "web" / "lib",
]

SKIP_PATHS = [
    "node_modules", ".next", "dist", "build", "target",
    "test", "__tests__", ".spec.", ".test.", "fixtures", "mocks",
]

SCAN_EXTENSIONS = {".java", ".kt", ".ts", ".tsx", ".js", ".jsx", ".sql"}

FORBIDDEN_PATTERNS = [
    (re.compile(r'\btenantId\b\s*[:=]\s*["\']default["\']'), 'tenantId="default"'),
    (re.compile(r'\btenant_id\b\s*[:=]\s*["\']default["\']'), 'tenant_id="default"'),
    (re.compile(r'\btenantKey\b\s*[:=]\s*["\']default["\']'), 'tenantKey="default"'),
    (re.compile(r'["\']tenant-default["\']'), '"tenant-default"'),
    (re.compile(r'["\']00000000-0000-0000-0000-000000000000["\']'), 'zero-uuid tenant'),
    (re.compile(r'["\']DEFAULT_TENANT_ID["\']'), 'env var DEFAULT_TENANT_ID'),
    (re.compile(r'["\']DEFAULT_TENANT["\']'), 'env var DEFAULT_TENANT'),
]


def should_skip(path: Path) -> bool:
    parts = path.parts
    for skip in SKIP_PATHS:
        if any(skip in p for p in parts):
            return True
    return False


def scan_file(path: Path) -> list[str]:
    violations = []
    try:
        text = path.read_text(encoding="utf-8", errors="replace")
    except (OSError, UnicodeDecodeError):
        return violations

    rel = path.relative_to(REPO_ROOT)
    rel_str = str(rel)

    if "seed_default_tenant" in rel_str:
        return violations

    cleaned_lines = []
    for line in text.splitlines():
        line_clean = re.sub(r'//.*$', '', line)
        line_clean = re.sub(r'--.*$', '', line_clean)
        cleaned_lines.append(line_clean)
    cleaned = "\n".join(cleaned_lines)
    cleaned = re.sub(r'/\*.*?\*/', '', cleaned, flags=re.DOTALL)

    for pattern, label in FORBIDDEN_PATTERNS:
        for m in pattern.finditer(cleaned):
            line_no = cleaned[:m.start()].count("\n") + 1
            violations.append(
                f"[HARDCODED-TENANT] {rel_str}:{line_no}  pattern={label}\n"
                f"    Policy: §8 — Hardcoded tenant IDs are forbidden. "
                f"Tenant resolution MUST come from Authenticated Session / Tenant Context."
            )
    return violations


def main() -> int:
    all_violations: list[str] = []
    files_scanned = 0

    for root in SCAN_ROOTS:
        if not root.exists():
            continue
        for path in root.rglob("*"):
            if not path.is_file():
                continue
            if path.suffix not in SCAN_EXTENSIONS:
                continue
            if should_skip(path):
                continue
            files_scanned += 1
            all_violations += scan_file(path)

    print(f"[INFO] Scanned {files_scanned} source files for hardcoded tenant IDs")
    if not all_violations:
        print("[PASS] No hardcoded tenant IDs detected — multi-tenant validation passed")
        return 0

    print(f"[FAIL] Hardcoded tenant ID violations: {len(all_violations)}\n")
    for v in all_violations:
        print(v)
        print()
    return 1


if __name__ == "__main__":
    sys.exit(main())
