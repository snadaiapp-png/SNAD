#!/usr/bin/env python3
"""Contract tests for the canonical G2 production visual smoke gate."""

from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github" / "workflows" / "production-release.yml"


class ProductionReleaseG2VisualContractTest(unittest.TestCase):
    def test_release_requires_dedicated_g2_production_credentials(self):
        text = WORKFLOW.read_text(encoding="utf-8")
        for role in ("EMPLOYEE", "MANAGER", "HR"):
            self.assertIn(
                f"E2E_{role}_EMAIL: ${{{{ secrets.G2_PROD_{role}_EMAIL }}}}",
                text,
            )
            self.assertIn(
                f"E2E_{role}_PASSWORD: ${{{{ secrets.G2_PROD_{role}_PASSWORD }}}}",
                text,
            )
        self.assertIn("E2E_EMPLOYEE_EMAIL E2E_EMPLOYEE_PASSWORD", text)
        self.assertIn("E2E_MANAGER_EMAIL E2E_MANAGER_PASSWORD", text)
        self.assertIn("E2E_HR_EMAIL E2E_HR_PASSWORD", text)

    def test_release_runs_exact_sha_g2_visual_matrix_fail_closed(self):
        text = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("Verify authenticated G2 production visual smoke", text)
        self.assertIn("PLAYWRIGHT_BASE_URL: ${{ env.WEB_PRODUCTION_BASE_URL }}", text)
        self.assertIn("GITHUB_HEAD_SHA: ${{ steps.target.outputs.sha }}", text)
        self.assertIn("/api/system/release", text)
        self.assertIn("npx playwright test --config=playwright-g2-visual.config.ts", text)
        self.assertIn("EXPECTED=60", text)
        self.assertIn('grep -v "\\\"sha\\\":\\\"$GITHUB_HEAD_SHA\\\""', text)
        self.assertIn("g2-production-visual=PASS", text)
        self.assertNotIn("continue-on-error: true", text)

    def test_release_persists_g2_visual_evidence(self):
        text = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("apps/web/test-results/g2-visual-evidence/", text)
        self.assertIn("apps/web/playwright-g2-visual-report/", text)
        self.assertIn('g2ProductionVisual:"PASS"', text)


if __name__ == "__main__":
    unittest.main(verbosity=2)
