#!/usr/bin/env python3
"""Tests for validate_post_merge_evidence.py — the independent fail-closed gate."""
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path

import importlib.util
spec = importlib.util.spec_from_file_location(
    "validate_post_merge_evidence",
    str(Path(__file__).resolve().parent.parent.parent / "scripts" / "ci" / "validate_post_merge_evidence.py")
)
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

EXPECTED_SHA = "abc1234567890abcdef1234567890abcdef123456"
EXPECTED_RUN_ID = "28890000000"


def _write(path: Path, payload: dict):
    path.write_text(json.dumps(payload), encoding="utf-8")


class TestPostMergeEvidenceValidator(unittest.TestCase):
    def setUp(self):
        self.tmpdir = Path(tempfile.mkdtemp())
        self.manifest = self.tmpdir / "manifest.json"
        self.secret = self.tmpdir / "secret.json"
        self.backend_meta = self.tmpdir / "backend-meta.json"
        self.backend_health = self.tmpdir / "backend-health.json"
        self.frontend_meta = self.tmpdir / "frontend-meta.json"

    def _base_manifest(self):
        return {
            "verificationType": "post-merge-main",
            "exactMainSha": EXPECTED_SHA,
            "workflowRunId": EXPECTED_RUN_ID,
            "result": "PASS",
            "failedGate": None,
            "checks": {k: {"status": "SUCCESS", "evidence": "ok"} for k in mod.CRITICAL_CHECK_KEYS},
            "passedChecks": list(mod.CRITICAL_CHECK_KEYS),
            "failedChecks": [],
            "skippedChecks": [],
        }

    def _base_secret(self):
        return {
            "scanner": "snad-policy-supplement",
            "result": "PASS",
            "commitSha": EXPECTED_SHA,
            "workflowRunId": EXPECTED_RUN_ID,
            "findingsCount": 0,
            "findings": [],
            "scanErrors": [],
        }

    def _base_backend_meta(self):
        return {
            "applicationPort": 8081,
            "managementPort": 8082,
            "healthUrl": "http://127.0.0.1:8082/actuator/health",
            "processStarted": True,
            "httpStatus": 200,
            "healthStatus": "UP",
            "result": "PASS",
            "failureType": None,
        }

    def _base_backend_health(self):
        return {"status": "UP"}

    def _base_frontend_meta(self):
        return {
            "port": 3001,
            "url": "http://127.0.0.1:3001/",
            "processStarted": True,
            "httpStatus": 200,
            "brandNamePresent": True,
            "result": "PASS",
            "failureType": None,
        }

    def _write_all_valid(self):
        _write(self.manifest, self._base_manifest())
        _write(self.secret, self._base_secret())
        _write(self.backend_meta, self._base_backend_meta())
        _write(self.backend_health, self._base_backend_health())
        _write(self.frontend_meta, self._base_frontend_meta())

    def _run(self):
        old_argv = sys.argv
        sys.argv = [
            "validate_post_merge_evidence.py",
            "--manifest", str(self.manifest),
            "--secret-report", str(self.secret),
            "--backend-metadata", str(self.backend_meta),
            "--backend-health", str(self.backend_health),
            "--frontend-metadata", str(self.frontend_meta),
            "--expected-sha", EXPECTED_SHA,
            "--expected-run-id", EXPECTED_RUN_ID,
        ]
        try:
            mod.main()
            return 0
        except SystemExit as e:
            return e.code
        finally:
            sys.argv = old_argv

    # ---- Happy path ----
    def test_all_valid_returns_zero(self):
        self._write_all_valid()
        self.assertEqual(self._run(), 0)

    # ---- Missing file cases ----
    def test_missing_manifest_fails(self):
        self._write_all_valid()
        self.manifest.unlink()
        self.assertEqual(self._run(), 1)

    def test_missing_secret_report_fails(self):
        self._write_all_valid()
        self.secret.unlink()
        self.assertEqual(self._run(), 1)

    def test_missing_backend_metadata_fails(self):
        self._write_all_valid()
        self.backend_meta.unlink()
        self.assertEqual(self._run(), 1)

    def test_missing_backend_health_fails(self):
        self._write_all_valid()
        self.backend_health.unlink()
        self.assertEqual(self._run(), 1)

    def test_missing_frontend_metadata_fails(self):
        self._write_all_valid()
        self.frontend_meta.unlink()
        self.assertEqual(self._run(), 1)

    # ---- Invalid JSON ----
    def test_malformed_manifest_fails(self):
        self._write_all_valid()
        self.manifest.write_text("{not valid json", encoding="utf-8")
        self.assertEqual(self._run(), 1)

    def test_empty_manifest_fails(self):
        self._write_all_valid()
        self.manifest.write_text("", encoding="utf-8")
        self.assertEqual(self._run(), 1)

    # ---- SHA mismatch ----
    def test_sha_mismatch_in_manifest_fails(self):
        self._write_all_valid()
        m = self._base_manifest()
        m["exactMainSha"] = "wrong"
        _write(self.manifest, m)
        self.assertEqual(self._run(), 1)

    def test_sha_mismatch_in_secret_report_fails(self):
        self._write_all_valid()
        s = self._base_secret()
        s["commitSha"] = "wrong"
        _write(self.secret, s)
        self.assertEqual(self._run(), 1)

    def test_run_id_mismatch_fails(self):
        self._write_all_valid()
        m = self._base_manifest()
        m["workflowRunId"] = "99999"
        _write(self.manifest, m)
        self.assertEqual(self._run(), 1)

    # ---- Result invariants ----
    def test_manifest_result_fail_rejected(self):
        self._write_all_valid()
        m = self._base_manifest()
        m["result"] = "FAIL"
        _write(self.manifest, m)
        self.assertEqual(self._run(), 1)

    def test_backend_meta_result_fail_rejected(self):
        self._write_all_valid()
        b = self._base_backend_meta()
        b["result"] = "FAIL"
        b["failureType"] = "HEALTH_NOT_UP"
        _write(self.backend_meta, b)
        self.assertEqual(self._run(), 1)

    def test_backend_health_not_up_rejected(self):
        self._write_all_valid()
        _write(self.backend_health, {"status": "DOWN"})
        self.assertEqual(self._run(), 1)

    def test_frontend_meta_result_fail_rejected(self):
        self._write_all_valid()
        f = self._base_frontend_meta()
        f["result"] = "FAIL"
        f["failureType"] = "STARTUP_TIMEOUT"
        _write(self.frontend_meta, f)
        self.assertEqual(self._run(), 1)

    def test_frontend_meta_legacy_url_rejected(self):
        self._write_all_valid()
        f = self._base_frontend_meta()
        f["url"] = "http://127.0.0.1:3001/auth/login"
        _write(self.frontend_meta, f)
        self.assertEqual(self._run(), 1)

    def test_frontend_meta_brand_missing_rejected(self):
        self._write_all_valid()
        f = self._base_frontend_meta()
        f["brandNamePresent"] = False
        _write(self.frontend_meta, f)
        self.assertEqual(self._run(), 1)

    def test_secret_findings_nonzero_rejected(self):
        self._write_all_valid()
        s = self._base_secret()
        s["result"] = "FAIL"
        s["findingsCount"] = 2
        s["findings"] = [{"ruleId": "x", "path": "y"}] * 2
        _write(self.secret, s)
        self.assertEqual(self._run(), 1)

    def test_secret_scan_errors_rejected(self):
        self._write_all_valid()
        s = self._base_secret()
        s["scanErrors"] = [{"path": "x", "errorType": "READ_ERROR", "message": "boom"}]
        _write(self.secret, s)
        self.assertEqual(self._run(), 1)

    # ---- Skipped / failed critical checks ----
    def test_skipped_critical_check_rejected(self):
        self._write_all_valid()
        m = self._base_manifest()
        m["checks"]["smoke_frontend"] = {"status": "SKIPPED"}
        m["skippedChecks"] = ["smoke_frontend"]
        _write(self.manifest, m)
        self.assertEqual(self._run(), 1)

    def test_failed_critical_check_rejected(self):
        self._write_all_valid()
        m = self._base_manifest()
        m["checks"]["secret_scan"] = {"status": "FAILURE"}
        m["failedChecks"] = ["secret_scan"]
        m["result"] = "FAIL"
        _write(self.manifest, m)
        self.assertEqual(self._run(), 1)

    def test_missing_critical_check_key_rejected(self):
        self._write_all_valid()
        m = self._base_manifest()
        del m["checks"]["smoke_frontend"]
        _write(self.manifest, m)
        self.assertEqual(self._run(), 1)


if __name__ == "__main__":
    unittest.main()
