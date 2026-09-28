#!/usr/bin/env python3
"""Fail-closed contract for the one-shot G2 production recovery path."""

from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github" / "workflows" / "g2-production-credential-reconciliation.yml"
RUNNER = ROOT / "scripts" / "operations" / "g2_credential_reconciliation.py"


class G2CredentialReconciliationContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = WORKFLOW.read_text(encoding="utf-8")
        cls.runner = RUNNER.read_text(encoding="utf-8")

    def test_workflow_is_narrow_and_production_scoped(self):
        text = self.workflow
        self.assertIn("recovery/g2-credential-reconciliation-once", text)
        self.assertIn("environment: production", text)
        self.assertIn("permissions:\n  contents: read", text)
        self.assertIn("AUTH_SMOKE_TENANT_B_ID", text)
        self.assertIn("G2_PROD_EMPLOYEE_PASSWORD", text)
        self.assertIn("G2_PROD_MANAGER_PASSWORD", text)
        self.assertIn("G2_PROD_HR_PASSWORD", text)

    def test_runner_uses_only_application_contract_for_mutation(self):
        text = self.runner
        self.assertIn("admin-reconcile-credential", text)
        self.assertNotIn("psql", text)
        self.assertNotIn("password_hash", text)
        self.assertNotIn("UPDATE users", text)
        self.assertNotIn("PRODUCTION_DATABASE_PASSWORD", self.workflow)

    def test_runner_requires_final_three_login_proofs(self):
        text = self.runner
        self.assertIn('LOGIN_HTTP=200', text)
        self.assertIn('tenant binding mismatch', text)
        self.assertIn('credential rotation remains required', text)
        self.assertIn('account not ACTIVE', text)


if __name__ == "__main__":
    unittest.main(verbosity=2)
