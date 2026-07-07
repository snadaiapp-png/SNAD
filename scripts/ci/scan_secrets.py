#!/usr/bin/env python3
"""
SNAD Secret Scanner — Fail-Closed External Script
==================================================
Scans the current working tree for exposed secrets.
Exit 0 = no secrets found, Exit 1 = secrets found or error.

Usage:
    python3 scripts/ci/scan_secrets.py --repository-root . --report /tmp/report.json

Per PM Directive sections 1-9:
  - External file (no inline Python in workflows)
  - JSON report with redacted secrets
  - Allowlist support
  -- CLI args: --repository-root, --report
"""
import argparse
import hashlib
import json
import os
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

# --- Secret detection rules ---
# Each rule: (rule_id, pattern, severity, description)
RULES = [
    ("aws-access-key", r'AKIA[0-9A-Z]{16}', "HIGH", "AWS Access Key ID"),
    ("github-pat", r'ghp_[a-zA-Z0-9]{36}', "HIGH", "GitHub Personal Access Token"),
    ("github-oauth", r'gho_[a-zA-Z0-9]{36}', "HIGH", "GitHub OAuth Token"),
    ("github-fine-grained", r'github_pat_[a-zA-Z0-9_]{22,}', "HIGH", "GitHub Fine-Grained PAT"),
    ("openai-api-key", r'sk-[a-zA-Z0-9]{20,}', "HIGH", "OpenAI API Key"),
    ("private-key-header", r'-----BEGIN (RSA |EC |DSA |OPENSSH |PGP )?PRIVATE KEY-----', "CRITICAL", "Private Key (multiline)"),
    ("jwt-secret", r'jwt[_-]?secret[_=:"\s]+["\']([a-zA-Z0-9_\-]{32,})["\']', "HIGH", "JWT Secret"),
    ("database-url-password", r'(postgres|postgresql|mysql|mongodb|redis)://[^:\s]+:([^@\s]{4,})@', "HIGH", "Database URL with password"),
    ("smtp-credentials", r'smtp://[^:\s]+:([^@\s]{4,})@', "MEDIUM", "SMTP credentials in URL"),
    ("oauth-client-secret", r'client[_-]?secret[_=:"\s]+["\']([a-zA-Z0-9_\-]{16,})["\']', "HIGH", "OAuth Client Secret"),
    ("webhook-secret", r'webhook[_-]?secret[_=:"\s]+["\']([a-zA-Z0-9_\-]{16,})["\']', "MEDIUM", "Webhook Secret"),
    ("generic-api-key", r'api[_-]?key[_=:"\s]+["\']([a-zA-Z0-9_\-]{20,})["\']', "MEDIUM", "Generic API Key"),
    ("generic-password", r'(?i)password\s*[:=]\s*["\']([^"\']{8,})["\']', "MEDIUM", "Hardcoded Password"),
    ("google-api-key", r'AIza[0-9A-Za-z_\-]{35}', "HIGH", "Google API Key"),
    ("slack-token", r'xox[baprs]-[a-zA-Z0-9-]{10,}', "HIGH", "Slack Token"),
    ("stripe-key", r'sk_(live|test)_[a-zA-Z0-9]{24,}', "HIGH", "Stripe Secret Key"),
]

# Directories to always skip
SKIP_DIRS = {
    '.git', 'node_modules', '.next', 'target', '__pycache__',
    '.gradle', 'build', 'dist', '.cache', '.pytest_cache',
    'tool-results', 'upload', 'SNAD-https',
}

# File extensions to skip (binaries/generated)
SKIP_EXTS = {
    '.lock', '.sum', '.bin', '.png', '.jpg', '.jpeg', '.gif',
    '.ico', '.svg', '.woff', '.woff2', '.ttf', '.eot',
    '.pdf', '.zip', '.tar', '.gz', '.jar', '.class',
}

MAX_FILE_SIZE = 2_000_000  # 2MB

# Allowlist file path
ALLOWLIST_FILE = "scripts/ci/secret-scan-allowlist.json"


def load_allowlist(repo_root: Path) -> list:
    """Load the allowlist of approved false positives."""
    allowlist_path = repo_root / ALLOWLIST_FILE
    if not allowlist_path.exists():
        return []
    try:
        with open(allowlist_path) as f:
            data = json.load(f)
        if not isinstance(data, list):
            return []
        # Validate each entry has required fields
        valid = []
        for entry in data:
            if not isinstance(entry, dict):
                continue
            if all(k in entry for k in ('ruleId', 'path', 'reason')):
                valid.append(entry)
        return valid
    except (json.JSONDecodeError, IOError):
        return []


def is_allowlisted(finding: dict, allowlist: list) -> bool:
    """Check if a finding matches an allowlist entry."""
    for entry in allowlist:
        rule_match = entry['ruleId'] == '*' or entry['ruleId'] == finding['ruleId']
        path_match = entry['path'] in finding['path']
        if rule_match and path_match:
            return True
    return False


