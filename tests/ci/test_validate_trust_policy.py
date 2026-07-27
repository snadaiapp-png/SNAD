from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MODULE_PATH = ROOT / "scripts/ci/validate_trust_policy.py"
SPEC = importlib.util.spec_from_file_location("trust_policy_validator", MODULE_PATH)
assert SPEC and SPEC.loader
trust_validator = importlib.util.module_from_spec(SPEC)
sys.modules["trust_policy_validator"] = trust_validator
SPEC.loader.exec_module(trust_validator)

POLICY_PATH = ROOT / "docs/security/independent-assurance/trust-policy.json"


def make_context(**overrides) -> trust_validator.TrustContext:
    """Build a valid trust context with sensible defaults."""
    defaults = dict(
        oidc_issuer="https://token.actions.githubusercontent.com",
        oidc_audience="https://github.com",
        oidc_subject="repo:SNADA/SNAD:pull_request",
        repository_owner="SNADA",
        repository_name="SNAD",
        head_sha="a" * 40,
        assessed_release_sha="b" * 40,
        commit_signatures={"a" * 40: True, "b" * 40: True},
        environment_name="rem-p0-006-closure",
        is_protected_environment=True,
        authority_token_present=True,
        ref="refs/heads/main",
        assessor_organization="External Independent LLC",
        project_organization="SNADA",
    )
    defaults.update(overrides)
    return trust_validator.TrustContext(**defaults)


class TrustPolicyValidatorTest(unittest.TestCase):

    def test_policy_loads_successfully(self):
        policy = trust_validator.load_policy(POLICY_PATH)
        self.assertEqual(policy["finding_id"], "REM-P0-006")
        self.assertEqual(policy["schema_version"], "1.0")
        self.assertEqual(len(policy["controls"]), 5)
        self.assertEqual(len(policy["rules"]), 2)

    def test_all_controls_pass_closure(self):
        ctx = make_context()
        result = trust_validator.validate_trust_policy(POLICY_PATH, "closure", ctx)
        self.assertTrue(result["all_passed"])
        self.assertEqual(len(result["controls"]), 5)
        self.assertTrue(all(c["passed"] for c in result["controls"]))

    def test_all_controls_pass_readiness(self):
        ctx = make_context()
        result = trust_validator.validate_trust_policy(POLICY_PATH, "readiness", ctx)
        self.assertTrue(result["all_passed"])

    def test_oidc_issuer_mismatch_fails(self):
        ctx = make_context(oidc_issuer="https://evil.com")
        result = trust_validator.validate_trust_policy(POLICY_PATH, "closure", ctx)
        self.assertFalse(result["all_passed"])
        oidc = [c for c in result["controls"] if c["control_id"] == "OIDC-IDENTITY"][0]
        self.assertFalse(oidc["passed"])
        self.assertIn("issuer mismatch", oidc["error"])

    def test_oidc_subject_mismatch_fails(self):
        ctx = make_context(oidc_subject="repo:evil/evil:pull_request")
        result = trust_validator.validate_trust_policy(POLICY_PATH, "closure", ctx)
        self.assertFalse(result["all_passed"])
        oidc = [c for c in result["controls"] if c["control_id"] == "OIDC-IDENTITY"][0]
        self.assertFalse(oidc["passed"])
        self.assertIn("does not match", oidc["error"])

    def test_unsigned_commit_fails(self):
        ctx = make_context(commit_signatures={"a" * 40: False})
        result = trust_validator.validate_trust_policy(POLICY_PATH, "closure", ctx)
        self.assertFalse(result["all_passed"])
        sig = [c for c in result["controls"] if c["control_id"] == "GIT-SIGNATURES"][0]
        self.assertFalse(sig["passed"])
        self.assertIn("Unsigned commits", sig["error"])

    def test_unprotected_environment_fails(self):
        ctx = make_context(is_protected_environment=False)
        result = trust_validator.validate_trust_policy(POLICY_PATH, "closure", ctx)
        self.assertFalse(result["all_passed"])
        env = [c for c in result["controls"] if c["control_id"] == "ENVIRONMENT-PROTECTION"][0]
        self.assertFalse(env["passed"])
        self.assertIn("not running in a protected environment", env["error"])

    def test_assessor_same_org_fails(self):
        ctx = make_context(assessor_organization="SNADA", project_organization="SNADA")
        result = trust_validator.validate_trust_policy(POLICY_PATH, "closure", ctx)
        self.assertFalse(result["all_passed"])
        assessor = [c for c in result["controls"] if c["control_id"] == "ASSESSOR-TRUST"][0]
        self.assertFalse(assessor["passed"])
        self.assertIn("matches project", assessor["error"])

    def test_missing_oidc_issuer_fails(self):
        ctx = make_context(oidc_issuer="")
        result = trust_validator.validate_trust_policy(POLICY_PATH, "closure", ctx)
        self.assertFalse(result["all_passed"])

    def test_missing_commit_signatures_fails(self):
        ctx = make_context(commit_signatures={})
        result = trust_validator.validate_trust_policy(POLICY_PATH, "closure", ctx)
        self.assertFalse(result["all_passed"])
        sig = [c for c in result["controls"] if c["control_id"] == "GIT-SIGNATURES"][0]
        self.assertFalse(sig["passed"])

    def test_invalid_policy_schema_version_fails(self):
        bad_policy = {
            "schema_version": "9.0",
            "finding_id": "REM-P0-006",
            "controls": [],
            "rules": [],
        }
        with tempfile.NamedTemporaryFile(mode="w", suffix=".json", delete=False) as f:
            json.dump(bad_policy, f)
            f.flush()
            with self.assertRaisesRegex(trust_validator.TrustPolicyError, "schema_version"):
                trust_validator.load_policy(Path(f.name))

    def test_missing_policy_file_fails(self):
        with self.assertRaisesRegex(trust_validator.TrustPolicyError, "not found"):
            trust_validator.load_policy(Path("/nonexistent/policy.json"))

    def test_readiness_only_needs_oidc(self):
        """Readiness mode only requires OIDC-IDENTITY; other failures are ignored."""
        ctx = make_context(
            is_protected_environment=False,
            commit_signatures={"a" * 40: False},
            assessor_organization="SNADA",
        )
        result = trust_validator.validate_trust_policy(POLICY_PATH, "readiness", ctx)
        self.assertTrue(result["all_passed"])
        oidc = [c for c in result["controls"] if c["control_id"] == "OIDC-IDENTITY"][0]
        self.assertTrue(oidc["passed"])

    def test_closure_result_structure(self):
        ctx = make_context()
        result = trust_validator.validate_trust_policy(POLICY_PATH, "closure", ctx)
        self.assertIn("policy_id", result)
        self.assertIn("mode", result)
        self.assertIn("controls", result)
        self.assertIn("rules", result)
        self.assertIn("all_passed", result)
        self.assertEqual(result["mode"], "closure")
        self.assertEqual(len(result["controls"]), 5)
        self.assertEqual(len(result["rules"]), 1)

    def test_readiness_rule_count(self):
        """Readiness mode should evaluate only RULE-002."""
        ctx = make_context()
        result = trust_validator.validate_trust_policy(POLICY_PATH, "readiness", ctx)
        self.assertEqual(len(result["rules"]), 1)
        self.assertEqual(result["rules"][0]["rule_id"], "RULE-002")


if __name__ == "__main__":
    unittest.main()
