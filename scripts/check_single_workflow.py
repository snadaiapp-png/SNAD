#!/usr/bin/env python3
"""
Single-file workflow security check.
Usage: python3 scripts/check_single_workflow.py <file.yml>
"""
import sys
import re
from pathlib import Path
import yaml

def scan(path: Path) -> int:
    rel = path
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines()
    critical = []
    warnings = []

    try:
        doc = yaml.safe_load(text)
    except yaml.YAMLError as e:
        print(f"YAML parse error: {e}")
        return 1

    on = doc.get("on") or doc.get(True)
    if isinstance(on, dict):
        wfd = on.get("workflow_dispatch")
        if isinstance(wfd, dict):
            inputs = wfd.get("inputs") or {}
            for name, spec in inputs.items():
                if not isinstance(spec, dict):
                    continue
                lname = name.lower()
                if any(s in lname for s in ["password", "secret", "token", "api_key", "private_key"]):
                    critical.append((0, f"workflow_dispatch input '{name}' looks like a credential — use secrets instead"))
                if spec.get("type") == "password":
                    critical.append((0, f"workflow_dispatch input '{name}' uses type=password — forbidden"))

    perms = doc.get("permissions")
    if isinstance(perms, dict):
        for k, v in perms.items():
            if k in ("pull-requests", "issues") and v == "write":
                warnings.append((0, f"permissions.{k}: write — verify it's actually needed"))

    if isinstance(on, dict) and "pull_request_target" in on:
        critical.append((0, "pull_request_target trigger is unsafe for untrusted PRs"))

    direct_interp_pattern = re.compile(r"\$\{\{\s*inputs\.\w+\s*\}\}")
    in_run_block = False
    for i, line in enumerate(lines, 1):
        stripped = line.strip()
        if stripped == "run:":
            in_run_block = True
            continue
        if in_run_block and direct_interp_pattern.search(line):
            # Check if this line is an env: declaration (allowed)
            if re.match(r"^\s+[A-Z_]+:\s+\$\{\{ inputs\.", line):
                continue
            critical.append((i, f"direct input interpolation in run block: {stripped[:120]}"))

    print(f"File: {path}")
    print(f"Critical findings: {len(critical)}")
    for l, m in critical:
        print(f"  line {l}: {m}")
    print(f"Warnings: {len(warnings)}")
    for l, m in warnings:
        print(f"  line {l}: {m}")
    print()
    if critical:
        print("RESULT: FAIL")
        return 1
    else:
        print("RESULT: PASS")
        return 0

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python3 check_single_workflow.py <file.yml>")
        sys.exit(2)
    sys.exit(scan(Path(sys.argv[1])))
