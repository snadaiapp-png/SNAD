#!/usr/bin/env python3
"""Fail-closed contract tests for Production auth-smoke tenant provisioning."""

import os
import unittest

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
PROVISIONER = os.path.join(
    REPO_ROOT,
    ".github",
    "workflows",
    "provision-auth-smoke-tenants.yml",
)


class TestProvisionAuthSmokeTenants(unittest.TestCase):
    def read_workflow(self):
        self.assertTrue(os.path.exists(PROVISIONER), "Auth-smoke provisioner must exist")
        with open(PROVISIONER, "r", encoding="utf-8") as handle:
            return handle.read()

    def test_membership_upsert_is_schema_compatible_and_admin_consistent(self):
        workflow = self.read_workflow()

        self.assertIn(
            "organization_memberships (id, tenant_id, organization_id, user_id, email, display_name, role_code, status, created_at, updated_at)",
            workflow,
            "Membership provisioning must populate identity and role metadata explicitly",
        )
        self.assertIn(
            "SELECT gen_random_uuid(), '$TID'::uuid, o.id, u.id, u.email, u.display_name, 'ADMIN', 'ACTIVE', NOW(), NOW()",
            workflow,
            "Membership identity must come from the provisioned user and role_code must match the ADMIN assignment",
        )
        self.assertIn(
            "NOT EXISTS (SELECT 1 FROM organization_memberships m",
            workflow,
            "Membership provisioning must remain idempotent",
        )

    def test_connection_and_tenant_identity_remain_secret_backed(self):
        workflow = self.read_workflow()

        required = (
            "secrets.PRODUCTION_DATABASE_URL",
            "secrets.PRODUCTION_DATABASE_USERNAME",
            "secrets.PRODUCTION_DATABASE_PASSWORD",
            "secrets.AUTH_SMOKE_TENANT_A_ID",
            "secrets.AUTH_SMOKE_TENANT_B_ID",
            "secrets.AUTH_SMOKE_TENANT_A_EMAIL",
            "secrets.AUTH_SMOKE_TENANT_B_EMAIL",
            "PGSSLMODE='require'",
            'psql -c "SELECT 1 as test;"',
        )
        for fragment in required:
            self.assertIn(fragment, workflow, f"Missing secret-backed provisioning contract: {fragment}")

        self.assertLess(
            workflow.index('psql -c "SELECT 1 as test;"'),
            workflow.index("INSERT INTO tenants"),
            "Connectivity must be proven before any tenant mutation",
        )

    def test_tenant_insert_supplies_deterministic_unique_subdomain(self):
        workflow = self.read_workflow()

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
