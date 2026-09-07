#!/usr/bin/env python3
import importlib.util
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
SCANNER_PATH = REPO_ROOT / "scripts/ci/scan_secrets.py"
PROBE_PATH = REPO_ROOT / "scripts/production/verify-workflow-y2-production-evidence.sh"

SPEC = importlib.util.spec_from_file_location("scan_secrets", SCANNER_PATH)
scan_module = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(scan_module)


class WorkflowY2ProbeSecretScanTest(unittest.TestCase):
    def test_probe_contains_no_secret_scanner_findings(self):
        errors = []
        findings = scan_module.scan_file(
            PROBE_PATH,
            scan_module.RULES,
            errors,
            REPO_ROOT,
        )

        self.assertEqual([], errors)
        self.assertEqual(
            [],
            findings,
            "Workflow Y2 production evidence probe must use only approved secret indirection",
        )


if __name__ == "__main__":
    unittest.main(verbosity=2)
