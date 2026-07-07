#!/usr/bin/env python3
"""Tests for validate_backend_smoke.py"""
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path

import importlib.util
spec = importlib.util.spec_from_file_location(
    "validate_backend_smoke",
    str(Path(__file__).resolve().parent.parent.parent / "scripts" / "ci" / "validate_backend_smoke.py")
)
mod = importlib.util.module_from_spec(spec)

class TestBackendSmokeValidator(unittest.TestCase):
    def setUp(self):
        self.tmpdir = tempfile.mkdtemp()
        self.health_file = os.path.join(self.tmpdir, "health.json")
        self.meta_file = os.path.join(self.tmpdir, "metadata.json")

    def _run(self, http_status, health_content=None):
        if health_content is not None:
            Path(self.health_file).write_text(health_content)
        elif not Path(self.health_file).exists():
            Path(self.health_file).write_text('{"status":"UP"}')

        spec.loader.exec_module(mod)
        old_argv = sys.argv
        sys.argv = [
            "validate_backend_smoke.py",
            "--health-file", self.health_file,
            "--metadata-file", self.meta_file,
            "--http-status", str(http_status),
            "--application-port", "8081",
            "--management-port", "8082",
            "--health-url", "http://127.0.0.1:8082/actuator/health",
        ]
        try:
            mod.main()
            return 0
        except SystemExit as e:
            return e.code
        finally:
            sys.argv = old_argv

    def test_http_200_up_passes(self):
        code = self._run("200", '{"status":"UP"}')
        self.assertEqual(code, 0)

    def test_http_404_fails(self):
        code = self._run("404", '{"status":"UP"}')
        self.assertEqual(code, 1)

    def test_http_500_fails(self):
        code = self._run("500", '{"status":"UP"}')
        self.assertEqual(code, 1)

    def test_empty_http_status_fails(self):
        code = self._run("", '{"status":"UP"}')
        self.assertEqual(code, 1)

    def test_non_numeric_http_status_fails(self):
        code = self._run("abc", '{"status":"UP"}')
        self.assertEqual(code, 1)

    def test_missing_health_file_fails(self):
        # Remove health file if it exists
        if os.path.exists(self.health_file):
            os.unlink(self.health_file)
        # Create a minimal file to avoid FileNotFoundError in _run
        Path(self.health_file).write_text('{"status":"UP"}')
        # Then remove it
        os.unlink(self.health_file)
        # Run with explicit health_content=None so _run doesn't recreate it
        old_run = self._run
        def custom_run(http_status, health_content=None):
            # Don't create the file
            spec.loader.exec_module(mod)
            old_argv = sys.argv
            sys.argv = [
                "validate_backend_smoke.py",
                "--health-file", self.health_file,
                "--metadata-file", self.meta_file,
                "--http-status", str(http_status),
                "--application-port", "8081",
                "--management-port", "8082",
                "--health-url", "http://127.0.0.1:8082/actuator/health",
            ]
            try:
                mod.main()
                return 0
            except SystemExit as e:
                return e.code
            finally:
                sys.argv = old_argv
        code = custom_run("200")
        self.assertEqual(code, 1)
        self.assertEqual(code, 1)

    def test_malformed_json_fails(self):
        code = self._run("200", "{invalid json}")
        self.assertEqual(code, 1)

    def test_missing_status_fails(self):
        code = self._run("200", '{"components":{}}')
        self.assertEqual(code, 1)

    def test_status_down_fails(self):
        code = self._run("200", '{"status":"DOWN"}')
        self.assertEqual(code, 1)

if __name__ == "__main__":
    unittest.main()
