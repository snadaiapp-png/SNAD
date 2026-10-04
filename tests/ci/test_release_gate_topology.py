import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PRE_MERGE = ROOT / ".github" / "workflows" / "pre-merge-operational-smoke.yml"
PRODUCTION = ROOT / ".github" / "workflows" / "production-operational-smoke.yml"


class ReleaseGateTopologyTest(unittest.TestCase):
    def test_pre_merge_gate_is_candidate_only(self):
        text = PRE_MERGE.read_text(encoding="utf-8")

        forbidden = (
            "snad-app.vercel.app",
            "api/system/release",
            "Verify production-alias",
            "Verify git-main-alias",
            "production route serving diagnostics",
            "smoke-summary:",
        )
        for token in forbidden:
            self.assertNotIn(
                token,
                text,
                msg=f"pre-merge gate must not depend on deployed production state: {token}",
            )

        self.assertIn("Verify candidate HRM G3 routes locally", text)
        self.assertIn("http://127.0.0.1:3001/hr/performance/goals", text.replace("$path", "hr/performance/goals") if False else text)
        self.assertIn("CANDIDATE_ROUTE_HTTP_FAILURE", text)
        self.assertIn("CANDIDATE_ROUTE_SOFT_404", text)

    def test_production_gate_is_main_only_and_exact_sha(self):
        text = PRODUCTION.read_text(encoding="utf-8")

        self.assertIn("name: Production Operational Smoke", text)
        self.assertIn("branches: [main]", text)
        self.assertIn("github.ref == 'refs/heads/main'", text)
        self.assertIn("refs/heads/main", text)
        self.assertIn("/api/system/release", text)
        self.assertIn('observed_sha" = "$GITHUB_SHA"', text)
        self.assertIn('observed_ref" = "main"', text)
        self.assertIn("check-production-readiness.py", text)
        self.assertIn("--frontend-routes-only", text)


if __name__ == "__main__":
    unittest.main()
