#!/usr/bin/env python3
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
WORKFLOW = ROOT / ".github/workflows/workflow-y2-production-entitlement-remediation.yml"
SCRIPT = ROOT / "scripts/production/remediate-workflow-production-entitlement.sh"


class WorkflowY2ProductionEntitlementRemediationTest(unittest.TestCase):
    def test_remediation_is_narrow_guarded_and_audited(self):
        workflow = WORKFLOW.read_text()
        required_workflow = [
            "name: Workflow Y2 Production Entitlement Remediation",
            "branches:\n      - main",
            '"scripts/production/remediate-workflow-production-entitlement.sh"',
            "environment: production",
            "SANAD_CONTROL_PLANE_TENANT_ID",
            "secrets.SANAD_ADMIN_EMAIL",
            "secrets.SANAD_ADMIN_PASSWORD",
            "remediate-workflow-production-entitlement.sh",
        ]
        for needle in required_workflow:
            self.assertIn(needle, workflow, f"missing remediation workflow guard: {needle}")

        script = SCRIPT.read_text()
        required_script = [
            "/api/platform/api/v1/auth/login",
            "/api/platform/api/v1/auth/me",
            "/api/platform/api/v1/executive/modules",
            "/api/platform/api/v1/executive/subscriptions/v2",
            "/api/platform/api/v1/executive/plans/",
            "/modules/WORKFLOW",
            "/api/platform/api/v1/executive/tenants/",
            "/entitlements",
            "/api/platform/api/v1/workflows/catalog/modules",
            '"EXECUTIVE_VIEW"',
            '"EXECUTIVE_MANAGE"',
            '"WORKFLOW.VIEW"',
        ]
        for needle in required_script:
            self.assertIn(needle, script, f"missing remediation safety contract: {needle}")

        lowered = script.lower()
        for forbidden in ["psql ", "delete from ", "update plan_module_entitlements", "insert into plan_module_entitlements"]:
            self.assertNotIn(forbidden, lowered, f"forbidden direct production mutation: {forbidden}")


if __name__ == "__main__":
    unittest.main()
