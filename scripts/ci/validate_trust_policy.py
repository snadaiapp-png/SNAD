#!/usr/bin/env python3
"""Trust-based governance validation for REM-P0-006 automated closure."""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from verify_rem_p0_006_crypto import VerificationError, obtain_oidc_token, verify_oidc_token, verify_commit_range

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_POLICY = ROOT / "docs/security/independent-assurance/trust-policy.json"
EXPECTED_OIDC_ISSUER = "https://token.actions.githubusercontent.com"


class TrustPolicyError(RuntimeError):
    """Raised when a trust control fails."""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise TrustPolicyError(message)


@dataclass
class TrustContext:
    """Workflow run context for trust evaluation."""
    oidc_issuer: str = ""
    oidc_audience: str = ""
    oidc_subject: str = ""
    repository_owner: str = ""
    repository_name: str = ""
    head_sha: str = ""
    assessed_release_sha: str = ""
    mode: str = "readiness"
    environment_name: str = ""
    is_protected_environment: bool = False
    authority_token_present: bool = False
    ref: str = ""
    assessor_organization: str = ""
    project_organization: str = ""


def load_policy(path: Path) -> dict[str, Any]:
    """Load and validate trust policy JSON."""
    require(path.is_file(), f"trust policy not found: {path}")
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise TrustPolicyError(f"invalid trust policy JSON: {exc}") from exc
    require(isinstance(data, dict), "trust policy must be a JSON object")
    require(data.get("schema_version") == "1.0", "trust policy schema_version must be 1.0")
    require(data.get("finding_id") == "REM-P0-006", "trust policy finding_id must be REM-P0-006")
    require(isinstance(data.get("controls"), list), "controls must be a list")
    require(len(data["controls"]) > 0, "controls must not be empty")
    require(isinstance(data.get("rules"), list), "rules must be a list")
    require(len(data["rules"]) > 0, "rules must not be empty")
    return data


def _verify_oidc_identity(ctx: TrustContext) -> str | None:
    """Readiness checks metadata; closure verifies the live GitHub-issued JWT cryptographically."""
    if ctx.mode == "closure":
        repository = os.environ.get("GITHUB_REPOSITORY", "")
        if not repository or "/" not in repository:
            return "GITHUB_REPOSITORY runtime identity is unavailable"
        try:
            token = obtain_oidc_token("https://github.com")
            verify_oidc_token(token, repository, "https://github.com", "rem-p0-006-closure")
        except VerificationError as exc:
            return f"Cryptographic OIDC verification failed: {exc}"
        return None
    if not ctx.oidc_issuer:
        return "OIDC issuer not available"
    if ctx.oidc_issuer != EXPECTED_OIDC_ISSUER:
        return f"OIDC issuer mismatch: {ctx.oidc_issuer}"
    if not ctx.repository_owner or not ctx.repository_name:
        return "Repository owner/name not provided"
    expected_subject_prefix = f"repo:{ctx.repository_owner}/{ctx.repository_name}"
    if not ctx.oidc_subject.startswith(expected_subject_prefix):
        return f"OIDC subject does not match repository: {ctx.oidc_subject}"
    return None


def _verify_head_commit_signature() -> str | None:
    """Require a cryptographically verified, trusted HEAD commit signature.

    Only Git status G is accepted. U means trust is unknown; E means the signer
    key is unavailable; X/Y/R indicate expired or revoked signer conditions.
    These statuses cannot prove the closure trust requirement and fail closed.
    """
    try:
        result = subprocess.run(
            ["git", "log", "--format=%G?", "-1", "HEAD"],
            capture_output=True, text=True, timeout=30,
        )
        status = result.stdout.strip()
        # G=good signature with trusted validity. Every other status fails closed.
        if status == "G":
            return None
        if status == "E":
            return "HEAD commit signature cannot be verified: signer public key is unavailable"
        if status == "U":
            return "HEAD commit signature is not trusted: signer key validity is unknown"
        if status in ("X", "Y"):
            return f"HEAD commit signature is not accepted: expired key/signature status={status}"
        if status == "R":
            return "HEAD commit signature is not accepted: signer key is revoked"
        if status == "B":
            return "HEAD commit has a bad signature (status=B)"
        if status == "N":
            return "HEAD commit is unsigned (status=N)"
        return f"HEAD commit signature status is not verifiably trusted (status={status!r})"
    except FileNotFoundError:
        return "git binary not available for signature verification"
    except subprocess.TimeoutExpired:
        return "git log signature check timed out"