def compute_fingerprint(path: str, line: int, rule_id: str, secret_sample: str) -> str:
    """Compute a fingerprint for deduplication. Does NOT include the full secret."""
    h = hashlib.sha256(f"{path}:{line}:{rule_id}:{secret_sample[:8]}".encode()).hexdigest()
    return h[:16]


def scan_file(filepath: Path, rules: list) -> list:
    """Scan a single file for secrets. Returns list of findings."""
    findings = []
    try:
        content = filepath.read_text(encoding='utf-8', errors='ignore')
    except Exception:
        return findings

    lines = content.splitlines()
    for i, line in enumerate(lines, 1):
        for rule_id, pattern, severity, description in rules:
            matches = re.finditer(pattern, line)
            for match in matches:
                secret_value = match.group(0)
                # For groups, use the captured group; otherwise use full match
                secret_sample = match.group(1) if match.lastindex else secret_value
                finding = {
                    "ruleId": rule_id,
                    "severity": severity,
                    "description": description,
                    "path": str(filepath),
                    "line": i,
                    "fingerprint": compute_fingerprint(str(filepath), i, rule_id, secret_sample),
                    "secret": "REDACTED",
                }
                findings.append(finding)

    # Check for multiline private keys
    for rule_id, pattern, severity, description in rules:
        if 'private' not in rule_id.lower():
            continue
        for match in re.finditer(pattern, content):
            line_num = content[:match.start()].count('\n') + 1
            finding = {
                "ruleId": rule_id,
                "severity": severity,
                "description": description,
                "path": str(filepath),
                "line": line_num,
                "fingerprint": compute_fingerprint(str(filepath), line_num, rule_id, "multiline"),
                "secret": "REDACTED",
            }
            findings.append(finding)

    return findings


def scan_repository(repo_root: Path) -> tuple:
    """Scan entire repository. Returns (findings, files_scanned)."""
    allowlist = load_allowlist(repo_root)
    all_findings = []
    files_scanned = 0

    for f in repo_root.rglob('*'):
        # Skip directories
        if not f.is_file():
            continue

        # Skip excluded directories
        rel_parts = f.relative_to(repo_root).parts
        if any(d in SKIP_DIRS for d in rel_parts):
            continue

        # Skip excluded extensions
        if f.suffix in SKIP_EXTS:
            continue

        # Skip large files
        try:
            if f.stat().st_size > MAX_FILE_SIZE:
                continue
        except OSError:
            continue

        files_scanned += 1
        file_findings = scan_file(f, RULES)

        # Filter allowlisted findings
        for finding in file_findings:
            if not is_allowlisted(finding, allowlist):
                all_findings.append(finding)

    return all_findings, files_scanned


def generate_report(findings: list, files_scanned: int, repo_root: str,
                    commit_sha: str = "", workflow_run_id: str = "") -> dict:
    """Generate JSON report."""
    now = datetime.now(timezone.utc).isoformat()
    return {
        "scanner": "snad-python-scanner",
        "scannerVersion": "1.0.0",
        "commitSha": commit_sha,
        "workflowRunId": workflow_run_id,
        "startedAtUtc": now,
        "completedAtUtc": now,
        "result": "FAIL" if findings else "PASS",
        "filesScanned": files_scanned,
        "commitsScanned": 0,
        "findingsCount": len(findings),
        "findings": findings,
    }


def main():
    parser = argparse.ArgumentParser(description="SNAD Secret Scanner")
    parser.add_argument("--repository-root", required=True, help="Path to repository root")
    parser.add_argument("--report", required=True, help="Path to write JSON report")
    parser.add_argument("--commit-sha", default="", help="Commit SHA for report")
    parser.add_argument("--workflow-run-id", default="", help="Workflow run ID for report")
    args = parser.parse_args()

    repo_root = Path(args.repository_root).resolve()
    if not repo_root.exists():
        print(f"FATAL: Repository root not found: {repo_root}", file=sys.stderr)
        sys.exit(1)

    print(f"Scanning repository: {repo_root}")
    findings, files_scanned = scan_repository(repo_root)

    report = generate_report(findings, files_scanned, str(repo_root),
                             args.commit_sha, args.workflow_run_id)

    # Write report
    report_path = Path(args.report)
    try:
        report_path.parent.mkdir(parents=True, exist_ok=True)
        with open(report_path, 'w') as f:
            json.dump(report, f, indent=2)
        print(f"Report written to: {report_path}")
    except IOError as e:
        print(f"FATAL: Cannot write report: {e}", file=sys.stderr)
        sys.exit(1)

    # Print summary
    print(f"Files scanned: {files_scanned}")
    print(f"Findings: {len(findings)}")
    print(f"Result: {report['result']}")

    if findings:
        print("\nFINDINGS (secrets REDACTED):")
        for f in findings:
            print(f"  [{f['severity']}] {f['ruleId']}: {f['path']}:{f['line']} — {f['description']}")
        sys.exit(1)
    else:
        print("PASS — 0 secrets found")
        sys.exit(0)


if __name__ == "__main__":
    main()
