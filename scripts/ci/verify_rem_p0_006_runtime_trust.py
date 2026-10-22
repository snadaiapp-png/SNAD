#!/usr/bin/env python3
"""Verify live REM-P0-006 runtime trust evidence. All checks fail closed."""
from __future__ import annotations

import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ISSUER = "https://token.actions.githubusercontent.com"
AUDIENCE = "https://github.com"
ENVIRONMENT = "rem-p0-006-closure"
TRUSTED_REVIEWER_LOGIN = "abdulrhmansenan1985-creator"
API = "https://api.github.com"


class GateError(RuntimeError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise GateError(message)


def api_get(url: str, token: str) -> dict:
    request = urllib.request.Request(url, headers={
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "SNAD-REM-P0-006-Trust-Validator",
    })
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            return json.loads(response.read().decode("utf-8"))
    except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError, ValueError) as exc:
        raise GateError(f"GitHub trust API could not be verified ({type(exc).__name__}); refusing closure") from exc


def verify_oidc() -> dict:
    request_url = os.environ.get("ACTIONS_ID_TOKEN_REQUEST_URL", "")
    request_token = os.environ.get("ACTIONS_ID_TOKEN_REQUEST_TOKEN", "")
    require(request_url and request_token, "GitHub OIDC token endpoint is unavailable")
    url = request_url + ("&" if "?" in request_url else "?") + urllib.parse.urlencode({"audience": AUDIENCE})
    request = urllib.request.Request(url, headers={"Authorization": f"Bearer {request_token}", "Accept": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            token_response = json.loads(response.read().decode("utf-8"))
            jwt_token = token_response.get("value", "")
    except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError, ValueError) as exc:
        raise GateError(f"Unable to obtain GitHub OIDC JWT ({type(exc).__name__})") from exc
    require(jwt_token and jwt_token.count(".") == 2, "OIDC endpoint did not return a JWT")
    try:
        import jwt
        from jwt import PyJWKClient
    except ImportError as exc:
        raise GateError("PyJWT with cryptographic dependencies is required") from exc
    try:
        signing_key = PyJWKClient(ISSUER + "/.well-known/jwks").get_signing_key_from_jwt(jwt_token)
        claims = jwt.decode(
            jwt_token, signing_key.key, algorithms=["RS256"], audience=AUDIENCE,
            issuer=ISSUER, options={"require": ["exp", "iat", "iss", "aud", "sub", "repository", "repository_owner", "sha", "ref"]},
        )
    except Exception as exc:
        raise GateError(f"OIDC JWT signature/claims validation failed ({type(exc).__name__})") from exc
    repo = os.environ.get("GITHUB_REPOSITORY", "")
    require(claims.get("repository", "").casefold() == repo.casefold(), "OIDC repository claim does not match this repository")
    require(claims.get("repository_owner", "").casefold() == repo.split("/", 1)[0].casefold(), "OIDC owner claim does not match this repository")
    require(claims.get("sub") == f"repo:{repo}:environment:{ENVIRONMENT}", "OIDC subject is not bound to the exact protected environment")
    require(claims.get("ref") == "refs/heads/main", "OIDC ref claim is not refs/heads/main")
    require(claims.get("sha") == os.environ.get("GITHUB_SHA", ""), "OIDC SHA claim does not match workflow SHA")
    require(claims.get("environment") == ENVIRONMENT, "OIDC token is not bound to the required protected environment")
    return claims


def verify_main_branch_protection(token: str, owner: str, repo: str) -> None:
    """Require repository-level main-branch PR review protection; environment rules alone are insufficient."""
    url = f"{API}/repos/{urllib.parse.quote(owner)}/{urllib.parse.quote(repo)}/branches/main/protection"
    protection = api_get(url, token)
    reviews = protection.get("required_pull_request_reviews")
    require(isinstance(reviews, dict), "main branch lacks required pull-request review protection")
    count = reviews.get("required_approving_review_count")
    require(isinstance(count, int) and count >= 1, "main branch must require at least one approving review")


def verify_environment(token: str, owner: str, repo: str) -> None:
    require(token, "GITHUB_TOKEN unavailable; cannot verify environment protection rules")
    url = f"{API}/repos/{urllib.parse.quote(owner)}/{urllib.parse.quote(repo)}/environments/{urllib.parse.quote(ENVIRONMENT)}"
    environment = api_get(url, token)
    rules = environment.get("protection_rules")
    require(isinstance(rules, list) and rules, "Protected environment has no configured protection rules")
    reviewer_rules = [r for r in rules if r.get("type") == "required_reviewers"]
    require(reviewer_rules, "Protected environment lacks required-reviewer protection")
    configured_reviewers = []
    for rule in reviewer_rules:
        for entry in rule.get("reviewers") or []:
            reviewer = entry.get("reviewer") or {}
            login = reviewer.get("login") or reviewer.get("slug")
            if login:
                configured_reviewers.append(login.casefold())
    require(TRUSTED_REVIEWER_LOGIN.casefold() in configured_reviewers,
            "Protected environment does not require the policy-allowlisted reviewer identity")
    verify_main_branch_protection(token, owner, repo)
    require(os.environ.get("CLOSURE_AUTHORITY_TOKEN", ""), "Closure authority secret is not configured")
    # Never print or persist the authority secret.


def git_output(args: list[str]) -> str:
    result = subprocess.run(args, text=True, capture_output=True, check=False, timeout=30)
    if result.returncode:
        raise GateError(f"Git verification failed for command {args[1]}: {result.stderr.strip()[:400]}")
    return result.stdout.strip()


def verify_release_and_signatures(release_sha: str, head_sha: str) -> None:
    require(re.fullmatch(r"[0-9a-f]{40}", release_sha) is not None, "Assessed release SHA must be an exact lowercase commit SHA")
    require(re.fullmatch(r"[0-9a-f]{40}", head_sha) is not None, "Workflow SHA must be an exact lowercase commit SHA")
    git_output(["git", "cat-file", "-e", f"{release_sha}^{{commit}}"])
    git_output(["git", "cat-file", "-e", f"{head_sha}^{{commit}}"])
    git_output(["git", "merge-base", "--is-ancestor", release_sha, head_sha])
    # A repository-controlled JSON boolean is not signature evidence. Verify each commit
    # directly with Git's cryptographic verifier and require an explicitly allowlisted signer.
    allowed = {item.strip().upper() for item in os.environ.get("REM_P0_006_ALLOWED_SIGNER_FINGERPRINTS", "").split(",") if item.strip()}
    require(allowed, "REM_P0_006_ALLOWED_SIGNER_FINGERPRINTS is not configured")
    commits = git_output(["git", "rev-list", "--reverse", f"{release_sha}^..{head_sha}"]).splitlines()
    if release_sha not in commits:
        commits.insert(0, release_sha)
    require(commits, "No commits found in the assessed range")
    for commit in commits:
        result = subprocess.run(["git", "verify-commit", "--raw", commit], text=True, capture_output=True, check=False, timeout=30)
        output = result.stdout + "\n" + result.stderr
        require(result.returncode == 0, f"Commit {commit} has no cryptographically valid Git signature")
        fingerprints = {m.upper() for m in re.findall(r"\[GNUPG:\] VALIDSIG ([0-9A-Fa-f]+)", output)}
        require(fingerprints & allowed, f"Commit {commit} signer is not in the trusted fingerprint allowlist")


def main() -> int:
    try:
        claims = verify_oidc()
        repository = os.environ.get("GITHUB_REPOSITORY", "")
        require("/" in repository, "GITHUB_REPOSITORY is malformed")
        owner, name = repository.split("/", 1)
        verify_environment(os.environ.get("GITHUB_ENVIRONMENT_AUDIT_TOKEN", ""), owner, name)
        verify_release_and_signatures(os.environ.get("RELEASE_SHA", ""), os.environ.get("GITHUB_SHA", ""))
        output = os.environ.get("GITHUB_OUTPUT", "")
        if output:
            with open(output, "a", encoding="utf-8") as handle:
                handle.write(f"oidc_issuer={claims['iss']}\n")
                handle.write(f"oidc_audience={AUDIENCE}\n")
                handle.write(f"oidc_subject={claims['sub']}\n")
                handle.write(f"repository_owner={owner}\n")
                handle.write(f"repository_name={name}\n")
                handle.write(f"head_sha={claims['sha']}\n")
                handle.write(f"assessed_release_sha={os.environ['RELEASE_SHA']}\n")
                handle.write(f"environment_name={ENVIRONMENT}\n")
                handle.write("is_protected_environment=true\n")
                handle.write("authority_token_present=true\n")
                handle.write(f"ref={claims['ref']}\n")
        print("Runtime trust checks passed: signed OIDC JWT, protected environment API, exact SHA ancestry, allowlisted cryptographic commit signatures.")
        return 0
    except (GateError, OSError, subprocess.TimeoutExpired) as exc:
        print(f"REM-P0-006 RUNTIME TRUST FAILED CLOSED: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
