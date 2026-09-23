#!/usr/bin/env python3
import hashlib
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

    def test_official_wordmark_integrity_rejects_missing_asset(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo_root = Path(tmp)
            violations = module.check_official_wordmark_integrity(repo_root)
            self.assertEqual(1, len(violations))
            self.assertIn("OFFICIAL_WORDMARK_MISSING", violations[0])

    def test_official_wordmark_integrity_rejects_wrong_bytes(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo_root = Path(tmp)
            asset = repo_root / module.OFFICIAL_WORDMARK_PATH
            asset.parent.mkdir(parents=True)
            asset.write_bytes(b"not-the-approved-logo")
            violations = module.check_official_wordmark_integrity(repo_root)
            self.assertEqual(1, len(violations))
            self.assertIn("OFFICIAL_WORDMARK_HASH_MISMATCH", violations[0])
            self.assertIn(hashlib.sha256(asset.read_bytes()).hexdigest(), violations[0])


if __name__ == "__main__":
    unittest.main()
