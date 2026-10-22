#!/usr/bin/env python3
"""Fail-closed verification of live GitHub protection configuration for REM-P0-006."""
from __future__ import annotations

import argparse
import json
import os
import urllib.error
import urllib.parse
import urllib.request
from typing import Any

API_ROOT = "https://api.github.com"
ENVIRONMENT = "rem-p0-006-closure"


class ProtectionVerificationError(RuntimeError):
    """Raised when live repository protection cannot be established."""


def api_get(path: str, token: str) -> dict[str, Any] | list[Any] | None:
    request = urllib.request.Request(
        API_ROOT + path,
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": "Bearer " + token,
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "SNAD-REM-P0-006-protection-verifier",
        },
        method="GET",
    )
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        if exc.code == 404:
            return None
        raise ProtectionVerificationError(
            f"GitHub protection API request failed with HTTP {exc.code}"
        ) from exc
    except Exception as exc:
        raise ProtectionVerificationError(
            f"GitHub protection API request failed: {type(exc).__name__}"
        ) from exc
    if not isinstance(payload, (dict, list)):
        raise ProtectionVerificationError("GitHub protection API returned an unexpected payload")
    return payload


def verify_environment(repository: str, token: str) -> int:
    owner, name = repository.split("/", 1)
    path = f"/repos/{urllib.parse.quote(owner)}/{urllib.parse.quote(name)}/environments/{urllib.parse.quote(ENVIRONMENT)}"
    payload = api_get(path, token)
    if not isinstance(payload, dict):
        raise ProtectionVerificationError("Protected closure environment could not be read")
    rules = payload.get("protection_rules")
    if not isinstance(rules, list):
        raise ProtectionVerificationError("Environment protection rules are missing or malformed")
    reviewer_rules = [rule for rule in rules if isinstance(rule, dict) and rule.get("type") == "required_reviewers"]
    if not reviewer_rules:
        raise ProtectionVerificationError("Closure environment has no required-reviewer protection rule")
    reviewer_count = 0
    for rule in reviewer_rules:
        reviewers = rule.get("reviewers")
        if isinstance(reviewers, list):
            reviewer_count += len(reviewers)
    if reviewer_count < 1:
        raise ProtectionVerificationError("Closure environment required-reviewer rule has no reviewers")
    return reviewer_count


def verify_legacy_branch_protection(payload: dict[str, Any]) -> None:
    reviews = payload.get("required_pull_request_reviews")
    checks = payload.get("required_status_checks")
    admins = payload.get("enforce_admins")
    signatures = payload.get("required_signatures")
    if not isinstance(reviews, dict):
        raise ProtectionVerificationError("main branch lacks required pull-request reviews")
    if not isinstance(checks, dict) or not checks.get("contexts"):
        raise ProtectionVerificationError("main branch lacks non-empty required status checks")
    if not isinstance(admins, dict) or admins.get("enabled") is not True:
        raise ProtectionVerificationError("main branch protection does not enforce rules for administrators")
    if not isinstance(signatures, dict) or signatures.get("enabled") is not True:
        raise ProtectionVerificationError("main branch does not require signed commits")


def verify_ruleset_branch_protection(repository: str, token: str) -> list[str]:
    owner, name = repository.split("/", 1)
    path = f"/repos/{urllib.parse.quote(owner)}/{urllib.parse.quote(name)}/rules/branches/main"
    payload = api_get(path, token)
    if not isinstance(payload, list):
        raise ProtectionVerificationError("main branch protection could not be verified through legacy protection or active rulesets")
    rule_types = {item.get("type") for item in payload if isinstance(item, dict)}
    required = {"pull_request", "required_status_checks", "required_signatures", "non_fast_forward"}
    missing = sorted(required - rule_types)
    if missing:
        raise ProtectionVerificationError("active main-branch rulesets are missing required rules: " + ", ".join(missing))
    return sorted(rule_types)


def verify_protection(repository: str, token: str) -> dict[str, Any]:
    if "/" not in repository:
        raise ProtectionVerificationError("repository must be owner/name")
    reviewer_count = verify_environment(repository, token)
    owner, name = repository.split("/", 1)
    path = f"/repos/{urllib.parse.quote(owner)}/{urllib.parse.quote(name)}/branches/main/protection"
    branch = api_get(path, token)
    if isinstance(branch, dict):
        verify_legacy_branch_protection(branch)
        branch_source = "legacy-branch-protection"
        rules = ["required_pull_request_reviews", "required_status_checks", "enforce_admins", "required_signatures"]
    else:
        rules = verify_ruleset_branch_protection(repository, token)
        branch_source = "active-rulesets"
    return {
        "repository": repository,
        "environment": ENVIRONMENT,
        "required_environment_reviewer_count": reviewer_count,
        "branch_protection_source": branch_source,
        "verified_branch_rules": rules,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository", default=os.environ.get("GITHUB_REPOSITORY", ""))
    args = parser.parse_args()
    token = os.environ.get("REM_P0_006_ENVIRONMENT_AUDIT_TOKEN", "")
    if not token:
        print("REM-P0-006 PROTECTION VERIFICATION FAILED: audit token is unavailable")
        return 1
    try:
        result = verify_protection(args.repository, token)
    except ProtectionVerificationError as exc:
        print(f"REM-P0-006 PROTECTION VERIFICATION FAILED: {exc}")
        return 1
    print("REM-P0-006 LIVE PROTECTION VERIFICATION PASSED")
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
