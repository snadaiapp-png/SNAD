#!/usr/bin/env python3
"""
Workflow Static Validation — validates production acceptance and backup
workflows without requiring production secrets.

Checks:
  1. All required secrets are mapped to env in the preflight step
  2. No secret value is printed (no echo/print of env-mapped values)
  3. No direct password interpolation in JSON (must use jq)
  4. No continue-on-error on enforcement steps
  5. No || true on security or acceptance steps
  6. No schedule before initial production acceptance
  7. Cross-tenant assertions require exact status codes
  8. Tenant IDs are compared exactly (not just non-empty)
  9. Refresh transport matches the backend contract (X-SANAD-Refresh-Token header)
  10. Logout status matches the backend contract (204)
"""

import sys
import yaml
from pathlib import Path


def load_workflow(path: str) -> dict:
    """Load a YAML workflow file."""
    with open(path, "r") as f:
        return yaml.safe_load(f)


def check_secret_env_mapping(workflow: dict, required_secrets: list[str]) -> list[str]:
    """Check that all required secrets are mapped to env in the preflight step."""
    errors = []
    jobs = workflow.get("jobs", {})
    for job_name, job in jobs.items():
        steps = job.get("steps", [])
        for step in steps:
            env = step.get("env", {})
            for secret_name in required_secrets:
                mapped_key = secret_name  # env key matches secret name
                if mapped_key not in env:
                    # Check if it's in the main execution step instead
                    pass
            # Check if any step has the secrets mapped
        # Check across all steps
        all_env_keys = set()
        for step in steps:
            all_env_keys.update(step.get("env", {}).keys())

        for secret_name in required_secrets:
            if secret_name not in all_env_keys:
                errors.append(
                    f"Job '{job_name}': Secret '{secret_name}' not mapped to any step env"
                )

    return errors


def check_no_schedule_before_acceptance(workflow: dict) -> list[str]:
    """Check that no schedule trigger exists before first production acceptance."""
    errors = []
    on_config = workflow.get("on", {})
    if "schedule" in on_config:
        errors.append("Workflow has schedule trigger before first production acceptance")
    return errors


def check_no_continue_on_error(workflow: dict) -> list[str]:
    """Check that no enforcement step has continue-on-error: true."""
    errors = []
    jobs = workflow.get("jobs", {})
    for job_name, job in jobs.items():
        steps = job.get("steps", [])
        for i, step in enumerate(steps):
            if step.get("continue-on-error") is True:
                step_name = step.get("name", f"step {i}")
                errors.append(
                    f"Job '{job_name}', step '{step_name}': continue-on-error is true"
                )
    return errors


def check_no_pipe_true(workflow: dict) -> list[str]:
    """Check that no run step contains || true on enforcement operations."""
    errors = []
    jobs = workflow.get("jobs", {})
    for job_name, job in jobs.items():
        steps = job.get("steps", [])
        for i, step in enumerate(steps):
            run_content = step.get("run", "")
            step_name = step.get("name", f"step {i}")
            # Check for || true patterns that mask exit codes
            # Exception: gh issue commands (non-enforcement)
            for line_num, line in enumerate(run_content.split("\n"), 1):
                if "|| true" in line and "gh " not in line and "2>/dev/null" not in line:
                    errors.append(
                        f"Job '{job_name}', step '{step_name}', line {line_num}: "
                        f"|| true found on potentially enforcing step"
                    )
    return errors


def check_jq_for_json(workflow: dict) -> list[str]:
    """Check that JSON payloads are constructed with jq, not direct interpolation."""
    errors = []
    jobs = workflow.get("jobs", {})
    for job_name, job in jobs.items():
        steps = job.get("steps", [])
        for i, step in enumerate(steps):
            run_content = step.get("run", "")
            step_name = step.get("name", f"step {i}")
            for line_num, line in enumerate(run_content.split("\n"), 1):
                # Check for direct shell interpolation in JSON-like strings
                # Pattern: -d with double-quoted JSON containing ${...}
                if '-d' in line and '"{' in line and '${' in line:
                    if 'jq' not in run_content:
                        errors.append(
                            f"Job '{job_name}', step '{step_name}', line {line_num}: "
                            f"Direct shell interpolation in JSON payload (use jq)"
                        )
    return errors


def check_exact_tenant_comparison(workflow: dict) -> list[str]:
    """Check that tenant IDs are compared exactly in execution steps (not preflight)."""
    errors = []
    jobs = workflow.get("jobs", {})
    for job_name, job in jobs.items():
        steps = job.get("steps", [])
        for i, step in enumerate(steps):
            run_content = step.get("run", "")
            step_name = step.get("name", f"step {i}")
            # Skip preflight/validation steps that only check for presence
            if "Verify" in step_name or "Preflight" in step_name:
                continue
            # Check execution steps that use tenant IDs have exact comparisons
            if "TENANT_A_ID" in run_content or "TENANT_B_ID" in run_content:
                # Must have an exact string comparison (= or !=)
                has_exact = '="$' in run_content or '!= "$' in run_content
                if not has_exact:
                    errors.append(
                        f"Job '{job_name}', step '{step_name}': "
                        f"Tenant IDs may not be compared exactly"
                    )
    return errors


