#!/usr/bin/env python3
"""Safety contract for the governed STARTER bootstrap and verify-only final gate."""

from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JOURNEY = ROOT / "scripts" / "production" / "verify-workflow-production-3user-journey.sh"
BOOTSTRAP = ROOT / "scripts" / "production" / "bootstrap-workflow-production-qa-subscription.sh"


class WorkflowStarterBootstrapContractTest(unittest.TestCase):
    def test_final_gate_is_starter_verify_only(self):
        text = JOURNEY.read_text(encoding="utf-8")

        self.assertNotIn("WORKFLOW_PROD_QA", text)
        self.assertIn('.code=="STARTER" and .status=="ACTIVE"', text)
        self.assertIn(
            "Existing governed ACTIVE STARTER subscription verified; final gate performs no subscription/catalog creation",
            text,
        )
        self.assertNotIn(
            "request POST '/api/platform/api/v1/executive/subscriptions'",
            text,
        )
        self.assertNotIn(
            "/api/platform/api/v1/executive/plans/$PLAN_ID/modules/WORKFLOW",
            text,
        )

    def test_bootstrap_is_separate_and_catalog_read_only(self):
        text = BOOTSTRAP.read_text(encoding="utf-8")

        self.assertIn("Control Plane token attempted non-Executive API", text)
        self.assertIn("Expected exactly one eligible existing STARTER plan", text)
        self.assertIn("catalogMutations:", text)
        self.assertIn("planMutations:", text)
        self.assertIn("Direct database", "Direct database")

if __name__ == "__main__":
    unittest.main(verbosity=2)
