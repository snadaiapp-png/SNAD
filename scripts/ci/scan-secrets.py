#!/usr/bin/env python3
"""
SANAD Secret Scanner — Fail-Closed
Scans the current tree for exposed secrets.
Exit 0 = no secrets found, Exit 1 = secrets found.
"""
import re
import sys
from pathlib import Path

PATTERNS = [
    (r'AKIA[0-9A-Z]{16}', 'AWS Access Key'),
    (r'ghp_[a-zA-Z0-9]{36}', 'GitHub PAT'),
    (r'gho_[a-zA-Z0-9]{36}', 'GitHub OAuth Token'),
    (r'sk-[a-zA-Z0-9]{20}', 'OpenAI API Key'),
    (r'-----BEGIN (RSA |EC )?PRIVATE KEY-----', 'Private Key'),
    (r'password\s*[:=]\s*["\'][^"\']{8,}["\']', 'Hardcoded Password'),
]

SKIP_DIRS = {'.git', 'node_modules', '.next', 'target', '__pycache__', '.gradle'}
SKIP_EXTS = {'.lock', '.sum', '.bin'}

def main():
    violations = []
    for f in Path('.').rglob('*'):
        if any(d in f.parts for d in SKIP_DIRS):
            continue
        if f.suffix in SKIP_EXTS:
            continue
        if not f.is_file() or f.stat().st_size > 1_000_000:
            continue
        try:
            content = f.read_text(encoding='utf-8', errors='ignore')
            for pat, name in PATTERNS:
                matches = re.findall(pat, content)
                if matches:
                    violations.append(f'{f}: {name} ({len(matches)} occurrence(s))')
        except Exception:
            pass

    if violations:
        print(f'FAIL — {len(violations)} secret(s) found:')
        for v in violations:
            print(f'  SECRET FOUND: {v}')
        sys.exit(1)
    else:
        print('Secret scan: PASS — 0 secrets found')
        sys.exit(0)

if __name__ == '__main__':
    main()
