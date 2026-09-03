#!/usr/bin/env python3
"""
SANAD — Workflow Security Policy Scanner (Structural)
======================================================
EXEC-PROMPT-010R9 Section 9.1: Structural YAML + shell content scanner.
Uses yaml.safe_load for structural analysis, falls back to raw text
inspection for shell scripts embedded in `run:` blocks.

Usage:
    python3 scripts/ci/check_workflow_security.py

Exit codes:
    0 = all workflows pass
    1 = one or more violations
"""

import os
import sys
import glob
import re

try:
    import yaml
except ImportError:
    print("ERROR: PyYAML not installed. Run: pip install pyyaml")
    sys.exit(2)

WORKFLOWS_DIR = os.path.join(os.path.dirname(__file__), "..", "..", ".github", "workflows")

# Safe contexts that should not be flagged
SAFE_CONTEXTS = [
    "Testcontainers",
    "test_",
    "fixtures/",
    "target/surefire-reports",
    "target/failsafe-reports",
    "ephemeral",
]


def is_safe_context(text):
    """Check if text is in a safe (test) context."""
    return any(safe in text for safe in SAFE_CONTEXTS)


def check_workflow_dispatch_inputs(parsed, filename):
    """Check for password-like workflow_dispatch inputs."""
    violations = []
    on_key = parsed.get("on", parsed.get(True, {}))  # yaml parses `on` as True

    if not isinstance(on_key, dict):
        return violations

    wfd = on_key.get("workflow_dispatch", {})
    if not isinstance(wfd, dict):
        return violations

    inputs = wfd.get("inputs", {})
    if not isinstance(inputs, dict):
        return violations

    for input_name, input_def in inputs.items():
        if not isinstance(input_def, dict):
            continue
        name_lower = input_name.lower()
        desc = str(input_def.get("description", "")).lower()

        # Password-like input names — exact match only to avoid false positives
        # on compound names like "force_new_jwt_secret" (which is a boolean toggle, not a secret input)
        if name_lower in ("password", "new_password", "admin_password", "secret", "new_secret",
                          "token", "new_token", "credential", "api_key", "private_key", "passphrase"):
            violations.append({
                "file": filename,
                "line": 0,
                "type": "password_dispatch_input",
                "detail": f"workflow_dispatch input '{input_name}' appears to accept a secret/password",
            })

        # Password-like descriptions
        if any(kw in desc for kw in ["password", "secret", "credential"]) and input_def.get("type") == "string":
            violations.append({
                "file": filename,
                "line": 0,
                "type": "password_dispatch_input",
                "detail": f"workflow_dispatch input '{input_name}' description mentions password/secret",
            })

    return violations


def check_environment_with_db_access(parsed, filename, raw_content):
    """Check for Production environment + direct database access patterns."""
    violations = []
    jobs = parsed.get("jobs", {})

    for job_name, job_def in jobs.items():
        if not isinstance(job_def, dict):
            continue

        environment = job_def.get("environment", "")
        if isinstance(environment, dict):
            environment = environment.get("name", "")

        is_production = str(environment).lower() in ("production", "prod")

        steps = job_def.get("steps", [])
        for step in steps:
            if not isinstance(step, dict):
                continue

            run_cmd = str(step.get("run", ""))
            uses = str(step.get("uses", ""))

            # Check for direct database mutation patterns
            if re.search(r"UPDATE\s+users\s+SET\s+password_hash", run_cmd, re.IGNORECASE):
                violations.append({
                    "file": filename,
                    "line": 0,
                    "type": "direct_password_hash_mutation",
                    "detail": f"Job '{job_name}' contains UPDATE users SET password_hash",
                })

            if re.search(r"DELETE\s+FROM\s+refresh_tokens", run_cmd, re.IGNORECASE):
                violations.append({
                    "file": filename,
                    "line": 0,
                    "type": "direct_refresh_token_deletion",
                    "detail": f"Job '{job_name}' contains DELETE FROM refresh_tokens",
                })

            # Check for psycopg2 with production secrets
            if "psycopg2" in run_cmd and is_production:
                violations.append({
                    "file": filename,
                    "line": 0,
                    "type": "production_psycopg2_access",
                    "detail": f"Job '{job_name}' uses psycopg2 with Production environment",
                })

            # Check for Render API + credential mutation combo
            if "api.render.com" in run_cmd and ("password_hash" in run_cmd or "UPDATE users" in run_cmd):
                violations.append({
                    "file": filename,
                    "line": 0,
                    "type": "render_env_credential_mutation",
                    "detail": f"Job '{job_name}' fetches Render env vars and mutates credentials",
                })

            # Check for production user enumeration
            if re.search(r"SELECT\s+.*\bid\b.*\bemail\b.*\bFROM\s+users.*LIMIT", run_cmd, re.IGNORECASE):
                if is_production or "PRODUCTION" in run_cmd or "DATABASE_URL" in run_cmd:
                    violations.append({
                        "file": filename,
                        "line": 0,
                        "type": "production_user_enumeration",
                        "detail": f"Job '{job_name}' enumerates users from database",
                    })

            # Check for printing user/tenant identifiers
            if re.search(r"print.*user_id.*tenant_id|print.*tenant_id.*user_id", run_cmd, re.IGNORECASE):
                violations.append({
                    "file": filename,
                    "line": 0,
                    "type": "identity_logging",
                    "detail": f"Job '{job_name}' prints user_id and tenant_id",
                })

            # Check for unpinned pip install with production secrets
            if re.search(r"pip\s+install.*psycopg2.*bcrypt", run_cmd) and is_production:
                violations.append({
                    "file": filename,
                    "line": 0,
                    "type": "unpinned_packages_with_secrets",
                    "detail": f"Job '{job_name}' installs unpinned packages with Production environment",
                })

    return violations


