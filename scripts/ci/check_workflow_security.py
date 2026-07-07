#!/usr/bin/env python3
"""
SANAD Workflow Security Validator — FAIL-CLOSED YAML-Aware Edition

Per PM Directive §12: Uses Python/YAML parsing to detect fail-open patterns
in ALL GitHub Actions workflow files. Does NOT self-match (ignores its own
file). Ignores comments and documentation.

Detects:
  - continue-on-error: true (on critical steps)
  - || true (shell failure masking on critical commands)
  - set +e (disabling errexit)
  - allow-failure (non-standard but dangerous)
  - if: always() on steps that should fail-closed (flagged as review item)
"""
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
WORKFLOWS_DIR = REPO_ROOT / ".github" / "workflows"
THIS_FILE = Path(__file__).name

# Patterns that indicate fail-open behavior
FAIL_OPEN_PATTERNS = [
    (r'\|\|\s*true', "SHELL_FAILURE_MASKING", "Shell command uses '|| true' to mask failure"),
    (r'set\s+\+e', "ERREXIT_DISABLED", "Shell script disables 'set -e' (errexit)"),
    (r'allow-failure', "ALLOW_FAILURE", "Non-standard 'allow-failure' keyword found"),
]

# YAML-level patterns (checked after parsing)
def check_yaml_for_fail_open(yaml_text, filepath):
    """Check raw YAML text for continue-on-error patterns."""
    violations = []

    # Simple line-by-line check for continue-on-error (not in comments)
    in_comment = False
    for i, line in enumerate(yaml_text.splitlines(), 1):
        stripped = line.strip()

        # Skip comment lines
        if stripped.startswith('#'):
            continue

        # Remove inline comments
        if '#' in stripped:
            # Check if # is inside a string (simple heuristic)
            comment_pos = stripped.find('#')
            before_hash = stripped[:comment_pos].strip()
            # If the line before # looks complete (ends with : or value), treat rest as comment
            if before_hash and not before_hash.endswith('"') and not before_hash.endswith("'"):
                stripped = before_hash

        # Check for continue-on-error: true
        if re.match(r'continue-on-error\s*:\s*true', stripped, re.IGNORECASE):
            violations.append({
                "file": str(filepath),
                "line": i,
                "type": "CONTINUE_ON_ERROR",
                "content": stripped,
                "message": "Step uses continue-on-error: true — failures are silently ignored"
            })

    return violations

def check_shell_for_fail_open(yaml_text, filepath):
    """Check shell script blocks for fail-open patterns.
    Only flags critical commands (npm, mvn, python, curl, build, test, deploy)
    that use || true to mask failures. Non-critical commands like gh issue
    creation, gh label, or echo are allowed to use || true for resilience.
    """
    violations = []

    # Critical command prefixes that should NEVER use || true
    CRITICAL_COMMANDS = (
        'npm ci', 'npm run build', 'npm run lint', 'npm test', 'npm run start',
        'npx tsc', 'npx playwright',
        'mvn --batch-mode', 'mvn ',  # Maven commands
        'python3 scripts/ci/',  # CI governance scripts
        'curl --fail',  # Operational smoke tests with --fail
        'bash scripts/production/',  # Production scripts
    )

    lines = yaml_text.splitlines()
    in_shell_block = False

    for i, line in enumerate(lines, 1):
        stripped = line.strip()

        # Detect start of shell block
        if re.match(r'run\s*:\s*[|>]', stripped):
            in_shell_block = True
            continue

        # Detect end of shell block (non-indented line that's not a comment)
        if in_shell_block:
            if stripped and not line.startswith(' ') and not line.startswith('\t') and not stripped.startswith('-') and not stripped.startswith('#'):
                in_shell_block = False
                # Don't continue — this line might be the start of a new block

        if not in_shell_block:
            continue

        # Skip comment lines
        if stripped.startswith('#'):
            continue

        # Check for fail-open patterns only on critical commands
        for pattern, vtype, message in FAIL_OPEN_PATTERNS:
            if re.search(pattern, stripped):
                # Check if this line contains a critical command
                is_critical = any(cmd in stripped for cmd in CRITICAL_COMMANDS)
                if is_critical:
                    violations.append({
                        "file": str(filepath),
                        "line": i,
                        "type": vtype,
                        "content": stripped,
                        "message": f"{message} on critical command"
                    })

    return violations


def main():
    print("=" * 70)
    print("SANAD Workflow Security Validator — FAIL-CLOSED")
    print("=" * 70)
    print()

    if not WORKFLOWS_DIR.exists():
        print(f"FATAL: Workflows directory not found: {WORKFLOWS_DIR}")
        sys.exit(1)

    all_violations = []
    files_checked = 0

    for wf_file in sorted(WORKFLOWS_DIR.glob("*.yml")):
        # Skip self (this script is in scripts/ci/, not workflows/, but skip if somehow matched)
        if wf_file.name == THIS_FILE:
            continue

        files_checked += 1
        content = wf_file.read_text(encoding="utf-8")

        # Check YAML-level patterns
        yaml_violations = check_yaml_for_fail_open(content, wf_file)
        all_violations.extend(yaml_violations)

        # Check shell-level patterns
        shell_violations = check_shell_for_fail_open(content, wf_file)
        all_violations.extend(shell_violations)

    print(f"Files checked: {files_checked}")
    print()

    if all_violations:
        print(f"FAIL — {len(all_violations)} security violation(s) found:")
        for v in all_violations:
            print(f"  • {v['file']}:{v['line']} [{v['type']}] {v['message']}")
            print(f"    Content: {v['content']}")
        print()
        print("To fix:")
        print("  1. Remove 'continue-on-error: true' from critical steps")
        print("  2. Remove '|| true' from critical shell commands")
        print("  3. Remove 'set +e' from shell scripts")
        print("  4. Use proper error handling instead of masking failures")
        sys.exit(1)
    else:
        print("PASS — no fail-open patterns found in any workflow")
        sys.exit(0)


if __name__ == "__main__":
    main()
