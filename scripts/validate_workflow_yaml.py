#!/usr/bin/env python3
"""Validate all workflow YAML files in .github/workflows/."""
import sys
from pathlib import Path
import yaml

ROOT = Path("/home/z/my-project")
WORKFLOW_DIR = ROOT / ".github" / "workflows"

# Files explicitly required by the executive order
REQUIRED_FILES = [
    WORKFLOW_DIR / "health-production-verification.yml",
    WORKFLOW_DIR / "commercial-go-live.yml",
]

FAILURES = 0

print("=" * 70)
print("YAML Validation — .github/workflows/")
print("=" * 70)

# Validate the two required files first
for f in REQUIRED_FILES:
    if not f.exists():
        print(f"FAIL: required file missing: {f.relative_to(ROOT)}")
        FAILURES += 1
        continue
    try:
        with f.open("r", encoding="utf-8") as handle:
            yaml.safe_load(handle)
        print(f"YAML PASS: {f.relative_to(ROOT)}")
    except yaml.YAMLError as e:
        print(f"YAML FAIL: {f.relative_to(ROOT)}")
        print(f"  error: {e}")
        FAILURES += 1

# Validate every other workflow file too
for f in sorted(WORKFLOW_DIR.glob("*.yml")):
    if f in REQUIRED_FILES:
        continue
    try:
        with f.open("r", encoding="utf-8") as handle:
            yaml.safe_load(handle)
        print(f"YAML PASS: {f.relative_to(ROOT)}")
    except yaml.YAMLError as e:
        print(f"YAML FAIL: {f.relative_to(ROOT)}")
        print(f"  error: {e}")
        FAILURES += 1

print("=" * 70)
if FAILURES == 0:
    print(f"RESULT: ALL YAML FILES VALID ({len(list(WORKFLOW_DIR.glob('*.yml')))} files)")
    sys.exit(0)
else:
    print(f"RESULT: {FAILURES} FILE(S) FAILED")
    sys.exit(1)
