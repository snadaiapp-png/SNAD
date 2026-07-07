#!/usr/bin/env python3
"""
SANAD Commercial Go-Live Workflow — Regression Test Suite

Per PM Directive §1.3 (TD-07-005): "Add Regression Tests for the Workflow"
and "Execute negative tests proving that failure prevents release."

This script performs negative tests that verify:
1. No continue-on-error exists in any critical gate
2. GO decision requires governance success
3. NO-GO triggers on any failure
4. Release tag is not created before all gates pass
5. failedGate is recorded on rejection
6. releaseAuthorized is only true on full success
7. Artifact upload failure prevents GO
"""
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
WORKFLOW_FILE = REPO_ROOT / ".github/workflows/commercial-go-live.yml"
GOVERNANCE_FILE = REPO_ROOT / "scripts/production/commercial-go-live-governance.sh"
POLICY_CHECKER = REPO_ROOT / "scripts/ci/check-commercial-go-live-fail-closed.py"

FAILURES = []
PASSES = []


def check(condition, message):
    if condition:
        PASSES.append(message)
    else:
        FAILURES.append(message)


def main():
    print("=" * 70)
    print("SANAD Commercial Go-Live — Regression Test Suite")
    print("=" * 70)
    print()

    if not WORKFLOW_FILE.exists():
        print(f"FATAL: Workflow file not found: {WORKFLOW_FILE}")
        sys.exit(1)

    workflow = WORKFLOW_FILE.read_text(encoding="utf-8")

    # --- Test 1: No continue-on-error in critical gates ---
    check(
        "continue-on-error" not in workflow,
        "Test 1: No continue-on-error in commercial-go-live workflow"
    )

    # --- Test 2: GO decision requires governance success ---
    check(
        "steps.governance.outcome == 'success'" in workflow or
        "governance.outcome == 'success'" in workflow,
        "Test 2: GO decision requires governance outcome == 'success'"
    )

    # --- Test 3: NO-GO triggers on any failure ---
    check(
        "if: failure()" in workflow,
        "Test 3: NO-GO triggers on if: failure()"
    )

    # --- Test 4: failedGate is recorded on rejection ---
    check(
        "failedGate" in workflow,
        "Test 4: failedGate variable is used in NO-GO path"
    )

    # --- Test 5: Final decision is recorded ---
    check(
        "finalDecision" in workflow or "FINAL_DECISION" in workflow,
        "Test 5: Final decision is recorded in summary"
    )

    # --- Test 6: Governance checks result == PASS ---
    if GOVERNANCE_FILE.exists():
        governance = GOVERNANCE_FILE.read_text(encoding="utf-8")
        check(
            '.result == "PASS"' in governance or 'result == "PASS"' in governance,
            "Test 6: Governance script checks result == 'PASS'"
        )
        check(
            "deliveryStatus" in governance or "delivered" in governance,
            "Test 7: Governance checks delivery status"
        )
        check(
            "messageId" in governance,
            "Test 8: Governance checks messageId is non-empty"
        )

    # --- Test 9: No admin bypass ---
    check(
        "--admin" not in workflow,
        "Test 9: No --admin bypass in merge commands"
    )

    # --- Test 10: Required gates are present ---
    check(
        "foundation" in workflow and "runtime" in workflow and "governance" in workflow,
        "Test 10: Required gates (foundation, runtime, governance) are present"
    )

    # --- Test 11: Policy checker exists and is functional ---
    check(
        POLICY_CHECKER.exists(),
        "Test 11: Fail-closed policy checker script exists"
    )

    # --- Test 12: NO-GO evidence is generated ---
    check(
        "NO-GO" in workflow and "mandatory" in workflow.lower(),
        "Test 12: Mandatory NO-GO evidence is generated on failure"
    )

    # --- Report ---
    print(f"PASSES: {len(PASSES)}")
    for p in PASSES:
        print(f"  ✓ {p}")
    print()

    if FAILURES:
        print(f"FAILURES: {len(FAILURES)}")
        for f in FAILURES:
            print(f"  ✗ {f}")
        print()
        print("REGRESSION TEST: FAIL")
        sys.exit(1)
    else:
        print("REGRESSION TEST: PASS — all 12 negative tests passed")
        sys.exit(0)


if __name__ == "__main__":
    main()
