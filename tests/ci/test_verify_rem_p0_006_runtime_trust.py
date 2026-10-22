from __future__ import annotations

import importlib.util
import json
import os
import subprocess
import sys
import types
import unittest
from unittest.mock import patch

ROOT = __import__("pathlib").Path(__file__).resolve().parents[2]
MODULE_PATH = ROOT / "scripts/ci/verify_rem_p0_006_runtime_trust.py"
SPEC = importlib.util.spec_from_file_location("runtime_trust_verifier", MODULE_PATH)
assert SPEC and SPEC.loader
runtime = importlib.util.module_from_spec(SPEC)
sys.modules["runtime_trust_verifier"] = runtime
SPEC.loader.exec_module(runtime)


class RuntimeTrustVerifierTest(unittest.TestCase):
    def test_missing_oidc_endpoint_fails_closed(self):
        with patch.dict(os.environ, {}, clear=True):
            with self.assertRaisesRegex(runtime.GateError, "OIDC token endpoint"):
                runtime.verify_oidc()

    def test_environment_audit_token_is_required(self):
        with patch.dict(os.environ, {"CLOSURE_AUTHORITY_TOKEN": "configured"}, clear=True):
            with self.assertRaisesRegex(runtime.GateError, "GITHUB_TOKEN unavailable"):
                runtime.verify_environment("", "snadaiapp-png", "SNAD")

    def test_environment_without_reviewer_protection_fails(self):
        with patch.dict(os.environ, {"CLOSURE_AUTHORITY_TOKEN": "configured"}, clear=True):
            with patch.object(runtime, "api_get", return_value={"protection_rules": []}):
                with self.assertRaisesRegex(runtime.GateError, "no configured protection rules"):
                    runtime.verify_environment("read-only-audit-token", "snadaiapp-png", "SNAD")

    def test_environment_rejects_unapproved_reviewer_identity(self):
        env = {
            "CLOSURE_AUTHORITY_TOKEN": "configured",
        }
        environment = {"protection_rules": [{
            "type": "required_reviewers",
            "reviewers": [{"type": "User", "reviewer": {"login": "someone-else"}}],
        }]}
        with patch.dict(os.environ, env, clear=True):
            with patch.object(runtime, "api_get", return_value=environment):
                with self.assertRaisesRegex(runtime.GateError, "policy-allowlisted reviewer"):
                    runtime.verify_environment("read-only-audit-token", "snadaiapp-png", "SNAD")

    def test_main_branch_requires_pull_request_approval_protection(self):
        with patch.object(runtime, "api_get", return_value={}):
            with self.assertRaisesRegex(runtime.GateError, "main branch lacks required pull-request review protection"):
                runtime.verify_main_branch_protection("read-only-audit-token", "snadaiapp-png", "SNAD")

    def test_main_branch_requires_at_least_one_approval(self):
        with patch.object(runtime, "api_get", return_value={
            "required_pull_request_reviews": {"required_approving_review_count": 0}
        }):
            with self.assertRaisesRegex(runtime.GateError, "at least one approving review"):
                runtime.verify_main_branch_protection("read-only-audit-token", "snadaiapp-png", "SNAD")

    def test_invalid_release_sha_fails_before_git_calls(self):
        with patch("runtime_trust_verifier.git_output") as git_output:
            with self.assertRaisesRegex(runtime.GateError, "exact lowercase commit SHA"):
                runtime.verify_release_and_signatures("not-a-sha", "a" * 40)
            git_output.assert_not_called()

    def test_unsigned_commit_fails_closed(self):
        release = "a" * 40
        head = "b" * 40
        with patch.dict(os.environ, {"REM_P0_006_ALLOWED_SIGNER_FINGERPRINTS": "ABCDEF123456"}):
            with patch.object(runtime, "git_output", side_effect=["", "", "", release]):
                unsigned = subprocess.CompletedProcess(args=[], returncode=1, stdout="", stderr="no signature")
                with patch("runtime_trust_verifier.subprocess.run", return_value=unsigned):
                    with self.assertRaisesRegex(runtime.GateError, "no cryptographically valid"):
                        runtime.verify_release_and_signatures(release, head)

    def test_validsig_must_match_allowlisted_fingerprint(self):
        release = "a" * 40
        head = "b" * 40
        with patch.dict(os.environ, {"REM_P0_006_ALLOWED_SIGNER_FINGERPRINTS": "ABCDEF123456"}):
            with patch.object(runtime, "git_output", side_effect=["", "", "", release]):
                signed = subprocess.CompletedProcess(
                    args=[], returncode=0, stdout="", stderr="[GNUPG:] VALIDSIG 999999999999\n")
                with patch("runtime_trust_verifier.subprocess.run", return_value=signed):
                    with self.assertRaisesRegex(runtime.GateError, "not in the trusted fingerprint allowlist"):
                        runtime.verify_release_and_signatures(release, head)

    def test_oidc_verifier_uses_jwks_and_checks_bound_claims(self):
        claims = {
            "iss": runtime.ISSUER, "aud": runtime.AUDIENCE, "sub": "repo:snadaiapp-png/SNAD:environment:rem-p0-006-closure",
            "repository": "snadaiapp-png/SNAD", "repository_owner": "snadaiapp-png",
            "sha": "c" * 40, "ref": "refs/heads/main", "environment": runtime.ENVIRONMENT,
        }
        class Response:
            def __enter__(self):
                return self
            def __exit__(self, *args):
                return False
            def read(self):
                return json.dumps({"value": "header.payload.signature"}).encode()
        fake_jwt = types.ModuleType("jwt")
        class FakeJwkClient:
            def __init__(self, url):
                self.url = url
            def get_signing_key_from_jwt(self, token):
                self.token = token
                return types.SimpleNamespace(key="public-key")
        fake_jwt.PyJWKClient = FakeJwkClient
        def decode(token, key, **kwargs):
            self.assertEqual(token, "header.payload.signature")
            self.assertEqual(key, "public-key")
            self.assertEqual(kwargs["algorithms"], ["RS256"])
            self.assertEqual(kwargs["audience"], runtime.AUDIENCE)
            self.assertEqual(kwargs["issuer"], runtime.ISSUER)
            return claims
        fake_jwt.decode = decode
        env = {
            "ACTIONS_ID_TOKEN_REQUEST_URL": "https://token.example/request",
            "ACTIONS_ID_TOKEN_REQUEST_TOKEN": "ephemeral-request-token",
            "GITHUB_REPOSITORY": "snadaiapp-png/SNAD",
            "GITHUB_SHA": "c" * 40,
        }
        with patch.dict(os.environ, env, clear=True):
            with patch.dict(sys.modules, {"jwt": fake_jwt}):
                with patch("runtime_trust_verifier.urllib.request.urlopen", return_value=Response()):
                    self.assertEqual(runtime.verify_oidc(), claims)


if __name__ == "__main__":
    unittest.main()
