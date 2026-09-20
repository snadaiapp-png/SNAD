#!/usr/bin/env python3
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]
WORKFLOW = ROOT / ".github/workflows/workflow-production-3user-final-gate.yml"
JOURNEY = ROOT / "scripts/production/verify-workflow-production-3user-journey.sh"
VISUAL = ROOT / "scripts/production/verify-workflow-production-visual.mjs"
BOOTSTRAP = ROOT / "scripts/production/bootstrap-workflow-production-qa-subscription.sh"
BOOTSTRAP_WORKFLOW = ROOT / ".github/workflows/workflow-production-qa-subscription-bootstrap.yml"

class WorkflowProduction3UserFinalGateContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = WORKFLOW.read_text()
        cls.journey = JOURNEY.read_text()
        cls.visual = VISUAL.read_text()
        cls.bootstrap = BOOTSTRAP.read_text()
        cls.bootstrap_workflow = BOOTSTRAP_WORKFLOW.read_text()

    def test_dedicated_qa_secrets_are_required(self):
        for name in (
            "AUTH_SMOKE_TENANT_B_ID",
            "AUTH_SMOKE_TENANT_B_EMAIL",
            "AUTH_SMOKE_TENANT_B_PASSWORD",
            "PROD_QA_TENANT_ID",
            "PROD_QA_ADMIN_EMAIL",
            "PROD_QA_ADMIN_PASSWORD",
        ):
            self.assertIn(name, self.workflow + self.journey)

    def test_existing_starter_catalog_only(self):
        self.assertIn('select(.code=="STARTER"', self.journey)
        self.assertNotIn("WORKFLOW_PROD_QA", self.journey)
        self.assertNotIn("qaPlanCreate", self.journey)
        self.assertNotIn("qaPlanVersionCreate", self.journey)
        self.assertNotIn("plans/$PLAN_ID/modules/WORKFLOW", self.journey)

    def test_no_cross_tenant_query_parameter(self):
        self.assertIn("/api/platform/api/v1/executive/subscriptions/v2?page=$page&size=100", self.journey)
        self.assertNotIn("executive/subscriptions?tenantId=$PROD_QA_TENANT_ID", self.journey)
        self.assertNotIn("subscriptions/v2?tenantId=$PROD_QA_TENANT_ID", self.journey)

    def test_final_gate_does_not_create_subscription_or_catalog(self):
        self.assertNotIn("request POST '/api/platform/api/v1/executive/subscriptions'", self.journey)
        self.assertNotIn("request POST '/api/platform/api/v1/executive/plans'", self.journey)
        self.assertNotIn("trialDays:0", self.journey)
        self.assertIn("requires exactly one governed ACTIVE subscription", self.journey)

    def test_no_direct_database_write_path(self):
        lowered = self.journey.lower()
        for token in ("psql ", "production_database_url", "production_database_password", "supabase"):
            self.assertNotIn(token, lowered)

    def test_stable_three_user_identities_are_idempotent(self):
        self.assertIn("workflow-prod-qa", self.journey)
        self.assertIn("@qa.snad.invalid", self.journey)
        self.assertIn('request GET "/api/platform/api/v1/users?tenantId=$PROD_QA_TENANT_ID"', self.journey)
        self.assertIn("created only when absent", self.journey)

    def test_visual_proof_uses_qa_identity_not_control_plane(self):
        self.assertIn("PROD_QA_ADMIN_EMAIL", self.visual)
        self.assertIn("PROD_QA_ADMIN_PASSWORD", self.visual)
        self.assertIn("PROD_QA_TENANT_ID", self.visual)
        self.assertIn('qaTenantBinding:"PASS"', self.visual)
        self.assertNotIn("PROD_ADMIN_EMAIL", self.visual)
        self.assertNotIn("PROD_ADMIN_PASSWORD", self.visual)

    def test_bootstrap_is_manual_exact_main_and_non_financial(self):
        self.assertIn("workflow_dispatch:", self.bootstrap_workflow)
        self.assertNotIn("schedule:", self.bootstrap_workflow)
        self.assertNotIn("push:", self.bootstrap_workflow)
        self.assertIn("bootstrap-workflow-production-qa", self.bootstrap_workflow)
        self.assertIn("EXPECTED_MAIN_SHA", self.bootstrap_workflow)
        self.assertIn('select(.code=="STARTER"', self.bootstrap)
        self.assertNotIn("WORKFLOW_PROD_QA", self.bootstrap)
        self.assertNotIn("request POST '/api/platform/api/v1/executive/plans'", self.bootstrap)
        self.assertNotIn("plans/$PLAN_ID/modules/WORKFLOW", self.bootstrap)
        self.assertNotIn("trialDays:0", self.bootstrap)
        self.assertIn('.status=="TRIALING"', self.bootstrap)
        self.assertIn('/subscriptions/$SUB_ID/provision', self.bootstrap)
        self.assertNotIn("executive/subscriptions?tenantId=$PROD_QA_TENANT_ID", self.bootstrap)
        self.assertIn("/api/platform/api/v1/executive/subscriptions/v2?page=$page&size=100", self.bootstrap)

    def test_bootstrap_has_no_direct_database_or_paid_invoice_path(self):
        lowered = self.bootstrap.lower()
        for token in ("psql ", "production_database_url", "production_database_password", "supabase", "/renew"):
            self.assertNotIn(token, lowered)
        self.assertIn('paidInvoicePath:"NOT_USED"', self.bootstrap)

    def test_final_gate_runs_contract_test_before_journey(self):
        contract = "python3 scripts/production/tests/test_workflow_production_3user_final_gate.py"
        journey = "bash scripts/production/verify-workflow-production-3user-journey.sh"
        self.assertIn(contract, self.workflow)
        self.assertLess(self.workflow.index(contract), self.workflow.index(journey))

if __name__ == "__main__":
    unittest.main()
