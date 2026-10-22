#!/usr/bin/env python3
"""Cryptographically verify REM-P0-006 GitHub Actions OIDC and assessed Git history.

Requires PyJWT[crypto]. Never accepts caller-supplied claims or boolean signature
assertions as proof. Intended to run inside the protected GitHub Actions job.
"""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import time
import urllib.parse
import urllib.request
from typing import Any

ISSUER = "https://token.actions.githubusercontent.com"
JWKS_URL = ISSUER + "/.well-known/jwks"


class VerificationError(RuntimeError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise VerificationError(message)


def obtain_oidc_token(audience: str) -> str:
    """Request an OIDC token directly from the GitHub Actions runtime."""
    request_url = os.environ.get("ACTIONS_ID_TOKEN_REQUEST_URL", "")
    request_token = os.environ.get("ACTIONS_ID_TOKEN_REQUEST_TOKEN", "")
    require(bool(request_url and request_token),
            "GitHub Actions OIDC request environment is unavailable")
    separator = "&" if "?" in request_url else "?"
    url = request_url + separator + urllib.parse.urlencode({"audience": audience})
    req = urllib.request.Request(
        url, headers={"Authorization": "Bearer " + request_token},
        method="GET",
    )
    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except Exception as exc:
        raise VerificationError(f"GitHub Actions OIDC token request failed: {type(exc).__name__}") from exc
    token = payload.get("value") if isinstance(payload, dict) else None
    require(isinstance(token, str) and token.count(".") == 2,
            "GitHub Actions did not return a JWT")
    return token


def verify_oidc_token(token: str, repository: str, audience: str,
                      expected_environment: str | None = None) -> dict[str, Any]:
    """Validate JWT signature using GitHub's JWKS and validate identity claims."""
    try:
        import jwt
    except ImportError as exc:
        raise VerificationError("PyJWT[crypto] is required for OIDC signature verification") from exc
    try:
        signing_key = jwt.PyJWKClient(JWKS_URL, cache_jwk_set=True, lifespan=300).get_signing_key_from_jwt(token)
        claims = jwt.decode(
            token, signing_key.key, algorithms=["RS256"], audience=audience,
            issuer=ISSUER, options={"require": ["iss", "aud", "sub", "exp", "iat", "nbf"]},
            leeway=0,
        )
    except Exception as exc:
        raise VerificationError(f"OIDC JWT cryptographic/claim verification failed: {type(exc).__name__}") from exc

    require(claims.get("iss") == ISSUER, "OIDC issuer mismatch")
    require(claims.get("aud") == audience or (
        isinstance(claims.get("aud"), list) and audience in claims["aud"]
    ), "OIDC audience mismatch")
    require(claims.get("repository") == repository, "OIDC repository claim mismatch")
    owner, name = repository.split("/", 1)
    require(claims.get("repository_owner", "").lower() == owner.lower(),
            "OIDC repository owner claim mismatch")
    subject = claims.get("sub", "")
    require(isinstance(subject, str) and subject.startswith(f"repo:{repository}:"),
            "OIDC subject does not bind the expected repository")
    if expected_environment:
        require(subject == f"repo:{repository}:environment:{expected_environment}",
                "OIDC subject does not bind the protected environment")
        require(claims.get("environment") == expected_environment,
                "OIDC environment claim mismatch")
    now = int(time.time())
    require(isinstance(claims.get("exp"), (int, float)) and claims["exp"] > now,
            "OIDC token expired")
    require(isinstance(claims.get("nbf"), (int, float)) and claims["nbf"] <= now,
            "OIDC token is not yet valid")
    require(isinstance(claims.get("iat"), (int, float)) and claims["iat"] <= now + 30,
            "OIDC issued-at claim is invalid")
    return claims


def git_output(args: list[str]) -> str:
    try:
        result = subprocess.run(["git", *args], check=True, capture_output=True,
                                text=True, timeout=30)
    except (subprocess.SubprocessError, OSError) as exc:
        raise VerificationError(f"git verification command failed: {type(exc).__name__}") from exc
    return result.stdout.strip()


def verify_commit_range(release_sha: str, head_sha: str) -> list[str]:
    """Verify ancestry and each commit signature with local Git trust configuration."""
    require(len(release_sha) == 40 and all(c in "0123456789abcdef" for c in release_sha),
            "release SHA must be a full lowercase 40-character SHA")
    require(len(head_sha) == 40 and all(c in "0123456789abcdef" for c in head_sha),
            "head SHA must be a full lowercase 40-character SHA")
    git_output(["cat-file", "-e", f"{release_sha}^{{commit}}"])
    git_output(["cat-file", "-e", f"{head_sha}^{{commit}}"])
    ancestor = subprocess.run(
        ["git", "merge-base", "--is-ancestor", release_sha, head_sha],
        capture_output=True, timeout=30,
    )
    require(ancestor.returncode == 0, "assessed release SHA is not an ancestor of workflow HEAD")
    # Include the release commit itself, including the equal-SHA case.
    commits = git_output(["rev-list", "--reverse", f"{release_sha}^..{head_sha}"]).splitlines()
    require(bool(commits), "assessed commit range is empty or unavailable")
    bad: list[str] = []
    for sha in commits:
        status = git_output(["log", "--format=%G?", "-1", sha])
        if status != "G":
            bad.append(f"{sha}:{status or 'EMPTY'}")
    require(not bad, "commit signatures are not cryptographically trusted: " + ", ".join(bad[:8]))
    return commits


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository", required=True)
    parser.add_argument("--release-sha", required=True)
    parser.add_argument("--head-sha", required=True)
    parser.add_argument("--audience", default="https://github.com")
    parser.add_argument("--environment", default="rem-p0-006-closure")
    parser.add_argument("--readiness", action="store_true")
    args = parser.parse_args()
    try:
        token = obtain_oidc_token(args.audience)
        claims = verify_oidc_token(
            token, args.repository, args.audience,
            None if args.readiness else args.environment,
        )
        commits = verify_commit_range(args.release_sha, args.head_sha)
        # Do not print the token or arbitrary claims. Emit only bounded verification metadata.
        print("REM-P0-006 CRYPTOGRAPHIC TRUST VERIFICATION PASSED")
        print(f"repository={args.repository}")
        print(f"oidc_issuer={ISSUER}")
        print(f"oidc_subject={claims['sub']}")
        print(f"release_sha={args.release_sha}")
        print(f"head_sha={args.head_sha}")
        print(f"verified_commit_count={len(commits)}")
        return 0
    except (VerificationError, ValueError, KeyError) as exc:
        print(f"REM-P0-006 CRYPTOGRAPHIC TRUST VERIFICATION FAILED: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
