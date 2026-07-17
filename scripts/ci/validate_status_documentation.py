#!/usr/bin/env python3
"""Fail closed when SANAD status documents are stale, unclassified or contradictory."""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GOV = ROOT / "docs" / "governance"
REGISTRY = GOV / "status-document-registry.json"
CURRENT = GOV / "CURRENT-STATUS.json"
MARKER_REQUIRED = {
    "README.md",
    "docs/README.md",
    "docs/governance/CURRENT-IMPLEMENTATION-STATUS.md",
}


class ValidationError(RuntimeError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValidationError(message)


def read_text(path: str) -> str:
    target = ROOT / path
    require(target.is_file(), f"missing registered document: {path}")
    return target.read_text(encoding="utf-8")


def main() -> int:
    registry = json.loads(REGISTRY.read_text(encoding="utf-8"))
    current = json.loads(CURRENT.read_text(encoding="utf-8"))

    require(registry.get("schema_version") == "1.0", "invalid registry schema")
    require(current.get("schema_version") == "1.0", "invalid current-status schema")
    require(current.get("status_authority") == "CURRENT", "current status is not authoritative")
    require(current.get("commercial_go_live") == "NOT_APPROVED", "commercial status must remain explicit")
    require(current.get("historical_gates", {}).get("ISSUE-101", {}).get("state") == "CLOSED", "Issue #101 is stale")

    open_findings = current.get("open_findings", {})
    closed_findings = current.get("closed_findings", {})
    require("REM-P0-003" in closed_findings, "P0-003 closure missing")
    require("REM-P1-008" in closed_findings, "P1-008 closure missing")
    require("REM-P0-001" in open_findings and "REM-P0-002" in open_findings, "deferred backend risks disappeared")

    stale_phrases = (
        "Issue #101: OPEN",
        "ISSUE_101: OPEN",
        "Render pilot deployment",
        "Current repository stage: **pilot integration foundation",
    )

    authority_paths = [item["path"] for item in registry["current_authorities"]]
    require(set(MARKER_REQUIRED).issubset(set(authority_paths)), "required current authorities missing from registry")
    for path in authority_paths:
        content = read_text(path)
        if path in MARKER_REQUIRED:
            require("STATUS_AUTHORITY: CURRENT" in content, f"missing current-authority marker: {path}")
        for phrase in stale_phrases:
            require(phrase not in content, f"stale phrase in current authority {path}: {phrase}")

    for item in registry["non_current_status_documents"]:
        path = item["path"]
        content = read_text(path)
        expected = f"DOCUMENT STATUS: {item['class']}"
        require(expected in content, f"missing visible classification in {path}: {expected}")
        require(item["replacement"] in content, f"missing replacement pointer in {path}")

    root_readme = read_text("README.md")
    current_md = read_text("docs/governance/CURRENT-IMPLEMENTATION-STATUS.md")
    require("Issue #516" in root_readme and "Issue #516" in current_md, "authoritative tracker not linked")
    require("CONDITIONAL CONTINUE" in current_md, "current executive decision missing")
    require("REM-P1-010" in current_md, "P1-010 status missing")

    print("P1-010 STATUS DOCUMENTATION VALIDATION PASSED")
    print(f"Current authorities={len(authority_paths)}")
    print(f"Classified non-current documents={len(registry['non_current_status_documents'])}")
    print("Issue #101=CLOSED; Issue #516=AUTHORITATIVE")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValidationError, OSError, KeyError, ValueError, json.JSONDecodeError) as exc:
        print(f"P1-010 STATUS DOCUMENTATION ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
