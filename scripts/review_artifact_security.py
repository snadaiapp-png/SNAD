#!/usr/bin/env python3
"""
SANAD — Commercial Go-Live Artifact Security Reviewer
Scans downloaded workflow artifacts for any sensitive data leakage.

Per executive order §16:
  - Passwords
  - Access Tokens / Bearer tokens
  - Refresh Tokens
  - API Keys
  - Database URLs / Passwords
  - Authorization Headers
  - Raw Session Cookies
  - Private Tenant Data
  - Unmasked Email Credentials

Exit code: 0 = clean, 1 = sensitive data found (BLOCKING)
"""
import sys
import re
from pathlib import Path

# Patterns considered sensitive — each is a (name, regex) tuple.
# False-positive guards: skip lines that look like ::add-mask::, mask prefixes,
# or template placeholders like <secret>, ${{ ... }}, or example.com.
SENSITIVE_PATTERNS = [
    ("JWT access token",
     r'eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}'),
    ("Bearer authorization header",
     r'(?i)\bAuthorization\s*:\s*Bearer\s+[A-Za-z0-9_\-\.=]{20,}'),
    ("Refresh token literal",
     r'(?i)x-sanad-refresh-token\s*:\s*[A-Za-z0-9_\-]{32,}'),
    ("API key literal",
     r'(?i)api[_-]?key\s*[:=]\s*[A-Za-z0-9]{20,}'),
    ("Database password literal",
     r'(?i)(?:PGPASSWORD|DATABASE_PASSWORD|DB_PASSWORD)\s*[:=]\s*[^\s\$"\']{6,}'),
    ("Database URL with embedded credentials",
     r'postgres(?:ql)?://[^:\s]+:[^@\s]+@[^\s]+'),
    ("Set-Cookie session value",
     r'(?i)set-cookie\s*:\s*(?:snad_refresh|session|JSESSIONID)=[^;\s]{20,}'),
    ("Email + password pair",
     r'(?i)[\w.+-]+@[\w.-]+\s*[:\s,]+\s*(?:password|passwd|pwd)\s*[:=]\s*\S+'),
    ("Private key block",
     r'-----BEGIN (?:RSA |EC |OPENSSH |)PRIVATE KEY-----'),
    ("Unmasked Identity B password",
     r'(?i)identity[_-]?b[_-]?password\s*[:=]\s*[A-Za-z0-9!@#\$%^&\*\(\)_\-\+=]{8,}'),
]

# Skip these lines entirely (false positives)
SKIP_LINE_PATTERNS = [
    r'::add-mask::',
    r'\$\{\{.*secrets\..*\}\}',
    r'<secret>',
    r'example\.com',
    r'placeholder',
    r'redact',
    r'\*\*\*\*',  # already masked
    r'XXXX',
]


def scan_file(path: Path):
    findings = []
    try:
        text = path.read_text(encoding="utf-8", errors="replace")
    except Exception as e:
        return [(0, f"read error: {e}")]

    for i, line in enumerate(text.splitlines(), 1):
        if any(re.search(p, line, re.IGNORECASE) for p in SKIP_LINE_PATTERNS):
            continue
        for name, pattern in SENSITIVE_PATTERNS:
            if re.search(pattern, line):
                findings.append((i, f"{name}: {line.strip()[:200]}"))
                break  # one finding per line is enough
    return findings


def main():
    if len(sys.argv) != 2:
        print("Usage: python3 review_artifact_security.py <artifacts_dir>")
        sys.exit(2)

    artifacts_dir = Path(sys.argv[1])
    if not artifacts_dir.exists():
        print(f"Error: {artifacts_dir} does not exist")
        sys.exit(2)

    print("=" * 70)
    print(f"SANAD — Artifact Security Review")
    print(f"Directory: {artifacts_dir}")
    print("=" * 70)

    total_findings = 0
    files_scanned = 0

    for f in sorted(artifacts_dir.rglob("*")):
        if not f.is_file():
            continue
        if f.suffix in {".zip", ".tar", ".gz", ".tgz"}:
            continue
        files_scanned += 1
        findings = scan_file(f)
        if findings:
            total_findings += len(findings)
            rel = f.relative_to(artifacts_dir)
            print(f"\n❌ {rel}: {len(findings)} sensitive finding(s)")
            for line_num, msg in findings[:10]:
                print(f"   line {line_num}: {msg}")
            if len(findings) > 10:
                print(f"   ... and {len(findings) - 10} more")

    print()
    print("=" * 70)
    print(f"Files scanned: {files_scanned}")
    print(f"Total sensitive findings: {total_findings}")
    print("=" * 70)

    if total_findings > 0:
        print("RESULT: FAIL — sensitive data found in artifacts")
        print()
        print("Per executive order §16, this requires:")
        print("  - Artifact deletion")
        print("  - Credential rotation")
        print("  - Incident recording")
        print("  - Workflow correction")
        print("  - Re-run")
        sys.exit(1)
    else:
        print("RESULT: PASS — no sensitive data in artifacts")
        sys.exit(0)


if __name__ == "__main__":
    main()
