#!/usr/bin/env python3
"""Structural security scanner for SANAD GitHub Actions workflows."""

from __future__ import annotations

import glob
import os
import re
import sys
from typing import Any

try:
    import yaml
except ImportError:
    print("ERROR: PyYAML not installed. Run: pip install pyyaml")
    raise SystemExit(2)

WORKFLOWS_DIR = os.path.join(
    os.path.dirname(__file__), "..", "..", ".github", "workflows"
)

SAFE_CONTEXTS = [
    "Testcontainers",
    "test_",
    "fixtures/",
    "target/surefire-reports",
    "target/failsafe-reports",
    "ephemeral",
]

# These workflows legitimately need contents:write for deployment metadata,
# release tags, or protected runtime recovery. Keep this allowlist exact.
CONTENTS_WRITE_WORKFLOWS = {
    "commercial-go-live.yml",
    "production-release.yml",
}
CONTENTS_WRITE_FILENAME_MARKERS = ("deploy", "release", "render-env")


def violation(filename: str, kind: str, detail: str) -> dict[str, Any]:
    return {"file": filename, "line": 0, "type": kind, "detail": detail}


def is_safe_context(text: str) -> bool:
    return any(safe in text for safe in SAFE_CONTEXTS)


def check_workflow_dispatch_inputs(
    parsed: dict[str, Any], filename: str
) -> list[dict[str, Any]]:
    violations: list[dict[str, Any]] = []
    on_key = parsed.get("on", parsed.get(True, {}))
    if not isinstance(on_key, dict):
        return violations

    workflow_dispatch = on_key.get("workflow_dispatch", {})
    if not isinstance(workflow_dispatch, dict):
        return violations

    inputs = workflow_dispatch.get("inputs", {})
    if not isinstance(inputs, dict):
        return violations

    forbidden_names = {
        "password",
        "new_password",
        "admin_password",
        "secret",
        "new_secret",
        "token",
        "new_token",
        "credential",
        "api_key",
        "private_key",
        "passphrase",
    }

    for input_name, input_def in inputs.items():
        if not isinstance(input_def, dict):
            continue
        name_lower = str(input_name).lower()
        description = str(input_def.get("description", "")).lower()

        if name_lower in forbidden_names:
            violations.append(
                violation(
                    filename,
                    "password_dispatch_input",
                    f"workflow_dispatch input '{input_name}' appears to accept a secret/password",
                )
            )

        if (
            any(word in description for word in ("password", "secret", "credential"))
            and input_def.get("type") == "string"
        ):
            violations.append(
                violation(
                    filename,
                    "password_dispatch_input",
                    f"workflow_dispatch input '{input_name}' description mentions password/secret",
                )
            )

    return violations


def check_environment_with_db_access(
    parsed: dict[str, Any], filename: str, raw_content: str
) -> list[dict[str, Any]]:
    del raw_content
    violations: list[dict[str, Any]] = []
    jobs = parsed.get("jobs", {})
    if not isinstance(jobs, dict):
        return violations

    for job_name, job_def in jobs.items():
        if not isinstance(job_def, dict):
            continue

        environment = job_def.get("environment", "")
        if isinstance(environment, dict):
            environment = environment.get("name", "")
        is_production = str(environment).lower() in ("production", "prod")

        steps = job_def.get("steps", [])
        if not isinstance(steps, list):
            continue

        for step in steps:
            if not isinstance(step, dict):
                continue
            run_command = str(step.get("run", ""))

            if re.search(
                r"UPDATE\s+users\s+SET\s+password_hash",
                run_command,
                re.IGNORECASE,
            ):
                violations.append(
                    violation(
                        filename,
                        "direct_password_hash_mutation",
                        f"Job '{job_name}' contains UPDATE users SET password_hash",
                    )
                )

            if re.search(
                r"DELETE\s+FROM\s+refresh_tokens", run_command, re.IGNORECASE
            ):
                violations.append(
                    violation(
                        filename,
                        "direct_refresh_token_deletion",
                        f"Job '{job_name}' contains DELETE FROM refresh_tokens",
                    )
                )

            if "psycopg2" in run_command and is_production:
                violations.append(
                    violation(
                        filename,
                        "production_psycopg2_access",
                        f"Job '{job_name}' uses psycopg2 with Production environment",
                    )
                )

            if "api.render.com" in run_command and (
                "password_hash" in run_command or "UPDATE users" in run_command
            ):
                violations.append(
                    violation(
                        filename,
                        "render_env_credential_mutation",
                        f"Job '{job_name}' fetches Render env vars and mutates credentials",
                    )
                )

            if re.search(
                r"SELECT\s+.*\bid\b.*\bemail\b.*\bFROM\s+users.*LIMIT",
                run_command,
                re.IGNORECASE,
            ) and (is_production or "PRODUCTION" in run_command or "DATABASE_URL" in run_command):
                violations.append(
                    violation(
                        filename,
                        "production_user_enumeration",
                        f"Job '{job_name}' enumerates users from database",
                    )
                )

            if re.search(
                r"print.*user_id.*tenant_id|print.*tenant_id.*user_id",
                run_command,
                re.IGNORECASE,
            ):
                violations.append(
                    violation(
                        filename,
                        "identity_logging",
                        f"Job '{job_name}' prints user_id and tenant_id",
                    )
                )

            if (
                re.search(r"pip\s+install.*psycopg2.*bcrypt", run_command)
                and is_production
            ):
                violations.append(
                    violation(
                        filename,
                        "unpinned_packages_with_secrets",
                        f"Job '{job_name}' installs unpinned packages with Production environment",
                    )
                )

    return violations


