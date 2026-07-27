#!/usr/bin/env python3
"""Fail-closed validation for REM-P0-006 independent security assurance."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_DIR = ROOT / "docs/security/independent-assurance"
DEFAULT_MANIFEST = DEFAULT_DIR / "assessment-manifest.json"
SHA_PATTERN = re.compile(r"^[0-9a-f]{40}$")
SHA256_PATTERN = re.compile(r"^sha256:[0-9a-f]{64}$")
DATE_PATTERN = re.compile(r"^\d{4}-\d{2}-\d{2}$")

REQUIRED_WORKSTREAMS = {
    "penetration_testing",
    "tenant_boundary_and_object_authorization",
    "production_configuration_and_secrets",
    "dependency_and_supply_chain",
    "privacy_and_threat_model",
    "remediation_retest",
}
REQUIRED_CASES = {
    "PEN-01": "penetration_testing",
    "PEN-02": "penetration_testing",
    "PEN-03": "penetration_testing",
    "TEN-01": "tenant_boundary_and_object_authorization",
    "TEN-02": "tenant_boundary_and_object_authorization",
    "TEN-03": "tenant_boundary_and_object_authorization",
    "TEN-04": "tenant_boundary_and_object_authorization",
    "CFG-01": "production_configuration_and_secrets",
    "CFG-02": "production_configuration_and_secrets",
    "CFG-03": "production_configuration_and_secrets",
    "SUP-01": "dependency_and_supply_chain",
    "SUP-02": "dependency_and_supply_chain",
    "PRI-01": "privacy_and_threat_model",
    "PRI-02": "privacy_and_threat_model",
    "BUS-01": "penetration_testing",
    "BUS-02": "penetration_testing",
    "AUD-01": "production_configuration_and_secrets",
    "RET-01": "remediation_retest",
    "RET-02": "remediation_retest",
}
WORKSTREAM_STATUSES = {
    "NOT_STARTED",
    "IN_PROGRESS",
    "FAIL",
    "PASS",
    "PASS_WITH_ACCEPTED_RESIDUAL_RISK",
}
CASE_STATUSES = {"NOT_STARTED", "IN_PROGRESS", "FAIL", "PASS"}
FINDING_STATUSES = {
    "OPEN",
    "IN_REMEDIATION",
    "READY_FOR_RETEST",
    "CLOSED",
    "RESIDUAL_RISK_ACCEPTED",
}
EVIDENCE_TYPES = {
    "REPORT",
    "TEST_OUTPUT",
    "SCREENSHOT",
    "LOG_EXPORT",
    "CONFIGURATION_EXPORT",
    "ATTESTATION",
    "APPOINTMENT",
    "APPROVAL",
    "RETEST_STATEMENT",
    "EXTERNAL_REFERENCE",
}
SEVERITIES = {"critical", "high", "medium", "low"}
APPROVAL_ROLES = {"independent_assessor", "security_governance", "project_owner"}


class ValidationError(RuntimeError):
    """Raised when the evidence package violates a closure invariant."""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValidationError(message)


def nonempty(value: Any) -> bool:
    return isinstance(value, str) and bool(value.strip())


def require_exact_keys(value: dict[str, Any], expected: set[str], label: str) -> None:
    require(set(value) == expected, f"{label} must contain exactly: {sorted(expected)}")


def no_duplicate_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ValidationError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def load_json(path: Path) -> dict[str, Any]:
    require(path.is_file(), f"required file does not exist: {path.name}")
    try:
        value = json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=no_duplicate_object)
    except json.JSONDecodeError as exc:
        raise ValidationError(f"invalid JSON in {path.name}: {exc}") from exc
    require(isinstance(value, dict), f"{path.name} must contain a JSON object")
    return value


def parse_utc(value: Any, label: str) -> datetime:
    require(nonempty(value), f"{label} is required")
    text = str(value)
    require(text.endswith("Z"), f"{label} must be an ISO-8601 UTC timestamp ending in Z")
    try:
        parsed = datetime.fromisoformat(text[:-1] + "+00:00")
    except ValueError as exc:
        raise ValidationError(f"{label} is not a valid timestamp") from exc
    require(parsed.tzinfo == timezone.utc, f"{label} must be UTC")
    return parsed


def require_unique(values: Iterable[str], label: str) -> None:
    items = list(values)
    duplicates = sorted(item for item, count in Counter(items).items() if count > 1)
    require(not duplicates, f"duplicate {label}: {duplicates}")


def validate_manifest_structure(data: dict[str, Any]) -> None:
    schema_version = data.get("schema_version")
    require(schema_version in {"2.0", "3.0"}, "assessment-manifest schema_version must be 2.0 or 3.0")
    require(data.get("finding_id") == "REM-P0-006", "finding_id must be REM-P0-006")
    require(data.get("closure_state") in {"NOT_READY", "READY_FOR_APPROVAL", "ACCEPTED"}, "invalid closure_state")
    require(data.get("commercial_go_live") == "NOT_APPROVED", "this package cannot approve commercial go-live")

    assessor = data.get("assessor")
    require(isinstance(assessor, dict), "assessor must be an object")
    require_exact_keys(
        assessor,
        {
            "independence_status",
            "organization",
            "lead_assessor",
            "engagement_id",
            "appointment_evidence_id",
            "independence_evidence_id",
        },
        "assessor",
    )
    valid_statuses = {"NOT_APPOINTED", "PENDING_VERIFICATION", "VERIFIED"}
    if schema_version == "3.0":
        valid_statuses.add("TRUST_VERIFIED")
    require(assessor.get("independence_status") in valid_statuses, "invalid assessor independence_status")

    release = data.get("assessed_release")
    require(isinstance(release, dict), "assessed_release must be an object")
    require_exact_keys(release, {"repository_sha", "deployment_id", "environment", "started_at", "completed_at"}, "assessed_release")

    workstreams = data.get("workstreams")
    require(isinstance(workstreams, list), "workstreams must be a list")
    require(all(isinstance(item, dict) for item in workstreams), "every workstream must be an object")
    identifiers = [item.get("id") for item in workstreams]
    require_unique(identifiers, "workstream id")
    require(set(identifiers) == REQUIRED_WORKSTREAMS, "workstreams must contain the exact REM-P0-006 scope")
    for item in workstreams:
        require_exact_keys(item, {"id", "status", "evidence_ids"}, f"workstream {item.get('id')}")
        require(item.get("status") in WORKSTREAM_STATUSES, f"invalid workstream status: {item.get('id')}")
        require(isinstance(item.get("evidence_ids"), list), f"evidence_ids must be a list: {item.get('id')}")
        require(all(nonempty(value) for value in item["evidence_ids"]), f"invalid workstream evidence id: {item.get('id')}")
        require_unique(item["evidence_ids"], f"evidence id in workstream {item.get('id')}")

    summary = data.get("findings_summary")
    require(isinstance(summary, dict), "findings_summary must be an object")
    require(set(summary) == SEVERITIES, "findings_summary must contain all severities")
    for severity, counts in summary.items():
        require(isinstance(counts, dict), f"findings_summary.{severity} must be an object")
        require_exact_keys(counts, {"open", "closed", "residual_accepted"}, f"findings_summary.{severity}")
        for name, count in counts.items():
            require(isinstance(count, int) and not isinstance(count, bool) and count >= 0, f"invalid findings count: {severity}.{name}")

    residual_risks = data.get("residual_risks")
    require(isinstance(residual_risks, list), "residual_risks must be a list")
    require(all(isinstance(item, dict) for item in residual_risks), "every residual risk must be an object")

    if schema_version == "2.0":
        approvals = data.get("approvals")
        require(isinstance(approvals, dict), "approvals must be an object")
        require(set(approvals) == APPROVAL_ROLES, "exactly three approval roles are required")
        for role, approval in approvals.items():
            require(isinstance(approval, dict), f"approval must be an object: {role}")
            require_exact_keys(approval, {"decision", "name", "approved_at", "evidence_id"}, f"approval {role}")
            require(approval.get("decision") in {"PENDING", "APPROVE", "REJECT"}, f"invalid approval decision: {role}")
    elif schema_version == "3.0":
        approvals = data.get("approvals")
        if approvals is not None:
            require(isinstance(approvals, dict), "v3.0 approvals must be an object if present")
        trust_policy = data.get("trust_policy")
        require(isinstance(trust_policy, dict), "v3.0 requires trust_policy object")
        require_exact_keys(trust_policy, {"policy_id", "schema_version", "evaluation_status", "evaluated_at"}, "trust_policy")
        require(trust_policy.get("evaluation_status") in {"PENDING", "PASS", "FAIL"}, "invalid trust_policy.evaluation_status")

    require(isinstance(data.get("closure_decision_evidence_id"), str), "closure_decision_evidence_id must be a string")


def validate_evidence_index(data: dict[str, Any], package_dir: Path, release_sha: str) -> dict[str, dict[str, Any]]:
    require(data.get("schema_version") == "2.0", "evidence-index schema_version must be 2.0")
    require(data.get("finding_id") == "REM-P0-006", "evidence-index finding_id mismatch")
    require(data.get("status") in {"EMPTY", "IN_PROGRESS", "COMPLETE"}, "invalid evidence-index status")
    records = data.get("evidence")
    require(isinstance(records, list), "evidence-index evidence must be a list")
    require(all(isinstance(item, dict) for item in records), "every evidence record must be an object")
    ids = [item.get("id") for item in records]
    require(all(nonempty(value) for value in ids), "every evidence record requires an id")
    require_unique(ids, "evidence id")
    if data["status"] == "EMPTY":
        require(not records, "EMPTY evidence-index must not contain evidence")
    if data["status"] == "COMPLETE":
        require(bool(records), "COMPLETE evidence-index must contain evidence")

    result: dict[str, dict[str, Any]] = {}
    package_root = package_dir.resolve()
    for record in records:
        required = {
            "id",
            "type",
            "title",
            "workstream",
            "coverage_case_ids",
            "location",
            "sha256",
            "created_at",
            "created_by",
            "classification",
            "sanitized",
            "assessed_release_sha",
        }
        require_exact_keys(record, required, f"evidence {record.get('id')}")
        evidence_id = record["id"]
        require(record.get("type") in EVIDENCE_TYPES, f"invalid evidence type: {evidence_id}")
        require(nonempty(record.get("title")), f"evidence title is required: {evidence_id}")
        require(record.get("workstream") in REQUIRED_WORKSTREAMS | {"governance", "cross_cutting"}, f"invalid evidence workstream: {evidence_id}")
        case_ids = record.get("coverage_case_ids")
        require(isinstance(case_ids, list), f"coverage_case_ids must be a list: {evidence_id}")
        require(all(nonempty(case_id) for case_id in case_ids), f"invalid coverage case id: {evidence_id}")
        require_unique(case_ids, f"coverage case id in evidence {evidence_id}")
        require(set(case_ids) <= set(REQUIRED_CASES), f"unknown coverage case in evidence: {evidence_id}")
        require(SHA256_PATTERN.fullmatch(str(record.get("sha256", ""))) is not None, f"invalid evidence digest: {evidence_id}")
        parse_utc(record.get("created_at"), f"evidence.{evidence_id}.created_at")
        require(nonempty(record.get("created_by")), f"created_by is required: {evidence_id}")
        require(record.get("classification") in {"PUBLIC", "INTERNAL", "RESTRICTED_EXTERNAL_REFERENCE"}, f"invalid evidence classification: {evidence_id}")
        require(isinstance(record.get("sanitized"), bool), f"sanitized must be boolean: {evidence_id}")
        require(record.get("assessed_release_sha") == release_sha, f"evidence release SHA mismatch: {evidence_id}")

        location = record.get("location")
        require(nonempty(location), f"evidence location is required: {evidence_id}")
        if location.startswith("file:"):
            require(record["classification"] in {"PUBLIC", "INTERNAL"}, f"local evidence cannot be restricted external: {evidence_id}")
            require(record["sanitized"] is True, f"local evidence must be sanitized: {evidence_id}")
            relative = location.removeprefix("file:")
            require(nonempty(relative), f"local evidence path is empty: {evidence_id}")
            resolved = (package_dir / relative).resolve()
            require(resolved.is_relative_to(package_root), f"evidence escapes assurance directory: {evidence_id}")
            require(resolved.is_file(), f"evidence file does not exist: {relative}")
            actual = "sha256:" + hashlib.sha256(resolved.read_bytes()).hexdigest()
            require(actual == record["sha256"], f"evidence digest mismatch: {evidence_id}")
        elif location.startswith("external:"):
            require(record["classification"] == "RESTRICTED_EXTERNAL_REFERENCE", f"external evidence must use restricted classification: {evidence_id}")
            require(len(location.removeprefix("external:").strip()) >= 8, f"external evidence locator is not immutable/specific: {evidence_id}")
        else:
            raise ValidationError(f"evidence location must start with file: or external:: {evidence_id}")
        result[evidence_id] = record
    return result


def validate_coverage_matrix(data: dict[str, Any], evidence: dict[str, dict[str, Any]], strict: bool) -> dict[str, dict[str, Any]]:
    require(data.get("schema_version") == "2.0", "coverage matrix schema_version must be 2.0")
    require(data.get("finding_id") == "REM-P0-006", "coverage matrix finding_id mismatch")
    require(data.get("status") in {"NOT_STARTED", "IN_PROGRESS", "COMPLETE"}, "invalid coverage matrix status")
    cases = data.get("cases")
    require(isinstance(cases, list), "coverage matrix cases must be a list")
    require(all(isinstance(item, dict) for item in cases), "every coverage case must be an object")
    ids = [item.get("id") for item in cases]
    require_unique(ids, "coverage case id")
    require(set(ids) == set(REQUIRED_CASES), "coverage matrix must contain the exact required case set")
    result: dict[str, dict[str, Any]] = {}
    for case in cases:
        require_exact_keys(case, {"id", "workstream", "objective", "status", "evidence_ids"}, f"coverage case {case.get('id')}")
        case_id = case["id"]
        require(case.get("workstream") == REQUIRED_CASES[case_id], f"coverage workstream mismatch: {case_id}")
        require(nonempty(case.get("objective")), f"coverage objective is required: {case_id}")
        require(case.get("status") in CASE_STATUSES, f"invalid coverage status: {case_id}")
        evidence_ids = case.get("evidence_ids")
        require(isinstance(evidence_ids, list), f"coverage evidence_ids must be a list: {case_id}")
        require_unique(evidence_ids, f"evidence id in coverage case {case_id}")
        for evidence_id in evidence_ids:
            require(evidence_id in evidence, f"unknown evidence id {evidence_id} in coverage case {case_id}")
            record = evidence[evidence_id]
            require(case_id in record["coverage_case_ids"], f"evidence {evidence_id} does not claim coverage case {case_id}")
            require(record["workstream"] in {case["workstream"], "cross_cutting"}, f"evidence workstream mismatch for case {case_id}: {evidence_id}")
        if case["status"] == "PASS":
            require(bool(evidence_ids), f"PASS coverage case has no evidence: {case_id}")
        if strict:
            require(case["status"] == "PASS", f"coverage case did not pass: {case_id}")
        result[case_id] = case
    if strict:
        require(data["status"] == "COMPLETE", "closure candidate requires COMPLETE coverage matrix")
    return result


def validate_findings_register(
    data: dict[str, Any],
    evidence: dict[str, dict[str, Any]],
    coverage: dict[str, dict[str, Any]],
    strict: bool,
) -> tuple[list[dict[str, Any]], dict[str, dict[str, int]]]:
    require(data.get("schema_version") == "2.0", "findings-register schema_version must be 2.0")
    require(data.get("finding_id") == "REM-P0-006", "findings-register finding_id mismatch")
    require(data.get("assessment_status") in {"NOT_STARTED", "IN_PROGRESS", "COMPLETE"}, "invalid findings-register assessment_status")
    findings = data.get("findings")
    require(isinstance(findings, list), "findings must be a list")
    require(all(isinstance(item, dict) for item in findings), "every finding must be an object")
    ids = [item.get("id") for item in findings]
    require(all(nonempty(value) for value in ids), "every finding requires an id")
    require_unique(ids, "finding id")

    summary = {severity: {"open": 0, "closed": 0, "residual_accepted": 0} for severity in SEVERITIES}
    required_fields = {
        "id",
        "title",
        "severity",
        "affected_asset",
        "workstream",
        "coverage_case_ids",
        "description",
        "reproduction_evidence_ids",
        "business_impact",
        "owner",
        "status",
        "remediation_reference",
        "retest_status",
        "retest_evidence_ids",
    }
    for finding in findings:
        finding_id = finding["id"]
        require_exact_keys(finding, required_fields, f"finding {finding_id}")
        for field in ("title", "affected_asset", "description", "business_impact", "owner"):
            require(nonempty(finding.get(field)), f"finding field missing: {finding_id}.{field}")
        severity = finding.get("severity")
        require(severity in SEVERITIES, f"invalid finding severity: {finding_id}")
        workstream = finding.get("workstream")
        require(workstream in REQUIRED_WORKSTREAMS, f"invalid finding workstream: {finding_id}")
        case_ids = finding.get("coverage_case_ids")
        require(isinstance(case_ids, list) and bool(case_ids), f"finding requires coverage_case_ids: {finding_id}")
        require_unique(case_ids, f"coverage case id in finding {finding_id}")
        for case_id in case_ids:
            require(case_id in coverage, f"unknown coverage case in finding {finding_id}: {case_id}")
            require(coverage[case_id]["workstream"] == workstream, f"finding coverage workstream mismatch: {finding_id}.{case_id}")
        reproduction_ids = finding.get("reproduction_evidence_ids")
        retest_ids = finding.get("retest_evidence_ids")
        require(isinstance(reproduction_ids, list), f"reproduction_evidence_ids must be a list: {finding_id}")
        require(isinstance(retest_ids, list), f"retest_evidence_ids must be a list: {finding_id}")
        require_unique(reproduction_ids, f"reproduction evidence id in finding {finding_id}")
        require_unique(retest_ids, f"retest evidence id in finding {finding_id}")
        for evidence_id in reproduction_ids + retest_ids:
            require(evidence_id in evidence, f"unknown evidence id in finding {finding_id}: {evidence_id}")
        status = finding.get("status")
        require(status in FINDING_STATUSES, f"invalid finding status: {finding_id}")
        require(finding.get("retest_status") in {"NOT_STARTED", "FAIL", "PASS"}, f"invalid retest status: {finding_id}")
        if status == "CLOSED":
            require(nonempty(finding.get("remediation_reference")), f"closed finding requires remediation_reference: {finding_id}")
            require(finding["retest_status"] == "PASS", f"closed finding requires PASS retest: {finding_id}")
            require(bool(retest_ids), f"closed finding requires retest evidence: {finding_id}")
            summary[severity]["closed"] += 1
        elif status == "RESIDUAL_RISK_ACCEPTED":
            require(severity in {"medium", "low"}, f"critical/high residual risk is forbidden: {finding_id}")
            summary[severity]["residual_accepted"] += 1
        else:
            summary[severity]["open"] += 1
        if strict and severity in {"critical", "high"}:
            require(status == "CLOSED", f"material finding is not closed: {finding_id}")
        if strict:
            require(status in {"CLOSED", "RESIDUAL_RISK_ACCEPTED"}, f"finding is not in a terminal disposition: {finding_id}")
    if strict:
        require(data["assessment_status"] == "COMPLETE", "closure candidate requires COMPLETE findings register")
    return findings, summary


def validate_residual_risks(
    risks: list[dict[str, Any]],
    findings: list[dict[str, Any]],
    evidence: dict[str, dict[str, Any]],
    strict: bool,
) -> None:
    finding_by_id = {item["id"]: item for item in findings}
    ids = [risk.get("id") for risk in risks]
    require(all(nonempty(value) for value in ids), "every residual risk requires an id")
    require_unique(ids, "residual risk id")
    finding_ids: list[str] = []
    for risk in risks:
        require_exact_keys(
            risk,
            {"id", "finding_id", "severity", "description", "owner", "treatment", "expiry_or_review_date", "approval_evidence_ids"},
            f"residual risk {risk.get('id')}",
        )
        for field in ("finding_id", "severity", "description", "owner", "treatment", "expiry_or_review_date"):
            require(nonempty(risk.get(field)), f"residual risk field missing: {risk.get('id')}.{field}")
        require(DATE_PATTERN.fullmatch(risk["expiry_or_review_date"]) is not None, f"invalid residual risk review date: {risk['id']}")
        finding_id = risk["finding_id"]
        require(finding_id in finding_by_id, f"residual risk references unknown finding: {risk['id']}")
        finding = finding_by_id[finding_id]
        require(finding["status"] == "RESIDUAL_RISK_ACCEPTED", f"residual risk finding is not accepted: {risk['id']}")
        require(risk["severity"] == finding["severity"], f"residual risk severity mismatch: {risk['id']}")
        approval_ids = risk.get("approval_evidence_ids")
        require(isinstance(approval_ids, list) and len(approval_ids) >= 2, f"residual risk needs at least two approval evidence records: {risk['id']}")
        require_unique(approval_ids, f"approval evidence id in residual risk {risk['id']}")
        for evidence_id in approval_ids:
            require(evidence_id in evidence, f"unknown residual risk approval evidence: {evidence_id}")
            require(evidence[evidence_id]["type"] == "APPROVAL", f"residual risk evidence must be APPROVAL: {evidence_id}")
        finding_ids.append(finding_id)
    require_unique(finding_ids, "finding id across residual risks")
    accepted_finding_ids = {item["id"] for item in findings if item["status"] == "RESIDUAL_RISK_ACCEPTED"}
    require(set(finding_ids) == accepted_finding_ids, "residual risk records must exactly match accepted residual findings")
    if strict:
        for risk in risks:
            require(datetime.fromisoformat(risk["expiry_or_review_date"]).date() > datetime.now(timezone.utc).date(), f"residual risk review date must be in the future: {risk['id']}")


def validate_assessor_and_release(
    manifest: dict[str, Any], evidence: dict[str, dict[str, Any]], strict: bool, expected_release_sha: str | None
) -> tuple[str, datetime | None]:
    release = manifest["assessed_release"]
    release_sha = str(release.get("repository_sha", ""))
    completed_at: datetime | None = None
    if strict:
        require(SHA_PATTERN.fullmatch(release_sha) is not None, "exact 40-character assessed release SHA is required")
        require(expected_release_sha is not None, "closure validation requires --expected-release-sha")
        require(release_sha == expected_release_sha, "assessed release SHA does not match the workflow-authorized release SHA")
        for field in ("deployment_id", "environment"):
            require(nonempty(release.get(field)), f"assessed_release.{field} is required")
        started_at = parse_utc(release.get("started_at"), "assessed_release.started_at")
        completed_at = parse_utc(release.get("completed_at"), "assessed_release.completed_at")
        require(started_at < completed_at, "assessment completed_at must be after started_at")

        assessor = manifest["assessor"]
        schema_version = manifest.get("schema_version", "2.0")
        if schema_version == "3.0":
            trust_policy = manifest.get("trust_policy", {})
            if trust_policy.get("evaluation_status") == "PASS":
                require(
                    assessor["independence_status"] in {"VERIFIED", "TRUST_VERIFIED"},
                    "assessor independence must be VERIFIED or TRUST_VERIFIED",
                )
            else:
                require(assessor["independence_status"] == "VERIFIED", "assessor independence must be VERIFIED")
        else:
            require(assessor["independence_status"] == "VERIFIED", "assessor independence must be VERIFIED")
        for field in ("organization", "lead_assessor", "engagement_id"):
            require(nonempty(assessor.get(field)), f"assessor.{field} is required")
        appointment_id = assessor["appointment_evidence_id"]
        independence_id = assessor["independence_evidence_id"]
        require(nonempty(appointment_id) and appointment_id in evidence, "valid assessor appointment evidence is required")
        require(nonempty(independence_id) and independence_id in evidence, "valid assessor independence evidence is required")
        require(appointment_id != independence_id, "appointment and independence evidence must be distinct")
        require(evidence[appointment_id]["type"] == "APPOINTMENT", "appointment evidence must use APPOINTMENT type")
        require(evidence[independence_id]["type"] == "ATTESTATION", "independence evidence must use ATTESTATION type")
        require(evidence[appointment_id]["workstream"] == "governance", "appointment evidence must be governance evidence")
        require(evidence[independence_id]["workstream"] == "governance", "independence evidence must be governance evidence")
    else:
        if release_sha:
            require(SHA_PATTERN.fullmatch(release_sha) is not None, "assessed release SHA must be 40 lowercase hex characters")
    return release_sha, completed_at


def validate_workstreams(
    manifest: dict[str, Any],
    evidence: dict[str, dict[str, Any]],
    coverage: dict[str, dict[str, Any]],
    findings: list[dict[str, Any]],
    strict: bool,
) -> None:
    residual_by_stream = Counter(item["workstream"] for item in findings if item["status"] == "RESIDUAL_RISK_ACCEPTED")
    open_by_stream = Counter(item["workstream"] for item in findings if item["status"] not in {"CLOSED", "RESIDUAL_RISK_ACCEPTED"})
    cases_by_stream: dict[str, list[dict[str, Any]]] = {stream: [] for stream in REQUIRED_WORKSTREAMS}
    for case in coverage.values():
        cases_by_stream[case["workstream"]].append(case)

    used_dedicated_evidence: set[str] = set()
    for stream in manifest["workstreams"]:
        stream_id = stream["id"]
        for evidence_id in stream["evidence_ids"]:
            require(evidence_id in evidence, f"unknown workstream evidence id: {stream_id}.{evidence_id}")
        dedicated = [evidence_id for evidence_id in stream["evidence_ids"] if evidence[evidence_id]["workstream"] == stream_id]
        if stream["status"] in {"PASS", "PASS_WITH_ACCEPTED_RESIDUAL_RISK"}:
            require(bool(dedicated), f"terminal workstream requires dedicated evidence: {stream_id}")
        reused = used_dedicated_evidence.intersection(dedicated)
        require(not reused, f"dedicated evidence reused across workstreams: {sorted(reused)}")
        used_dedicated_evidence.update(dedicated)

        if strict:
            require(all(case["status"] == "PASS" for case in cases_by_stream[stream_id]), f"workstream has incomplete coverage cases: {stream_id}")
            require(open_by_stream[stream_id] == 0, f"workstream has open findings: {stream_id}")
            expected = "PASS_WITH_ACCEPTED_RESIDUAL_RISK" if residual_by_stream[stream_id] else "PASS"
            require(stream["status"] == expected, f"workstream status does not match findings disposition: {stream_id}")


def validate_findings_summary(manifest: dict[str, Any], derived: dict[str, dict[str, int]]) -> None:
    require(manifest["findings_summary"] == derived, "findings_summary does not reconcile with findings-register.json")
    require(derived["critical"]["residual_accepted"] == 0, "critical residual risk is forbidden")
    require(derived["high"]["residual_accepted"] == 0, "high residual risk is forbidden")


def validate_approvals(
    manifest: dict[str, Any], evidence: dict[str, dict[str, Any]], strict: bool, completed_at: datetime | None
) -> None:
    # v3.0 with trust-based governance replaces manual approvals
    schema_version = manifest.get("schema_version", "2.0")
    if schema_version == "3.0":
        trust_policy = manifest.get("trust_policy")
        if trust_policy and trust_policy.get("evaluation_status") == "PASS":
            return  # trust-based governance replaces manual approvals
    approval_evidence_ids: list[str] = []
    for role, approval in manifest.get("approvals", {}).items():
        if approval["decision"] == "PENDING":
            require(not approval["name"] and not approval["approved_at"] and not approval["evidence_id"], f"pending approval must not contain approval claims: {role}")
            continue
        if approval["decision"] == "REJECT":
            require(nonempty(approval["name"]), f"rejection name is required: {role}")
            require(nonempty(approval["approved_at"]), f"rejection timestamp is required: {role}")
            require(nonempty(approval["evidence_id"]), f"rejection evidence is required: {role}")
        if strict:
            require(approval["decision"] == "APPROVE", f"approval is not APPROVE: {role}")
        if approval["decision"] == "APPROVE":
            require(nonempty(approval["name"]), f"approval name is required: {role}")
            approved_at = parse_utc(approval["approved_at"], f"approval.{role}.approved_at")
            if completed_at is not None:
                require(approved_at >= completed_at, f"approval predates assessment completion: {role}")
            evidence_id = approval["evidence_id"]
            require(evidence_id in evidence, f"unknown approval evidence: {role}.{evidence_id}")
            record = evidence[evidence_id]
            require(record["type"] == "APPROVAL", f"approval evidence must use APPROVAL type: {role}")
            require(record["workstream"] == "governance", f"approval evidence must be governance evidence: {role}")
            approval_evidence_ids.append(evidence_id)
    require_unique(approval_evidence_ids, "approval evidence id")


def validate_package(manifest_path: Path, mode: str, expected_release_sha: str | None = None) -> dict[str, Any]:
    manifest_path = manifest_path.resolve()
    package_dir = manifest_path.parent
    manifest = load_json(manifest_path)
    validate_manifest_structure(manifest)

    state = manifest["closure_state"]
    require(mode in {"readiness", "closure"}, "invalid validation mode")
    if mode == "readiness":
        require(state != "ACCEPTED", "readiness mode cannot validate an ACCEPTED closure claim")
    if mode == "closure":
        require(state in {"READY_FOR_APPROVAL", "ACCEPTED"}, "closure mode requires READY_FOR_APPROVAL or ACCEPTED")
    strict = state in {"READY_FOR_APPROVAL", "ACCEPTED"}

    release_sha = str(manifest["assessed_release"].get("repository_sha", ""))
    evidence_index = load_json(package_dir / "evidence-index.json")
    evidence = validate_evidence_index(evidence_index, package_dir, release_sha)
    coverage = validate_coverage_matrix(load_json(package_dir / "TEST-COVERAGE-MATRIX.json"), evidence, strict)
    findings, derived_summary = validate_findings_register(load_json(package_dir / "findings-register.json"), evidence, coverage, strict)
    validate_findings_summary(manifest, derived_summary)
    validate_residual_risks(manifest["residual_risks"], findings, evidence, strict)
    _, completed_at = validate_assessor_and_release(manifest, evidence, strict, expected_release_sha)
    validate_workstreams(manifest, evidence, coverage, findings, strict)
    validate_approvals(manifest, evidence, mode == "closure" or state == "ACCEPTED", completed_at)

    if strict:
        require(evidence_index["status"] == "COMPLETE", "closure candidate requires COMPLETE evidence-index")
    if state == "ACCEPTED":
        closure_id = manifest["closure_decision_evidence_id"]
        require(nonempty(closure_id) and closure_id in evidence, "ACCEPTED closure requires closure decision evidence")
        require(evidence[closure_id]["type"] == "APPROVAL", "closure decision evidence must use APPROVAL type")
        require(evidence[closure_id]["workstream"] == "governance", "closure decision evidence must be governance evidence")
    else:
        require(not manifest["closure_decision_evidence_id"], "closure decision evidence is allowed only for ACCEPTED state")

    return manifest


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--mode", choices=("readiness", "closure"), default="readiness")
    parser.add_argument("--expected-release-sha")
    args = parser.parse_args()
    manifest = validate_package(args.manifest, args.mode, args.expected_release_sha)
    print(f"REM-P0-006 {args.mode.upper()} VALIDATION PASSED")
    print(f"closure_state={manifest['closure_state']}")
    print(f"assessor_independence={manifest['assessor']['independence_status']}")
    print(f"assessed_release_sha={manifest['assessed_release']['repository_sha'] or 'UNSET'}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValidationError, OSError, ValueError, KeyError, TypeError) as exc:
        print(f"REM-P0-006 VALIDATION ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
