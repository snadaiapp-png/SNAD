#!/usr/bin/env python3
"""
Stage 04A.3.6.2 — Closure-Debt Register Reconciliation Gate.

Computes debt counts directly from the `inheritedDebt` and `newDebt`
arrays in the closure-debt register JSON, then compares them against
the `summary` block. Any mismatch fails CI.

Computed metrics:
    totalDebtCount
    closedDebtCount
    openDebtCount
    inProgressDebtCount
    reopenedDebtCount
    blockedDebtCount
    openBlockingP1DebtCount
    blockingDebtIds
    closedDebtIds

Usage:
    python3 scripts/ci/check_closure_debt_register.py docs/infrastructure-hardening/closure-debt-register.json

Exit codes:
    0 — summary matches computed counts
    1 — summary mismatch (printed in detail)
    2 — unexpected error (file missing, parse failure)
"""
from __future__ import annotations

import json
import sys
from pathlib import Path


def compute_counts(debts: list[dict]) -> dict:
    """Compute reconciliation metrics from a list of debt objects.

    Status counting follows the convention used by the existing
    `tests/test_closure_debt_register.py::test_summary_counts_match_items`:
    each status (OPEN, IN_PROGRESS, BLOCKED, READY_FOR_VERIFICATION,
    REOPENED, ACCEPTED_RISK, CLOSED) is counted separately, and
    `openDebtCount` counts ONLY items with status == "OPEN".
    """
    closed = []
    blocking = []
    counts = {
        "totalDebtCount": 0,
        "closedDebtCount": 0,
        "openDebtCount": 0,
        "inProgressDebtCount": 0,
        "reopenedDebtCount": 0,
        "blockedDebtCount": 0,
        "readyForVerificationDebtCount": 0,
        "acceptedRiskDebtCount": 0,
        "openBlockingP1DebtCount": 0,
        "openP0DebtCount": 0,
        "closedDebtIds": [],
        "blockingDebtIds": [],
    }

    for debt in debts:
        debt_id = debt.get("id", "<unknown>")
        status = debt.get("status", "UNKNOWN")
        severity = debt.get("severity", "")
        is_blocking = bool(debt.get("blocking", False))

        counts["totalDebtCount"] += 1

        if status == "CLOSED":
            counts["closedDebtCount"] += 1
            closed.append(debt_id)
        elif status == "OPEN":
            counts["openDebtCount"] += 1
        elif status == "IN_PROGRESS":
            counts["inProgressDebtCount"] += 1
        elif status == "REOPENED":
            counts["reopenedDebtCount"] += 1
        elif status == "BLOCKED":
            counts["blockedDebtCount"] += 1
        elif status == "READY_FOR_VERIFICATION":
            counts["readyForVerificationDebtCount"] += 1
        elif status == "ACCEPTED_RISK":
            counts["acceptedRiskDebtCount"] += 1
            # Treated as no longer blocking — counted as closed for gate purposes.
            closed.append(debt_id)

        # P0 open count (matches existing test logic).
        if severity == "P0" and status not in ("CLOSED", "ACCEPTED_RISK"):
            counts["openP0DebtCount"] += 1

        # Blocking debt = any debt with blocking=true AND status not CLOSED/ACCEPTED_RISK.
        if is_blocking and status not in ("CLOSED", "ACCEPTED_RISK"):
            blocking.append(debt_id)
            if severity == "P1":
                counts["openBlockingP1DebtCount"] += 1

    counts["closedDebtIds"] = sorted(closed)
    counts["blockingDebtIds"] = sorted(blocking)
    return counts


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        print("Usage: check_closure_debt_register.py <register.json>", file=sys.stderr)
        return 2

    register_path = Path(argv[1])
    if not register_path.exists():
        print(f"FAIL: register file not found: {register_path}", file=sys.stderr)
        return 2

    try:
        register = json.loads(register_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        print(f"FAIL: register JSON parse error: {exc}", file=sys.stderr)
        return 2

    inherited = register.get("inheritedDebt", []) or []
    new = register.get("newDebt", []) or []
    all_debts = inherited + new

    computed = compute_counts(all_debts)
    summary = register.get("summary", {}) or {}

    print(f"Register: {register_path}")
    print(f"  inheritedDebt: {len(inherited)}")
    print(f"  newDebt:       {len(new)}")
    print(f"  total:         {computed['totalDebtCount']}")
    print()
    print("Computed metrics:")
    for key in (
        "totalDebtCount",
        "closedDebtCount",
        "openDebtCount",
        "inProgressDebtCount",
        "reopenedDebtCount",
        "blockedDebtCount",
        "openBlockingP1DebtCount",
    ):
        summary_value = summary.get(key)
        computed_value = computed[key]
        match = "OK" if summary_value == computed_value else "MISMATCH"
        print(f"  {key}: computed={computed_value} summary={summary_value} [{match}]")

    # Compare lists (sorted).
    for list_key in ("closedDebtIds", "blockingDebtIds"):
        summary_list = sorted(summary.get(list_key, []) or [])
        computed_list = computed[list_key]
        match = "OK" if summary_list == computed_list else "MISMATCH"
        if match == "MISMATCH":
            only_in_summary = sorted(set(summary_list) - set(computed_list))
            only_in_computed = sorted(set(computed_list) - set(summary_list))
            print(f"  {list_key}: {match}")
            if only_in_summary:
                print(f"    only in summary: {only_in_summary}")
            if only_in_computed:
                print(f"    only in computed: {only_in_computed}")
        else:
            print(f"  {list_key}: OK ({len(computed_list)} ids)")

    # Verify the summary's debtGate matches the computed blocking state.
    expected_gate = "BLOCKED" if computed["blockingDebtIds"] else "PASS"
    summary_gate = summary.get("debtGate")
    gate_match = "OK" if summary_gate == expected_gate else "MISMATCH"
    print(f"  debtGate: computed={expected_gate} summary={summary_gate} [{gate_match}]")

    # Determine overall pass/fail.
    scalar_keys = (
        "totalDebtCount",
        "closedDebtCount",
        "openDebtCount",
        "inProgressDebtCount",
        "reopenedDebtCount",
        "blockedDebtCount",
        "openBlockingP1DebtCount",
    )
    mismatches = []
    for key in scalar_keys:
        if summary.get(key) != computed[key]:
            mismatches.append(key)
    if sorted(summary.get("closedDebtIds", []) or []) != computed["closedDebtIds"]:
        mismatches.append("closedDebtIds")
    if sorted(summary.get("blockingDebtIds", []) or []) != computed["blockingDebtIds"]:
        mismatches.append("blockingDebtIds")
    if summary.get("debtGate") != expected_gate:
        mismatches.append("debtGate")

    if mismatches:
        print()
        print(f"FAIL: closure-debt register summary is out of sync with the debt list.")
        print(f"  Mismatched fields: {mismatches}")
        print()
        print("Update the `summary` block in the register JSON to match the computed values.")
        return 1

    print()
    print("CLOSURE-DEBT REGISTER RECONCILIATION: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
