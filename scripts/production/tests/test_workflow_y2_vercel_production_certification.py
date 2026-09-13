#!/usr/bin/env python3
import subprocess
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
WORKFLOW = ROOT / ".github/workflows/workflow-y2-vercel-production-certification.yml"
SCRIPT = ROOT / "scripts/production/verify-workflow-vercel-production-runtime.sh"
HARNESS = ROOT / "scripts/production/tests/verify-workflow-vercel-production-runtime.test.sh"

class WorkflowY2VercelProductionCertificationTest(unittest.TestCase):
    def test_workflow_contract_is_fail_closed_and_read_only(self):
        text = WORKFLOW.read_text()
        required = [
            "workflow_dispatch:",
            "backend_release_sha:",
            "run-vercel-workflow-certification",
            "environment: production",
            "WEB_PRODUCTION_BASE_URL",
            "secrets.SANAD_ADMIN_EMAIL",
            "secrets.SANAD_ADMIN_PASSWORD",
            "secrets.RENDER_API_KEY",
            "secrets.RENDER_SERVICE_ID",
            'git diff --quiet "$BACKEND_RELEASE_SHA" HEAD -- apps/sanad-platform',
            "verify-workflow-vercel-production-runtime.sh",
            "workflow-vercel-production-certification-${{ github.run_id }}",
        ]
        for needle in required:
            self.assertIn(needle, text, f"missing Vercel certification contract: {needle}")

        forbidden = ["flyway repair", "docker ", "testcontainers", "psql "]
        lowered = text.lower()
        for needle in forbidden:
            self.assertNotIn(needle, lowered, f"forbidden certification behavior: {needle}")
        self.assertNotIn("-X POST", text, "certification workflow must not mutate Render via API")
        self.assertNotIn("--request POST", text, "certification workflow must not mutate Render via API")

    def test_runtime_probe_uses_vercel_bff_and_exact_release_identity(self):
        text = SCRIPT.read_text()
        required = [
            "/api/system/release",
            "/api/system/backend-status",
            "/workflow",
            "/api/platform/api/v1/auth/login",
            "/api/platform/api/v1/auth/me",
            "/api/platform/api/v1/workflows/definitions",
            "/api/platform/api/v1/workflows/catalog/modules",
            "WORKFLOW_MODULE_NOT_ENTITLED",
            "WORKFLOW_VERCEL_BACKEND_STATUS_ATTEMPTS",
            "/api/platform/api/v1/workflows/instances",
            "/api/platform/api/v1/workflows/monitoring/health",
            "/api/platform/api/v1/workflows/notifications",
            "/validate",
            "/simulate",
            'transport:"vercel-bff"',
        ]
        for needle in required:
            self.assertIn(needle, text, f"missing runtime probe contract: {needle}")

    def test_shell_syntax_and_behavioral_harness(self):
        subprocess.run(["bash", "-n", str(SCRIPT)], check=True)
        result = subprocess.run(
            ["bash", str(HARNESS)],
            check=False,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
        )
        self.assertEqual(0, result.returncode, result.stdout)
        self.assertIn("verify-workflow-vercel-production-runtime tests: PASS", result.stdout)

if __name__ == "__main__":
    unittest.main()
