#!/usr/bin/env python3
import subprocess
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
WORKFLOW = ROOT / ".github/workflows/workflow-y2-vercel-production-certification.yml"
PROVISIONER = ROOT / "scripts/production/ensure-workflow-runtime-tenant.sh"
SCRIPT = ROOT / "scripts/production/verify-workflow-vercel-production-runtime.sh"
HARNESS = ROOT / "scripts/production/tests/verify-workflow-vercel-production-runtime.test.sh"


class WorkflowY2VercelProductionCertificationTest(unittest.TestCase):
    def test_workflow_contract_uses_dedicated_runtime_identity_and_fail_closed_provisioning(self):
        text = WORKFLOW.read_text()
        required = [
            "workflow_dispatch:",
            "backend_release_sha:",
            "run-vercel-workflow-certification",
            "environment: production",
            "WEB_PRODUCTION_BASE_URL",
            "secrets.SANAD_ADMIN_EMAIL",
            "secrets.SANAD_ADMIN_PASSWORD",
            "secrets.IDENTITY_B_EMAIL",
            "secrets.IDENTITY_B_PASSWORD",
            "secrets.IDENTITY_B_TENANT_ID",
            "ensure-workflow-runtime-tenant.sh",
            'git diff --quiet "$BACKEND_RELEASE_SHA" HEAD -- apps/sanad-platform',
            "verify-workflow-vercel-production-runtime.sh",
            "workflow-vercel-production-certification-${{ github.run_id }}",
        ]
        for needle in required:
            self.assertIn(needle, text, f"missing Vercel certification contract: {needle}")

        self.assertNotIn(
            "WORKFLOW_RUNTIME_ADMIN_EMAIL: ${{ secrets.SANAD_ADMIN_EMAIL }}",
            text,
            "Workflow runtime certification must not reuse the Control Plane admin identity",
        )
        self.assertNotIn(
            "WORKFLOW_RUNTIME_ADMIN_PASSWORD: ${{ secrets.SANAD_ADMIN_PASSWORD }}",
            text,
            "Workflow runtime certification must not reuse the Control Plane admin credential",
        )
        self.assertIn(
            "WORKFLOW_RUNTIME_TENANT_ID: ${{ secrets.IDENTITY_B_TENANT_ID }}",
            text,
        )

        forbidden = ["flyway repair", "docker ", "testcontainers", "psql "]
        lowered = text.lower()
        for needle in forbidden:
            self.assertNotIn(needle, lowered, f"forbidden certification behavior: {needle}")
        self.assertNotIn("-X POST", text, "workflow must not mutate Render via API")

    def test_runtime_provisioner_is_create_only_and_refuses_terminal_resurrection(self):
        text = PROVISIONER.read_text()
        required = [
            "/api/platform/api/v1/executive/plans",
            "/api/platform/api/v1/executive/plans/$plan_id/modules",
            "/api/platform/api/v1/executive/subscriptions?tenantId=$WORKFLOW_RUNTIME_TENANT_ID",
            "/api/platform/api/v1/executive/subscriptions",
            'trialDays:0',
            '.moduleCode == "WORKFLOW" and .moduleEnabled == true',
            "automatic resurrection is forbidden",
            "/api/platform/api/v1/workflows/catalog/modules",
            'subscriptionAction:$action',
        ]
        for needle in required:
            self.assertIn(needle, text, f"missing runtime provisioning contract: {needle}")

        # The provisioning repair may create the first subscription only. It may
        # not mutate shared plan entitlements, resume/renew terminal history, or
        # alter/cancel an existing subscription.
        forbidden = [
            "request PUT",
            "request PATCH",
            "request DELETE",
            "/change-plan",
            "/resume",
            "/renew",
            "/cancel",
        ]
        for needle in forbidden:
            self.assertNotIn(needle, text, f"forbidden runtime provisioning mutation: {needle}")

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
        subprocess.run(["bash", "-n", str(PROVISIONER)], check=True)
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
