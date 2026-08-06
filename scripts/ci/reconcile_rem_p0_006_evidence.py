#!/usr/bin/env python3
"""Reconcile an immutable REM-P0-006 root Artifact into a truthful IN_PROGRESS package.

This script verifies technical evidence integrity and exposes independent-assessment gaps.
It never marks coverage PASS, verifies assessor independence, records approvals, or closes
REM-P0-006.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ARTIFACT_DIGEST_RE = re.compile(r"^sha256:[0-9a-f]{64}$")
REQUIRED_WORKFLOW_KEYS = {
    "crm",
    "nvd",
    "security_baseline",
    "development_security",
    "owasp",
    "provenance",
    "business_e2e",
}


def fail(message: str) -> None:
    raise RuntimeError(message)


def read_json(path: Path) -> dict[str, Any]:
    if not path.is_file():
        fail(f"required JSON file missing: {path}")
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        fail(f"JSON root must be an object: {path}")
    return value


def write_json(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def find_one(root: Path, name: str) -> Path:
    matches = sorted(root.rglob(name))
    if len(matches) != 1:
        fail(f"expected exactly one {name}, found {len(matches)}")
    if not matches[0].is_file() or matches[0].stat().st_size == 0:
        fail(f"required evidence file is empty: {matches[0]}")
    return matches[0]


def sha256(path: Path) -> str:
    return "sha256:" + hashlib.sha256(path.read_bytes()).hexdigest()


def verify_internal_sha256s(root: Path) -> Path:
    sums_path = find_one(root, "SHA256SUMS.txt")
    checksum_root = sums_path.parent.resolve()
    lines = [line for line in sums_path.read_text(encoding="utf-8").splitlines() if line.strip()]
    if not lines:
        fail("SHA256SUMS.txt is empty")
    verified = 0
    for line in lines:
        try:
            expected, relative = line.split(maxsplit=1)
        except ValueError as exc:
            raise RuntimeError(f"invalid SHA256SUMS line: {line!r}") from exc
        relative = relative.lstrip("*")
        candidate = (checksum_root / relative).resolve()
        if not candidate.is_relative_to(checksum_root):
            fail(f"checksum path escapes Artifact root: {relative}")
        if not candidate.is_file():
            fail(f"checksummed file missing: {relative}")
        actual = hashlib.sha256(candidate.read_bytes()).hexdigest()
        if actual != expected:
            fail(f"checksum mismatch: {relative}")
        verified += 1
    if verified < 8:
        fail(f"unexpectedly small root evidence set: {verified} checksums")
    return sums_path


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def validate_root(root: Path, target: str) -> dict[str, Any]:
    sums_path = verify_internal_sha256s(root)
    context_path = find_one(root, "run-context.json")
    index_path = find_one(root, "workflow-run-index.json")
    safe_http_path = find_one(root, "safe-http.json")
    zap_web_summary_path = find_one(root, "zap-web-summary.json")
    zap_backend_summary_path = find_one(root, "zap-backend-summary.json")
    zap_web_execution_path = find_one(root, "zap-web-execution.json")
    zap_backend_execution_path = find_one(root, "zap-backend-execution.json")
    crm_smoke_path = find_one(root, "crm008r-production-smoke-sanitized.json")

    context = read_json(context_path)
    run_index = read_json(index_path)
    safe_http = read_json(safe_http_path)
    zap_web_summary = read_json(zap_web_summary_path)
    zap_backend_summary = read_json(zap_backend_summary_path)
    zap_web_execution = read_json(zap_web_execution_path)
    zap_backend_execution = read_json(zap_backend_execution_path)
    crm_smoke = read_json(crm_smoke_path)

    require(context.get("status") == "success", "root assessment status is not success")
    require(context.get("target_sha") == target, "root assessment target SHA mismatch")
    workflow_runs = run_index.get("workflow_runs")
    require(run_index.get("target_sha") == target, "workflow index target SHA mismatch")
    require(isinstance(workflow_runs, dict), "workflow-run-index workflow_runs must be an object")
    require(set(workflow_runs) == REQUIRED_WORKFLOW_KEYS, "workflow-run-index does not contain the exact required workflow set")
    require(all(isinstance(value, int) and value > 0 for value in workflow_runs.values()), "workflow-run-index contains an invalid run id")

    require(safe_http.get("target_sha") == target, "safe HTTP target SHA mismatch")
    require(safe_http.get("findings") == [], "safe HTTP evidence contains findings")
    require(zap_web_summary.get("fail_new") == 0, "web ZAP contains new failures")
    require(zap_backend_summary.get("fail_new") == 0, "backend ZAP contains new failures")
    require(isinstance(zap_web_summary.get("warn_new"), int), "web ZAP warning count missing")
    require(isinstance(zap_backend_summary.get("warn_new"), int), "backend ZAP warning count missing")
    require(isinstance(zap_web_execution.get("container_exit_code"), int), "web ZAP execution code missing")
    require(isinstance(zap_backend_execution.get("container_exit_code"), int), "backend ZAP execution code missing")

    checks = crm_smoke.get("checks", {})
    require(crm_smoke.get("result") == "PASS", "CRM Production evidence did not pass")
    require(crm_smoke.get("releaseSha") == target, "CRM Production evidence release SHA mismatch")
    for name in (
        "authenticatedTwoTenantLogin",
        "crossTenantCursorRejected400",
        "crossTenantEntityReadRejected404",
        "sameEtagRaceExactlyOneWinner",
        "missingIfMatchRejected428",
        "temporaryDataArchived",
    ):
        require(checks.get(name) == "PASS", f"CRM Production check did not pass: {name}")

    workflow_results: dict[str, dict[str, Any]] = {}
    for key, run_id in workflow_runs.items():
        run_path = find_one(root, f"{key}-run.json")
        data = read_json(run_path)
        require(data.get("conclusion") == "success", f"workflow conclusion is not success: {key}")
        require(data.get("head_sha") == target, f"workflow head SHA mismatch: {key}")
        workflow_results[key] = {
            "run_id": run_id,
            "name": data.get("name"),
            "event": data.get("event"),
            "head_sha": data.get("head_sha"),
            "status": data.get("status"),
            "conclusion": data.get("conclusion"),
            "html_url": data.get("html_url"),
            "created_at": data.get("created_at"),
            "updated_at": data.get("updated_at"),
        }

    return {
        "sums_path": sums_path,
        "context": context,
        "safe_http": safe_http,
        "zap_web_summary": zap_web_summary,
        "zap_backend_summary": zap_backend_summary,
        "zap_web_execution": zap_web_execution,
        "zap_backend_execution": zap_backend_execution,
        "crm_smoke": crm_smoke,
        "workflow_results": workflow_results,
    }


def evidence_record(
    package: Path,
    evidence_id: str,
    evidence_type: str,
    title: str,
    workstream: str,
    case_ids: list[str],
    path: Path,
    creator: str,
    created_at: str,
    release_sha: str,
) -> dict[str, Any]:
    return {
        "id": evidence_id,
        "type": evidence_type,
        "title": title,
        "workstream": workstream,
        "coverage_case_ids": case_ids,
        "location": "file:" + str(path.relative_to(package)),
        "sha256": sha256(path),
        "created_at": created_at,
        "created_by": creator,
        "classification": "INTERNAL",
        "sanitized": True,
        "assessed_release_sha": release_sha,
    }


def build_package(args: argparse.Namespace, verified: dict[str, Any]) -> None:
    package = args.package_dir.resolve()
    evidence_dir = package / "evidence"
    evidence_dir.mkdir(parents=True, exist_ok=True)
    for path in evidence_dir.glob("EV-ROOT-*"):
        if path.is_file():
            path.unlink()

    generated_at = datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
    target = args.assessed_release_sha
    context = verified["context"]

    documents: dict[str, dict[str, Any]] = {
        "EV-ROOT-INTEGRITY.json": {
            "schema": "snad.rem-p0-006.root-artifact-integrity.v1",
            "assessed_release_sha": target,
            "controller_sha": args.controller_sha,
            "root_run_id": args.root_run_id,
            "root_run_url": args.root_run_url,
            "artifact_id": args.artifact_id,
            "artifact_name": args.artifact_name,
            "artifact_digest": args.artifact_digest,
            "artifact_download_api": args.artifact_url,
            "artifact_expires_at": args.artifact_expires,
            "internal_sha256sums_verified": True,
            "internal_sha256sums_file_digest": sha256(verified["sums_path"]),
            "verified_at": generated_at,
            "boundary": "Automated technical evidence; not independent certification.",
        },
        "EV-ROOT-WORKFLOWS.json": {
            "schema": "snad.rem-p0-006.workflow-results.v1",
            "assessed_release_sha": target,
            "workflow_results": verified["workflow_results"],
        },
        "EV-ROOT-SAFE-HTTP.json": verified["safe_http"],
        "EV-ROOT-ZAP.json": {
            "schema": "snad.rem-p0-006.zap-summary.v1",
            "assessed_release_sha": target,
            "web": {"summary": verified["zap_web_summary"], "execution": verified["zap_web_execution"]},
            "backend": {"summary": verified["zap_backend_summary"], "execution": verified["zap_backend_execution"]},
            "boundary": "Complete passive ZAP summaries; manual penetration testing remains required.",
        },
        "EV-ROOT-TENANT-CRM.json": verified["crm_smoke"],
        "EV-ROOT-GAP-ANALYSIS.json": {
            "schema": "snad.rem-p0-006.independent-gap-analysis.v1",
            "assessed_release_sha": target,
            "status": "INDEPENDENT_ASSESSMENT_REQUIRED",
            "not_proven_by_automation": [
                "PEN-03 injection, SSRF, unsafe parsing, import and stored-content testing",
                "TEN-03 horizontal and vertical privilege escalation across platform scope",
                "TEN-04 revoked, stale and conflicting role/capability behavior",
                "CFG-03 Production IAM, break-glass and audit configuration review",
                "PRI-01 personal-data inventory, minimization, retention and deletion",
                "PRI-02 independent threat-model challenge and abuse-case review",
                "RET-01 independent retest or explicit no-material-findings statement",
            ],
            "partially_evidenced_cases_require_independent_judgment": [
                "PEN-01", "PEN-02", "TEN-01", "TEN-02", "CFG-01", "CFG-02",
                "SUP-01", "SUP-02", "BUS-01", "BUS-02", "AUD-01", "RET-02",
            ],
            "decision": "Do not set READY_FOR_APPROVAL until every required case has independent PASS evidence.",
        },
    }
    for name, document in documents.items():
        write_json(evidence_dir / name, document)

    checklist = package / "INDEPENDENT-ASSESSOR-EXECUTION-CHECKLIST.md"
    checklist.write_text(
        "# REM-P0-006 Independent Assessor Execution Checklist\n\n"
        f"**Engagement:** `{args.engagement_id}`  \n"
        f"**Assessor:** `@{args.assessor_login}`  \n"
        f"**Exact assessed release:** `{target}`  \n"
        f"**Root run:** `{args.root_run_id}`  \n"
        f"**Root Artifact:** `{args.artifact_id}` / `{args.artifact_name}`  \n"
        f"**Artifact digest:** `{args.artifact_digest}`\n\n"
        "The committed automated evidence has been integrity-checked. It is input evidence only. "
        "The assessor must independently execute or review every case in `TEST-COVERAGE-MATRIX.json`, "
        "publish reproducible evidence and findings, and provide an explicit independence attestation.\n\n"
        "## Mandatory outputs\n\n"
        "1. Independence attestation satisfying `APPOINTMENT-REM-P0-006-2026-07-27.md`.\n"
        "2. Independent PASS/FAIL decision and evidence for all 19 coverage cases.\n"
        "3. Findings register with reproduction evidence, owner, remediation reference and retest evidence.\n"
        "4. Explicit no-material-findings statement when applicable; absence of automated findings is not sufficient.\n"
        f"5. Final independent retest on `{target}`.\n"
        "6. Independent Assessor approval only after every material finding is closed.\n\n"
        "## Current blocking gaps\n\n"
        "- `PEN-03`, `TEN-03`, `TEN-04`, `CFG-03`, `PRI-01`, `PRI-02`, `RET-01`: no complete independent evidence.\n"
        "- Remaining cases have automated evidence only or partial scope and require independent judgment.\n"
        "- Security Governance and Project Owner approvals are not yet requested because the package is not `READY_FOR_APPROVAL`.\n\n"
        "Do not merge this PR as closure evidence. It is the controlled independent-assessment workspace.\n",
        encoding="utf-8",
    )

    appointment = package / "APPOINTMENT-REM-P0-006-2026-07-27.md"
    require(appointment.is_file(), "appointment record is missing")
    records = [
        evidence_record(package, "EV-APPOINTMENT", "APPOINTMENT", "Independent assessor appointment", "governance", [], appointment, "Project Owner", generated_at, target),
        evidence_record(package, "EV-ROOT-INTEGRITY", "REPORT", "Immutable root Artifact identity and checksum verification", "cross_cutting", [], evidence_dir / "EV-ROOT-INTEGRITY.json", "REM-P0-006 Evidence Reconciler", generated_at, target),
        evidence_record(package, "EV-PEN-AUTOMATED", "TEST_OUTPUT", "Automated safe-HTTP and passive DAST evidence", "penetration_testing", ["PEN-01", "PEN-02"], evidence_dir / "EV-ROOT-ZAP.json", "REM-P0-006 Root Assessment", generated_at, target),
        evidence_record(package, "EV-TENANT-CRM", "TEST_OUTPUT", "Authenticated two-tenant CRM Production evidence", "tenant_boundary_and_object_authorization", ["TEN-01", "TEN-02"], evidence_dir / "EV-ROOT-TENANT-CRM.json", "CRM Production Closure Gate", generated_at, target),
        evidence_record(package, "EV-CONFIG-HTTP", "TEST_OUTPUT", "Production release identity, health, CORS and header checks", "production_configuration_and_secrets", ["CFG-01"], evidence_dir / "EV-ROOT-SAFE-HTTP.json", "REM-P0-006 Root Assessment", generated_at, target),
        evidence_record(package, "EV-SUPPLY-CHAIN", "TEST_OUTPUT", "NVD, OWASP, baseline and provenance workflow results", "dependency_and_supply_chain", ["SUP-01", "SUP-02"], evidence_dir / "EV-ROOT-WORKFLOWS.json", "REM-P0-006 Root Assessment", generated_at, target),
        evidence_record(package, "EV-PRIVACY-GAP", "REPORT", "Privacy and threat-model independent evidence gap", "privacy_and_threat_model", ["PRI-01", "PRI-02"], evidence_dir / "EV-ROOT-GAP-ANALYSIS.json", "REM-P0-006 Evidence Reconciler", generated_at, target),
        evidence_record(package, "EV-RETEST-AUTOMATED", "RETEST_STATEMENT", "Automated exact-release regression and retest input", "remediation_retest", ["RET-02"], evidence_dir / "EV-ROOT-INTEGRITY.json", "REM-P0-006 Root Assessment", generated_at, target),
    ]
    write_json(package / "evidence-index.json", {
        "schema_version": "2.0",
        "finding_id": "REM-P0-006",
        "status": "IN_PROGRESS",
        "evidence": records,
    })

    coverage = read_json(package / "TEST-COVERAGE-MATRIX.json")
    coverage["status"] = "IN_PROGRESS"
    mapping = {
        "PEN-01": ["EV-PEN-AUTOMATED"],
        "PEN-02": ["EV-PEN-AUTOMATED"],
        "TEN-01": ["EV-TENANT-CRM"],
        "TEN-02": ["EV-TENANT-CRM"],
        "CFG-01": ["EV-CONFIG-HTTP"],
        "SUP-01": ["EV-SUPPLY-CHAIN"],
        "SUP-02": ["EV-SUPPLY-CHAIN"],
        "PRI-01": ["EV-PRIVACY-GAP"],
        "PRI-02": ["EV-PRIVACY-GAP"],
        "RET-02": ["EV-RETEST-AUTOMATED"],
    }
    partial = {"PEN-01", "PEN-02", "TEN-01", "TEN-02", "CFG-01", "SUP-01", "SUP-02", "RET-02"}
    for case in coverage["cases"]:
        case["status"] = "IN_PROGRESS" if case["id"] in partial else "NOT_STARTED"
        case["evidence_ids"] = mapping.get(case["id"], [])
    write_json(package / "TEST-COVERAGE-MATRIX.json", coverage)

    findings = read_json(package / "findings-register.json")
    findings["assessment_status"] = "IN_PROGRESS"
    findings["findings"] = []
    write_json(package / "findings-register.json", findings)

    manifest = read_json(package / "assessment-manifest.json")
    manifest["closure_state"] = "NOT_READY"
    manifest["commercial_go_live"] = "NOT_APPROVED"
    manifest["assessor"] = {
        "independence_status": "PENDING_VERIFICATION",
        "organization": "External independent individual assessor",
        "lead_assessor": args.assessor_login,
        "engagement_id": args.engagement_id,
        "appointment_evidence_id": "EV-APPOINTMENT",
        "independence_evidence_id": "",
    }
    manifest["assessed_release"] = {
        "repository_sha": target,
        "deployment_id": "snad-production-vercel-render-f34f2dd7",
        "environment": "production",
        "started_at": context["started_at_utc"],
        "completed_at": context["completed_at_utc"],
    }
    stream_evidence = {
        "penetration_testing": ["EV-PEN-AUTOMATED"],
        "tenant_boundary_and_object_authorization": ["EV-TENANT-CRM"],
        "production_configuration_and_secrets": ["EV-CONFIG-HTTP"],
        "dependency_and_supply_chain": ["EV-SUPPLY-CHAIN"],
        "privacy_and_threat_model": ["EV-PRIVACY-GAP"],
        "remediation_retest": ["EV-RETEST-AUTOMATED"],
    }
    for stream in manifest["workstreams"]:
        stream["status"] = "IN_PROGRESS"
        stream["evidence_ids"] = stream_evidence[stream["id"]]
    manifest["findings_summary"] = {
        severity: {"open": 0, "closed": 0, "residual_accepted": 0}
        for severity in ("critical", "high", "medium", "low")
    }
    manifest["residual_risks"] = []
    manifest["approvals"] = {
        role: {"decision": "PENDING", "name": "", "approved_at": "", "evidence_id": ""}
        for role in ("independent_assessor", "security_governance", "project_owner")
    }
    manifest["closure_decision_evidence_id"] = ""
    write_json(package / "assessment-manifest.json", manifest)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--package-dir", type=Path, required=True)
    parser.add_argument("--root-dir", type=Path, required=True)
    parser.add_argument("--assessed-release-sha", required=True)
    parser.add_argument("--root-run-id", type=int, required=True)
    parser.add_argument("--root-run-url", required=True)
    parser.add_argument("--controller-sha", required=True)
    parser.add_argument("--artifact-id", type=int, required=True)
    parser.add_argument("--artifact-name", required=True)
    parser.add_argument("--artifact-digest", required=True)
    parser.add_argument("--artifact-url", required=True)
    parser.add_argument("--artifact-expires", required=True)
    parser.add_argument("--assessor-login", required=True)
    parser.add_argument("--engagement-id", required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    require(ARTIFACT_DIGEST_RE.fullmatch(args.artifact_digest) is not None, "invalid GitHub Artifact digest")
    verified = validate_root(args.root_dir.resolve(), args.assessed_release_sha)
    build_package(args, verified)
    print(json.dumps({
        "status": "IN_PROGRESS_PACKAGE_GENERATED",
        "assessed_release_sha": args.assessed_release_sha,
        "root_run_id": args.root_run_id,
        "artifact_id": args.artifact_id,
        "artifact_digest": args.artifact_digest,
        "independence_status": "PENDING_VERIFICATION",
        "closure_state": "NOT_READY",
    }, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
