from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/independent-security-assurance.yml"


class RemP0ClosureWorkflowIntegrityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = WORKFLOW.read_text(encoding="utf-8")

    def test_closure_validates_canonical_manifest(self):
        self.assertIn("--mode closure", self.workflow)
        self.assertIn("--manifest docs/security/independent-assurance/assessment-manifest.json", self.workflow)
        self.assertIn('--expected-release-sha "$RELEASE_SHA"', self.workflow)

    def test_workflow_does_not_promote_synthetic_acceptance(self):
        forbidden = (
            "Promote runtime manifest",
            "case['status'] = 'PASS'",
            "findings['findings'] = []",
            "manifest['closure_state'] = 'ACCEPTED'",
            "manifest['assessor']['independence_status'] = 'TRUST_VERIFIED'",
            "ATTESTATION-INDEPENDENCE.md",
            "EV-CLOSURE-DECISION",
        )
        for pattern in forbidden:
            with self.subTest(pattern=pattern):
                self.assertNotIn(pattern, self.workflow)

    def test_workflow_cannot_admin_merge_or_close_governance_issue(self):
        self.assertNotIn("gh pr merge", self.workflow)
        self.assertNotIn("--admin", self.workflow)
        self.assertNotIn("gh issue close", self.workflow)

    def test_workflow_checks_that_evidence_package_is_not_mutated(self):
        self.assertIn("git diff --quiet -- docs/security/independent-assurance", self.workflow)
        self.assertIn("Canonical evidence package remained unchanged.", self.workflow)

    def test_workflow_does_not_fabricate_trust_context(self):
        self.assertNotIn("'is_protected_environment': True", self.workflow)
        self.assertNotIn("'authority_token_present': True", self.workflow)
        self.assertNotIn("'oidc_subject': 'repo:", self.workflow)
        self.assertNotIn("steps.oidc.outputs.token", self.workflow)
        self.assertNotIn("--is-protected-environment", self.workflow)
        self.assertNotIn("--authority-token-present", self.workflow)

    def test_unverified_trust_inputs_fail_closed(self):
        self.assertIn("Fail closed until trust inputs are cryptographically verified", self.workflow)
        self.assertIn("python3 scripts/ci/validate_trust_policy.py", self.workflow)
        self.assertIn('--head-sha "$GITHUB_SHA"', self.workflow)
        self.assertIn('--assessed-release-sha "$RELEASE_SHA"', self.workflow)
        self.assertIn('--ref "$GITHUB_REF"', self.workflow)

    def test_workflow_states_success_is_not_the_closure_decision(self):
        self.assertIn("A successful validation run is not itself the formal closure decision.", self.workflow)
        self.assertIn("P0 remains on hold", self.workflow)


if __name__ == "__main__":
    unittest.main()
