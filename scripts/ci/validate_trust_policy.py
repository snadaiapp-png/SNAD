#!/usr/bin/env python3
"""Fail-closed trust-policy validation for REM-P0-006."""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_POLICY = ROOT / "docs/security/independent-assurance/trust-policy.json"
EXPECTED_OIDC_ISSUER = "https://token.actions.githubusercontent.com"
EXPECTED_OIDC_AUDIENCE = "https://github.com"


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
    commit_signatures: dict[str, bool] = field(default_factory=dict)
    environment_name: str = ""
    is_protected_environment: bool = False
    authority_token_present: bool = False
    ref: str = ""
    assessor_organization: str = ""
    project_organization: str = ""


def load_policy(path: Path) -> dict[str, Any]:
    require(path.is_file(), f"trust policy not found: {path}")
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise TrustPolicyError(f"invalid trust policy JSON: {exc}") from exc
    require(isinstance(data, dict), "trust policy must be a JSON object")
    require(data.get("schema_version") == "1.0", "trust policy schema_version must be 1.0")
    require(data.get("finding_id") == "REM-P0-006", "trust policy finding_id must be REM-P0-006")
    require(isinstance(data.get("controls"), list) and data["controls"], "controls must not be empty")
    require(isinstance(data.get("rules"), list) and data["rules"], "rules must not be empty")
    return data


def _verify_oidc_identity(ctx: TrustContext) -> str | None:
    if not ctx.oidc_issuer:
        return "OIDC issuer not available"
    if ctx.oidc_issuer != EXPECTED_OIDC_ISSUER:
        return f"OIDC issuer mismatch: {ctx.oidc_issuer}"
    if ctx.oidc_audience != EXPECTED_OIDC_AUDIENCE:
        return "OIDC audience missing or does not match the required GitHub audience"
    if not ctx.repository_owner or not ctx.repository_name:
        return "Repository owner/name not provided"
    expected_subject_prefix = f"repo:{ctx.repository_owner}/{ctx.repository_name}:"
    if not ctx.oidc_subject.startswith(expected_subject_prefix):
        return "OIDC subject does not match the exact repository identity"
    return None


def _verify_head_commit_signature() -> str | None:
    """Require a cryptographically good signature; unknown/unavailable keys fail."""
    try:
        result = subprocess.run(
            ["git", "log", "--format=%G?", "-1", "HEAD"],
            capture_output=True, text=True, timeout=30, check=False,
        )
        if result.returncode != 0:
            return f"Unable to inspect HEAD signature: {result.stderr.strip()}"
        status = result.stdout.strip()
        if status == "G":
            return None
        if status == "U":
            return "Signature key validity is unknown; trusted verification is required"
        if status == "E":
            return "Signature exists but signer public key is unavailable"
        if status == "X":
            return "Signature made by an expired key"
        if status == "Y":
            return "Signature made by a key that expired after signing"
        if status == "R":
            return "Signature made by a revoked key"
        if status == "B":
            return "Commit signature is bad"
        if status == "N":
            return "Commit is unsigned"
        return f"Unable to establish a trusted commit signature (status={status or 'empty'})"
    except FileNotFoundError:
        return "git binary not available for signature verification"
    except subprocess.TimeoutExpired:
        return "git log signature check timed out"


