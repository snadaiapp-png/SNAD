#!/usr/bin/env python3
"""Tests for the fail-closed post-merge evidence validator."""

import argparse
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MODULE_PATH = ROOT / "scripts" / "ci" / "validate_post_merge_evidence.py"
SPEC = importlib.util.spec_from_file_location("validate_post_merge_evidence", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(MODULE)


class TestValidatePostMergeEvidence(unittest.TestCase):
    def setUp(self):
        self.tempdir = tempfile.TemporaryDirectory()
        self.root = Path(self.tempdir.name)
        self.sha = "a" * 40
        self.run_id = "12345"
        self.paths = {
            "manifest": self.root / "manifest.json",
            "scan_report": self.root / "scan.json",
            "backend_metadata": self.root / "backend-meta.json",
            "backend_health": self.root / "backend-health.json",
            "frontend_metadata": self.root / "frontend-meta.json",
        }
        self.payloads = {
            "manifest": {
                "result": "PASS",
                "exactMainSha": self.sha,
                "workflowRunId": self.run_id,
                "failedGate": None,
                "failedChecks": [],
                "skippedChecks": [],
            },
            "scan_report": {
                "result": "PASS",
                "findingsCount": 0,
                "scanErrors": [],
                "commitSha": self.sha,
                "workflowRunId": self.run_id,
            },
            "backend_metadata": {
                "result": "PASS",
                "httpStatus": 200,
                "healthStatus": "UP",
                "processStarted": True,
            },
            "backend_health": {"status": "UP"},
            "frontend_metadata": {
                "result": "PASS",
                "httpStatus": 200,
                "brandNamePresent": True,
                "processStarted": True,
                "url": "http://127.0.0.1:3001/",
            },
        }
        self.write_all()

    def tearDown(self):
        self.tempdir.cleanup()

    def write_all(self):
        for key, path in self.paths.items():
            path.write_text(json.dumps(self.payloads[key]), encoding="utf-8")

    def args(self):
        return argparse.Namespace(
            **{key: str(path) for key, path in self.paths.items()},
            expected_sha=self.sha,
            expected_run_id=self.run_id,
        )

    def test_valid_evidence_passes(self):
        self.assertEqual(MODULE.validate(self.args()), [])

    def test_manifest_sha_mismatch_fails(self):
        self.payloads["manifest"]["exactMainSha"] = "b" * 40
        self.write_all()
        self.assertTrue(any("exactMainSha" in e for e in MODULE.validate(self.args())))

    def test_manifest_run_id_mismatch_fails(self):
        self.payloads["manifest"]["workflowRunId"] = "999"
        self.write_all()
        self.assertTrue(any("workflowRunId" in e for e in MODULE.validate(self.args())))

    def test_failed_checks_fail(self):
        self.payloads["manifest"]["failedChecks"] = ["frontend"]
        self.write_all()
        self.assertTrue(any("failedChecks" in e for e in MODULE.validate(self.args())))

    def test_skipped_checks_fail(self):
        self.payloads["manifest"]["skippedChecks"] = ["frontend"]
        self.write_all()
        self.assertTrue(any("skippedChecks" in e for e in MODULE.validate(self.args())))

    def test_scan_findings_fail(self):
        self.payloads["scan_report"]["findingsCount"] = 1
        self.write_all()
        self.assertTrue(any("findingsCount" in e for e in MODULE.validate(self.args())))

    def test_scan_errors_fail(self):
        self.payloads["scan_report"]["scanErrors"] = [{"type": "READ_ERROR"}]
        self.write_all()
        self.assertTrue(any("scanErrors" in e for e in MODULE.validate(self.args())))

    def test_backend_down_fails(self):
        self.payloads["backend_health"]["status"] = "DOWN"
        self.write_all()
        self.assertTrue(any("backend health" in e for e in MODULE.validate(self.args())))

    def test_frontend_wrong_url_fails(self):
        self.payloads["frontend_metadata"]["url"] = "http://127.0.0.1:3001/invalid"
        self.write_all()
        self.assertTrue(any("canonical root" in e for e in MODULE.validate(self.args())))

    def test_frontend_missing_brand_fails(self):
        self.payloads["frontend_metadata"]["brandNamePresent"] = False
        self.write_all()
        self.assertTrue(any("brandNamePresent" in e for e in MODULE.validate(self.args())))

    def test_missing_file_fails(self):
        self.paths["manifest"].unlink()
        self.assertTrue(any("missing file" in e for e in MODULE.validate(self.args())))

    def test_malformed_json_fails(self):
        self.paths["manifest"].write_text("{bad", encoding="utf-8")
        self.assertTrue(any("invalid JSON" in e for e in MODULE.validate(self.args())))


if __name__ == "__main__":
    unittest.main()
