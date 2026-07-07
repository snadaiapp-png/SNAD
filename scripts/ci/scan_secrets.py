#!/usr/bin/env python3
"""
SNAD Secret Scanner — Fail-Closed External Script
==================================================
Scans the current working tree for exposed secrets.
Exit 0 = no secrets found, Exit 1 = secrets found or error.

Per PM Directive: exact path matching, fail-closed, no wildcards.
"""
import argparse
import hashlib
import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath

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

SKIP_DIRS = {
    '.git', 'node_modules', '.next', 'target', '__pycache__',
    '.gradle', 'build', 'dist', '.cache', '.pytest_cache',
    'tool-results', 'upload', 'SNAD-https',
}

SKIP_EXTS = {
    '.lock', '.sum', '.bin', '.png', '.jpg', '.jpeg', '.gif',
    '.ico', '.svg', '.woff', '.woff2', '.ttf', '.eot',
    '.pdf', '.zip', '.tar', '.gz', '.jar', '.class',
}

MAX_FILE_SIZE = 2_000_000
ALLOWLIST_FILE = "scripts/ci/secret-scan-allowlist.json"

VALID_RULE_IDS = {r[0] for r in RULES}
REQUIRED_ALLOWLIST_FIELDS = {"ruleId", "path", "fingerprint", "reason", "owner", "approvalReference", "expirationDate"}


def load_allowlist(repo_root: Path):
    """Load and validate allowlist. Fail-closed on any error."""
    allowlist_path = repo_root / ALLOWLIST_FILE
    if not allowlist_path.exists():
        return [], []
    errors = []
    try:
        with open(allowlist_path) as f:
            data = json.load(f)
    except json.JSONDecodeError as e:
        errors.append({"path": str(allowlist_path), "errorType": "MALFORMED_JSON", "message": str(e)})
        return [], errors
    except IOError as e:
        errors.append({"path": str(allowlist_path), "errorType": "READ_ERROR", "message": str(e)})
        return [], errors

    if not isinstance(data, list):
        errors.append({"path": str(allowlist_path), "errorType": "INVALID_SCHEMA", "message": "Allowlist must be a JSON array"})
        return [], errors

    valid = []
    seen_refs = set()
    for i, entry in enumerate(data):
        if not isinstance(entry, dict):
            errors.append({"path": str(allowlist_path), "errorType": "INVALID_ENTRY", "message": f"Entry {i} is not a dict"})
            continue
        missing = REQUIRED_ALLOWLIST_FIELDS - set(entry.keys())
        if missing:
            errors.append({"path": str(allowlist_path), "errorType": "MISSING_FIELD", "message": f"Entry {i} missing: {missing}"})
            continue
        if entry["ruleId"] not in VALID_RULE_IDS:
            errors.append({"path": str(allowlist_path), "errorType": "UNKNOWN_RULE_ID", "message": f"Entry {i} unknown ruleId: {entry['ruleId']}"})
            continue
        if ".." in entry["path"] or entry["path"].startswith("/"):
            errors.append({"path": str(allowlist_path), "errorType": "UNSAFE_PATH", "message": f"Entry {i} unsafe path: {entry['path']}"})
            continue
        ref = entry.get("approvalReference", "")
        if ref in seen_refs:
            errors.append({"path": str(allowlist_path), "errorType": "DUPLICATE_REF", "message": f"Entry {i} duplicate approvalReference: {ref}"})
            continue
        seen_refs.add(ref)
        # Check expiration
        try:
            exp = datetime.strptime(entry["expirationDate"], "%Y-%m-%d")
            if exp < datetime.now():
                errors.append({"path": str(allowlist_path), "errorType": "EXPIRED_ENTRY", "message": f"Entry {i} expired: {entry['expirationDate']}"})
                continue
        except (ValueError, TypeError):
            errors.append({"path": str(allowlist_path), "errorType": "INVALID_DATE", "message": f"Entry {i} invalid date: {entry.get('expirationDate')}"})
            continue
        valid.append(entry)
    return valid, errors


