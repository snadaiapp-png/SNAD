#!/usr/bin/env python3
"""Contract tests for the production 3-user final gate credential boundary."""

from pathlib import Path
import unittest
import json
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github" / "workflows" / "workflow-production-3user-final-gate.yml"
JOURNEY = ROOT / "scripts" / "production" / "verify-workflow-production-3user-journey.sh"


class WorkflowProduction3UserFinalGateContractTest(unittest.TestCase):
    def test_control_plane_credentials_are_canonical(self):
        text = WORKFLOW.read_text(encoding="utf-8")

        self.assertIn(
            "PROD_ADMIN_EMAIL: ${{ secrets.CONTROL_PLANE_ADMIN_EMAIL }}",
            text,
        )
        self.assertIn(
            "PROD_ADMIN_PASSWORD: ${{ secrets.CONTROL_PLANE_ADMIN_PASSWORD }}",
            text,
        )
        self.assertNotIn("PROD_ADMIN_EMAIL: ${{ secrets.SANAD_ADMIN_EMAIL }}", text)
        self.assertNotIn("PROD_ADMIN_PASSWORD: ${{ secrets.SANAD_ADMIN_PASSWORD }}", text)

        # Tenant B remains the only credential source for tenant-scoped
        # Workflow/user/UI proof.
        self.assertIn(
            "PROD_QA_ADMIN_EMAIL: ${{ secrets.AUTH_SMOKE_TENANT_B_EMAIL }}",
            text,
        )
        self.assertIn(
            "PROD_QA_ADMIN_PASSWORD: ${{ secrets.AUTH_SMOKE_TENANT_B_PASSWORD }}",
            text,
        )


    def test_control_plane_subscription_read_avoids_cross_tenant_query_parameter(self):
        text = JOURNEY.read_text(encoding="utf-8")

        self.assertNotIn(
            "/api/platform/api/v1/executive/subscriptions?tenantId=$PROD_QA_TENANT_ID",
            text,
        )
        self.assertIn(
            "/api/platform/api/v1/executive/subscriptions/v2?page=$page&size=100",
            text,
        )
        # Exercise the real jq selectors after pagination. Other tenants and
        # inactive subscriptions must not influence the selected authority.
        assignments = '\n'.join(line for line in text.splitlines()
                                if line.startswith(('QA_ACTIVE_COUNT=', 'SUB_ID=', 'ACTIVE_PLAN_ID=')))
        self.assertTrue(assignments, 'Subscription authority selectors are required')
        script = assignments + '\nprintf "%s\n%s\n%s\n" "$QA_ACTIVE_COUNT" "$SUB_ID" "$ACTIVE_PLAN_ID"'
        rows = [
            {'tenantId':'a', 'status':'ACTIVE', 'id':'other', 'planId':'other-plan'},
            {'tenantId':'b', 'status':'SUSPENDED', 'id':'inactive', 'planId':'inactive-plan'},
            {'tenantId':'b', 'status':'ACTIVE', 'id':'owned', 'planId':'starter'},
        ]
        with tempfile.TemporaryDirectory() as directory:
            fixture = Path(directory) / 'all-subscriptions.json'
            for tenant, expected in [('b', ['1','owned','starter']), ('missing', ['0','null','null'])]:
                fixture.write_text(json.dumps(rows))
                env = dict(os.environ, WORK_DIR=directory, PROD_QA_TENANT_ID=tenant)
                result = subprocess.run(['bash','-euc',script], env=env, capture_output=True, text=True)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(result.stdout.splitlines(), expected)
            fixture.write_text(json.dumps(rows + [dict(rows[-1], id='duplicate')]))
            env = dict(os.environ, WORK_DIR=directory, PROD_QA_TENANT_ID='b')
            result = subprocess.run(['bash','-euc',script], env=env, capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(result.stdout.splitlines()[0], '2')


if __name__ == "__main__":
    unittest.main(verbosity=2)
