#!/usr/bin/env python3
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EVIDENCE = ROOT / "docs/operations/stage06-readiness-evidence.json"
STAGE06 = ROOT / "docs/operations/STAGE06-PRODUCTION-READINESS.md"
GATE = ROOT / "docs/audit/SANAD-GATE-STATUS.md"
PLAN = ROOT / "docs/operations/SANAD-ROLLBACK-DRILL-PLAN.md"
REPORT = ROOT / "docs/operations/SANAD-ROLLBACK-DRILL-REPORT.md"
WORKFLOW = ROOT / ".github/workflows/stage06-production-readiness.yml"

EXPECTED_JOBS = {
    "stage06-governance", "stage06-doc-integrity", "stage06-backend",
    "stage06-frontend", "stage06-postgres-rehearsal", "stage06-runtime-smoke",
    "stage06-security", "stage06-rollback-rehearsal", "stage06-final"
}


def require(value: bool, message: str) -> None:
    if not value:
        raise AssertionError(message)


def text(path: Path) -> str:
    require(path.exists(), f"missing artifact: {path}")
    return path.read_text(encoding="utf-8")


def main() -> int:
    evidence = json.loads(text(EVIDENCE))
    workflow = text(WORKFLOW)
    readiness = text(STAGE06)
    gate = text(GATE)
    plan = text(PLAN)
    report = text(REPORT)
    print("Stage 06 executable readiness artifacts loaded")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, json.JSONDecodeError) as exc:
        print(f"Stage 06 executable readiness gate: FAIL — {exc}", file=sys.stderr)
        raise SystemExit(1)