def is_allowlisted(finding: dict, allowlist: list) -> bool:
    """Check if a finding matches an allowlist entry using EXACT path + ruleId + fingerprint."""
    finding_path = PurePosixPath(finding["path"])
    for entry in allowlist:
        entry_path = PurePosixPath(entry["path"])
        if (finding_path == entry_path
            and finding["ruleId"] == entry["ruleId"]
            and finding.get("fingerprint") == entry.get("fingerprint")):
            return True
    return False


def compute_fingerprint(path: str, line: int, rule_id: str, secret_sample: str) -> str:
    h = hashlib.sha256(f"{path}:{line}:{rule_id}:{secret_sample[:8]}".encode()).hexdigest()
    return h[:16]


def scan_file(filepath: Path, rules: list, scan_errors: list, repo_root: Path = None) -> list:
    """Scan a single file. Records errors instead of silently passing."""
    findings = []
    try:
        content = filepath.read_text(encoding='utf-8', errors='ignore')
    except Exception as e:
        scan_errors.append({"path": str(filepath), "errorType": "READ_ERROR", "message": str(e)})
        return findings

    # Use relative path if repo_root is provided
    try:
        rel_path = str(filepath.relative_to(repo_root)) if repo_root else str(filepath)
    except ValueError:
        rel_path = str(filepath)

    lines = content.splitlines()
    for i, line in enumerate(lines, 1):
        for rule_id, pattern, severity, description in rules:
            try:
                matches = re.finditer(pattern, line)
                for match in matches:
                    secret_value = match.group(0)
                    secret_sample = match.group(1) if match.lastindex else secret_value
                    finding = {
                        "ruleId": rule_id,
                        "severity": severity,
                        "description": description,
                        "path": rel_path,
                        "line": i,
                        "fingerprint": compute_fingerprint(rel_path, i, rule_id, secret_sample),
                        "secret": "REDACTED",
                    }
                    findings.append(finding)
            except re.error as e:
                scan_errors.append({"path": rel_path, "errorType": "REGEX_ERROR", "message": f"Rule {rule_id}: {e}"})
                continue

    # Multiline private keys
    for rule_id, pattern, severity, description in rules:
        if 'private' not in rule_id.lower():
            continue
        try:
            for match in re.finditer(pattern, content):
                line_num = content[:match.start()].count('\n') + 1
                finding = {
                    "ruleId": rule_id,
                    "severity": severity,
                    "description": description,
                    "path": rel_path,
                    "line": line_num,
                    "fingerprint": compute_fingerprint(rel_path, line_num, rule_id, "multiline"),
                    "secret": "REDACTED",
                }
                findings.append(finding)
        except re.error as e:
            scan_errors.append({"path": rel_path, "errorType": "REGEX_ERROR", "message": f"Rule {rule_id}: {e}"})

    return findings


