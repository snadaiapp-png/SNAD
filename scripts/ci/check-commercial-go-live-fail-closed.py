#!/usr/bin/env python3
"""SANAD Commercial Go-Live Fail-Closed Policy Checker."""
import sys
from pathlib import Path
REPO_ROOT = Path(__file__).resolve().parent.parent.parent
WORKFLOW_FILE = REPO_ROOT / ".github/workflows/commercial-go-live.yml"
GOVERNANCE_FILE = REPO_ROOT / "scripts/production/commercial-go-live-governance.sh"
FAILURES = []
def check(condition, message):
    if not condition:
        FAILURES.append(message)
def main():
    workflow = WORKFLOW_FILE.read_text(encoding="utf-8")
    governance = GOVERNANCE_FILE.read_text(encoding="utf-8")
    check("continue-on-error" not in workflow, "FAIL: continue-on-error found")
    check("steps.governance.outcome == 'success'" in workflow, "FAIL: GO does not require governance")
    check("if: failure()" in workflow, "FAIL: NO-GO does not trigger on all failures")
    check('.result == "PASS"' in governance, "FAIL: governance does not check result == PASS")
    if FAILURES:
        print("Commercial Go-Live fail-closed policy: FAIL")
        for f in FAILURES:
            print(f"  X {f}")
        sys.exit(1)
    else:
        print("Commercial Go-Live fail-closed policy: PASS")
        sys.exit(0)
if __name__ == "__main__":
    main()
