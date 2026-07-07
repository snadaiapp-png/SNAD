#!/usr/bin/env python3
"""Regression contract ensuring the frontend smoke probe resolves successfully."""

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github" / "workflows" / "post-merge-verification.yml"
ROOT_PAGE = ROOT / "apps" / "web" / "app" / "page.tsx"
COMPAT_PAGE = ROOT / "apps" / "web" / "app" / "auth" / "login" / "page.tsx"


class TestPostMergeFrontendRoute(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = WORKFLOW.read_text(encoding="utf-8")
        match = re.search(
            r"- name: Smoke test — Frontend auth route \(operational\)(.*?)(?=\n\s*- name:|\Z)",
            cls.workflow,
            flags=re.DOTALL,
        )
        if not match:
            raise AssertionError("frontend smoke step not found")
        cls.step = match.group(1)

    def test_root_page_renders_auth_entry(self):
        text = ROOT_PAGE.read_text(encoding="utf-8")
        self.assertIn("AuthEntry", text)

    def test_compatibility_route_exists(self):
        self.assertTrue(COMPAT_PAGE.is_file())

    def test_compatibility_route_renders_auth_entry(self):
        text = COMPAT_PAGE.read_text(encoding="utf-8")
        self.assertIn("AuthEntry", text)

    def test_configured_probe_has_a_resolvable_route(self):
        root_probe = "http://127.0.0.1:3001/"
        compatibility_probe = root_probe + "auth/" + "login"
        self.assertTrue(root_probe in self.step or compatibility_probe in self.step)

    def test_probe_timeout_is_finite(self):
        self.assertRegex(self.step, r"for i in \$\(seq 1 [0-9]+\)")

    def test_evidence_validator_exists(self):
        validator = ROOT / "scripts" / "ci" / "validate_post_merge_evidence.py"
        self.assertTrue(validator.is_file())

    def test_manifest_generator_exists(self):
        generator = ROOT / "scripts" / "ci" / "generate_post_merge_manifest.py"
        self.assertTrue(generator.is_file())


if __name__ == "__main__":
    unittest.main()
