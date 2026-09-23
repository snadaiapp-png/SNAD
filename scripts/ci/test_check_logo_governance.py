#!/usr/bin/env python3
"""Regression tests for SNAD logo-governance CI lint."""

from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("check-logo-governance.py")
SPEC = importlib.util.spec_from_file_location("check_logo_governance", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class LogoGovernanceTest(unittest.TestCase):
    def test_rejects_direct_official_wordmark_png_outside_sds(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "fake.tsx"
            path.write_text(
                '<img src="/assets/brand/snad-logo-official-wordmark.png" alt="SNAD" />\n',
                encoding="utf-8",
            )
            violations = MODULE.check_direct_brand_references(
                path,
                "apps/web/components/auth/fake.tsx",
            )

        self.assertTrue(
            any("snad-logo-official-wordmark.png" in violation for violation in violations),
            violations,
        )


if __name__ == "__main__":
    unittest.main()