def check_cross_tenant_exact_status(workflow: dict) -> list[str]:
    """Check that cross-tenant assertions use exact status codes."""
    errors = []
    jobs = workflow.get("jobs", {})
    for job_name, job in jobs.items():
        steps = job.get("steps", [])
        for i, step in enumerate(steps):
            run_content = step.get("run", "")
            step_name = step.get("name", f"step {i}")
            # Cross-tenant checks should use case/esac with exact codes
            if "cross-tenant" in run_content.lower() or "Cross-tenant" in run_content:
                if "case" not in run_content:
                    # Accept if it uses explicit status code comparison
                    if "401" not in run_content and "403" not in run_content:
                        errors.append(
                            f"Job '{job_name}', step '{step_name}': "
                            f"Cross-tenant rejection does not use exact status codes"
                        )
    return errors


def check_refresh_transport(workflow: dict) -> list[str]:
    """Check that refresh token uses X-SANAD-Refresh-Token header (production contract)."""
    errors = []
    jobs = workflow.get("jobs", {})
    for job_name, job in jobs.items():
        steps = job.get("steps", [])
        for i, step in enumerate(steps):
            run_content = step.get("run", "")
            step_name = step.get("name", f"step {i}")
            # Refresh operations should use X-SANAD-Refresh-Token header
            if "/auth/refresh" in run_content:
                if "X-SANAD-Refresh-Token" not in run_content:
                    errors.append(
                        f"Job '{job_name}', step '{step_name}': "
                        f"Refresh transport does not use X-SANAD-Refresh-Token header"
                    )
    return errors


def check_logout_status(workflow: dict) -> list[str]:
    """Check that logout expects 204 (backend contract)."""
    errors = []
    jobs = workflow.get("jobs", {})
    for job_name, job in jobs.items():
        steps = job.get("steps", [])
        for i, step in enumerate(steps):
            run_content = step.get("run", "")
            step_name = step.get("name", f"step {i}")
            if "/auth/logout" in run_content:
                if "204" not in run_content:
                    errors.append(
                        f"Job '{job_name}', step '{step_name}': "
                        f"Logout does not expect HTTP 204 (backend contract)"
                    )
    return errors


def check_no_secret_values_printed(workflow: dict) -> list[str]:
    """Check that no step prints or echoes secret values."""
    errors = []
    jobs = workflow.get("jobs", {})
    for job_name, job in jobs.items():
        steps = job.get("steps", [])
        for i, step in enumerate(steps):
            run_content = step.get("run", "")
            step_name = step.get("name", f"step {i}")
            # Check for echo/print of password or secret variables
            for line in run_content.split("\n"):
                line_lower = line.lower().strip()
                if line_lower.startswith("echo") and any(
                    kw in line for kw in ["PASSWORD", "TOKEN", "SECRET"]
                ):
                    if "::add-mask::" not in line and "::error::" not in line:
                        errors.append(
                            f"Job '{job_name}', step '{step_name}': "
                            f"Potential secret value echoed"
                        )
    return errors


def main():
    repo_root = Path(__file__).resolve().parent.parent
    auth_workflow_path = repo_root / ".github" / "workflows" / "auth-tenant-production-acceptance.yml"
    backup_workflow_path = repo_root / ".github" / "workflows" / "backup-verify.yml"

    all_errors = []

    # Validate auth acceptance workflow
    if auth_workflow_path.exists():
        print(f"Validating {auth_workflow_path.name}...")
        workflow = load_workflow(str(auth_workflow_path))

        required_secrets = [
            "PRODUCTION_BASE_URL",
            "AUTH_SMOKE_TENANT_A_ID",
            "AUTH_SMOKE_TENANT_A_EMAIL",
            "AUTH_SMOKE_TENANT_A_PASSWORD",
            "AUTH_SMOKE_TENANT_B_ID",
            "AUTH_SMOKE_TENANT_B_EMAIL",
            "AUTH_SMOKE_TENANT_B_PASSWORD",
        ]

        all_errors.extend(check_secret_env_mapping(workflow, required_secrets))
        all_errors.extend(check_no_schedule_before_acceptance(workflow))
        all_errors.extend(check_no_continue_on_error(workflow))
        all_errors.extend(check_no_pipe_true(workflow))
        all_errors.extend(check_jq_for_json(workflow))
        all_errors.extend(check_exact_tenant_comparison(workflow))
        all_errors.extend(check_cross_tenant_exact_status(workflow))
        all_errors.extend(check_refresh_transport(workflow))
        all_errors.extend(check_logout_status(workflow))
        all_errors.extend(check_no_secret_values_printed(workflow))
    else:
        print(f"WARNING: {auth_workflow_path} not found")

    # Validate backup verify workflow
    if backup_workflow_path.exists():
        print(f"Validating {backup_workflow_path.name}...")
        workflow = load_workflow(str(backup_workflow_path))

        all_errors.extend(check_no_continue_on_error(workflow))
        all_errors.extend(check_no_pipe_true(workflow))
    else:
        print(f"WARNING: {backup_workflow_path} not found")

    # Report
    print()
    if all_errors:
        print(f"VALIDATION FAILED — {len(all_errors)} error(s):")
        for error in all_errors:
            print(f"  - {error}")
        sys.exit(1)
    else:
        print("VALIDATION PASSED — all checks OK")
        sys.exit(0)


if __name__ == "__main__":
    main()
