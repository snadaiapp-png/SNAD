#!/usr/bin/env python3
"""Fail-closed contracts for Task 7 G2 production identity + canonical HR provisioning."""

from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
IDENTITY_WORKFLOW = ROOT / ".github" / "workflows" / "g2-production-identity-provisioning.yml"
HR_BOOTSTRAP_WORKFLOW = ROOT / ".github" / "workflows" / "g2-production-canonical-hr-bootstrap.yml"
HR_BOOTSTRAP_SCRIPT = ROOT / "scripts" / "production" / "bootstrap-g2-canonical-hr.sh"


class G2IdentityProvisioningContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.identity = IDENTITY_WORKFLOW.read_text(encoding="utf-8")
        cls.bootstrap_workflow = HR_BOOTSTRAP_WORKFLOW.read_text(encoding="utf-8")
        cls.bootstrap = HR_BOOTSTRAP_SCRIPT.read_text(encoding="utf-8")

    def test_role_capability_flow_uses_canonical_access_api_contract(self):
        text = self.identity
        self.assertNotIn("/roles/$role_id/capabilities", text)
        self.assertNotIn("$API/roles?tenantId=$G2_TENANT_ID", text)
        self.assertIn("/access/roles?tenantId=$G2_TENANT_ID", text)
        self.assertIn("/access/roles/$role_id/access-items?tenantId=$G2_TENANT_ID", text)
        self.assertIn("capabilityCode", text)

    def test_direct_admin_password_payload_is_not_used(self):
        text = self.identity
        self.assertNotIn("admin-reset-password", text)
        self.assertNotIn("newPassword", text)
        self.assertIn("admin-initialize-credential", text)
        self.assertIn("change-credential", text)

    def test_hr_bootstrap_uses_only_governed_api_surfaces(self):
        text = self.bootstrap_workflow + self.bootstrap
        self.assertNotIn("psql", text)
        self.assertNotIn("PRODUCTION_DATABASE_", text)
        self.assertIn("API_V1=", self.bootstrap)
        self.assertIn("HR_API=", self.bootstrap)
        self.assertIn("$API_V1/organizations?tenantId=$G2_TENANT_ID", self.bootstrap)
        self.assertIn("/legal-entities/eligible?tenantId=$G2_TENANT_ID&effectiveDate=$G2_EFFECTIVE_DATE", self.bootstrap)
        self.assertIn('"$HR_API/people"', self.bootstrap)
        self.assertIn("/user-link", self.bootstrap)
        self.assertIn('"$HR_API/employments"', self.bootstrap)
        self.assertIn('"$HR_API/assignments"', self.bootstrap)
        self.assertIn("Idempotency-Key", self.bootstrap)

    def test_hr_bootstrap_fails_closed_on_ambiguous_employer_context(self):
        text = self.bootstrap
        self.assertIn("ACTIVE_ORG_COUNT", text)
        self.assertIn('if [ "$ACTIVE_ORG_COUNT" != "1" ]', text)
        self.assertIn("ELIGIBLE_LE_COUNT", text)
        self.assertIn('if [ "$ELIGIBLE_LE_COUNT" != "1" ]', text)
        self.assertIn("HR_CONTEXT_AMBIGUOUS", text)

    def test_hr_bootstrap_builds_canonical_manager_relationship(self):
        text = self.bootstrap
        self.assertIn("MGR_ASSIGNMENT_ID", text)
        self.assertIn("reportsToAssignmentId", text)
        self.assertIn('"$MGR_ASSIGNMENT_ID"', text)
        self.assertIn("EMP_ASSIGNMENT_ID", text)
        self.assertIn("HR_ASSIGNMENT_ID", text)
        self.assertIn(".reportsToAssignmentId", text)

    def test_bootstrap_preflight_temporarily_deploys_exact_sha_and_restores_previous_image(self):
        text = self.bootstrap_workflow
        self.assertIn("Verify exact current main SHA", text)
        self.assertIn("docker manifest inspect", text)
        self.assertIn("Deploy exact candidate image temporarily", text)
        self.assertIn("Bootstrap canonical User Person Employment Assignment graph", text)
        self.assertIn("Restore previous live image", text)
        self.assertIn("Verify previous image restored", text)
        self.assertIn("directDatabaseMutation:false", text)


if __name__ == "__main__":
    unittest.main(verbosity=2)