def _verify_git_signatures(ctx: TrustContext) -> str | None:
    """Verify commit signatures cryptographically; reject caller-supplied boolean evidence."""
    if not ctx.head_sha or not ctx.assessed_release_sha:
        return "HEAD SHA and assessed release SHA are required for signature verification"
    if ctx.commit_signatures:
        return "Caller-supplied signature booleans are not cryptographic evidence"
    if not re.fullmatch(r"[0-9a-f]{40}", ctx.head_sha) or not re.fullmatch(r"[0-9a-f]{40}", ctx.assessed_release_sha):
        return "HEAD and assessed release must be exact lowercase commit SHAs"
    allowed = {item.strip().upper() for item in os.environ.get("REM_P0_006_ALLOWED_SIGNER_FINGERPRINTS", "").split(",") if item.strip()}
    if not allowed:
        return "Trusted signer fingerprint allowlist is not configured"
    try:
        for args in (
            ["git", "cat-file", "-e", f"{ctx.assessed_release_sha}^{{commit}}"],
            ["git", "cat-file", "-e", f"{ctx.head_sha}^{{commit}}"],
            ["git", "merge-base", "--is-ancestor", ctx.assessed_release_sha, ctx.head_sha],
        ):
            result = subprocess.run(args, capture_output=True, text=True, timeout=30, check=False)
            if result.returncode != 0:
                return "Assessed release is missing or is not an ancestor of workflow HEAD"
        result = subprocess.run(["git", "rev-list", "--reverse", f"{ctx.assessed_release_sha}^..{ctx.head_sha}"],
                                capture_output=True, text=True, timeout=30, check=False)
        if result.returncode != 0:
            return "Unable to enumerate assessed commit range"
        commits = result.stdout.splitlines()
        if ctx.assessed_release_sha not in commits:
            commits.insert(0, ctx.assessed_release_sha)
        for commit in commits:
            result = subprocess.run(["git", "verify-commit", "--raw", commit],
                                    capture_output=True, text=True, timeout=30, check=False)
            output = result.stdout + "\n" + result.stderr
            if result.returncode != 0:
                return f"Commit {commit} lacks a cryptographically valid Git signature"
            fingerprints = {value.upper() for value in re.findall(r"\[GNUPG:\] VALIDSIG ([0-9A-Fa-f]+)", output)}
            if not (fingerprints & allowed):
                return f"Commit {commit} signer is not in the trusted fingerprint allowlist"
        return None
    except FileNotFoundError:
        return "git binary not available for signature verification"
    except subprocess.TimeoutExpired:
        return "Git signature or ancestry verification timed out"


def _verify_environment_protection(ctx: TrustContext) -> str | None:
    if not ctx.is_protected_environment:
        return "Workflow is not running in a verified protected environment"
    if ctx.environment_name != "rem-p0-006-closure":
        return "Required protected environment rem-p0-006-closure is not verified"
    if not ctx.authority_token_present:
        return "Closure authority token is not configured in the protected environment"
    return None


def _verify_release_sha_authorization(ctx: TrustContext) -> str | None:
    if len(ctx.assessed_release_sha) != 40 or any(c not in "0123456789abcdef" for c in ctx.assessed_release_sha):
        return "Assessed release SHA must be an exact 40-character lowercase commit SHA"
    if len(ctx.head_sha) != 40 or any(c not in "0123456789abcdef" for c in ctx.head_sha):
        return "Workflow HEAD SHA must be an exact 40-character lowercase commit SHA"
    if ctx.ref != "refs/heads/main":
        return "Closure is authorized only from refs/heads/main"
    # Cryptographic ancestry and per-commit signature verification are enforced by
    # _verify_git_signatures; do not infer them from caller-supplied JSON booleans.
    return None


def _verify_assessor_independence(ctx: TrustContext) -> str | None:
    if not ctx.assessor_organization:
        return "Assessor organization not provided"
    if not ctx.project_organization:
        return "Project organization not provided"
    if ctx.assessor_organization.strip().casefold() == ctx.project_organization.strip().casefold():
        return f"Assessor organization matches project: {ctx.assessor_organization}"
    return None


CONTROL_EVALUATORS = {
    "OIDC_IDENTITY_VERIFICATION": _verify_oidc_identity,
    "GIT_SIGNATURE_VERIFICATION": _verify_git_signatures,
    "ENVIRONMENT_PROTECTION": _verify_environment_protection,
    "RELEASE_SHA_AUTHORIZATION": _verify_release_sha_authorization,
    "ASSESSOR_INDEPENDENCE": _verify_assessor_independence,
}


def evaluate_controls(policy: dict[str, Any], ctx: TrustContext) -> list[dict[str, Any]]:
    results = []
    for control in policy["controls"]:
        control_id = control["id"]
        evaluator = CONTROL_EVALUATORS.get(control["type"])
        if evaluator is None:
            raise TrustPolicyError(f"unknown control type: {control['type']}")
        error = evaluator(ctx)
        results.append({
            "control_id": control_id,
            "control_type": control["type"],
            "passed": error is None,
            "required": control.get("required", True),
            "error": error or "",
        })
    return results


