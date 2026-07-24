from __future__ import annotations

import hashlib
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MODULE_PATH = ROOT / "scripts/ci/validate_independent_security_assurance.py"
SPEC = importlib.util.spec_from_file_location("assurance_validator", MODULE_PATH)
validator = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(validator)


def load_manifest() -> dict:
    return json.loads((ROOT / "docs/security/independent-assurance/assessment-manifest.json").read_text())


def complete_manifest(tmp_path: Path) -> dict:
    data = load_manifest()
    evidence = tmp_path / "assessor-report.json"
    evidence.write_text('{"result":"pass"}\n', encoding="utf-8")
    reference = {
        "id": "EV-001",
        "path": evidence.name,
        "sha256": "sha256:" + hashlib.sha256(evidence.read_bytes()).hexdigest(),
    }
    data["closure_state"] = "READY_FOR_APPROVAL"
    data["assessor"] = {
        "independence_status": "VERIFIED",
        "organization": "Independent Example LLC",
        "lead_assessor": "Named Assessor",
        "engagement_id": "ENG-001",
        "conflict_of_interest_attestation": "No delivery or reporting conflict",
        "appointment_evidence": "contract:ENG-001",
    }
    data["assessed_release"] = {
        "repository_sha": "a" * 40,
        "deployment_id": "prod-001",
        "environment": "production clone",
        "started_at": "2026-07-17T00:00:00Z",
        "completed_at": "2026-07-17T01:00:00Z",
    }
    for stream in data["workstreams"]:
        stream["status"] = "PASS"
        stream["evidence"] = [reference]
    data["approvals"] = {
        role: {"decision": "APPROVE", "name": role, "approved_at": "2026-07-17T02:00:00Z", "evidence": f"approval:{role}"}
        for role in ("independent_assessor", "security_governance", "project_owner")
    }
    return data


class IndependentSecurityAssuranceValidatorTest(unittest.TestCase):
    def test_readiness_template_passes_without_claiming_closure(self):
        data = load_manifest()
        validator.validate_manifest(data, "readiness", ROOT / "docs/security/independent-assurance")
        self.assertEqual(data["closure_state"], "NOT_READY")

    def test_closure_rejects_unappointed_assessor(self):
        with self.assertRaisesRegex(validator.ValidationError, "READY_FOR_APPROVAL"):
            validator.validate_manifest(load_manifest(), "closure", ROOT)

    def test_closure_accepts_complete_independent_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            validator.validate_manifest(complete_manifest(path), "closure", path)

    def test_closure_rejects_open_material_findings(self):
        for severity in ("critical", "high"):
            with self.subTest(severity=severity), tempfile.TemporaryDirectory() as directory:
                path = Path(directory)
                data = complete_manifest(path)
                data["findings"][severity]["open"] = 1
                with self.assertRaisesRegex(validator.ValidationError, f"{severity} findings remain open"):
                    validator.validate_manifest(data, "closure", path)

    def test_closure_rejects_evidence_tampering(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            data = complete_manifest(path)
            (path / "assessor-report.json").write_text("tampered", encoding="utf-8")
            with self.assertRaisesRegex(validator.ValidationError, "digest mismatch"):
                validator.validate_manifest(data, "closure", path)

    def test_closure_requires_all_three_approvals(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            data = complete_manifest(path)
            data["approvals"]["project_owner"]["decision"] = "PENDING"
            with self.assertRaisesRegex(validator.ValidationError, "project_owner"):
                validator.validate_manifest(data, "closure", path)


if __name__ == "__main__":
    unittest.main()
