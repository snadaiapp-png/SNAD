#!/usr/bin/env python3
import importlib.util
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("check-logo-governance.py")
spec = importlib.util.spec_from_file_location("logo_governance", SCRIPT)
assert spec and spec.loader
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class LogoGovernanceTest(unittest.TestCase):
    def test_direct_official_png_reference_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            file = root / "login.tsx"
            file.write_text(
                'export const x = <img src="/assets/brand/snad-logo-official-wordmark.png" />;',
                encoding="utf-8",
            )
            violations = module.check_direct_brand_references(file, "apps/web/login.tsx")
            self.assertTrue(violations)

    def test_canonical_snadlogo_component_is_allowlisted(self):
        with tempfile.TemporaryDirectory() as tmp:
            file = Path(tmp) / "SnadLogo.tsx"
            file.write_text(
                "const src = '/assets/brand/snad-logo-official-wordmark.png';",
                encoding="utf-8",
            )
            violations = module.check_direct_brand_references(
                file,
                "apps/web/components/sds/SnadLogo.tsx",
            )
            self.assertEqual([], violations)


if __name__ == "__main__":
    unittest.main()
