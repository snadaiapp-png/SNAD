from __future__ import annotations

import hashlib
import importlib.util
import json
import shutil
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MODULE_PATH = ROOT / "scripts/ci/validate_independent_security_assurance.py"
SPEC = importlib.util.spec_from_file_location("assurance_validator", MODULE_PATH)
assert SPEC and SPEC.loader
validator = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(validator)
TEMPLATE_DIR = ROOT / "docs/security/independent-assurance"
RELEASE_SHA = "a" * 40


def read_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, value: dict) -> None:
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def copy_package(target: Path) -> None:
    for name in (
        "assessment-manifest.json",
        "evidence-index.json",
        "findings-register.json",
        "TEST-COVERAGE-MATRIX.json",
    ):
        shutil.copy2(TEMPLATE_DIR / name, target / name)


def add_local_evidence(package: Path, evidence_id: str, evidence_type: str, workstream: str, case_ids: list[str], created_by: str) -> dict:
    evidence_dir = package / "evidence"
    evidence_dir.mkdir(exist_ok=True)
    file_path = evidence_dir / f"{evidence_id}.json"
    file_path.write_text(json.dumps({"id": evidence_id, "result": "PASS"}) + "\n", encoding="utf-8")
    return {
        "id": evidence_id,
        "type": evidence_type,
        "title": evidence_id,
        "workstream": workstream,
        "coverage_case_ids": case_ids,
        "location": f"file:evidence/{file_path.name}",
        "sha256": "sha256:" + hashlib.sha256(file_path.read_bytes()).hexdigest(),
        "created_at": "2026-07-26T10:30:00Z",
        "created_by": created_by,
        "classification": "INTERNAL",
        "sanitized": True,
        "assessed_release_sha": RELEASE_SHA,
    }


def complete_candidate(package: Path, approve: bool = True) -> None:
    manifest = read_json(package / "assessment-manifest.json")
    coverage = read_json(package / "TEST-COVERAGE-MATRIX.json")
    findings = read_json(package / "findings-register.json")
    index = read_json(package / "evidence-index.json")

    cases_by_stream: dict[str, list[str]] = {}
    for case in coverage["cases"]:
        cases_by_stream.setdefault(case["workstream"], []).append(case["id"])

    records: list[dict] = []
    stream_evidence: dict[str, str] = {}
    for number, stream in enumerate(sorted(validator.REQUIRED_WORKSTREAMS), start=1):
        evidence_id = f"EV-STREAM-{number:02d}"
        stream_evidence[stream] = evidence_id
        records.append(add_local_evidence(package, evidence_id, "TEST_OUTPUT", stream, cases_by_stream[stream], "Independent Assessor"))

    governance_specs = [
        ("EV-APPOINTMENT", "APPOINTMENT", "Contract Authority"),
        ("EV-INDEPENDENCE", "ATTESTATION", "Independent Assessor"),
        ("EV-APPROVAL-ASSESSOR", "APPROVAL", "Independent Assessor"),
        ("EV-APPROVAL-SECURITY", "APPROVAL", "Security Governance"),
        ("EV-APPROVAL-OWNER", "APPROVAL", "Project Owner"),
    ]
    for evidence_id, evidence_type, creator in governance_specs:
        records.append(add_local_evidence(package, evidence_id, evidence_type, "governance", [], creator))

    index["status"] = "COMPLETE"
    index["evidence"] = records
    write_json(package / "evidence-index.json", index)

    coverage["status"] = "COMPLETE"
    for case in coverage["cases"]:
        case["status"] = "PASS"
        case["evidence_ids"] = [stream_evidence[case["workstream"]]]
    write_json(package / "TEST-COVERAGE-MATRIX.json", coverage)

    findings["assessment_status"] = "COMPLETE"
    findings["findings"] = []
    write_json(package / "findings-register.json", findings)
    manifest["findings_summary"] = {
        "critical": {"open": 0, "closed": 0, "residual_accepted": 0},
        "high": {"open": 0, "closed": 0, "residual_accepted": 0},
        "medium": {"open": 0, "closed": 0, "residual_accepted": 0},
        "low": {"open": 0, "closed": 0, "residual_accepted": 0},
    }
    manifest["residual_risks"] = []

    manifest["closure_state"] = "READY_FOR_APPROVAL"
    manifest["assessor"] = {
        "independence_status": "VERIFIED",
        "organization": "Independent Example LLC",
        "lead_assessor": "Independent Assessor",
        "engagement_id": "ENG-001",
        "appointment_evidence_id": "EV-APPOINTMENT",
        "independence_evidence_id": "EV-INDEPENDENCE",
    }
    manifest["assessed_release"] = {
        "repository_sha": RELEASE_SHA,
        "deployment_id": "prod-001",
        "environment": "production-like",
        "started_at": "2026-07-26T08:00:00Z",
        "completed_at": "2026-07-26T10:00:00Z",
    }
    for stream in manifest["workstreams"]:
        stream["status"] = "PASS"
        stream["evidence_ids"] = [stream_evidence[stream["id"]]]
    if approve:
        manifest["approvals"] = {
            "independent_assessor": {"decision": "APPROVE", "name": "Independent Assessor", "approved_at": "2026-07-26T11:00:00Z", "evidence_id": "EV-APPROVAL-ASSESSOR"},
            "security_governance": {"decision": "APPROVE", "name": "Security Governance", "approved_at": "2026-07-26T11:05:00Z", "evidence_id": "EV-APPROVAL-SECURITY"},
            "project_owner": {"decision": "APPROVE", "name": "Project Owner", "approved_at": "2026-07-26T11:10:00Z", "evidence_id": "EV-APPROVAL-OWNER"},
        }
    write_json(package / "assessment-manifest.json", manifest)