def check_permissions(parsed, filename):
    """Check for overly broad permissions."""
    violations = []

    # Top-level permissions
    perms = parsed.get("permissions", {})
    if isinstance(perms, str) and perms == "write-all":
        violations.append({
            "file": filename,
            "line": 0,
            "type": "write_all_permissions",
            "detail": "Workflow uses permissions: write-all",
        })

    # Per-job permissions
    jobs = parsed.get("jobs", {})
    for job_name, job_def in jobs.items():
        if not isinstance(job_def, dict):
            continue
        job_perms = job_def.get("permissions", {})
        if isinstance(job_perms, str) and job_perms == "write-all":
            violations.append({
                "file": filename,
                "line": 0,
                "type": "write_all_permissions",
                "detail": f"Job '{job_name}' uses permissions: write-all",
            })

        if isinstance(job_perms, dict):
            if job_perms.get("contents") == "write" and "deployment" not in job_name.lower():
                # contents:write is suspicious for non-deployment jobs
                # But allow for workflows that legitimately need it
                if not any(kw in filename.lower() for kw in ["deploy", "release", "render-env"]):
                    violations.append({
                        "file": filename,
                        "line": 0,
                        "type": "unnecessary_contents_write",
                        "detail": f"Job '{job_name}' has contents:write but is not a deployment job",
                    })

    return violations


def check_raw_patterns(raw_content, filename):
    """Check raw content for patterns that YAML structural analysis might miss."""
    violations = []

    # Check for force-push commands
    if re.search(r"git\s+push\s+--force\b(?!-with-lease)", raw_content):
        violations.append({
            "file": filename,
            "line": 0,
            "type": "force_push_command",
            "detail": "Workflow contains force-push command (not --force-with-lease)",
        })

    # Check for direct main ref update
    if re.search(r"git\s+push\s+origin\s+main\b", raw_content):
        violations.append({
            "file": filename,
            "line": 0,
            "type": "direct_main_push",
            "detail": "Workflow pushes directly to main",
        })

    return violations


def scan_workflow(filepath):
    """Scan a single workflow file using structural + raw analysis."""
    violations = []

    try:
        with open(filepath, "r", encoding="utf-8") as f:
            raw_content = f.read()
    except Exception as e:
        return [{"file": filepath, "type": "read_error", "detail": str(e)}]

    if not raw_content.strip():
        return []

    # Structural YAML analysis
    try:
        parsed = yaml.safe_load(raw_content)
    except yaml.YAMLError as e:
        violations.append({
            "file": filepath,
            "type": "yaml_parse_error",
            "detail": str(e)[:100],
        })
        return violations

    if not isinstance(parsed, dict):
        return violations

    basename = os.path.basename(filepath)

    # Skip safe contexts
    if is_safe_context(basename) or is_safe_context(raw_content[:500]):
        return violations

    # Run all checks
    violations.extend(check_workflow_dispatch_inputs(parsed, basename))
    violations.extend(check_environment_with_db_access(parsed, basename, raw_content))
    violations.extend(check_permissions(parsed, basename))
    violations.extend(check_raw_patterns(raw_content, basename))

    return violations


def main():
    if not os.path.isdir(WORKFLOWS_DIR):
        print(f"ERROR: Workflows directory not found: {WORKFLOWS_DIR}")
        return 1

    workflow_files = sorted(
        glob.glob(os.path.join(WORKFLOWS_DIR, "*.yml")) +
        glob.glob(os.path.join(WORKFLOWS_DIR, "*.yaml"))
    )

    if not workflow_files:
        print("No workflow files found.")
        return 0

    print(f"Scanning {len(workflow_files)} workflow files (structural analysis)...")
    print()

    all_violations = []
    for wf in workflow_files:
        basename = os.path.basename(wf)
        violations = scan_workflow(wf)
        if violations:
            all_violations.extend(violations)
            print(f"  ❌ {basename}: {len(violations)} violation(s)")
            for v in violations:
                print(f"     [{v['type']}] {v['detail']}")
        else:
            print(f"  ✅ {basename}: PASS")

    print()
    if all_violations:
        print(f"FAILED: {len(all_violations)} security violation(s) found.")
        return 1
    else:
        print(f"PASSED: All {len(workflow_files)} workflow files comply with security policy.")
        return 0


if __name__ == "__main__":
    sys.exit(main())
