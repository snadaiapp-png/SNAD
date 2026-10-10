import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PRE_MERGE = ROOT / ".github" / "workflows" / "pre-merge-operational-smoke.yml"
PRODUCTION = ROOT / ".github" / "workflows" / "production-operational-smoke.yml"
RECONCILE = ROOT / ".github" / "workflows" / "vercel-main-production-reconcile.yml"
VERCEL_CONFIG = ROOT / "apps" / "web" / "vercel.json"
RECOVERY_SCRIPT = ROOT / "scripts" / "operations" / "vercel_main_production_reconcile.py"


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
        self.assertIn("id-token: write", text)
        self.assertIn("await core.getIDToken()", text)
        self.assertIn("VERCEL_TRUSTED_OIDC_TOKEN", text)
        self.assertIn("x-vercel-trusted-oidc-idp-token", text)

    def test_reconcile_uses_rest_identity_and_trusted_oidc(self):
        text = RECONCILE.read_text(encoding="utf-8")

        self.assertIn("Verify exact production deployment via Vercel REST API", text)
        self.assertIn("https://api.vercel.com/v13/deployments/", text)
        self.assertIn('meta.get("snadMainSha") or meta.get("githubCommitSha")', text)
        self.assertIn('state == "READY"', text)
        self.assertIn('target == "production"', text)
        self.assertIn("production_host in aliases", text)
        self.assertIn("id-token: write", text)
        self.assertIn("await core.getIDToken()", text)
        self.assertIn("x-vercel-trusted-oidc-idp-token", text)
        self.assertNotIn('vercel@latest --token="$VERCEL_TOKEN" curl', text)
        self.assertNotIn('vercel@latest curl "$marker_url"', text)
        self.assertNotIn('--deployment "$PRODUCTION_WEB_BASE_URL"', text)
        self.assertNotIn("2>/dev/null", text)
        self.assertIn("Deploy verified prebuilt artifact to Vercel Production", text)
        self.assertIn("vercel@latest deploy", text)
        self.assertIn("--prebuilt", text)
        self.assertIn("--prod", text)
        self.assertIn('--meta "snadMainSha=$GITHUB_SHA"', text)
        self.assertIn('--meta "snadDeploymentMethod=verified-prebuilt"', text)
        self.assertIn('--env "SNAD_RELEASE_SHA=$GITHUB_SHA"', text)
        self.assertIn("VERCEL_PREBUILT_IGNORE_BYPASS = ACTIVE", text)
        self.assertIn("VERCEL_PREBUILT_IGNORE_BYPASS = PASS", text)
        self.assertIn("VERCELIGNORE_RESTORE_FAILED", text)
        self.assertIn("Verify Vercel monorepo project settings", text)
        self.assertIn('root_directory != "apps/web"', text)
        self.assertIn("sourceFilesOutsideRootDirectory", text)
        self.assertIn("HRM_G3_ROUTE_ARTIFACT_MISSING", text)
        self.assertIn("HRM_G3_PREBUILT_ROUTE_ARTIFACTS = PASS", text)
        self.assertNotIn("Verify HRM G3 routes on created deployment", text)
        self.assertIn("Verify live HRM G3 routes", text)
        self.assertIn('--production-url "$PRODUCTION_WEB_BASE_URL"', text)
        self.assertIn("PRODUCTION_ALIAS_DEPLOYMENT_DRIFT", text)
        self.assertIn("PRODUCTION_ALIAS_CREATED_DEPLOYMENT = PASS", text)
        self.assertNotIn("find .vercel/output -type f | grep -E 'hr/performance/(goals|reviews)' || true", text)
        self.assertNotIn('"gitSource": {', text)
        self.assertNotIn('"snadDeploymentMethod": "git-source-rest"', text)

    def test_recovery_script_matches_protected_exact_main_contract(self):
        text = RECOVERY_SCRIPT.read_text(encoding="utf-8")

        self.assertIn('"ci": True', text)
        self.assertIn('"rootDirectory": "apps/web"', text)
        self.assertIn('"snadMainSha": release_sha', text)
        self.assertIn("forceNew=1&skipAutoDetectionConfirmation=1", text)
        self.assertIn("VERCEL_TRUSTED_OIDC_TOKEN", text)
        self.assertIn("x-vercel-trusted-oidc-idp-token", text)
        self.assertIn("VERCEL_ORG_ID", text)
        self.assertIn('meta.get("snadMainSha") or meta.get("githubCommitSha")', text)

    def test_vercel_production_requires_explicit_manual_dispatch(self):
        for workflow in (
            RECONCILE,
            ROOT / ".github" / "workflows" / "snad-release-orchestrator.yml",
        ):
            text = workflow.read_text(encoding="utf-8")
            trigger_block = text.partition("permissions:")[0]
            self.assertIn("workflow_dispatch:", trigger_block)
            self.assertNotIn("push:", trigger_block)
        reconcile = RECONCILE.read_text(encoding="utf-8")
        self.assertIn("environment: production", reconcile)
        self.assertIn("VERCEL_RECONCILE_NOT_CURRENT_MAIN", reconcile)

    def test_production_has_single_writer(self):
        import json

        config = json.loads(VERCEL_CONFIG.read_text(encoding="utf-8"))
        deployment_enabled = config["git"]["deploymentEnabled"]
        self.assertIs(
            deployment_enabled.get("main"),
            False,
            msg="Vercel Git Integration must not auto-deploy main; governed REST reconcile is the single production writer",
        )


if __name__ == "__main__":
    unittest.main()