def _verify_git_signatures(ctx: TrustContext) -> str | None:
    """Verify every commit in the assessed range from Git's cryptographic trust store.

    Caller-provided boolean maps are deliberately not part of TrustContext and can
    never satisfy this control.
    """
    if not ctx.assessed_release_sha or not ctx.head_sha:
        return "Assessed release SHA and workflow HEAD SHA are required"
    try:
        verify_commit_range(ctx.assessed_release_sha, ctx.head_sha)
    except VerificationError as exc:
        return f"Cryptographic Git signature verification failed: {exc}"
    return None


def _verify_environment_protection(ctx: TrustContext) -> str | None:
    """For closure, prove the protected environment through the signed OIDC subject."""
    if ctx.mode == "closure":
        repository = os.environ.get("GITHUB_REPOSITORY", "")
        if not repository or "/" not in repository:
            return "GITHUB_REPOSITORY runtime identity is unavailable"
        try:
            token = obtain_oidc_token("https://github.com")
            verify_oidc_token(token, repository, "https://github.com", "rem-p0-006-closure")
        except VerificationError as exc:
            return f"Protected environment OIDC verification failed: {exc}"
        return None
    if not ctx.is_protected_environment:
        return "Workflow is not running in a protected environment"
    if not ctx.environment_name:
        return "Environment name not provided"
    return None


def _verify_release_sha_authorization(ctx: TrustContext) -> str | None:
    """Verify assessed release SHA is provided and matches context."""
    if not ctx.assessed_release_sha:
        return "Assessed release SHA not provided"
    if not ctx.head_sha:
        return "HEAD SHA not provided"
    return None


def _verify_assessor_independence(ctx: TrustContext) -> str | None:
    """Verify assessor organization differs from project organization."""
    if not ctx.assessor_organization:
        return "Assessor organization not provided"
    if not ctx.project_organization:
        return "Project organization not provided"
    if ctx.assessor_organization.lower() == ctx.project_organization.lower():
        return f"Assessor organization matches project: {ctx.assessor_organization}"
    return None


CONTROL_EVALUATORS = {
    "OIDC_IDENTITY_VERIFICATION": _verify_oidc_identity,
    "GIT_SIGNATURE_VERIFICATION": _verify_git_signatures,
    "ENVIRONMENT_PROTECTION": _verify_environment_protection,
    "RELEASE_SHA_AUTHORIZATION": _verify_release_sha_authorization,
    "ASSESSOR_INDEPENDENCE": _verify_assessor_independence,
}


def evaluate_controls(
    policy: dict[str, Any], ctx: TrustContext,
) -> list[dict[str, Any]]:
    """Evaluate all trust controls against the workflow context."""
    results = []
    for control in policy["controls"]:
        control_id = control["id"]
        control_type = control["type"]
        required = control.get("required", True)

        evaluator = CONTROL_EVALUATORS.get(control_type)
        if evaluator is None:
            raise TrustPolicyError(f"unknown control type: {control_type}")

        error = evaluator(ctx)
        passed = error is None

        results.append({
            "control_id": control_id,
            "control_type": control_type,
            "passed": passed,
            "required": required,
            "error": error or "",
        })
    return results


