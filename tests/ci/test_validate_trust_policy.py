from __future__ import annotations

import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
MODULE_PATH = ROOT / "scripts/ci/validate_trust_policy.py"
SPEC = importlib.util.spec_from_file_location("trust_policy_validator", MODULE_PATH)
assert SPEC and SPEC.loader
trust_validator = importlib.util.module_from_spec(SPEC)
sys.modules["trust_policy_validator"] = trust_validator
SPEC.loader.exec_module(trust_validator)
POLICY_PATH = ROOT / "docs/security/independent-assurance/trust-policy.json"


def make_context(**overrides) -> trust_validator.TrustContext:
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


def control(result, control_id):
    return next(item for item in result["controls"] if item["control_id"] == control_id)


class TrustPolicyValidatorTest(unittest.TestCase):
    def test_policy_loads(self):
        policy = trust_validator.load_policy(POLICY_PATH)
        self.assertEqual(policy["finding_id"], "REM-P0-006")
        self.assertEqual(len(policy["controls"]), 5)

    def test_valid_trust_context_passes_when_runtime_signature_verifier_passes(self):
        with patch.dict(trust_validator.CONTROL_EVALUATORS, {"GIT_SIGNATURE_VERIFICATION": lambda ctx: None}):
            result = trust_validator.validate_trust_policy(POLICY_PATH, "closure", make_context(commit_signatures={}))
        self.assertTrue(result["all_passed"])
        self.assertEqual(len(result["controls"]), 5)

    def test_readiness_only_requires_oidc_control(self):
        ctx = make_context(is_protected_environment=False, authority_token_present=False,
                           commit_signatures={"a" * 40: False}, assessor_organization="SNADA")
        result = trust_validator.validate_trust_policy(POLICY_PATH, "readiness", ctx)
        self.assertTrue(result["all_passed"])
        self.assertEqual([r["rule_id"] for r in result["rules"]], ["RULE-002"])

    def test_oidc_issuer_audience_and_subject_are_required(self):
        for override, expected in [
            ({"oidc_issuer": "https://evil.example"}, "issuer mismatch"),
            ({"oidc_audience": "wrong-audience"}, "audience"),
            ({"oidc_subject": "repo:evil/evil:pull_request"}, "subject"),
        ]:
            with self.subTest(override=override):
                result = trust_validator.validate_trust_policy(
                    POLICY_PATH, "closure", make_context(**override))
                self.assertFalse(control(result, "OIDC-IDENTITY")["passed"])
                self.assertIn(expected, control(result, "OIDC-IDENTITY")["error"])

    def test_unprotected_environment_and_missing_authority_fail(self):
        for override in [
            {"is_protected_environment": False},
            {"authority_token_present": False},
            {"environment_name": "other"},
        ]:
            with self.subTest(override=override):
                result = trust_validator.validate_trust_policy(
                    POLICY_PATH, "closure", make_context(**override))
                self.assertFalse(control(result, "ENVIRONMENT-PROTECTION")["passed"])

    def test_caller_supplied_boolean_signature_evidence_is_rejected(self):
        result = trust_validator.validate_trust_policy(
            POLICY_PATH, "closure", make_context(commit_signatures={"a" * 40: True}))
        self.assertFalse(control(result, "GIT-SIGNATURES")["passed"])
        self.assertIn("not cryptographic evidence", control(result, "GIT-SIGNATURES")["error"])

    def test_git_signature_requires_allowlisted_crypto_verified_commits(self):
        sha = "c" * 40
        ctx = make_context(head_sha=sha, assessed_release_sha=sha, commit_signatures={})
        completed = subprocess.CompletedProcess(args=[], returncode=0, stdout="", stderr="")
        listing = subprocess.CompletedProcess(args=[], returncode=0, stdout="", stderr="")
        signed = subprocess.CompletedProcess(args=[], returncode=0, stdout="", stderr="[GNUPG:] VALIDSIG ABCDEF123456\n")
        with patch.dict("os.environ", {"REM_P0_006_ALLOWED_SIGNER_FINGERPRINTS": "ABCDEF123456"}):
            with patch("trust_policy_validator.subprocess.run", side_effect=[completed, completed, completed, listing, signed]):
                self.assertIsNone(trust_validator._verify_git_signatures(ctx))
            unsigned = subprocess.CompletedProcess(args=[], returncode=1, stdout="", stderr="")
            with patch("trust_policy_validator.subprocess.run", side_effect=[completed, completed, completed, listing, unsigned]):
                self.assertIn("lacks a cryptographically valid", trust_validator._verify_git_signatures(ctx))

    def test_same_organization_assessor_fails(self):
        result = trust_validator.validate_trust_policy(
            POLICY_PATH, "closure",
            make_context(assessor_organization="snada", project_organization="SNADA"))
        self.assertFalse(control(result, "ASSESSOR-TRUST")["passed"])

    def test_release_sha_format_and_main_ref_are_required(self):
        for override in [
            {"assessed_release_sha": "not-a-sha"},
            {"head_sha": "not-a-sha"},
            {"ref": "refs/heads/feature"},
        ]:
            with self.subTest(override=override):
                result = trust_validator.validate_trust_policy(
                    POLICY_PATH, "closure", make_context(**override))
                self.assertFalse(control(result, "RELEASE-SHA-AUTH")["passed"])

    def test_missing_policy_fails(self):
        with self.assertRaisesRegex(trust_validator.TrustPolicyError, "not found"):
            trust_validator.load_policy(Path("/nonexistent/policy.json"))

    def test_invalid_policy_schema_fails(self):
        bad_policy = {"schema_version": "9.0", "finding_id": "REM-P0-006",
                      "controls": [], "rules": []}
        with tempfile.NamedTemporaryFile(mode="w", suffix=".json", delete=False) as handle:
            json.dump(bad_policy, handle)
            handle.flush()
            with self.assertRaisesRegex(trust_validator.TrustPolicyError, "schema_version"):
                trust_validator.load_policy(Path(handle.name))

    def test_unknown_control_type_fails_closed(self):
        policy = json.loads(POLICY_PATH.read_text(encoding="utf-8"))
        policy["controls"][0]["type"] = "UNKNOWN_CONTROL"
        with tempfile.NamedTemporaryFile(mode="w", suffix=".json", delete=False) as handle:
            json.dump(policy, handle)
            handle.flush()
            with self.assertRaisesRegex(trust_validator.TrustPolicyError, "unknown control type"):
                trust_validator.validate_trust_policy(Path(handle.name), "readiness", make_context())


if __name__ == "__main__":
    unittest.main()