def evaluate_rules(policy: dict[str, Any], results: list[dict[str, Any]], mode: str) -> list[dict[str, Any]]:
    result_map = {result["control_id"]: result for result in results}
    rule_results = []
    for rule in policy["rules"]:
        trigger = rule.get("trigger", "").upper()
        if trigger != mode.upper():
            continue
        if rule.get("effect", "ALL_MUST_PASS") != "ALL_MUST_PASS":
            rule_results.append({"rule_id": rule["id"], "trigger": trigger, "passed": False, "error": "unknown rule effect"})
            continue
        failed = [
            cid for cid in rule.get("control_ids", [])
            if cid not in result_map or (not result_map[cid]["passed"] and result_map[cid]["required"])
        ]
        rule_results.append({
            "rule_id": rule["id"], "trigger": trigger,
            "passed": not failed,
            "error": f"failed or missing controls: {', '.join(failed)}" if failed else "",
        })
    return rule_results


def validate_trust_policy(policy_path: Path, mode: str, ctx: TrustContext) -> dict[str, Any]:
    policy = load_policy(policy_path)
    require(mode in {"readiness", "closure"}, "invalid mode")
    controls = evaluate_controls(policy, ctx)
    rules = evaluate_rules(policy, controls, mode)
    return {
        "policy_id": policy["policy_id"], "mode": mode,
        "controls": controls, "rules": rules,
        "all_passed": bool(rules) and all(rule["passed"] for rule in rules),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Fail-closed trust-policy validator for REM-P0-006")
    parser.add_argument("--policy", type=Path, default=DEFAULT_POLICY)
    parser.add_argument("--mode", choices=("readiness", "closure"), required=True)
    parser.add_argument("--oidc-issuer", default="")
    parser.add_argument("--oidc-audience", default="")
    parser.add_argument("--oidc-subject", default="")
    parser.add_argument("--repository-owner", default="")
    parser.add_argument("--repository-name", default="")
    parser.add_argument("--head-sha", default="")
    parser.add_argument("--assessed-release-sha", default="")
    parser.add_argument("--commit-signatures-json", default="{}",
                        help="Deprecated compatibility field; non-empty JSON is rejected because booleans are not cryptographic evidence")
    parser.add_argument("--environment-name", default="")
    parser.add_argument("--is-protected-environment", action="store_true")
    parser.add_argument("--authority-token-present", action="store_true")
    parser.add_argument("--ref", default="")
    parser.add_argument("--assessor-organization", default="")
    parser.add_argument("--project-organization", default="")
    args = parser.parse_args()
    try:
        commit_sigs = json.loads(args.commit_signatures_json)
        if not isinstance(commit_sigs, dict):
            raise TrustPolicyError("--commit-signatures-json must be a JSON object")
        if commit_sigs:
            raise TrustPolicyError("--commit-signatures-json is not accepted; use cryptographic Git verification")
        ctx = TrustContext(
            oidc_issuer=args.oidc_issuer, oidc_audience=args.oidc_audience,
            oidc_subject=args.oidc_subject, repository_owner=args.repository_owner,
            repository_name=args.repository_name, head_sha=args.head_sha,
            assessed_release_sha=args.assessed_release_sha, commit_signatures=commit_sigs,
            environment_name=args.environment_name,
            is_protected_environment=args.is_protected_environment,
            authority_token_present=args.authority_token_present, ref=args.ref,
            assessor_organization=args.assessor_organization,
            project_organization=args.project_organization,
        )
        result = validate_trust_policy(args.policy, args.mode, ctx)
        if not result["all_passed"]:
            print("TRUST POLICY VALIDATION FAILED", file=sys.stderr)
            for control in result["controls"]:
                if not control["passed"] and control["required"]:
                    print(f"  CONTROL FAILED: {control['control_id']} - {control['error']}", file=sys.stderr)
            for rule in result["rules"]:
                if not rule["passed"]:
                    print(f"  RULE FAILED: {rule['rule_id']} - {rule['error']}", file=sys.stderr)
            return 1
        print("TRUST POLICY VALIDATION PASSED")
        print(f"mode={result['mode']}")
        print(f"policy_id={result['policy_id']}")
        print(f"controls_passed={sum(1 for control in result['controls'] if control['passed'])}/{len(result['controls'])}")
        return 0
    except (TrustPolicyError, OSError, ValueError, KeyError, TypeError) as exc:
        print(f"TRUST POLICY ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
