"""Guard Users production release against unsafe Flyway/backend cutover regressions.

This tests source logic only. It NEVER executes a production workflow.
"""
import pathlib
import re
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/users-production-release.yml"


class UsersReleaseG1GuardTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = WORKFLOW.read_text(encoding="utf-8")

    def test_runtime_flyway_disabled_and_preflight_before_mutation(self):
        s = self.source
        self.assertIn('test "$FLYWAY_ENABLED_LIVE" = "false"', s)
        self.assertNotIn('test "$FLYWAY_ENABLED_LIVE" = "true"', s)
        self.assertLess(
            s.index("- name: Preflight backend Flyway-disabled topology before Render mutation"),
            s.index("- name: Deploy exact Users image to Render"),
        )
        self.assertLess(
            s.index("- name: Preflight production Flyway head and auditor privileges before Render mutation"),
            s.index("- name: Deploy exact Users image to Render"),
        )
        self.assertIn("bash scripts/production/verify-flyway.sh", s)

    def test_manual_dispatch_inputs_are_data_not_shell_source(self):
        s = self.source
        self.assertIn("GOVERNANCE_SHA_INPUT: ${{ inputs.governance_sha }}", s)
        self.assertIn("DEPLOYMENT_IMAGE_SHA_INPUT: ${{ inputs.deployment_image_sha }}", s)
        # Workflow expressions inside shell blocks are risky when values are user-controlled.
        self.assertNotRegex(s, r"(?m)^\s+(?:GOVERNANCE_SHA|DEPLOYMENT_IMAGE_SHA)='\$\{\{\s*inputs\.")
        self.assertNotRegex(s, r"--arg\s+\w+\s+'\$\{\{\s*inputs\.")
        self.assertIn('GOVERNANCE_SHA="$GOVERNANCE_SHA_INPUT"', s)
        self.assertIn('DEPLOYMENT_IMAGE_SHA="$DEPLOYMENT_IMAGE_SHA_INPUT"', s)

    def test_independent_audit_secrets_remain_required(self):
        s = self.source
        for key in ("PROD_RELEASE_AUDIT_URL", "PROD_RELEASE_AUDIT_USER", "PROD_RELEASE_AUDIT_PASSWORD"):
            self.assertIn(f"secrets.{key}", s)
        self.assertIn("AUDIT_DATABASE_URL AUDIT_DATABASE_USERNAME AUDIT_DATABASE_PASSWORD", s)
        self.assertIn('test "$FLYWAY_ENABLED_LIVE" = "false"', s)

    def test_auto_rollback_is_disabled_by_default(self):
        s = self.source
        body = s.split("rollback_on_failure:", 1)[1].split("permissions:", 1)[0]
        self.assertRegex(body, r"(?m)^\s+default:\s+false\s*$")
        self.assertNotRegex(body, r"(?m)^\s+default:\s+true\s*$")

    def test_render_mutations_remain_after_security_preflight(self):
        s = self.source
        deployment = s.index("- name: Deploy exact Users image to Render")
        for marker in (
            "- name: Validate protected Users release inputs",
            "- name: Verify governance SHA is current main head",
            "- name: Verify exact immutable image exists without Docker",
            "- name: Validate Render runtime configuration",
            "- name: Preflight backend Flyway-disabled topology before Render mutation",
            "- name: Preflight production Flyway head and auditor privileges before Render mutation",
        ):
            self.assertLess(s.index(marker), deployment, marker)


if __name__ == "__main__":
    unittest.main()
