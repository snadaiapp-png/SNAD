import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PRE_MERGE = ROOT / ".github" / "workflows" / "pre-merge-operational-smoke.yml"
PRODUCTION = ROOT / ".github" / "workflows" / "production-operational-smoke.yml"
RECONCILE = ROOT / ".github" / "workflows" / "vercel-main-production-reconcile.yml"
VERCEL_CONFIG = ROOT / "apps" / "web" / "vercel.json"


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

        self.assertIn("/hr/performance/goals", text)
        self.assertIn("/hr/performance/reviews", text)
        self.assertIn("app-paths-manifest.json", text)
        self.assertIn("CANDIDATE_ROUTE_MANIFEST=PASS", text)
        self.assertIn("http://127.0.0.1:3001$path", text)
        self.assertIn("CANDIDATE_ROUTE_HTTP_FAILURE", text)
        self.assertNotIn("CANDIDATE_ROUTE_SOFT_404", text)

    def test_production_gate_runs_only_after_successful_reconcile(self):
        text = PRODUCTION.read_text(encoding="utf-8")

        self.assertIn("name: Production Operational Smoke", text)
        self.assertIn("workflow_run:", text)
        self.assertIn('workflows: ["Vercel Main Production Reconcile"]', text)
        self.assertIn("types: [completed]", text)
        self.assertIn("branches: [main]", text)
        self.assertNotIn("  push:\n", text)
        self.assertIn("github.event.workflow_run.conclusion == 'success'", text)
        self.assertIn("github.event.workflow_run.head_branch == 'main'", text)
        self.assertIn("TARGET_SHA:", text)
        self.assertIn("github.event.workflow_run.head_sha || github.sha", text)
        self.assertIn("/api/system/release", text)
        self.assertIn('observed_sha" = "$TARGET_SHA"', text)
        self.assertIn('observed_ref" = "main"', text)
        self.assertIn("PRODUCTION_SMOKE_SUPERSEDED", text)
        self.assertIn("check-production-readiness.py", text)
        self.assertIn("--frontend-routes-only", text)

    def test_reconcile_uses_full_production_urls_with_vercel_curl(self):
        text = RECONCILE.read_text(encoding="utf-8")

        self.assertIn('marker_url="$PRODUCTION_WEB_BASE_URL/__snad_release_sha.txt"', text)
        self.assertIn('vercel@latest curl "$marker_url"', text)
        self.assertIn('vercel@latest curl "$PRODUCTION_WEB_BASE_URL/api/system/release"', text)
        self.assertIn('--token="$VERCEL_TOKEN"', text)
        self.assertNotIn('--deployment "$PRODUCTION_WEB_BASE_URL"', text)
        self.assertNotIn("2>/dev/null", text)
        self.assertIn("Vercel protected marker probe failed", text)

    def test_production_has_single_writer(self):
        import json

        config = json.loads(VERCEL_CONFIG.read_text(encoding="utf-8"))
        deployment_enabled = config["git"]["deploymentEnabled"]
        self.assertIs(
            deployment_enabled.get("main"),
            False,
            msg="Vercel Git Integration must not auto-deploy main; governed CLI reconcile is the single production writer",
        )


if __name__ == "__main__":
    unittest.main()
