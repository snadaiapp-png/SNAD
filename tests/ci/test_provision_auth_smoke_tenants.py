#!/usr/bin/env python3
"""Regression contract for production auth-smoke tenant provisioning."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github" / "workflows" / "provision-auth-smoke-tenants.yml"


class TestProvisionAuthSmokeTenants(unittest.TestCase):
    def workflow_text(self):
        self.assertTrue(WORKFLOW.exists(), f"Missing workflow: {WORKFLOW}")
        return WORKFLOW.read_text(encoding="utf-8")

    def test_tenant_insert_supplies_deterministic_unique_subdomain(self):
        workflow = self.workflow_text()

        self.assertIn('SUBDOMAIN="auth-smoke-${label,,}-${TID//-/}"', workflow)
        self.assertIn(
            "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)",
            workflow,
        )
        self.assertIn(
            "VALUES ('$TID'::uuid, 'Auth Smoke $label', '$SUBDOMAIN', 'ACTIVE', NOW(), NOW())",
            workflow,
        )


if __name__ == "__main__":
    unittest.main(verbosity=2)
