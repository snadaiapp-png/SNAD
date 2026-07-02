#!/usr/bin/env python3
"""Stage 06 production-readiness governance gate.

This gate intentionally distinguishes repository-certifiable readiness from
external commercial release dependencies. It must fail if the repository claims
commercial go-live, HA/SLA, external audit, or provider rollback completion
without explicit evidence.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
STAGE06 = ROOT / "docs" / "operations" / "STAGE06-PRODUCTION-READINESS.md"
GATE_STATUS = ROOT / "docs" / "audit" / "SANAD-GATE-STATUS.md"
ROLLBACK_PLAN = ROOT / "docs" / "operations" / "SANAD-ROLLBACK-DRILL-PLAN.md"
ROLLBACK_REPORT = ROOT / "docs" / "operations" / "SANAD-ROLLBACK-DRILL-REPORT.md"

REQUIRED_STAGE06_TERMS = [
    "f16c97297cde39cc4ad899e520b65b7b8b71cc95",
    "28620212355",
    "15/15 jobs passed",
    "63 tests, 0 failures, 0 errors, 0 skipped",
    "544 tests, 0 failures, 0 errors",
    "CERTIFIED FOR CONTROLLED RELEASE PREPARATION",
    "Commercial Production Release: NOT AUTHORIZED",
    "Stage 07 / Release Authorization: REQUIRED",
    "EXTERNAL-DEPENDENCY",
    "CONTROLLED-IN-CI",
]

FORBIDDEN_FALSE_CLAIMS = [
    r"Commercial Production Release:\s*AUTHORIZED",
    r"Commercial Go-Live:\s*AUTHORIZED",
    r"External security audit:\s*PASS",
    r"HA/SLA:\s*PASS",
    r"Provider rollback:\s*PASS",
    r"Final Go/No-Go\s*\|\s*PASS",
]


def read(path: Path) -> str:
    if not path.exists():
        raise AssertionError(f"Missing required Stage 06 artifact: {path}")
    return path.read_text(encoding="utf-8")


def assert_contains(text: str, term: str, label: str) -> None:
    if term not in text:
        raise AssertionError(f"{label}: missing required term: {term}")


def assert_not_matches(text: str, pattern: str, label: str) -> None:
    if re.search(pattern, text, flags=re.IGNORECASE):
        raise AssertionError(f"{label}: forbidden false readiness claim matched: {pattern}")


def main() -> int:
    stage06 = read(STAGE06)
    gate_status = read(GATE_STATUS)
    rollback_plan = read(ROLLBACK_PLAN)
    rollback_report = read(ROLLBACK_REPORT)

    for term in REQUIRED_STAGE06_TERMS:
        assert_contains(stage06, term, "stage06 document")

    for pattern in FORBIDDEN_FALSE_CLAIMS:
        assert_not_matches(stage06, pattern, "stage06 document")
        assert_not_matches(gate_status, pattern, "gate status")

    assert_contains(rollback_plan, "No database-destructive rollback", "rollback plan")
    assert_contains(rollback_report, "non-destructive", "rollback report")
    assert_contains(rollback_report, "Stage 06", "rollback report")

    # The status file must not pretend commercial production is authorized.
    assert_contains(gate_status, "Commercial Go-Live Gate", "gate status")
    assert_contains(gate_status, "Production Readiness Gate", "gate status")

    print("Stage 06 readiness governance gate: PASS")
    print("stage05_baseline_present=true")
    print("stage06_control_matrix_valid=true")
    print("rollback_drill_status_not_falsified=true")
    print("commercial_go_live_not_authorized=true")
    print("external_dependencies_declared=true")
    print("quality_gate_reference_present=true")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as exc:
        print(f"Stage 06 readiness governance gate: FAIL — {exc}", file=sys.stderr)
        raise SystemExit(1)
