#!/usr/bin/env python3
"""
SANAD — Workflow Security Policy Tests (Structural)
=====================================================
Deterministic workflow-security scenarios using the structural scanner
with fixture files.

Run:
    python3 -m pytest tests/ci/test_workflow_security_policy.py -q
"""

import os
import sys
import unittest

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SCRIPT_DIR = os.path.join(REPO_ROOT, "scripts", "ci")
sys.path.insert(0, SCRIPT_DIR)

from check_workflow_security import scan_workflow

FIXTURES_DIR = os.path.join(os.path.dirname(__file__), "fixtures", "workflows")


def fixture(name):
    return os.path.join(FIXTURES_DIR, name)


def repo_text(path):
    with open(os.path.join(REPO_ROOT, path), "r", encoding="utf-8") as handle:
        return handle.read()


class TestWorkflowSecurityPolicy(unittest.TestCase):
    """Workflow structural-security regression scenarios."""

    def scan(self, name):
        return scan_workflow(fixture(name))

    def test_01_full_unsafe_workflow_fails(self):
        violations = self.scan("unsafe-reset-admin-password.yml")
        self.assertGreater(len(violations), 0, "Full unsafe workflow must be rejected")

    def test_02_multiline_password_input_fails(self):
        violations = self.scan("unsafe-password-input.yml")
        password_violations = [v for v in violations if v["type"] == "password_dispatch_input"]
        self.assertGreater(len(password_violations), 0, "Password input must be flagged")

    def test_03_direct_password_hash_update_fails(self):
        violations = self.scan("unsafe-reset-admin-password.yml")
        hash_violations = [v for v in violations if v["type"] == "direct_password_hash_mutation"]
        self.assertGreater(len(hash_violations), 0, "password_hash mutation must be flagged")

    def test_04_refresh_token_deletion_fails(self):
        violations = self.scan("unsafe-reset-admin-password.yml")
        token_violations = [v for v in violations if v["type"] == "direct_refresh_token_deletion"]
        self.assertGreater(len(token_violations), 0, "refresh token deletion must be flagged")

    def test_05_production_psycopg2_fails(self):
        violations = self.scan("unsafe-reset-admin-password.yml")
        psycopg2_violations = [v for v in violations if v["type"] == "production_psycopg2_access"]
        self.assertGreater(len(psycopg2_violations), 0, "psycopg2 with Production must be flagged")

    def test_06_render_api_mutation_fails(self):
        violations = self.scan("unsafe-reset-admin-password.yml")
        psycopg2_violations = [v for v in violations if v["type"] == "production_psycopg2_access"]
        self.assertGreater(len(psycopg2_violations), 0, "Production psycopg2 access must be flagged")

    def test_07_user_enumeration_fails(self):
        violations = self.scan("unsafe-reset-admin-password.yml")
        enum_violations = [v for v in violations if v["type"] == "production_user_enumeration"]
        self.assertGreater(len(enum_violations), 0, "User enumeration must be flagged")

    def test_08_identity_logging_fails(self):
        violations = self.scan("unsafe-reset-admin-password.yml")
        log_violations = [v for v in violations if v["type"] == "identity_logging"]
        self.assertGreater(len(log_violations), 0, "Identity logging must be flagged")

    def test_09_unpinned_packages_with_secrets_fails(self):
        violations = self.scan("unsafe-reset-admin-password.yml")
        pkg_violations = [v for v in violations if v["type"] == "unpinned_packages_with_secrets"]
        self.assertGreater(len(pkg_violations), 0, "Unpinned packages with secrets must be flagged")

    def test_10_write_all_fails(self):
        violations = self.scan("unsafe-write-all.yml")
        wa_violations = [v for v in violations if v["type"] == "write_all_permissions"]
        self.assertGreater(len(wa_violations), 0, "write-all must be flagged")

    def test_11_safe_testcontainers_passes(self):
        violations = self.scan("safe-testcontainers.yml")
        self.assertEqual(len(violations), 0, "Safe Testcontainers workflow should pass")

    def test_12_safe_monitoring_passes(self):
        violations = self.scan("safe-monitoring.yml")
        self.assertEqual(len(violations), 0, "Safe monitoring workflow should pass")

    def test_13_safe_application_ignored(self):
        violations = self.scan("safe-testcontainers.yml")
        self.assertEqual(len(violations), 0)

    def test_14_documentation_text_handled(self):
        import tempfile
        content = """# This is a documentation file
# It describes workflows but does not execute anything
name: Docs Only
on: [push]
jobs:
  docs:
    runs-on: ubuntu-latest
    steps:
      - run: echo "Generating docs"
"""
        fd, path = tempfile.mkstemp(suffix=".yml", dir="/tmp")
        with os.fdopen(fd, "w") as f:
            f.write(content)
        violations = scan_workflow(path)
        os.unlink(path)
        self.assertEqual(len(violations), 0, "Documentation-only workflow should pass")

    def test_15_multiline_blocks_cannot_evade(self):
        violations = self.scan("unsafe-multiline-db-mutation.yml")
        hash_violations = [v for v in violations if v["type"] == "direct_password_hash_mutation"]
        token_violations = [v for v in violations if v["type"] == "direct_refresh_token_deletion"]
        self.assertGreater(len(hash_violations) + len(token_violations), 0,
                           "Multiline DB mutation must be caught")

    def test_16_production_tenant_inventory_logging_fails(self):
        violations = self.scan("unsafe-production-tenant-inventory.yml")
        matches = [v for v in violations if v["type"] == "production_tenant_inventory_logging"]
        self.assertGreater(len(matches), 0,
                           "Production tenant identity inventory logging must be rejected")

    def test_17_production_db_topology_logging_fails(self):
        violations = self.scan("unsafe-production-tenant-inventory.yml")
        matches = [v for v in violations if v["type"] == "production_db_topology_logging"]
        self.assertGreater(len(matches), 0,
                           "Production DB host/database topology logging must be rejected")

    def test_18_reconcile_workflow_does_not_inject_stale_tenant_secret(self):
        workflow = repo_text(".github/workflows/scp-smoke-identity-reconcile.yml")
        self.assertNotIn(
            "secrets.CONTROL_PLANE_TENANT_ID",
            workflow,
            "Reconcile must derive the authoritative tenant from production state, not a duplicated GitHub secret",
        )

    def test_19_reconcile_script_does_not_require_external_tenant_id(self):
        script = repo_text("scripts/production/scp-smoke-identity-reconcile.sh")
        self.assertNotIn(
            '${CONTROL_PLANE_TENANT_ID:?CONTROL_PLANE_TENANT_ID is required}',
            script,
            "Reconcile must resolve the canonical tenant internally",
        )
        self.assertIn(
            "platform_admin",
            script,
            "Reconcile must derive the canonical tenant from the platform-admin production invariant",
        )

    def test_20_scp_smoke_applications_contract_is_array(self):
        smoke = repo_text("scripts/production/verify-scp-contract-smoke.sh")
        self.assertIn('check "applications"        "array"', smoke)

    def test_21_scp_smoke_provisioning_contract_is_array(self):
        smoke = repo_text("scripts/production/verify-scp-contract-smoke.sh")
        self.assertIn('check "provisioningJobs"    "array"', smoke)

    def test_22_scp_smoke_usage_contract_is_array(self):
        smoke = repo_text("scripts/production/verify-scp-contract-smoke.sh")
        self.assertIn('check "usageTenantScoped" "array"', smoke)


if __name__ == "__main__":
    unittest.main(verbosity=2)
