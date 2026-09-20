#!/usr/bin/env python3
"""Contract tests for the production 3-user final gate credential boundary."""

from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github" / "workflows" / "workflow-production-3user-final-gate.yml"
JOURNEY = ROOT / "scripts" / "production" / "verify-workflow-production-3user-journey.sh"
BOOTSTRAP = ROOT / "scripts" / "production" / "bootstrap-workflow-production-qa-subscription.sh"


class WorkflowProduction3UserFinalGateContractTest(unittest.TestCase):
    def test_control_plane_credentials_are_canonical(self):
        text = WORKFLOW.read_text(encoding="utf-8")

        self.assertIn(
            "PROD_ADMIN_EMAIL: ${{ secrets.CONTROL_PLANE_ADMIN_EMAIL }}",
            text,
        )
        self.assertIn(
            "PROD_ADMIN_PASSWORD: ${{ secrets.CONTROL_PLANE_ADMIN_PASSWORD }}",
            text,
        )
        self.assertNotIn("PROD_ADMIN_EMAIL: ${{ secrets.SANAD_ADMIN_EMAIL }}", text)
        self.assertNotIn("PROD_ADMIN_PASSWORD: ${{ secrets.SANAD_ADMIN_PASSWORD }}", text)

        # Tenant B remains the only credential source for tenant-scoped
        # Workflow/user/UI proof.
        self.assertIn(
            "PROD_QA_ADMIN_EMAIL: ${{ secrets.AUTH_SMOKE_TENANT_B_EMAIL }}",
            text,
        )
        self.assertIn(
            "PROD_QA_ADMIN_PASSWORD: ${{ secrets.AUTH_SMOKE_TENANT_B_PASSWORD }}",
            text,
        )


    def test_control_plane_subscription_read_avoids_cross_tenant_query_parameter(self):
        text = JOURNEY.read_text(encoding="utf-8")

        self.assertNotIn(
            "/api/platform/api/v1/executive/subscriptions?tenantId=$PROD_QA_TENANT_ID",
            text,
        )
        self.assertIn(
            "/api/platform/api/v1/executive/subscriptions/v2?page=$page&size=100",
            text,
        )
        self.assertIn(
            ".content[]? | select(.tenantId==$tenant)",
            text,
        )
        self.assertIn(
            "Executive paginated read; Tenant B filtered locally",
            text,
        )


    def test_final_gate_uses_existing_starter_and_governed_bootstrap(self):
        workflow = WORKFLOW.read_text(encoding="utf-8")
        journey = JOURNEY.read_text(encoding="utf-8")
        bootstrap = BOOTSTRAP.read_text(encoding="utf-8")

        self.assertIn("PROD_QA_PLAN_CODE: STARTER", workflow)
        self.assertIn("Bootstrap governed STARTER QA subscription", workflow)
        self.assertIn('select(.code=="STARTER"', journey)
        self.assertIn('select(.code=="STARTER"', bootstrap)
        self.assertNotIn("WORKFLOW_PROD_QA", journey)
        self.assertNotIn("request POST '/api/platform/api/v1/executive/plans'", bootstrap)
        self.assertNotIn("plans/$PLAN_ID/modules/WORKFLOW", bootstrap)

    def test_bootstrap_is_non_financial_and_canonical(self):
        bootstrap = BOOTSTRAP.read_text(encoding="utf-8")

        self.assertIn("trialDays:$trialDays", bootstrap)
        self.assertIn("/api/platform/api/v1/executive/subscriptions/$SUB_ID/provision", bootstrap)
        self.assertIn('paidInvoicePath:"NOT_USED"', bootstrap)
        self.assertNotIn("trialDays:0", bootstrap)


if __name__ == "__main__":
    unittest.main(verbosity=2)
