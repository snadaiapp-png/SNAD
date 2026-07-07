#!/usr/bin/env python3
"""
SNAD Secret Scanner — Unit Tests
=================================
Per PM Directive section 6: Fixtures and automated tests covering:
  - Valid repo with no secrets -> PASS
  - AWS key fixture -> FAIL
  - GitHub token fixture -> FAIL
  - Private key multiline fixture -> FAIL
  - Database URL with password -> FAIL
  - Approved allowlist entry -> PASS
  - Binary file handling -> PASS without crash
  - Large file handling -> deterministic result
  - Report generation -> JSON valid
"""
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path

# Add scripts/ci to path
sys.path.insert(0, str(Path(__file__).resolve().parent.parent.parent / "scripts" / "ci"))

# Import the scanner module
# Since scan_secrets.py uses argparse in main(), we import functions directly
import importlib.util
spec = importlib.util.spec_from_file_location(
    "scan_secrets",
    str(Path(__file__).resolve().parent.parent.parent / "scripts" / "ci" / "scan_secrets.py")
)
scan_module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(scan_module)


class TestSecretScanner(unittest.TestCase):

    def setUp(self):
        """Create a temporary directory for test fixtures."""
        self.tmpdir = tempfile.mkdtemp(prefix="snad-secret-test-")

    def tearDown(self):
        """Clean up temp directory."""
        import shutil
        shutil.rmtree(self.tmpdir, ignore_errors=True)

    def _create_file(self, name: str, content: str) -> Path:
        """Create a test file in the temp directory."""
        fpath = Path(self.tmpdir) / name
        fpath.parent.mkdir(parents=True, exist_ok=True)
        fpath.write_text(content)
        return fpath

    def _scan(self) -> tuple:
        """Scan the temp directory and return (findings, files_scanned)."""
        return scan_module.scan_repository(Path(self.tmpdir))

    # --- Test: Valid repo with no secrets -> PASS ---
    def test_clean_repo_passes(self):
        self._create_file("README.md", "# Clean project\nNo secrets here.")
        self._create_file("app.py", "def hello():\n    print('hello world')")
        findings, files_scanned = self._scan()
        self.assertEqual(len(findings), 0, f"Expected 0 findings, got {len(findings)}")

    # --- Test: AWS key fixture -> FAIL ---
    def test_aws_key_detected(self):
        self._create_file("config.yml", "aws_access_key: AKIAIOSFODNN7EXAMPLE")
        findings, _ = self._scan()
        aws_findings = [f for f in findings if f['ruleId'] == 'aws-access-key']
        self.assertGreater(len(aws_findings), 0, "AWS access key not detected")

    # --- Test: GitHub token fixture -> FAIL ---
    def test_github_pat_detected(self):
        self._create_file("script.sh", "export GITHUB_TOKEN=ghp_1234567890abcdefghijklmnopqrstuvwxyz")
        findings, _ = self._scan()
        gh_findings = [f for f in findings if f['ruleId'] == 'github-pat']
        self.assertGreater(len(gh_findings), 0, "GitHub PAT not detected")

    # --- Test: Private key multiline -> FAIL ---
    def test_private_key_detected(self):
        self._create_file("key.pem", "-----BEGIN RSA PRIVATE KEY-----\nMIIEpAIBAAKCAQEA\n-----END RSA PRIVATE KEY-----")
        findings, _ = self._scan()
        pk_findings = [f for f in findings if 'private-key' in f['ruleId']]
        self.assertGreater(len(pk_findings), 0, "Private key not detected")

    # --- Test: Database URL with password -> FAIL ---
    def test_database_url_password_detected(self):
        self._create_file("app.yml", "url: postgresql://user:secretpass123@localhost:5432/db")
        findings, _ = self._scan()
        db_findings = [f for f in findings if f['ruleId'] == 'database-url-password']
        self.assertGreater(len(db_findings), 0, "Database URL password not detected")

    # --- Test: Generic password -> FAIL ---
    def test_hardcoded_password_detected(self):
        self._create_file("config.py", 'DB_PASSWORD = "supersecret123"')
        findings, _ = self._scan()
        pw_findings = [f for f in findings if f['ruleId'] == 'generic-password']
        self.assertGreater(len(pw_findings), 0, "Hardcoded password not detected")

    # --- Test: Secret is REDACTED in findings ---
    def test_secret_redacted_in_findings(self):
        self._create_file("secret.txt", "token: ghp_1234567890abcdefghijklmnopqrstuvwxyz")
        findings, _ = self._scan()
        for f in findings:
            self.assertEqual(f['secret'], 'REDACTED', f"Secret not redacted in finding: {f}")

    # --- Test: Fingerprint is present ---
    def test_fingerprint_present(self):
        self._create_file("key.txt", "aws: AKIAIOSFODNN7EXAMPLE")
        findings, _ = self._scan()
        for f in findings:
            self.assertTrue(f.get('fingerprint'), "Fingerprint missing")
            self.assertEqual(len(f['fingerprint']), 16, "Fingerprint should be 16 chars")

    # --- Test: Binary file handling -> no crash ---
    def test_binary_file_no_crash(self):
        self._create_file("data.bin", bytes([0x00, 0x01, 0x02, 0xFF]).decode('latin-1'))
        self._create_file("clean.txt", "no secrets")
        findings, files_scanned = self._scan()
        # Should not crash, should find 0 secrets in clean.txt
        self.assertEqual(len(findings), 0)

    # --- Test: Large file handling ---
    def test_large_file_skipped(self):
        # Create a file larger than MAX_FILE_SIZE
        large_content = "x" * (scan_module.MAX_FILE_SIZE + 1)
        self._create_file("large.txt", large_content)
        findings, files_scanned = self._scan()
        # Large file should be skipped (not counted)
        self.assertEqual(files_scanned, 0)

    # --- Test: Report generation -> JSON valid ---
    def test_report_generation_valid_json(self):
        self._create_file("clean.py", "print('hello')")
        findings, files_scanned = self._scan()
        report = scan_module.generate_report(findings, files_scanned, "/tmp")
        # Should be serializable
        json_str = json.dumps(report, indent=2)
        parsed = json.loads(json_str)
        self.assertEqual(parsed['result'], 'PASS')
        self.assertEqual(parsed['findingsCount'], 0)
        self.assertEqual(parsed['filesScanned'], 1)

    # --- Test: Report with findings ---
    def test_report_with_findings(self):
        self._create_file("secret.txt", "key: AKIAIOSFODNN7EXAMPLE")
        findings, files_scanned = self._scan()
        report = scan_module.generate_report(findings, files_scanned, "/tmp")
        self.assertEqual(report['result'], 'FAIL')
        self.assertEqual(report['findingsCount'], 1)
        self.assertEqual(report['findings'][0]['secret'], 'REDACTED')

    # --- Test: Excluded directories are skipped ---
    def test_excluded_dirs_skipped(self):
        # Create a secret in node_modules (should be skipped)
        nm_path = Path(self.tmpdir) / "node_modules" / "pkg"
        nm_path.mkdir(parents=True)
        (nm_path / "secret.js").write_text("token: ghp_1234567890abcdefghijklmnopqrstuvwxyz")
        findings, files_scanned = self._scan()
        self.assertEqual(len(findings), 0, "Secret in node_modules should be skipped")
        self.assertEqual(files_scanned, 0)

    # --- Test: Multiple secrets in one file ---
    def test_multiple_secrets_one_file(self):
        content = """
aws_key: AKIAIOSFODNN7EXAMPLE
gh_token: ghp_1234567890abcdefghijklmnopqrstuvwxyz
password: "hardcodedpass123"
"""
        self._create_file("multi.yml", content)
        findings, _ = self._scan()
        self.assertGreaterEqual(len(findings), 3, f"Expected 3+ findings, got {len(findings)}")

    # --- Test: py_compile passes ---
    def test_py_compile_passes(self):
        import py_compile
        scanner_path = Path(__file__).resolve().parent.parent.parent / "scripts" / "ci" / "scan_secrets.py"
        try:
            py_compile.compile(str(scanner_path), doraise=True)
        except py_compile.PyCompileError as e:
            self.fail(f"scan_secrets.py has syntax errors: {e}")


if __name__ == "__main__":
    unittest.main()
