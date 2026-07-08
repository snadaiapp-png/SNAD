#!/usr/bin/env python3
"""Fail-closed governance guard for SANAD production GO claims.

This script prevents accidental release-governance drift. It does not call
GitHub or external systems; it validates repository evidence files and blocks
unsafe textual GO declarations unless the explicit CI override is set for a
formal release workflow.
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
    re.compile(r"FINAL[_ -]?RELEASE[_ -]?DECISION\s*[:=]\s*GO\b", re.IGNORECASE),
    re.compile(r"PRODUCTION[_ -]?AUTHORIZATION\s*[:=]\s*(YES|APPROVED|GO)\b", re.IGNORECASE),
    re.compile(r"COMMERCIAL[_ -]?RELEASE\s*[:=]\s*GO\b", re.IGNORECASE),
    re.compile(r"GOVERNANCE[_ -]?GO\s*[:=]\s*(APPROVED|YES|TRUE|GO)\b", re.IGNORECASE),
    re.compile(r"releaseAuthorized\s*[:=]\s*true\b", re.IGNORECASE),
]

SAFE_REFERENCE_PATTERNS = [
    "HOSTING_READY != APPLICATION_PASS != GOVERNANCE_GO",
]


def fail(message: str) -> None:
    print(f"GOVERNANCE_GUARD_FAIL: {message}")
    sys.exit(1)


def main() -> int:
    missing = [path for path in REQUIRED_FILES if not (ROOT / path).is_file()]
    if missing:
        fail("missing required governance files: " + ", ".join(missing))

    allow_go = os.getenv("SANAD_ALLOW_PRODUCTION_GO", "").lower() == "true"

    if not allow_go:
        for path in ROOT.rglob("*.md"):
            if ".git" in path.parts:
                continue
            text = path.read_text(encoding="utf-8", errors="ignore")
            safe_text = text
            for safe in SAFE_REFERENCE_PATTERNS:
                safe_text = safe_text.replace(safe, "")
            for pattern in UNSAFE_GO_PATTERNS:
                if pattern.search(safe_text):
                    rel = path.relative_to(ROOT)
                    fail(f"unsafe production GO declaration found in {rel}; set SANAD_ALLOW_PRODUCTION_GO=true only in a formal release workflow with all blocker evidence")

    print("GOVERNANCE_GUARD_PASS: required files exist and no unsafe production GO declaration was found")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
