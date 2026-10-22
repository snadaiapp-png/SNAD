from __future__ import annotations

import importlib.util
import sys
import time
import unittest
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
MODULE_PATH = ROOT / "scripts/ci/verify_rem_p0_006_crypto.py"
SPEC = importlib.util.spec_from_file_location("rem_p0_006_crypto", MODULE_PATH)
assert SPEC and SPEC.loader
crypto = importlib.util.module_from_spec(SPEC)
sys.modules["rem_p0_006_crypto"] = crypto
SPEC.loader.exec_module(crypto)


class FakeSigningKey:
    def __init__(self, key):
        self.key = key


class FakeJwkClient:
    key = None

    def __init__(self, *args, **kwargs):
        pass

    def get_signing_key_from_jwt(self, token):
        return FakeSigningKey(self.key)


class OidcCryptographicVerificationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        from cryptography.hazmat.primitives.asymmetric import rsa
        cls.private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        cls.public_key = cls.private_key.public_key()
        FakeJwkClient.key = cls.public_key

    def token(self, **overrides):
        import jwt
        now = int(time.time())
        claims = {
            "iss": crypto.ISSUER,
            "aud": "https://github.com",
            "sub": "repo:snadaiapp-png/SNAD:environment:rem-p0-006-closure",
            "repository": "snadaiapp-png/SNAD",
            "repository_owner": "snadaiapp-png",
            "environment": "rem-p0-006-closure",
            "iat": now - 1,
            "nbf": now - 1,
            "exp": now + 120,
        }
        claims.update(overrides)
        return jwt.encode(claims, self.private_key, algorithm="RS256", headers={"kid": "test-key"})

    def verify(self, token):
        with patch.object(crypto.jwt if hasattr(crypto, "jwt") else __import__("jwt"), "PyJWKClient", FakeJwkClient):
            return crypto.verify_oidc_token(
                token, "snadaiapp-png/SNAD", "https://github.com", "rem-p0-006-closure"
            )

    def test_valid_signed_oidc_token_passes(self):
        claims = self.verify(self.token())
        self.assertEqual(claims["repository"], "snadaiapp-png/SNAD")

    def test_forged_signature_fails(self):
        import jwt
        from cryptography.hazmat.primitives.asymmetric import rsa
        attacker_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        forged = jwt.encode({
            "iss": crypto.ISSUER, "aud": "https://github.com",
            "sub": "repo:snadaiapp-png/SNAD:environment:rem-p0-006-closure",
            "repository": "snadaiapp-png/SNAD", "repository_owner": "snadaiapp-png",
            "environment": "rem-p0-006-closure", "iat": int(time.time()) - 1,
            "nbf": int(time.time()) - 1, "exp": int(time.time()) + 120,
        }, attacker_key, algorithm="RS256", headers={"kid": "attacker"})
        with self.assertRaisesRegex(crypto.VerificationError, "cryptographic/claim"):
            self.verify(forged)

    def test_expired_token_fails(self):
        with self.assertRaises(crypto.VerificationError):
            self.verify(self.token(exp=int(time.time()) - 30))

    def test_wrong_audience_fails(self):
        with self.assertRaises(crypto.VerificationError):
            self.verify(self.token(aud="https://evil.example"))

    def test_wrong_repository_fails(self):
        with self.assertRaisesRegex(crypto.VerificationError, "repository claim"):
            self.verify(self.token(repository="attacker/other"))

    def test_wrong_environment_subject_fails(self):
        with self.assertRaisesRegex(crypto.VerificationError, "subject"):
            self.verify(self.token(sub="repo:snadaiapp-png/SNAD:ref:refs/heads/main"))


class GitSignatureVerificationTests(unittest.TestCase):
    def test_unsigned_or_untrusted_commit_fails(self):
        def fake_run(args, **kwargs):
            if args[1:3] == ["merge-base", "--is-ancestor"]:
                return type("Result", (), {"returncode": 0, "stdout": "", "stderr": ""})()
            if args[1:3] == ["rev-list", "--reverse"]:
                return type("Result", (), {"returncode": 0, "stdout": "a" * 40, "stderr": ""})()
            if args[1:3] == ["log", "--format=%G?", "-1"]:
                return type("Result", (), {"returncode": 0, "stdout": "U\n", "stderr": ""})()
            return type("Result", (), {"returncode": 0, "stdout": "", "stderr": ""})()
        with patch.object(crypto.subprocess, "run", side_effect=fake_run):
            with self.assertRaisesRegex(crypto.VerificationError, "not cryptographically trusted"):
                crypto.verify_commit_range("b" * 40, "c" * 40)


if __name__ == "__main__":
    unittest.main()