class IndependentSecurityAssuranceValidatorTest(unittest.TestCase):
    def package(self):
        temporary = tempfile.TemporaryDirectory()
        path = Path(temporary.name)
        copy_package(path)
        return temporary, path

    def test_readiness_template_passes_without_closure_claim(self):
        validator.validate_package(TEMPLATE_DIR / "assessment-manifest.json", "readiness")

    def test_closure_rejects_not_ready_state(self):
        with self.assertRaisesRegex(validator.ValidationError, "READY_FOR_APPROVAL"):
            validator.validate_package(TEMPLATE_DIR / "assessment-manifest.json", "closure", RELEASE_SHA)

    def test_complete_candidate_passes_readiness_with_pending_approvals(self):
        temporary, package = self.package()
        self.addCleanup(temporary.cleanup)
        complete_candidate(package, approve=False)
        validator.validate_package(package / "assessment-manifest.json", "readiness", RELEASE_SHA)

    def test_complete_candidate_passes_closure(self):
        temporary, package = self.package()
        self.addCleanup(temporary.cleanup)
        complete_candidate(package)
        validator.validate_package(package / "assessment-manifest.json", "closure", RELEASE_SHA)

    def test_closure_rejects_release_sha_mismatch(self):
        temporary, package = self.package()
        self.addCleanup(temporary.cleanup)
        complete_candidate(package)
        with self.assertRaisesRegex(validator.ValidationError, "workflow-authorized"):
            validator.validate_package(package / "assessment-manifest.json", "closure", "b" * 40)

    def test_exact_coverage_scope_is_required(self):
        temporary, package = self.package()
        self.addCleanup(temporary.cleanup)
        complete_candidate(package)
        matrix = read_json(package / "TEST-COVERAGE-MATRIX.json")
        matrix["cases"].pop()
        write_json(package / "TEST-COVERAGE-MATRIX.json", matrix)
        with self.assertRaisesRegex(validator.ValidationError, "exact required case set"):
            validator.validate_package(package / "assessment-manifest.json", "closure", RELEASE_SHA)

    def test_findings_summary_must_reconcile(self):
        temporary, package = self.package()
        self.addCleanup(temporary.cleanup)
        complete_candidate(package)
        manifest = read_json(package / "assessment-manifest.json")
        manifest["findings_summary"]["high"]["closed"] = 1
        write_json(package / "assessment-manifest.json", manifest)
        with self.assertRaisesRegex(validator.ValidationError, "does not reconcile"):
            validator.validate_package(package / "assessment-manifest.json", "closure", RELEASE_SHA)

    def test_open_high_finding_blocks_closure(self):
        temporary, package = self.package()
        self.addCleanup(temporary.cleanup)
        complete_candidate(package)
        findings = read_json(package / "findings-register.json")
        findings["findings"] = [{
            "id": "SEC-001",
            "title": "Cross-tenant read",
            "severity": "high",
            "affected_asset": "CRM API",
            "workstream": "tenant_boundary_and_object_authorization",
            "coverage_case_ids": ["TEN-01"],
            "description": "Cross-tenant object was readable",
            "reproduction_evidence_ids": ["EV-STREAM-06"],
            "business_impact": "Tenant confidentiality breach",
            "owner": "Backend",
            "status": "OPEN",
            "remediation_reference": "",
            "retest_status": "NOT_STARTED",
            "retest_evidence_ids": []
        }]
        write_json(package / "findings-register.json", findings)
        manifest = read_json(package / "assessment-manifest.json")
        manifest["findings_summary"]["high"]["open"] = 1
        write_json(package / "assessment-manifest.json", manifest)
        with self.assertRaisesRegex(validator.ValidationError, "material finding is not closed"):
            validator.validate_package(package / "assessment-manifest.json", "closure", RELEASE_SHA)

    def test_high_residual_risk_is_forbidden(self):
        temporary, package = self.package()
        self.addCleanup(temporary.cleanup)
        complete_candidate(package)
        findings = read_json(package / "findings-register.json")
        finding = {
            "id": "SEC-002",
            "title": "Material weakness",
            "severity": "high",
            "affected_asset": "Identity",
            "workstream": "penetration_testing",
            "coverage_case_ids": ["PEN-02"],
            "description": "Material weakness",
            "reproduction_evidence_ids": ["EV-STREAM-02"],
            "business_impact": "Account compromise",
            "owner": "Identity",
            "status": "RESIDUAL_RISK_ACCEPTED",
            "remediation_reference": "deferred",
            "retest_status": "NOT_STARTED",
            "retest_evidence_ids": []
        }
        findings["findings"] = [finding]
        write_json(package / "findings-register.json", findings)
        with self.assertRaisesRegex(validator.ValidationError, "critical/high residual"):
            validator.validate_package(package / "assessment-manifest.json", "closure", RELEASE_SHA)

    def test_evidence_tampering_is_rejected(self):
        temporary, package = self.package()
        self.addCleanup(temporary.cleanup)
        complete_candidate(package)
        (package / "evidence/EV-STREAM-01.json").write_text("tampered\n", encoding="utf-8")
        with self.assertRaisesRegex(validator.ValidationError, "digest mismatch"):
            validator.validate_package(package / "assessment-manifest.json", "closure", RELEASE_SHA)

    def test_cross_workstream_single_evidence_shortcut_is_rejected(self):
        temporary, package = self.package()
        self.addCleanup(temporary.cleanup)
        complete_candidate(package)
        manifest = read_json(package / "assessment-manifest.json")
        shared = manifest["workstreams"][0]["evidence_ids"][0]
        for stream in manifest["workstreams"]:
            stream["evidence_ids"] = [shared]
        write_json(package / "assessment-manifest.json", manifest)
        with self.assertRaisesRegex(validator.ValidationError, "dedicated evidence"):
            validator.validate_package(package / "assessment-manifest.json", "closure", RELEASE_SHA)

    def test_approval_evidence_must_be_distinct(self):
        temporary, package = self.package()
        self.addCleanup(temporary.cleanup)
        complete_candidate(package)
        manifest = read_json(package / "assessment-manifest.json")
        shared = manifest["approvals"]["independent_assessor"]["evidence_id"]
        manifest["approvals"]["security_governance"]["evidence_id"] = shared
        write_json(package / "assessment-manifest.json", manifest)
        with self.assertRaisesRegex(validator.ValidationError, "duplicate approval evidence"):
            validator.validate_package(package / "assessment-manifest.json", "closure", RELEASE_SHA)

    def test_approval_cannot_predate_assessment_completion(self):
        temporary, package = self.package()
        self.addCleanup(temporary.cleanup)
        complete_candidate(package)
        manifest = read_json(package / "assessment-manifest.json")
        manifest["approvals"]["project_owner"]["approved_at"] = "2026-07-26T09:00:00Z"
        write_json(package / "assessment-manifest.json", manifest)
        with self.assertRaisesRegex(validator.ValidationError, "predates"):
            validator.validate_package(package / "assessment-manifest.json", "closure", RELEASE_SHA)

    def test_duplicate_json_keys_are_rejected(self):
        temporary, package = self.package()
        self.addCleanup(temporary.cleanup)
        (package / "assessment-manifest.json").write_text('{"schema_version":"2.0","schema_version":"2.0"}\n', encoding="utf-8")
        with self.assertRaisesRegex(validator.ValidationError, "duplicate JSON key"):
            validator.validate_package(package / "assessment-manifest.json", "readiness")

    # --- v3.0 schema tests ---

    def _upgrade_to_v30(self, package: Path, trust_status: str = "PENDING") -> None:
        """Upgrade a v2.0 package to v3.0 schema."""
        manifest = read_json(package / "assessment-manifest.json")
        manifest["schema_version"] = "3.0"
        manifest["trust_policy"] = {
            "policy_id": "REM-P0-006-TRUST-POLICY-001",
            "schema_version": "1.0",
            "evaluation_status": trust_status,
            "evaluated_at": "2026-07-27T12:00:00Z" if trust_status == "PASS" else "",
        }
        if trust_status == "PASS":
            manifest["approvals"] = None
        write_json(package / "assessment-manifest.json", manifest)

    def test_v30_readiness_template_passes(self):
        temporary, package = self.package()
        self.addCleanup(temporary.cleanup)
        complete_candidate(package, approve=False)
        self._upgrade_to_v30(package, "PENDING")
        validator.validate_package(package / "assessment-manifest.json", "readiness", RELEASE_SHA)

    def test_v30_trust_verified_assessor_accepted(self):
        temporary, package = self.package()
        self.addCleanup(temporary.cleanup)
        complete_candidate(package)
        self._upgrade_to_v30(package, "PASS")
        manifest = read_json(package / "assessment-manifest.json")
        manifest["assessor"]["independence_status"] = "TRUST_VERIFIED"
        write_json(package / "assessment-manifest.json", manifest)
        validator.validate_package(package / "assessment-manifest.json", "closure", RELEASE_SHA)

    def test_v30_trust_policy_replaces_approvals(self):
        temporary, package = self.package()
        self.addCleanup(temporary.cleanup)
        complete_candidate(package, approve=False)
        self._upgrade_to_v30(package, "PASS")
        # No approvals set, but trust_policy=PASS should allow closure
        validator.validate_package(package / "assessment-manifest.json", "closure", RELEASE_SHA)

    def test_v30_missing_trust_policy_rejected(self):
        temporary, package = self.package()
        self.addCleanup(temporary.cleanup)
        complete_candidate(package, approve=False)
        manifest = read_json(package / "assessment-manifest.json")
        manifest["schema_version"] = "3.0"
        # Remove trust_policy to trigger validation error
        del manifest["trust_policy"]
        write_json(package / "assessment-manifest.json", manifest)
        with self.assertRaisesRegex(validator.ValidationError, "trust_policy"):
            validator.validate_package(package / "assessment-manifest.json", "readiness", RELEASE_SHA)


if __name__ == "__main__":
    unittest.main()