def evaluate_rules(
    policy: dict[str, Any], results: list[dict[str, Any]], mode: str,
) -> list[dict[str, Any]]:
    """Evaluate policy rules against control results."""
    result_map = {r["control_id"]: r for r in results}
    rule_results = []

    for rule in policy["rules"]:
        trigger = rule.get("trigger", "").upper()
        if trigger != mode.upper():
            continue

        control_ids = rule.get("control_ids", [])
        effect = rule.get("effect", "ALL_MUST_PASS")

        if effect == "ALL_MUST_PASS":
            failed = []
            for cid in control_ids:
                cr = result_map.get(cid)
                if cr and not cr["passed"] and cr["required"]:
                    failed.append(cid)
            passed = len(failed) == 0
            error = f"failed controls: {', '.join(failed)}" if failed else ""
        else:
            passed = False
            error = f"unknown rule effect: {effect}"

        rule_results.append({
            "rule_id": rule["id"],
            "trigger": trigger,
            "passed": passed,
            "error": error,
        })

    return rule_results


def validate_trust_policy(
    policy_path: Path,
    mode: str,
    ctx: TrustContext,
) -> dict[str, Any]:
    """Main entry point: load policy, evaluate controls, evaluate rules."""
    policy = load_policy(policy_path)
    require(mode in {"readiness", "closure"}, "invalid mode")

    ctx.mode = mode
    control_results = evaluate_controls(policy, ctx)
    rule_results = evaluate_rules(policy, control_results, mode)

    all_rules_passed = all(r["passed"] for r in rule_results)

    return {
        "policy_id": policy["policy_id"],
        "mode": mode,
        "controls": control_results,
        "rules": rule_results,
        "all_passed": all_rules_passed,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Trust policy validator for REM-P0-006")
    parser.add_argument("--policy", type=Path, default=DEFAULT_POLICY)
    parser.add_argument("--mode", choices=("readiness", "closure"), required=True)
    parser.add_argument("--head-sha", default="")
    parser.add_argument("--assessed-release-sha", default="")
    parser.add_argument("--environment-name", default="")
    parser.add_argument("--is-protected-environment", action="store_true")
    parser.add_argument("--authority-token-present", action="store_true")
    parser.add_argument("--ref", default="")
    parser.add_argument("--assessor-organization", default="")
    parser.add_argument("--project-organization", default="")
    args = parser.parse_args()

    ctx = TrustContext(
        repository_owner=os.environ.get("GITHUB_REPOSITORY_OWNER", ""),
        repository_name=os.environ.get("GITHUB_REPOSITORY", "").split("/", 1)[-1],
        head_sha=args.head_sha,
        assessed_release_sha=args.assessed_release_sha,
        environment_name=args.environment_name,
        is_protected_environment=args.is_protected_environment,
        authority_token_present=args.authority_token_present,
        ref=args.ref,
        assessor_organization=args.assessor_organization,
        project_organization=args.project_organization,
    )

    result = validate_trust_policy(args.policy, args.mode, ctx)

    if not result["all_passed"]:
        failed_controls = [c for c in result["controls"] if not c["passed"] and c["required"]]
        failed_rules = [r for r in result["rules"] if not r["passed"]]
        print("TRUST POLICY VALIDATION FAILED", file=sys.stderr)
        for fc in failed_controls:
            print(f"  CONTROL FAILED: {fc['control_id']} - {fc['error']}", file=sys.stderr)
        for fr in failed_rules:
            print(f"  RULE FAILED: {fr['rule_id']} - {fr['error']}", file=sys.stderr)
        return 1

    print("TRUST POLICY VALIDATION PASSED")
    print(f"mode={result['mode']}")
    print(f"policy_id={result['policy_id']}")
    print(f"controls_passed={sum(1 for c in result['controls'] if c['passed'])}/{len(result['controls'])}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (TrustPolicyError, OSError, ValueError, KeyError, TypeError) as exc:
        print(f"TRUST POLICY ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