def scan_repository(repo_root: Path):
    """Scan repository. Returns (findings, files_scanned, scan_errors)."""
    allowlist, allowlist_errors = load_allowlist(repo_root)
    all_findings = []
    files_scanned = 0
    scan_errors = list(allowlist_errors)
    skipped_files = []

    for f in repo_root.rglob('*'):
        if not f.is_file():
            continue
        rel_parts = f.relative_to(repo_root).parts
        if any(d in SKIP_DIRS for d in rel_parts):
            continue
        if f.suffix in SKIP_EXTS:
            continue
        try:
            if f.stat().st_size > MAX_FILE_SIZE:
                # Stream-scan large files line by line instead of skipping
                try:
                    with open(f, 'r', encoding='utf-8', errors='ignore') as fh:
                        line_num = 0
                        for line in fh:
                            line_num += 1
                            for rule_id, pattern, severity, description in RULES:
                                try:
                                    for match in re.finditer(pattern, line):
                                        secret_value = match.group(0)
                                        secret_sample = match.group(1) if match.lastindex else secret_value
                                        finding = {
                                            "ruleId": rule_id,
                                            "severity": severity,
                                            "description": description,
                                            "path": str(f.relative_to(repo_root)),
                                            "line": line_num,
                                            "fingerprint": compute_fingerprint(str(f.relative_to(repo_root)), line_num, rule_id, secret_sample),
                                            "secret": "REDACTED",
                                        }
                                        if not is_allowlisted(finding, allowlist):
                                            all_findings.append(finding)
                                except re.error:
                                    pass
                    files_scanned += 1
                except Exception as e:
                    scan_errors.append({"path": str(f), "errorType": "STREAM_READ_ERROR", "message": str(e)})
                continue
        except OSError as e:
            scan_errors.append({"path": str(f), "errorType": "STAT_ERROR", "message": str(e)})
            continue

        files_scanned += 1
        file_findings = scan_file(f, RULES, scan_errors, repo_root)
        for finding in file_findings:
            if not is_allowlisted(finding, allowlist):
                all_findings.append(finding)

    return all_findings, files_scanned, scan_errors, skipped_files


def generate_report(findings, files_scanned, scan_errors, skipped_files, repo_root, commit_sha="", workflow_run_id=""):
    now = datetime.now(timezone.utc).isoformat()
    result = "FAIL" if findings or scan_errors else "PASS"
    return {
        "scanner": "snad-policy-supplement",
        "role": "defense-in-depth-current-tree-policy-check",
        "historyScan": False,
        "scannerVersion": "2.0.0",
        "commitSha": commit_sha,
        "workflowRunId": workflow_run_id,
        "startedAtUtc": now,
        "completedAtUtc": now,
        "result": result,
        "filesScanned": files_scanned,
        "commitsScanned": 0,
        "findingsCount": len(findings),
        "findings": findings,
        "scanErrors": scan_errors,
        "skippedFiles": skipped_files,
    }


def main():
    parser = argparse.ArgumentParser(description="SNAD Secret Scanner")
    parser.add_argument("--repository-root", required=True)
    parser.add_argument("--report", required=True)
    parser.add_argument("--commit-sha", default="")
    parser.add_argument("--workflow-run-id", default="")
    args = parser.parse_args()

    repo_root = Path(args.repository_root).resolve()
    if not repo_root.exists():
        print(f"FATAL: Repository root not found: {repo_root}", file=sys.stderr)
        sys.exit(1)

    print(f"Scanning repository: {repo_root}")
    findings, files_scanned, scan_errors, skipped_files = scan_repository(repo_root)

    report = generate_report(findings, files_scanned, scan_errors, skipped_files, str(repo_root),
                             args.commit_sha, args.workflow_run_id)

    try:
        report_path = Path(args.report)
        report_path.parent.mkdir(parents=True, exist_ok=True)
        with open(report_path, 'w') as f:
            json.dump(report, f, indent=2)
        print(f"Report written to: {report_path}")
    except IOError as e:
        print(f"FATAL: Cannot write report: {e}", file=sys.stderr)
        sys.exit(1)

    print(f"Files scanned: {files_scanned}")
    print(f"Findings: {len(findings)}")
    print(f"Scan errors: {len(scan_errors)}")
    print(f"Result: {report['result']}")

    if findings:
        print("\nFINDINGS (secrets REDACTED):")
        for f in findings:
            print(f"  [{f['severity']}] {f['ruleId']}: {f['path']}:{f['line']} — {f['description']}")
    if scan_errors:
        print("\nSCAN ERRORS:")
        for e in scan_errors:
            print(f"  [{e['errorType']}] {e['path']} — {e['message']}")

    if findings or scan_errors:
        sys.exit(1)
    else:
        print("PASS — 0 secrets found, 0 scan errors")
        sys.exit(0)


if __name__ == "__main__":
    main()
