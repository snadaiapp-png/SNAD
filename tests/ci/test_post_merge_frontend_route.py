#!/usr/bin/env python3
"""Regression contract for the post-merge frontend smoke route."""

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github" / "workflows" / "post-merge-verification.yml"


class TestPostMergeFrontendRoute(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = WORKFLOW.read_text(encoding="utf-8")
        match = re.search(
            r"- name: Smoke test — Frontend auth route \(operational\)(.*?)(?=\n\s*- name:|\Z)",
            cls.text,
            flags=re.DOTALL,
        )
        if not match:
            raise AssertionError("frontend smoke step not found")
        cls.step = match.group(1)

    def test_canonical_root_url_is_defined_once(self):
        self.assertIn('FRONTEND_SMOKE_URL="http://127.0.0.1:3001/"', self.step)
        self.assertEqual(self.step.count("FRONTEND_SMOKE_URL="), 1)

    def test_readiness_uses_canonical_variable(self):
        self.assertRegex(self.step, r"curl[\s\S]*?\"\$FRONTEND_SMOKE_URL\"")

    def test_validator_receives_canonical_variable(self):
        self.assertIn('--url "$FRONTEND_SMOKE_URL"', self.step)

    def test_obsolete_probe_path_is_absent(self):
        obsolete = "/auth/" + "login"
        self.assertNotIn(obsolete, self.step)

    def test_timeout_is_bounded_to_180_seconds(self):
        self.assertIn("up to 180s", self.step)
        self.assertIn("for i in $(seq 1 60)", self.step)
        self.assertNotIn("600s", self.step)

    def test_backend_metadata_is_uploaded(self):
        self.assertIn("${{ runner.temp }}/backend-smoke-metadata.json", self.text)

    def test_frontend_metadata_is_uploaded(self):
        self.assertIn("${{ runner.temp }}/frontend-smoke-metadata.json", self.text)

    def test_final_evidence_validator_is_called(self):
        self.assertIn("scripts/ci/validate_post_merge_evidence.py", self.text)


if __name__ == "__main__":
    unittest.main()
