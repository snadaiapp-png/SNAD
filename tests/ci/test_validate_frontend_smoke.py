#!/usr/bin/env python3
"""Tests for validate_frontend_smoke.py"""
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

    def _run(self, http_status, html_content=None):
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
            "--url", "http://127.0.0.1:3001/auth/login",
            "--port", "3001",
        ]
        try:
            mod.main()
            return 0
        except SystemExit as e:
            return e.code
        finally:
            sys.argv = old_argv

    def test_200_with_snad_passes(self):
        code = self._run("200", "<html><title>SNAD</title></html>")
        self.assertEqual(code, 0)

    def test_200_with_arabic_brand_passes(self):
        code = self._run("200", "<html>سند منصة</html>")
        self.assertEqual(code, 0)

    def test_500_fails(self):
        code = self._run("500", "<html>SNAD</html>")
        self.assertEqual(code, 1)

    def test_missing_identity_fails(self):
        code = self._run("200", "<html>Some other app</html>")
        self.assertEqual(code, 1)

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
            "--url", "http://127.0.0.1:3001/auth/login",
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
        self.assertEqual(code, 1)

    def test_non_numeric_status_fails(self):
        code = self._run("abc", "<html>SNAD</html>")
        self.assertEqual(code, 1)

if __name__ == "__main__":
    unittest.main()
