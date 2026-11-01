#!/usr/bin/env python3
"""Fail-closed contract for G2 production identity provisioning API usage."""

from pathlib import Path
import unittest
import os
import subprocess
import textwrap

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
        # Execute the real capability loop. Dynamic shell output need not
        # exist literally in the workflow, and every missing grant must fail.
        text = self.text
        start = text.index('          for capability in ')
        end_marker = '          echo "ADMIN_CAPABILITY_PREFLIGHT=PASS"'
        end = text.index(end_marker, start) + len(end_marker)
        script = 'set -euo pipefail\n' + textwrap.dedent(text[start:end])
        required = (
            'USER.READ', 'USER.CREATE', 'USER.WRITE', 'ROLE.READ', 'ROLE.WRITE',
            'CAPABILITY.READ', 'USER.GRANT_ROLE', 'ORGANIZATION.READ',
            'ORGANIZATION.WRITE', 'HRM.EMPLOYEE.VIEW', 'HRM.EMPLOYEE.CREATE',
            'HRM.EMPLOYEE.UPDATE', 'HRM.USER_LINK.MANAGE',
            'HRM.ASSIGNMENT.VIEW', 'HRM.ASSIGNMENT.MANAGE',
        )
        for missing in (None, *required):
            with self.subTest(missing=missing):
                env = dict(os.environ, CAPABILITIES='\n'.join(c for c in required if c != missing))
                result = subprocess.run(['bash', '-c', script], env=env, text=True, capture_output=True)
                self.assertEqual(result.returncode, 0 if missing is None else 1)
                if missing is None:
                    self.assertIn('ADMIN_CAPABILITY_PREFLIGHT=PASS', result.stdout)
                else:
                    self.assertIn('ADMIN_CAPABILITY_PREFLIGHT=FAIL', result.stdout)
                    self.assertNotIn('ADMIN_CAPABILITY_PREFLIGHT=PASS', result.stdout)

    def test_canonical_hr_graph_is_provisioned_before_scope_verification(self):
        text = self.text
        self.assertIn("uses: actions/checkout@v4", text)
        self.assertIn("bash scripts/g2/provision-canonical-hr.sh", text)
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
