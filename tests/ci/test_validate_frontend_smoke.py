#!/usr/bin/env python3
"""Tests for validate_frontend_smoke.py

Covers the cases required by the P0-2 Final Closure order:
  - Root page HTTP 200 + SNAD title  → PASS
  - Root page HTTP 200 + سند identity → PASS
  - HTTP 404                         → FAIL
  - Missing brand identity           → FAIL
  - HTTP 500                         → FAIL
  - Malformed or missing HTML        → FAIL
"""
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path

import importlib.util
spec = importlib.util.spec_from_file_location(
    "validate_frontend_smoke",
    str(Path(__file__).resolve().parent.parent.parent / "scripts" / "ci" / "validate_frontend_smoke.py")
)
mod = importlib.util.module_from_spec(spec)


class TestFrontendSmokeValidator(unittest.TestCase):
    def setUp(self):
        self.tmpdir = tempfile.mkdtemp()
        self.html_file = os.path.join(self.tmpdir, "auth.html")
        self.meta_file = os.path.join(self.tmpdir, "metadata.json")

    def _run(self, http_status, html_content=None, url="http://127.0.0.1:3001/"):
        if html_content is not None:
            Path(self.html_file).write_text(html_content)
        elif not Path(self.html_file).exists():
            Path(self.html_file).write_text("<html>SNAD platform</html>")

        spec.loader.exec_module(mod)
        old_argv = sys.argv
        sys.argv = [
            "validate_frontend_smoke.py",
            "--html-file", self.html_file,
            "--metadata-file", self.meta_file,
            "--http-status", str(http_status),
            "--url", url,
            "--port", "3001",
        ]
        try:
            mod.main()
            return 0
        except SystemExit as e:
            return e.code
        finally:
            sys.argv = old_argv

    def _read_meta(self):
        return json.loads(Path(self.meta_file).read_text())

    # ---- PASS cases (root route, brand present) ----
    def test_200_root_with_snad_passes(self):
        code = self._run("200", "<html><title>SNAD</title></html>")
        self.assertEqual(code, 0)
        meta = self._read_meta()
        self.assertEqual(meta["result"], "PASS")
        self.assertTrue(meta["brandNamePresent"])

    def test_200_root_with_arabic_brand_passes(self):
        code = self._run("200", "<html>سند منصة</html>")
        self.assertEqual(code, 0)
        meta = self._read_meta()
        self.assertEqual(meta["result"], "PASS")
        self.assertTrue(meta["brandNamePresent"])

    def test_200_redirect_passes(self):
        # 302/307 are approved redirects (e.g. middleware redirect to locale)
        code = self._run("302", "<html>SNAD redirect</html>")
        self.assertEqual(code, 0)

    # ---- FAIL cases ----
    def test_404_fails(self):
        # Per PM directive: hitting a non-existent route like /auth/login
        # returns 404. The validator must treat this as a hard FAIL.
        code = self._run("404", "<html>Not Found</html>")
        self.assertEqual(code, 1)
        meta = self._read_meta()
        self.assertEqual(meta["result"], "FAIL")
        self.assertEqual(meta["failureType"], "UNEXPECTED_HTTP_STATUS")

    def test_500_fails(self):
        code = self._run("500", "<html>SNAD</html>")
        self.assertEqual(code, 1)
        meta = self._read_meta()
        self.assertEqual(meta["failureType"], "HTTP_5XX")

    def test_missing_identity_fails(self):
        code = self._run("200", "<html>Some other app</html>")
        self.assertEqual(code, 1)
        meta = self._read_meta()
        self.assertEqual(meta["failureType"], "BRAND_IDENTITY_MISSING")

    def test_missing_html_file_fails(self):
        if os.path.exists(self.html_file):
            os.unlink(self.html_file)
        spec.loader.exec_module(mod)
        old_argv = sys.argv
        sys.argv = [
            "validate_frontend_smoke.py",
            "--html-file", self.html_file,
            "--metadata-file", self.meta_file,
            "--http-status", "200",
            "--url", "http://127.0.0.1:3001/",
            "--port", "3001",
        ]
        try:
            mod.main()
            code = 0
        except SystemExit as e:
            code = e.code
        finally:
            sys.argv = old_argv
        self.assertEqual(code, 1)

    def test_empty_html_file_fails(self):
        # Malformed / empty HTML — validator must not crash, must FAIL.
        Path(self.html_file).write_text("")
        code = self._run("200")
        self.assertEqual(code, 1)
        meta = self._read_meta()
        self.assertEqual(meta["failureType"], "BRAND_IDENTITY_MISSING")

    def test_non_numeric_status_fails(self):
        code = self._run("abc", "<html>SNAD</html>")
        self.assertEqual(code, 1)
        meta = self._read_meta()
        self.assertEqual(meta["failureType"], "INVALID_HTTP_STATUS")

    # ---- Metadata contract ----
    def test_metadata_records_url_and_port(self):
        code = self._run("200", "<html>SNAD</html>", url="http://127.0.0.1:3001/")
        self.assertEqual(code, 0)
        meta = self._read_meta()
        self.assertEqual(meta["url"], "http://127.0.0.1:3001/")
        self.assertEqual(meta["port"], 3001)
        self.assertTrue(meta["processStarted"])


if __name__ == "__main__":
    unittest.main()
