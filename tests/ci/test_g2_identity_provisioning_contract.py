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

    def test_admin_preflight_covers_every_rbac_mutation_dependency(self):
        text = self.text
        for capability in (
            "USER.READ",
            "USER.CREATE",
            "USER.WRITE",
            "ROLE.READ",
            "ROLE.WRITE",
            "CAPABILITY.READ",
            "USER.GRANT_ROLE",
        ):
            self.assertIn(capability, text)
            self.assertIn(f"Admin has {capability} capability.", text)

    def test_canonical_hr_graph_is_provisioned_before_scope_verification(self):
        text = self.text
        self.assertIn("uses: actions/checkout@v4", text)
        self.assertIn("bash scripts/g2/provision-canonical-hr.sh", text)
        script = (ROOT / "scripts/g2/provision-canonical-hr.sh").read_text(encoding="utf-8")
        self.assertIn("/legal-entity?effectiveDate=$G2_EMPLOYMENT_START_DATE", script)
        self.assertIn("G2_EMPLOYER_CONTEXT=RESOLVED_FROM_CANONICAL_LINK", script)
        self.assertNotIn("uuid.uuid5", script)
        self.assertNotIn("auth-smoke-legal-entity", script)
        for capability in (
            "ORGANIZATION.READ",
            "HRM.EMPLOYEE.VIEW",
            "HRM.EMPLOYEE.CREATE",
            "HRM.EMPLOYEE.UPDATE",
            "HRM.USER_LINK.MANAGE",
            "HRM.ASSIGNMENT.VIEW",
            "HRM.ASSIGNMENT.MANAGE",
        ):
            self.assertIn(capability, text)
        self.assertLess(
            text.index("bash scripts/g2/provision-canonical-hr.sh"),
            text.index("Verify authentication tenant binding and RBAC"),
        )

    def test_role_capability_mutation_is_fail_closed(self):
        text = self.text
        self.assertIn("ASSIGN_CAP_HTTP_STATUS=", text)
        self.assertIn("ASSIGN_CAP_RESULT=PASS", text)
        self.assertIn("POST_ASSIGN_VERIFY=PASS", text)
        self.assertIn("--fail-with-body", text)

    def test_user_role_grant_is_fail_closed(self):
        text = self.text
        self.assertIn("ROLE_GRANT_HTTP_STATUS=", text)
        self.assertIn("ROLE_GRANT_RESULT=PASS", text)
        self.assertIn("POST_ROLE_GRANT_VERIFY=PASS", text)

    def test_user_role_grant_must_be_active_before_it_counts_as_linked(self):
        text = self.text
        active_selector = '.[] | select(.roleId == $role and .status == "ACTIVE")'
        self.assertGreaterEqual(text.count(active_selector), 2)
        self.assertIn("ROLE_GRANT_STATUS=ACTIVE", text)

    def test_direct_admin_password_payload_is_not_used(self):
        text = self.text
        self.assertNotIn("admin-reset-password", text)
        self.assertNotIn("newPassword", text)
        self.assertIn("admin-initialize-credential", text)
        self.assertIn("change-credential", text)


if __name__ == "__main__":
    unittest.main(verbosity=2)
