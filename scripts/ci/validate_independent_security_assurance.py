#!/usr/bin/env python3
"""Validate REM-P0-006 evidence without turning internal checks into independence."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_MANIFEST = ROOT / "docs/security/independent-assurance/assessment-manifest.json"
SHA_PATTERN = re.compile(r"^[0-9a-f]{40}$")
SHA256_PATTERN = re.compile(r"^sha256:[0-9a-f]{64}$")
REQUIRED_WORKSTREAMS = {
    "penetration_testing",
    "tenant_boundary_and_object_authorization",
    "production_configuration_and_secrets",
    "dependency_and_supply_chain",
    "privacy_and_threat_model",
    "remediation_retest",
}
TERMINAL_PASS = {"PASS", "PASS_WITH_ACCEPTED_RESIDUAL_RISK"}


class ValidationError(RuntimeError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValidationError(message)


def nonempty(value: Any) -> bool:
    return isinstance(value, str) and bool(value.strip())


def validate_structure(data: dict[str, Any]) -> None:
    require(data.get("schema_version") == "1.0", "schema_version must be 1.0")
    require(data.get("finding_id") == "REM-P0-006", "finding_id must be REM-P0-006")
    require(data.get("closure_state") in {"NOT_READY", "READY_FOR_APPROVAL", "ACCEPTED"}, "invalid closure_state")
    require(data.get("commercial_go_live") == "NOT_APPROVED", "this manifest cannot approve commercial go-live")

    assessor = data.get("assessor", {})
    require(isinstance(assessor, dict), "assessor must be an object")
    require(assessor.get("independence_status") in {"NOT_APPOINTED", "PENDING_VERIFICATION", "VERIFIED"}, "invalid independence_status")

    release = data.get("assessed_release", {})
    require(isinstance(release, dict), "assessed_release must be an object")

    workstreams = data.get("workstreams")
    require(isinstance(workstreams, list), "workstreams must be a list")
    identifiers = {item.get("id") for item in workstreams if isinstance(item, dict)}
    require(identifiers == REQUIRED_WORKSTREAMS, "workstreams must contain the exact required REM-P0-006 scope")
    for item in workstreams:
        require(item.get("status") in {"NOT_STARTED", "IN_PROGRESS", "PASS", "PASS_WITH_ACCEPTED_RESIDUAL_RISK", "FAIL"}, f"invalid workstream status: {item.get('id')}")
        require(isinstance(item.get("evidence"), list), f"evidence must be a list: {item.get('id')}")

    findings = data.get("findings")
    require(isinstance(findings, dict), "findings must be an object")
    for severity in ("critical", "high", "medium", "low"):
        require(isinstance(findings.get(severity), dict), f"missing findings severity: {severity}")
        require(isinstance(findings[severity].get("open"), int) and findings[severity]["open"] >= 0, f"invalid open count: {severity}")
        require(isinstance(findings[severity].get("closed"), int) and findings[severity]["closed"] >= 0, f"invalid closed count: {severity}")

    require(isinstance(data.get("residual_risks"), list), "residual_risks must be a list")
    approvals = data.get("approvals")
    require(isinstance(approvals, dict), "approvals must be an object")
    require(set(approvals) == {"independent_assessor", "security_governance", "project_owner"}, "exactly three approval roles are required")
    for role, approval in approvals.items():
        require(approval.get("decision") in {"PENDING", "APPROVE", "REJECT"}, f"invalid approval decision: {role}")


def validate_evidence_reference(reference: dict[str, Any], manifest_dir: Path) -> None:
    require(nonempty(reference.get("id")), "evidence id is required")
    require(nonempty(reference.get("path")), f"evidence path is required: {reference.get('id')}")
    require(SHA256_PATTERN.fullmatch(str(reference.get("sha256", ""))) is not None, f"invalid evidence digest: {reference.get('id')}")
    path = (manifest_dir / reference["path"]).resolve()
    require(path.is_relative_to(manifest_dir.resolve()), f"evidence escapes assurance directory: {reference.get('id')}")
    require(path.is_file(), f"evidence file does not exist: {reference.get('path')}")
    actual = "sha256:" + hashlib.sha256(path.read_bytes()).hexdigest()
    require(actual == reference["sha256"], f"evidence digest mismatch: {reference.get('id')}")


def validate_closure(data: dict[str, Any], manifest_dir: Path) -> None:
    require(data.get("closure_state") in {"READY_FOR_APPROVAL", "ACCEPTED"}, "closure mode requires READY_FOR_APPROVAL or ACCEPTED")
    assessor = data["assessor"]
    require(assessor.get("independence_status") == "VERIFIED", "assessor independence must be VERIFIED")
    for field in ("organization", "lead_assessor", "engagement_id", "conflict_of_interest_attestation", "appointment_evidence"):
        require(nonempty(assessor.get(field)), f"assessor.{field} is required")

    release = data["assessed_release"]
    require(SHA_PATTERN.fullmatch(str(release.get("repository_sha", ""))) is not None, "exact 40-character repository SHA is required")
    for field in ("deployment_id", "environment", "started_at", "completed_at"):
        require(nonempty(release.get(field)), f"assessed_release.{field} is required")

    for item in data["workstreams"]:
        require(item["status"] in TERMINAL_PASS, f"workstream did not pass: {item['id']}")
        require(bool(item["evidence"]), f"workstream has no evidence: {item['id']}")
        for reference in item["evidence"]:
            validate_evidence_reference(reference, manifest_dir)

    require(data["findings"]["critical"]["open"] == 0, "critical findings remain open")
    require(data["findings"]["high"]["open"] == 0, "high findings remain open")

    residual_ids = set()
    for risk in data["residual_risks"]:
        for field in ("id", "severity", "description", "owner", "treatment", "expiry_or_review_date"):
            require(nonempty(risk.get(field)), f"residual risk field missing: {field}")
        require(risk["id"] not in residual_ids, f"duplicate residual risk: {risk['id']}")
        residual_ids.add(risk["id"])

    for role, approval in data["approvals"].items():
        require(approval.get("decision") == "APPROVE", f"approval is not APPROVE: {role}")
        for field in ("name", "approved_at", "evidence"):
            require(nonempty(approval.get(field)), f"approval field missing: {role}.{field}")

    if data["closure_state"] == "ACCEPTED":
        require(nonempty(data.get("closure_decision_reference")), "accepted closure requires closure_decision_reference")


def validate_manifest(data: dict[str, Any], mode: str, manifest_dir: Path) -> None:
    validate_structure(data)
    if mode == "closure":
        validate_closure(data, manifest_dir)
    else:
        require(data.get("closure_state") != "ACCEPTED", "readiness manifest cannot claim ACCEPTED")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--mode", choices=("readiness", "closure"), default="readiness")
    args = parser.parse_args()
    manifest = args.manifest.resolve()
    data = json.loads(manifest.read_text(encoding="utf-8"))
    validate_manifest(data, args.mode, manifest.parent)
    print(f"REM-P0-006 {args.mode.upper()} VALIDATION PASSED")
    print(f"closure_state={data['closure_state']}")
    print(f"assessor_independence={data['assessor']['independence_status']}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValidationError, OSError, ValueError, KeyError, json.JSONDecodeError) as exc:
        print(f"REM-P0-006 VALIDATION ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
