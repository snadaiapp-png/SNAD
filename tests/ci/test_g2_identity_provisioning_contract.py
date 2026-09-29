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
            "ROLE.READ",
            "ROLE.WRITE",
            "CAPABILITY.READ",
            "USER.GRANT_ROLE",
        ):
            self.assertIn(capability, text)
            self.assertIn(f"Admin has {capability} capability.", text)

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

    def test_direct_admin_password_payload_is_not_used(self):
        text = self.text
        self.assertNotIn("admin-reset-password", text)
        self.assertNotIn("newPassword", text)
        self.assertIn("admin-initialize-credential", text)
        self.assertIn("change-credential", text)


if __name__ == "__main__":
    unittest.main(verbosity=2)