def check_permissions(
    parsed: dict[str, Any], filename: str
) -> list[dict[str, Any]]:
    violations: list[dict[str, Any]] = []
    permissions = parsed.get("permissions", {})
    if permissions == "write-all":
        violations.append(
            violation(filename, "write_all_permissions", "Workflow uses permissions: write-all")
        )

    jobs = parsed.get("jobs", {})
    if not isinstance(jobs, dict):
        return violations

    filename_lower = filename.lower()
    contents_write_allowed = (
        filename_lower in CONTENTS_WRITE_WORKFLOWS
        or any(marker in filename_lower for marker in CONTENTS_WRITE_FILENAME_MARKERS)
    )

    for job_name, job_def in jobs.items():
        if not isinstance(job_def, dict):
            continue
        job_permissions = job_def.get("permissions", {})

        if job_permissions == "write-all":
            violations.append(
                violation(
                    filename,
                    "write_all_permissions",
                    f"Job '{job_name}' uses permissions: write-all",
                )
            )
            continue

        if (
            isinstance(job_permissions, dict)
            and job_permissions.get("contents") == "write"
            and not contents_write_allowed
        ):
            violations.append(
                violation(
                    filename,
                    "unnecessary_contents_write",
                    f"Job '{job_name}' has contents:write but is not an approved deployment/release job",
                )
            )

    return violations


def check_raw_patterns(raw_content: str, filename: str) -> list[dict[str, Any]]:
    violations: list[dict[str, Any]] = []

    if re.search(r"git\s+push\s+--force\b(?!-with-lease)", raw_content):
        violations.append(
            violation(
                filename,
                "force_push_command",
                "Workflow contains force-push command (not --force-with-lease)",
            )
        )

    if re.search(r"git\s+push\s+origin\s+main\b", raw_content):
        violations.append(
            violation(
                filename,
                "direct_main_push",
                "Workflow pushes directly to main",
            )
        )

    return violations


def scan_workflow(filepath: str) -> list[dict[str, Any]]:
    try:
        with open(filepath, "r", encoding="utf-8") as file_handle:
            raw_content = file_handle.read()
    except OSError as error:
        return [
            {
                "file": filepath,
                "line": 0,
                "type": "read_error",
                "detail": str(error),
            }
        ]

    if not raw_content.strip():
        return []

    try:
        parsed = yaml.safe_load(raw_content)
    except yaml.YAMLError as error:
        return [
            {
                "file": filepath,
                "line": 0,
                "type": "yaml_parse_error",
                "detail": str(error)[:100],
            }
        ]

    if not isinstance(parsed, dict):
        return []

    filename = os.path.basename(filepath)
    if is_safe_context(filename) or is_safe_context(raw_content[:500]):
        return []

    violations: list[dict[str, Any]] = []
    violations.extend(check_workflow_dispatch_inputs(parsed, filename))
    violations.extend(check_environment_with_db_access(parsed, filename, raw_content))
    violations.extend(check_permissions(parsed, filename))
    violations.extend(check_raw_patterns(raw_content, filename))
    return violations


def main() -> int:
    if not os.path.isdir(WORKFLOWS_DIR):
        print(f"ERROR: Workflows directory not found: {WORKFLOWS_DIR}")
        return 1

    workflow_files = sorted(
        glob.glob(os.path.join(WORKFLOWS_DIR, "*.yml"))
        + glob.glob(os.path.join(WORKFLOWS_DIR, "*.yaml"))
    )
    if not workflow_files:
        print("No workflow files found.")
        return 0

    print(f"Scanning {len(workflow_files)} workflow files (structural analysis)...")
    print()

    all_violations: list[dict[str, Any]] = []
    for workflow_file in workflow_files:
        filename = os.path.basename(workflow_file)
        violations = scan_workflow(workflow_file)
        if violations:
            all_violations.extend(violations)
            print(f"  ❌ {filename}: {len(violations)} violation(s)")
            for item in violations:
                print(f"     [{item['type']}] {item['detail']}")
        else:
            print(f"  ✅ {filename}: PASS")

    print()
    if all_violations:
        print(f"FAILED: {len(all_violations)} security violation(s) found.")
        return 1

    print(f"PASSED: All {len(workflow_files)} workflow files comply with security policy.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
