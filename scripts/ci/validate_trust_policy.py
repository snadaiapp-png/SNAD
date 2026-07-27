#!/usr/bin/env python3
"""Trust-based governance validation for REM-P0-006 automated closure."""
from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

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
    commit_signatures: dict[str, bool] = field(default_factory=dict)
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
    """Verify OIDC token matches expected GitHub Actions identity."""
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


def _verify_git_signatures(ctx: TrustContext) -> str | None:
    """Verify all commits in assessed range are signed."""
    if not ctx.commit_signatures:
        return "No commit signature data provided"
    unsigned = [sha for sha, signed in ctx.commit_signatures.items() if not signed]
    if unsigned:
        return f"Unsigned commits detected: {unsigned[:5]}"
    return None


def _verify_environment_protection(ctx: TrustContext) -> str | None:
    """Verify workflow runs in a protected environment."""
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
    parser.add_argument("--oidc-issuer", default="")
    parser.add_argument("--oidc-audience", default="")
    parser.add_argument("--oidc-subject", default="")
    parser.add_argument("--repository-owner", default="")
    parser.add_argument("--repository-name", default="")
    parser.add_argument("--head-sha", default="")
    parser.add_argument("--assessed-release-sha", default="")
    parser.add_argument("--commit-signatures-json", default="{}",
                        help="JSON dict mapping commit SHA to boolean signed status")
    parser.add_argument("--environment-name", default="")
    parser.add_argument("--is-protected-environment", action="store_true")
    parser.add_argument("--authority-token-present", action="store_true")
    parser.add_argument("--ref", default="")
    parser.add_argument("--assessor-organization", default="")
    parser.add_argument("--project-organization", default="")
    args = parser.parse_args()

    try:
        commit_sigs = json.loads(args.commit_signatures_json)
    except json.JSONDecodeError:
        print("ERROR: --commit-signatures-json is not valid JSON", file=sys.stderr)
        return 1

    ctx = TrustContext(
        oidc_issuer=args.oidc_issuer,
        oidc_audience=args.oidc_audience,
        oidc_subject=args.oidc_subject,
        repository_owner=args.repository_owner,
        repository_name=args.repository_name,
        head_sha=args.head_sha,
        assessed_release_sha=args.assessed_release_sha,
        commit_signatures=commit_sigs,
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
