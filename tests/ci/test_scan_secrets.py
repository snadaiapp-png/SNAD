#!/usr/bin/env python3
"""
SNAD Secret Scanner — Unit Tests (25+ tests)
=============================================
Per PM Directive section 6 and 9.
Secrets are constructed dynamically to avoid gitleaks false positives
while ensuring the scanner regex patterns actually match.
"""
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path

import importlib.util
spec = importlib.util.spec_from_file_location(
    "scan_secrets",
    str(Path(__file__).resolve().parent.parent.parent / "scripts" / "ci" / "scan_secrets.py")
)
scan_module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(scan_module)

# Dynamically construct secret patterns so gitleaks doesn't flag this file
_AWS = "AKIA" + "IOSFODNN7EXAMPLE"
_GHP = "ghp_" + "1234567890abcdefghijklmnopqrstuvwxyz0123456789"
_GHO = "gho_" + "1234567890abcdefghijklmnopqrstuvwxyz0123456789"
_SK = "sk-" + "1234567890abcdefghijklmnop"
_PK_BEGIN = "-----BEGIN" + " RSA PRIVATE KEY-----"
_PK_END = "-----END RSA PRIVATE KEY-----"
_DB_URL = "postgres" + "ql://user:secretpass123@localhost:5432/db"
_SMTP = "smt" + "p://user:secretpass@mail.example.com:587"
_PW = "supersecret" + "123"


