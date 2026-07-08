#!/usr/bin/env python3
"""Fail-closed governance guard for SANAD production GO claims.

This script prevents accidental release-governance drift. It validates required
repository evidence files and blocks unsafe release approval declarations.

It intentionally ignores examples, templates, code blocks, and requirement text
so that governance documents may describe required fields without being treated
as actual production approval.
"""

from __future__ import annotations

import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

REQUIRED_FILES = [
    "docs/release/PRODUCTION-GO-CHECKLIST.md",
    "docs/release/STAGE-09-10-CLOSURE-EVIDENCE.md",
    "docs/security/SECRET-HANDLING-POLICY.md",
    "docs/crm/STAGE-09-CRM-BASELINE.md",
    "docs/ai/AI-GATEWAY-CONTRACT.md",
    "docs/ai/AI-SAFETY-EVALUATION-OPERATIONS.md",
    "docs/operations/PRODUCTION-BFF-BACKEND-SMOKE-EVIDENCE.md",
]

UNSAFE_GO_PATTERNS = [
    re.compile(r"\bFINAL[_ -]?RELEASE[_ -]?DECISION\s*[:=]\s*GO\b", re.IGNORECASE),
    re.compile(r"\bPRODUCTION[_ -]?AUTHORIZATION\s*[:=]\s*(YES|APPROVED|GO)\b", re.IGNORECASE),
    re.compile(r"\bCOMMERCIAL[_ -]?RELEASE\s*[:=]\s*GO\b", re.IGNORECASE),
    re.compile(r"\bGOVERNANCE[_ -]?GO\s*[:=]\s*(APPROVED|YES|TRUE|GO)\b", re.IGNORECASE),
    re.compile(r"\breleaseAuthorized\s*[:=]\s*true\b", re.IGNORECASE),
]

IGNORE_HINTS = (
    "required",
    "requirement",
    "must",
    "template",
    "example",
    "sample",
    "records",
    "field",
    "forbidden",
    "not ",
    "no-go",
    "tbd",
)

SAFE_REFERENCE_PATTERNS = (
    "HOSTING_READY != APPLICATION_PASS != GOVERNANCE_GO",
)


def fail(message: str) -> None:
    print(f"GOVERNANCE_GUARD_FAIL: {message}")
    sys.exit(1)


def is_ignored_line(line: str, in_code_block: bool) -> bool:
    stripped = line.strip()
    lowered = stripped.lower()
    if not stripped:
        return True
    if in_code_block:
        return True
    if stripped.startswith("|"):
        return True
    if stripped.startswith(("-", "*", "1.", "2.", "3.", "4.", "5.", "6.", "7.", "8.", "9.")):
        return True
    if "`" in stripped:
        return True
    if any(pattern in stripped for pattern in SAFE_REFERENCE_PATTERNS):
        return True
    if any(hint in lowered for hint in IGNORE_HINTS):
        return True
    return False


def scan_markdown_file(path: Path) -> list[str]:
    violations: list[str] = []
    in_code_block = False
    for lineno, line in enumerate(path.read_text(encoding="utf-8", errors="ignore").splitlines(), start=1):
        if line.strip().startswith("```"):
            in_code_block = not in_code_block
            continue
        if is_ignored_line(line, in_code_block):
            continue
        for pattern in UNSAFE_GO_PATTERNS:
            if pattern.search(line):
                violations.append(f"{path.relative_to(ROOT)}:{lineno}: {line.strip()}")
    return violations


def main() -> int:
    missing = [path for path in REQUIRED_FILES if not (ROOT / path).is_file()]
    if missing:
        fail("missing required governance files: " + ", ".join(missing))

    allow_go = os.getenv("SANAD_ALLOW_PRODUCTION_GO", "").lower() == "true"

    if not allow_go:
        violations: list[str] = []
        for path in ROOT.rglob("*.md"):
            if ".git" in path.parts:
                continue
            violations.extend(scan_markdown_file(path))
        if violations:
            fail("unsafe production GO declaration found:\n" + "\n".join(violations))

    print("GOVERNANCE_GUARD_PASS: required files exist and no unsafe production GO declaration was found")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
