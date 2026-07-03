#!/usr/bin/env python3
"""SANAD Stage 07 fail-closed release authorization validator."""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EVIDENCE = ROOT / "docs/operations/stage07-release-evidence.json"
CHARTER = ROOT / "docs/operations/STAGE07-RELEASE-AUTHORIZATION.md"
GATE_STATUS = ROOT / "docs/audit/SANAD-GATE-STATUS.md"
GO_LIVE = ROOT / "docs/production-readiness/go-live-checklist.md"
GAP_REGISTER = ROOT / "docs/production-readiness/final-gap-register.md"

BASELINE_SHA = "fab656fda377edfe7e06a43896a4c9806ec6c78b"
BASELINE_RUN = 28624469724
ALLOWED_STAGE_STATUS = {"OPEN", "REPOSITORY_CERTIFIED", "AUTHORIZED", "NO_GO"}
ALLOWED_GATE_STATUS = {
    "PENDING", "PASS", "FAIL", "EXTERNAL_DEPENDENCY",
    "NOT_AUTHORIZED", "FORMALLY_ACCEPTED"
}
EXTERNAL_GATES = {
    "productionHaSla",
    "externalSecurityAudit",
    "legalAndDataProtection",
    "disasterRecovery",
    "providerRollback",
}
APPROVERS = {
    "engineering", "security", "operations", "legal",
    "businessOwner", "releaseAuthority"
}


def require(value: bool, message: str) -> None:
    if not value:
        raise AssertionError(message)


def read_text(path: Path) -> str:
    require(path.exists(), f"missing required artifact: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def valid_evidence_ref(value: object) -> bool:
    if not isinstance(value, str) or not value.strip():
        return False
    path = ROOT / value
    return path.exists() and path.is_file() and path.stat().st_size > 0


def main() -> int:
    raw = read_text(EVIDENCE)
    evidence = json.loads(raw)
    charter = read_text(CHARTER)
    gate_status = read_text(GATE_STATUS)
    go_live = read_text(GO_LIVE)
    gaps = read_text(GAP_REGISTER)

    require(evidence.get("stage") == "07", "stage must equal 07")
    require(evidence.get("scope") == "Release Authorization and Commercial Go-Live Gate",
            "Stage 07 scope mismatch")
    status = evidence.get("status")
    require(status in ALLOWED_STAGE_STATUS, f"unsupported Stage 07 status: {status}")

    baseline = evidence.get("baseline", {})
    require(baseline.get("stage06MergeCommit") == BASELINE_SHA,
            "Stage 06 merge baseline mismatch")
    require(baseline.get("stage06WorkflowRun") == BASELINE_RUN,
            "Stage 06 workflow baseline mismatch")
    require(baseline.get("stage06Result") == "3/3 PASS",
            "Stage 06 result must be 3/3 PASS")

    candidate = evidence.get("candidate", {})
    candidate_sha = candidate.get("sha")
    digest = candidate.get("artifactDigest")
    quality_run = candidate.get("qualityGateRun")
    quality_result = candidate.get("qualityGateResult")

    if status == "OPEN":
        require(candidate_sha is None or re.fullmatch(r"[0-9a-f]{40}", str(candidate_sha)),
                "OPEN candidate SHA must be null or full lowercase SHA")
        require(digest is None or re.fullmatch(r"sha256:[0-9a-f]{64}", str(digest)),
                "OPEN artifact digest must be null or sha256 digest")
    else:
        require(re.fullmatch(r"[0-9a-f]{40}", str(candidate_sha or "")) is not None,
                "locked Stage 07 state requires exact candidate SHA")
        require(re.fullmatch(r"sha256:[0-9a-f]{64}", str(digest or "")) is not None,
                "locked Stage 07 state requires immutable artifact digest")
        require(isinstance(quality_run, int) and quality_run > 0,
                "locked Stage 07 state requires Quality Gate run ID")
        require(quality_result == "PASS",
                "locked Stage 07 state requires Quality Gate PASS")

    gates = evidence.get("gates", {})
    required_gates = {
        "repository", "artifactProvenance", "productionHaSla", "loadAndCapacity",
        "externalSecurityAudit", "legalAndDataProtection", "backupRestore",
        "disasterRecovery", "providerRollback", "observabilityAndOnCall",
        "supportSla", "finalGoNoGo"
    }
    require(set(gates) == required_gates,
            f"gate set mismatch: missing={sorted(required_gates - set(gates))}")
    for gate, gate_status_value in gates.items():
        require(gate_status_value in ALLOWED_GATE_STATUS,
                f"invalid status for gate {gate}: {gate_status_value}")

    evidence_refs = evidence.get("evidenceRefs", {})
    for gate in EXTERNAL_GATES:
        if gates[gate] == "PASS":
            require(valid_evidence_ref(evidence_refs.get(gate)),
                    f"external gate {gate} cannot PASS without a committed evidence file")

    defects = evidence.get("defects", {})
    p0 = defects.get("p0Open")
    p1 = defects.get("p1Open")
    if status in {"REPOSITORY_CERTIFIED", "AUTHORIZED"}:
        require(p0 == 0, "repository certification requires zero open P0 defects")
        require(p1 == 0 or gates.get("finalGoNoGo") == "FORMALLY_ACCEPTED",
                "open P1 defects require formal acceptance")

    approvals = evidence.get("approvals", {})
    require(set(approvals) == APPROVERS, "approval role set mismatch")
    if status == "AUTHORIZED":
        for role, approval in approvals.items():
            require(isinstance(approval, dict), f"missing structured approval for {role}")
            require(bool(approval.get("name")), f"approval name missing for {role}")
            require(bool(approval.get("approvedAt")), f"approval timestamp missing for {role}")
            require(valid_evidence_ref(approval.get("evidence")),
                    f"approval evidence missing for {role}")
        require(all(value == "PASS" for value in gates.values()),
                "AUTHORIZED requires every gate to PASS")
        require(evidence.get("commercialProductionAuthorized") is True,
                "AUTHORIZED requires commercialProductionAuthorized=true")
    else:
        require(evidence.get("commercialProductionAuthorized") is False,
                "non-authorized state must keep commercialProductionAuthorized=false")
        require(gates.get("finalGoNoGo") != "PASS",
                "final Go/No-Go cannot PASS before AUTHORIZED")

    for required in [
        "Commercial production deployment: NOT AUTHORIZED",
        "Final Go/No-Go: PENDING",
        "Stage 07 / Release Authorization: REQUIRED",
    ]:
        require(required in gate_status, f"gate status missing fail-closed marker: {required}")

    require("Issues #30 through #36 are closed with evidence" in go_live,
            "go-live checklist lost child-gate rule")
    require("Commercial production approval cannot be granted" in gaps,
            "gap register lost decision boundary")
    require("Stage 07 is the final release-authorization gate" in charter,
            "Stage 07 charter is incomplete")

    print("Stage 07 release authorization validator: PASS")
    print(f"stage07_status={status}")
    print(f"commercial_production_authorized={str(evidence.get('commercialProductionAuthorized')).lower()}")
    print(f"candidate_sha={candidate_sha or 'UNLOCKED'}")
    print(f"quality_gate_result={quality_result}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, json.JSONDecodeError) as exc:
        print(f"Stage 07 release authorization validator: FAIL — {exc}", file=sys.stderr)
        raise SystemExit(1)
