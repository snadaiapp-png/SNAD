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

    require(evidence.get("stage") == "06", "stage must be 06")
    require(evidence.get("scope") == "Production Readiness and Operational Closure", "scope mismatch")
    require(evidence.get("status") in {"PENDING_EXECUTION", "CERTIFIED"}, "invalid status")
    baseline = evidence.get("stage05Baseline", {})
    require(baseline.get("mergeCommit") == "f16c97297cde39cc4ad899e520b65b7b8b71cc95",
            "Stage 05 merge SHA mismatch")
    require(baseline.get("qualityGateRun") == 28620212355, "Stage 05 run mismatch")
    require(baseline.get("jobsPassed") == 15, "Stage 05 passed-job count mismatch")
    require(baseline.get("jobsTotal") == 15, "Stage 05 total-job count mismatch")

    jobs = set(evidence.get("requiredJobs", []))
    require(jobs == EXPECTED_JOBS, "required job matrix mismatch")
    for job in EXPECTED_JOBS:
        require(f"  {job}:" in workflow, f"workflow job missing: {job}")

    require(evidence.get("commercialProductionAuthorized") is False,
            "commercial production must remain unauthorized")
    require(evidence.get("databaseDestructiveRollbackAllowed") is False,
            "database-destructive rollback must remain forbidden")
    require(len(evidence.get("externalDependencies", [])) >= 4,
            "external dependencies are incomplete")

    print("Stage 06 executable readiness manifest: PASS")
    print(f"evidence_status={evidence['status']}")
    print("required_jobs=9")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, json.JSONDecodeError) as exc:
        print(f"Stage 06 executable readiness gate: FAIL — {exc}", file=sys.stderr)
        raise SystemExit(1)
