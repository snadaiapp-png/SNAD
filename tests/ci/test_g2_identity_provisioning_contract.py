#!/usr/bin/env python3
"""Fail-closed contract for G2 production identity provisioning API usage."""

from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github" / "workflows" / "g2-production-identity-provisioning.yml"


class G2IdentityProvisioningContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = WORKFLOW.read_text(encoding="utf-8")

    def test_role_capability_flow_uses_canonical_access_api_contract(self):
        text = self.text
        self.assertNotIn("/roles/$role_id/capabilities", text)
        self.assertNotIn("$API/roles?tenantId=$G2_TENANT_ID", text)
        self.assertIn("/access/roles?tenantId=$G2_TENANT_ID", text)
        self.assertIn("/access/roles/$role_id/access-items?tenantId=$G2_TENANT_ID", text)
        self.assertIn("capabilityCode", text)

    def test_direct_admin_password_payload_is_not_used(self):
        text = self.text
        self.assertNotIn("admin-reset-password", text)
        self.assertNotIn("newPassword", text)
        self.assertIn("admin-initialize-credential", text)
        self.assertIn("change-credential", text)

    def test_hr_bootstrap_uses_only_governed_api_surfaces(self):
        text = self.text
        self.assertNotIn("psql", text)
        self.assertNotIn("PRODUCTION_DATABASE_", text)
        self.assertIn("/api/v1/organizations?tenantId=$G2_TENANT_ID", text)
        self.assertIn("/legal-entities/eligible?tenantId=$G2_TENANT_ID&effectiveDate=$G2_EFFECTIVE_DATE", text)
        self.assertIn("/api/v2/hr/people", text)
        self.assertIn("/user-link", text)
        self.assertIn("/api/v2/hr/employments", text)
        self.assertIn("/api/v2/hr/assignments", text)
        self.assertIn("Idempotency-Key", text)

    def test_hr_bootstrap_fails_closed_on_ambiguous_employer_context(self):
        text = self.text
        self.assertIn("ACTIVE_ORG_COUNT", text)
        self.assertIn('if [ "$ACTIVE_ORG_COUNT" != "1" ]', text)
        self.assertIn("ELIGIBLE_LE_COUNT", text)
        self.assertIn('if [ "$ELIGIBLE_LE_COUNT" != "1" ]', text)
        self.assertIn("HR_CONTEXT_AMBIGUOUS", text)

    def test_hr_bootstrap_builds_canonical_manager_relationship(self):
        text = self.text
        self.assertIn("MGR_ASSIGNMENT_ID", text)
        self.assertIn("reportsToAssignmentId", text)
        self.assertIn("$MGR_ASSIGNMENT_ID", text)
        self.assertIn("EMP_ASSIGNMENT_ID", text)
        self.assertIn("HR_ASSIGNMENT_ID", text)


if __name__ == "__main__":
    unittest.main(verbosity=2)
