#!/usr/bin/env python3
"""Regression tests for source-level secret indirection handling.

These tests intentionally exercise the scanner itself. A reference to a secret
stored outside the repository is not a hardcoded secret; a literal or a shell
default containing a literal still is.
"""
import importlib.util
import shutil
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parent.parent.parent
SPEC = importlib.util.spec_from_file_location(
    "scan_secrets",
    str(REPO_ROOT / "scripts" / "ci" / "scan_secrets.py"),
)
scan_module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(scan_module)


class TestSecretSourceIndirection(unittest.TestCase):
    def setUp(self):
        self.tmpdir = Path(tempfile.mkdtemp(prefix="snad-secret-indirection-"))

    def tearDown(self):
        shutil.rmtree(self.tmpdir, ignore_errors=True)

    def _scan_line(self, line: str):
        path = self.tmpdir / "sample.sh"
        path.write_text(line + "\n")
        findings, _, errors, _ = scan_module.scan_repository(self.tmpdir)
        self.assertEqual(errors, [])
        return findings

    def test_plain_shell_env_reference_is_not_a_hardcoded_password(self):
        findings = self._scan_line('PGPASSWORD="$SPRING_DATASOURCE_PASSWORD"')
        self.assertEqual(findings, [])

    def test_braced_shell_env_reference_is_not_a_hardcoded_password(self):
        findings = self._scan_line('DB_PASSWORD="${DATABASE_PASSWORD}"')
        self.assertEqual(findings, [])

    def test_render_env_accessor_is_not_a_hardcoded_password(self):
        findings = self._scan_line('password="$(get_render_var DATABASE_PASSWORD)"')
        self.assertEqual(findings, [])

    def test_github_secret_expression_is_not_a_hardcoded_password(self):
        findings = self._scan_line('password="${{ secrets.DATABASE_PASSWORD }}"')
        self.assertEqual(findings, [])

    def test_literal_password_remains_detected(self):
        findings = self._scan_line('DB_PASSWORD="synthetic-hardcoded-password"')
        self.assertTrue(any(f["ruleId"] == "generic-password" for f in findings))

    def test_shell_default_with_literal_remains_detected(self):
        findings = self._scan_line('DB_PASSWORD="${DATABASE_PASSWORD:-synthetic-hardcoded-password}"')
        self.assertTrue(any(f["ruleId"] == "generic-password" for f in findings))

    def test_arbitrary_command_substitution_remains_detected(self):
        findings = self._scan_line('DB_PASSWORD="$(printf synthetic-hardcoded-password)"')
        self.assertTrue(any(f["ruleId"] == "generic-password" for f in findings))


if __name__ == "__main__":
    unittest.main()
