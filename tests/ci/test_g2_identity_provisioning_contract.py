#!/usr/bin/env python3
"""Fail-closed contracts for Task 7 G2 identity and canonical HR release provisioning."""

from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
IDENTITY_WORKFLOW = ROOT / ".github" / "workflows" / "g2-production-identity-provisioning.yml"
PRODUCTION_RELEASE = ROOT / ".github" / "workflows" / "production-release.yml"
HR_BOOTSTRAP_SCRIPT = ROOT / "scripts" / "production" / "bootstrap-g2-canonical-hr.sh"
RELEASE_BOOTSTRAP_SCRIPT = ROOT / "scripts" / "production" / "bootstrap-g2-release-hr.sh"


class G2IdentityProvisioningContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.identity = IDENTITY_WORKFLOW.read_text(encoding="utf-8")
        cls.release = PRODUCTION_RELEASE.read_text(encoding="utf-8")
        cls.bootstrap = HR_BOOTSTRAP_SCRIPT.read_text(encoding="utf-8")
        cls.release_bootstrap = RELEASE_BOOTSTRAP_SCRIPT.read_text(encoding="utf-8")

    def test_role_capability_flow_uses_canonical_access_api_contract(self):
        text = self.identity
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
        text = self.identity
        self.assertNotIn("admin-reset-password", text)
        self.assertNotIn("newPassword", text)
        self.assertIn("admin-initialize-credential", text)
        self.assertIn("change-credential", text)

    def test_hr_bootstrap_uses_only_governed_api_surfaces(self):
        text = self.release + self.release_bootstrap + self.bootstrap
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

    def test_release_bootstrap_requires_existing_unique_g2_users(self):
        text = self.release_bootstrap
        self.assertIn("expected exactly one existing $label G2 user", text)
        self.assertIn("HR_CONTEXT_AMBIGUOUS", text)
        self.assertIn("bootstrap-g2-canonical-hr.sh", text)
        self.assertNotIn("admin-initialize-credential", text)
        self.assertNotIn("change-credential", text)

    def test_canonical_hr_bootstrap_runs_inside_exact_sha_release_before_g2_visual(self):
        text = self.release
        bootstrap = text.index("Bootstrap canonical G2 HR identity graph")
        g2_visual = text.index("Verify authenticated G2 production visual smoke")
        deploy = text.index("Deploy exact image to Render")
        readiness = text.index("Wait for production readiness")
        self.assertLess(deploy, readiness)
        self.assertLess(readiness, bootstrap)
        self.assertLess(bootstrap, g2_visual)
        self.assertIn("bash scripts/production/bootstrap-g2-release-hr.sh", text)
        self.assertIn("g2-canonical-hr-bootstrap-evidence.json", text)
        self.assertIn('g2CanonicalHr:"PASS"', text)

    def test_no_separate_temporary_production_bootstrap_workflow_exists(self):
        self.assertFalse((ROOT / ".github" / "workflows" / "g2-production-canonical-hr-bootstrap.yml").exists())


if __name__ == "__main__":
    unittest.main(verbosity=2)
