#!/usr/bin/env python3
"""SANAD owner-governance checker.

Validates the owner-only governance model without weakening technical gates.
"""

from __future__ import annotations

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
REQUIRED = [
    "docs/governance/OWNER-AUTHORITY-MODEL.md",
    "docs/release/OWNER-PRODUCTION-GO-CHECKLIST.md",
    "docs/security/OWNER-RISK-ACCEPTANCE-REGISTER.md",
]


def main() -> int:
    missing = [path for path in REQUIRED if not (ROOT / path).is_file()]
    if missing:
        print("OWNER_GOVERNANCE_FAIL: missing " + ", ".join(missing))
        return 1

    authority = (ROOT / "docs/governance/OWNER-AUTHORITY-MODEL.md").read_text(encoding="utf-8")
    if "snadaiapp-png" not in authority:
        print("OWNER_GOVERNANCE_FAIL: owner account is not recorded")
        return 1
    if "Owner authority replaces multi-account approval; it does not replace evidence" not in authority:
        print("OWNER_GOVERNANCE_FAIL: evidence-preservation rule missing")
        return 1

    print("OWNER_GOVERNANCE_PASS: owner authority model is documented and evidence gates remain mandatory")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
