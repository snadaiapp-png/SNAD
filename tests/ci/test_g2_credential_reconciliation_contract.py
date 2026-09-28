#!/usr/bin/env python3
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
RECONCILE = ROOT / ".github/workflows/g2-production-credential-reconciliation.yml"
DISPATCH = ROOT / ".github/workflows/g2-recovery-release-dispatch.yml"
RUNNER = ROOT / "scripts/operations/g2_credential_reconciliation.py"


class G2CredentialRecoveryContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.reconcile = RECONCILE.read_text(encoding="utf-8")
        cls.dispatch = DISPATCH.read_text(encoding="utf-8")
        cls.runner = RUNNER.read_text(encoding="utf-8")

    def test_release_dispatch_is_explicit_and_recovery_scoped(self):
        self.assertIn("Publish Render Backend Image", self.dispatch)
        self.assertIn("PRODUCTION-RECOVERY-G2-AUTHORIZED", self.dispatch)
        self.assertIn("rollback_on_failure='false'", self.dispatch)

    def test_reconciliation_is_production_scoped_and_closes_recovery_surface(self):
        self.assertIn("SANAD Production Release", self.reconcile)
        self.assertIn("environment: production", self.reconcile)
        self.assertIn("AUTH_SMOKE_TENANT_B_ID", self.reconcile)
        self.assertIn("Recovery endpoint is not proven disabled", self.reconcile)
        self.assertIn("rollback_on_failure='true'", self.reconcile)

    def test_mutation_stays_on_application_api(self):
        self.assertIn("admin-reconcile-credential", self.runner)
        self.assertNotIn("psql", self.runner)
        self.assertNotIn("password_hash", self.runner)
        self.assertNotIn("PRODUCTION_DATABASE_PASSWORD", self.reconcile)

    def test_three_login_proofs_remain_mandatory(self):
        self.assertIn("LOGIN_HTTP=200", self.runner)
        self.assertIn("tenant binding mismatch", self.runner)
        self.assertIn("credential rotation remains required", self.runner)
        self.assertIn("account not ACTIVE", self.runner)


if __name__ == "__main__":
    unittest.main(verbosity=2)