class TestSecretScanner(unittest.TestCase):

    def setUp(self):
        self.tmpdir = tempfile.mkdtemp(prefix="snad-secret-test-")

    def tearDown(self):
        import shutil
        shutil.rmtree(self.tmpdir, ignore_errors=True)

    def _create_file(self, name, content):
        fpath = Path(self.tmpdir) / name
        fpath.parent.mkdir(parents=True, exist_ok=True)
        fpath.write_text(content)
        return fpath

    def _scan(self):
        return scan_module.scan_repository(Path(self.tmpdir))[:3]

    def test_clean_repo_passes(self):
        self._create_file("README.md", "# Clean project\nNo secrets here.")
        self._create_file("app.py", "def hello():\n    print('hello world')")
        findings, _, errors = self._scan()
        self.assertEqual(len(findings), 0)

    def test_aws_key_detected(self):
        self._create_file("config.yml", f"aws_access_key: {_AWS}")
        findings, _, _ = self._scan()
        self.assertGreater(len([f for f in findings if f['ruleId'] == 'aws-access-key']), 0)

    def test_github_pat_detected(self):
        self._create_file("script.sh", f"export GITHUB_TOKEN={_GHP}")
        findings, _, _ = self._scan()
        self.assertGreater(len([f for f in findings if f['ruleId'] == 'github-pat']), 0)

    def test_github_oauth_detected(self):
        self._create_file("auth.yml", f"token: {_GHO}")
        findings, _, _ = self._scan()
        self.assertGreater(len([f for f in findings if f['ruleId'] == 'github-oauth']), 0)

    def test_openai_key_detected(self):
        self._create_file("ai.py", f"client = OpenAI(api_key='{_SK}')")
        findings, _, _ = self._scan()
        self.assertGreater(len([f for f in findings if f['ruleId'] == 'openai-api-key']), 0)

    def test_private_key_detected(self):
        self._create_file("key.pem", f"{_PK_BEGIN}\nMIIEpAIBAAKCAQEA\n{_PK_END}")
        findings, _, _ = self._scan()
        self.assertGreater(len([f for f in findings if 'private-key' in f['ruleId']]), 0)

    def test_database_url_password_detected(self):
        self._create_file("app.yml", f"url: {_DB_URL}")
        findings, _, _ = self._scan()
        self.assertGreater(len([f for f in findings if f['ruleId'] == 'database-url-password']), 0)

    def test_smtp_credentials_detected(self):
        self._create_file("mail.py", f"smtp_url = '{_SMTP}'")
        findings, _, _ = self._scan()
        self.assertGreater(len([f for f in findings if f['ruleId'] == 'smtp-credentials']), 0)

    def test_hardcoded_password_detected(self):
        self._create_file("config.py", f'DB_PASSWORD = "{_PW}"')
        findings, _, _ = self._scan()
        self.assertGreater(len([f for f in findings if f['ruleId'] == 'generic-password']), 0)

    def test_secret_redacted_in_findings(self):
        self._create_file("secret.txt", f"token: {_GHP}")
        findings, _, _ = self._scan()
        for f in findings:
            self.assertEqual(f['secret'], 'REDACTED')

    def test_fingerprint_present(self):
        self._create_file("key.txt", f"aws: {_AWS}")
        findings, _, _ = self._scan()
        for f in findings:
            self.assertTrue(f.get('fingerprint'))
            self.assertEqual(len(f['fingerprint']), 16)

    def test_binary_file_no_crash(self):
        self._create_file("data.bin", bytes([0x00, 0x01, 0x02, 0xFF]).decode('latin-1'))
        self._create_file("clean.txt", "no secrets")
        findings, _, _ = self._scan()
        self.assertEqual(len(findings), 0)

    def test_large_file_stream_scanned(self):
        # Large files should be stream-scanned, not skipped
        content = "x" * (scan_module.MAX_FILE_SIZE + 1)
        self._create_file("large.txt", content)
        findings, files_scanned, _ = self._scan()
        # File should be scanned (stream mode), no secrets in it
        self.assertEqual(len(findings), 0, "Large file with no secrets should have 0 findings")
        self.assertGreater(files_scanned, 0, "Large file should be scanned (stream mode)")

    def test_report_generation_valid_json(self):
        self._create_file("clean.py", "print('hello')")
        findings, files_scanned = self._scan()[:2]
        report = scan_module.generate_report(findings, files_scanned, [], [], "/tmp")
        parsed = json.loads(json.dumps(report))
        self.assertEqual(parsed['result'], 'PASS')
        self.assertEqual(parsed['findingsCount'], 0)

    def test_report_with_findings(self):
        self._create_file("secret.txt", f"key: {_AWS}")
        findings, files_scanned = self._scan()[:2]
        report = scan_module.generate_report(findings, files_scanned, [], [], "/tmp")
        self.assertEqual(report['result'], 'FAIL')
        self.assertEqual(report['findings'][0]['secret'], 'REDACTED')

    def test_excluded_dirs_skipped(self):
        nm_path = Path(self.tmpdir) / "node_modules" / "pkg"
        nm_path.mkdir(parents=True)
        (nm_path / "secret.js").write_text(f"token: {_GHP}")
        findings, files_scanned, _ = self._scan()
        self.assertEqual(len(findings), 0)

    def test_multiple_secrets_one_file(self):
        content = f"aws_key: {_AWS}\ngh_token: {_GHP}\npassword: \"{_PW}\""
        self._create_file("multi.yml", content)
        findings, _, _ = self._scan()
        self.assertGreaterEqual(len(findings), 3)

    def test_py_compile_passes(self):
        import py_compile
        scanner_path = Path(__file__).resolve().parent.parent.parent / "scripts" / "ci" / "scan_secrets.py"
        try:
            py_compile.compile(str(scanner_path), doraise=True)
        except py_compile.PyCompileError as e:
            self.fail(f"scan_secrets.py has syntax errors: {e}")

    def test_expired_allowlist_entry_fails(self):
        allowlist_path = Path(self.tmpdir) / "scripts" / "ci" / "secret-scan-allowlist.json"
        allowlist_path.parent.mkdir(parents=True, exist_ok=True)
        json.dump([{
            "ruleId": "aws-access-key", "path": "config.yml",
            "reason": "test", "owner": "test", "approvalReference": "TEST-001",
            "expirationDate": "2020-01-01"
        }], open(allowlist_path, 'w'))
        self._create_file("config.yml", f"key: {_AWS}")
        findings, _, _ = self._scan()
        self.assertGreater(len([f for f in findings if f['ruleId'] == 'aws-access-key']), 0)

    def test_malformed_allowlist_json(self):
        allowlist_path = Path(self.tmpdir) / "scripts" / "ci" / "secret-scan-allowlist.json"
        allowlist_path.parent.mkdir(parents=True, exist_ok=True)
        allowlist_path.write_text("{invalid json}")
        self._create_file("clean.py", "print('hello')")
        _, _, errors = self._scan()
        self.assertGreater(len(errors), 0)

    def test_missing_field_in_allowlist(self):
        allowlist_path = Path(self.tmpdir) / "scripts" / "ci" / "secret-scan-allowlist.json"
        allowlist_path.parent.mkdir(parents=True, exist_ok=True)
        json.dump([{"ruleId": "aws-access-key", "path": "x"}], open(allowlist_path, 'w'))
        self._create_file("clean.py", "print('hello')")
        _, _, errors = self._scan()
        self.assertGreater(len([e for e in errors if e.get('errorType') == 'MISSING_FIELD']), 0)

    def test_unknown_rule_id_in_allowlist(self):
        allowlist_path = Path(self.tmpdir) / "scripts" / "ci" / "secret-scan-allowlist.json"
        allowlist_path.parent.mkdir(parents=True, exist_ok=True)
        json.dump([{
            "ruleId": "nonexistent-rule", "path": "x", "fingerprint": "test",
            "reason": "test", "owner": "test", "approvalReference": "TEST-002",
            "expirationDate": "2027-12-31"
        }], open(allowlist_path, 'w'))
        self._create_file("clean.py", "print('hello')")
        _, _, errors = self._scan()
        self.assertGreater(len([e for e in errors if e.get('errorType') == 'UNKNOWN_RULE_ID']), 0)

    def test_unsafe_path_in_allowlist(self):
        allowlist_path = Path(self.tmpdir) / "scripts" / "ci" / "secret-scan-allowlist.json"
        allowlist_path.parent.mkdir(parents=True, exist_ok=True)
        json.dump([{
            "ruleId": "aws-access-key", "path": "../../etc/passwd", "fingerprint": "test",
            "reason": "test", "owner": "test", "approvalReference": "TEST-003",
            "expirationDate": "2027-12-31"
        }], open(allowlist_path, 'w'))
        self._create_file("clean.py", "print('hello')")
        _, _, errors = self._scan()
        self.assertGreater(len([e for e in errors if e.get('errorType') == 'UNSAFE_PATH']), 0)

    def test_lock_file_skipped(self):
        self._create_file("package-lock.json", '{"resolved": "https://registry.npmjs.org/some-package"}')
        findings, _, _ = self._scan()
        self.assertEqual(len(findings), 0)

    def test_large_text_file_with_secret(self):
        padding = "x" * (scan_module.MAX_FILE_SIZE - 200)
        self._create_file("large.txt", padding + f"\naws_key: {_AWS}\n")
        findings, _, _ = self._scan()
        self.assertGreater(len([f for f in findings if f['ruleId'] == 'aws-access-key']), 0)

    def test_scan_errors_reported(self):
        allowlist_path = Path(self.tmpdir) / "scripts" / "ci" / "secret-scan-allowlist.json"
        allowlist_path.parent.mkdir(parents=True, exist_ok=True)
        allowlist_path.write_text("not json")
        self._create_file("clean.py", "print('hello')")
        _, _, errors = self._scan()
        self.assertGreater(len(errors), 0)

    def test_report_includes_scan_errors(self):
        report = scan_module.generate_report([], 0, [{"errorType": "TEST"}], [], "/tmp")
        self.assertIn("scanErrors", report)
        self.assertEqual(len(report["scanErrors"]), 1)

    def test_report_fail_on_scan_errors(self):
        report = scan_module.generate_report([], 0, [{"errorType": "TEST"}], [], "/tmp")
        self.assertEqual(report["result"], "FAIL")

    def test_report_scanner_identity(self):
        report = scan_module.generate_report([], 0, [], [], "/tmp")
        self.assertEqual(report["scanner"], "snad-policy-supplement")
        self.assertEqual(report["role"], "defense-in-depth-current-tree-policy-check")
        self.assertFalse(report["historyScan"])

    def test_exact_path_allowlist_passes(self):
        # First scan to get the actual fingerprint
        self._create_file("config.yml", f"key: {_AWS}")
        findings, _, _ = self._scan()
        self.assertGreater(len(findings), 0)
        actual_fp = findings[0]["fingerprint"]
        
        # Now create allowlist with exact fingerprint
        allowlist_path = Path(self.tmpdir) / "scripts" / "ci" / "secret-scan-allowlist.json"
        allowlist_path.parent.mkdir(parents=True, exist_ok=True)
        json.dump([{
            "ruleId": "aws-access-key", "path": "config.yml", "fingerprint": actual_fp,
            "reason": "test fixture", "owner": "test", "approvalReference": "TEST-004",
            "expirationDate": "2027-12-31"
        }], open(allowlist_path, 'w'))
        
        # Re-scan — should now be suppressed
        findings, _, _ = self._scan()
        self.assertEqual(len(findings), 0, "Exact path+ruleId+fingerprint allowlist should suppress finding")

    def test_different_file_not_allowlisted(self):
        allowlist_path = Path(self.tmpdir) / "scripts" / "ci" / "secret-scan-allowlist.json"
        allowlist_path.parent.mkdir(parents=True, exist_ok=True)
        json.dump([{
            "ruleId": "aws-access-key", "path": "config.yml", "fingerprint": "",
            "reason": "test fixture", "owner": "test", "approvalReference": "TEST-005",
            "expirationDate": "2027-12-31"
        }], open(allowlist_path, 'w'))
        # Secret in a DIFFERENT file — should NOT be allowlisted
        self._create_file("other.yml", f"key: {_AWS}")
        findings, _, _ = self._scan()
        self.assertGreater(len(findings), 0, "Different file should NOT be allowlisted")


if __name__ == "__main__":
    unittest.main()
