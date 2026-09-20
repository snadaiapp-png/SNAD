#!/usr/bin/env python3
"""Unified fail-closed contract for SANAD Workflow production final closure."""

from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
FINAL_WF = ROOT / ".github" / "workflows" / "workflow-production-3user-final-gate.yml"
RECONCILE_WF = ROOT / ".github" / "workflows" / "workflow-production-qa-subscription-bootstrap-once.yml"
JOURNEY = ROOT / "scripts" / "production" / "verify-workflow-production-3user-journey.sh"
RECONCILE = ROOT / "scripts" / "production" / "bootstrap-workflow-production-qa-subscription.sh"
VISUAL = ROOT / "scripts" / "production" / "verify-workflow-production-visual.mjs"


class WorkflowFinalClosureUnifiedContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.final_wf = FINAL_WF.read_text(encoding="utf-8")
        cls.reconcile_wf = RECONCILE_WF.read_text(encoding="utf-8")
        cls.journey = JOURNEY.read_text(encoding="utf-8")
        cls.reconcile = RECONCILE.read_text(encoding="utf-8")
        cls.visual = VISUAL.read_text(encoding="utf-8")

    def test_control_plane_and_tenant_b_boundaries(self):
        self.assertIn("CONTROL_PLANE_ADMIN_EMAIL", self.final_wf)
        self.assertIn("CONTROL_PLANE_ADMIN_PASSWORD", self.final_wf)
        self.assertIn("AUTH_SMOKE_TENANT_B_EMAIL", self.final_wf)
        self.assertIn("AUTH_SMOKE_TENANT_B_PASSWORD", self.final_wf)
        self.assertNotIn("AUTH_SMOKE_TENANT_B_ID", self.final_wf)
        self.assertNotIn("AUTH_SMOKE_TENANT_B_ID", self.journey)
        self.assertIn("Control Plane token attempted non-Executive API", self.journey)
        self.assertIn("Tenant B token attempted Executive API", self.journey)
        self.assertIn("Control Plane token attempted non-Executive API", self.reconcile)
        self.assertIn("Tenant B token attempted Executive API", self.reconcile)

    def test_tenant_b_is_resolved_from_canonical_directory(self):
        self.assertIn("PROD_QA_TENANT_CODE", self.journey)
        self.assertIn("/api/platform/api/v1/executive/tenants/v2?search=", self.journey)
        self.assertIn('.code==$code and .status=="ACTIVE"', self.journey)
        self.assertIn("PROD_QA_TENANT_ID=$PROD_QA_TENANT_ID", self.journey)
        self.assertIn("workflow-prod-qa", self.final_wf)
        self.assertIn("workflow-prod-qa", self.reconcile_wf)

    def test_reconcile_is_application_native_and_cleanup_safe(self):
        forbidden = ("psql ", "DATABASE_URL", "jdbc:postgresql", "INSERT INTO ", "UPDATE tenants ")
        for token in forbidden:
            self.assertNotIn(token, self.reconcile)
        self.assertIn("SANAD_SECURITY_BOOTSTRAP_ENABLED", self.reconcile)
        self.assertIn("SANAD_SECURITY_BOOTSTRAP_TENANT_SUBDOMAIN", self.reconcile)
        self.assertIn("/api/platform/api/v1/auth/change-credential", self.reconcile)
        self.assertIn("emergency_disable", self.reconcile)
        self.assertIn("delete_render_var()", self.reconcile)
        self.assertIn("delete_render_var SANAD_SECURITY_BOOTSTRAP_TENANT_ID", self.reconcile)
        self.assertIn("delete_render_var SANAD_SECURITY_BOOTSTRAP_ADMIN_PASSWORD", self.reconcile)
        self.assertNotIn('set_render_var SANAD_SECURITY_BOOTSTRAP_TENANT_ID ""', self.reconcile)
        self.assertNotIn('set_render_var SANAD_SECURITY_BOOTSTRAP_ADMIN_PASSWORD ""', self.reconcile)
        self.assertIn("BOOTSTRAP_ARMED=false", self.reconcile)
        self.assertIn('bootstrapEnabledFinal:false', self.reconcile)
        self.assertIn('directDatabaseWrites:"NONE"', self.reconcile)

    def test_existing_starter_only_and_no_catalog_mutation(self):
        self.assertNotIn("WORKFLOW_PROD_QA", self.journey)
        self.assertIn('.code=="STARTER" and .status=="ACTIVE"', self.journey)
        self.assertIn('.code=="STARTER" and .status=="ACTIVE"', self.reconcile)
        self.assertNotIn("request POST '/api/platform/api/v1/executive/plans'", self.journey)
        self.assertNotIn("request POST '/api/platform/api/v1/executive/plans'", self.reconcile)
        self.assertNotIn("/plans/$PLAN_ID/modules/WORKFLOW", self.journey)
        self.assertNotIn("/plans/$PLAN_ID/modules/WORKFLOW", self.reconcile)
        self.assertIn('catalogMutations:"NONE"', self.reconcile)
        self.assertIn('planMutations:"NONE"', self.reconcile)

    def test_reconcile_owns_subscription_creation_final_gate_is_verify_only(self):
        self.assertIn("request POST '/api/platform/api/v1/executive/subscriptions'", self.reconcile)
        self.assertIn("/api/platform/api/v1/executive/subscriptions/$SUB_ID/provision", self.reconcile)
        self.assertNotIn("request POST '/api/platform/api/v1/executive/subscriptions'", self.journey)
        self.assertIn("requires exactly one governed ACTIVE subscription", self.journey)
        self.assertIn("Existing governed ACTIVE STARTER subscription verified", self.journey)

    def test_validate_boundary_accepts_clean_tenant_state(self):
        self.assertNotIn("No definition exists for boundary check", self.journey)
        self.assertIn("validateBoundaryPrecondition", self.journey)
        self.assertIn("validateBoundary PASS", self.journey)
        self.assertIn('/api/platform/api/v1/workflows/definitions/$DEF_ID/validate', self.journey)

    def test_three_user_y2_and_immutability_contract(self):
        self.assertIn("for n in 1 2 3", self.journey)
        self.assertIn("3/3 QA-user-linked instances reached COMPLETED", self.journey)
        self.assertIn('.status=="COMPLETED" and .engineGeneration=="Y2"', self.journey)
        self.assertIn("definitionSimulate", self.journey)
        self.assertIn("definitionPublish", self.journey)
        self.assertIn('publicationState=="PUBLISHED" and .engineGeneration=="Y2"', self.journey)
        self.assertIn('[ "$status" = 409 ]', self.journey)
        self.assertIn("publishedImmutability PASS 'HTTP 409'", self.journey)

    def test_authenticated_desktop_mobile_visual_contract(self):
        self.assertIn("PROD_QA_ADMIN_EMAIL", self.visual)
        self.assertIn("PROD_QA_ADMIN_PASSWORD", self.visual)
        self.assertIn("PROD_QA_TENANT_ID", self.visual)
        self.assertNotIn("PROD_ADMIN_EMAIL", self.visual)
        lower = self.visual.lower()
        self.assertIn("desktop", lower)
        self.assertIn("mobile", lower)

    def test_one_time_reconcile_is_exact_parent_fail_closed(self):
        self.assertIn("EXPECTED_PARENT_SHA", self.reconcile_wf)
        self.assertIn("ONE_TIME_PARENT_GUARD", self.reconcile_wf)
        self.assertIn("[workflow-qa-bootstrap-once]", self.reconcile_wf)
        self.assertIn("IMAGE_REF", self.reconcile_wf)
        self.assertIn("WORKFLOW_QA_RECONCILE=PASS", self.reconcile_wf)
        self.assertIn("Direct database writes: NONE", self.reconcile_wf)
        self.assertIn("Plan/catalog/version mutations: NONE", self.reconcile_wf)

    def test_final_gate_records_complete_closure_evidence(self):
        for phrase in (
            "3/3 COMPLETED",
            "Published immutability: PASS (409)",
            "Desktop + mobile visual evidence: CAPTURED",
            "Direct database writes: NONE",
            "Control Plane operator: Executive APIs only",
        ):
            self.assertIn(phrase, self.final_wf)


if __name__ == "__main__":
    unittest.main(verbosity=2)
