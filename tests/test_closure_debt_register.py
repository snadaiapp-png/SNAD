"""Tests for SNAD closure debt register integrity.

Validates that the debt register JSON is well-formed, internally consistent,
and follows the governance rules defined in the Infrastructure Hardening program.
"""

import json
import pathlib
import pytest

REGISTER_PATH = pathlib.Path(__file__).resolve().parent.parent / "docs" / "infrastructure-hardening" / "closure-debt-register.json"

VALID_STATUSES = {
    "OPEN",
    "IN_PROGRESS",
    "BLOCKED",
    "READY_FOR_VERIFICATION",
    "CLOSED",
    "REOPENED",
    "ACCEPTED_RISK",
}

REQUIRED_FIELDS = {"id", "title", "severity", "status", "blocking", "evidence"}


def load_register():
    """Load the debt register JSON."""
    with REGISTER_PATH.open("r", encoding="utf-8") as f:
        return json.load(f)


def test_register_exists():
    """The debt register JSON file must exist."""
    assert REGISTER_PATH.is_file(), f"Debt register not found at {REGISTER_PATH}"


def test_register_is_valid_json():
    """The debt register must be valid JSON."""
    data = load_register()
    assert isinstance(data, dict)


def test_unique_ids():
    """All debt IDs must be unique across inherited and new debt."""
    data = load_register()
    all_debt = data.get("inheritedDebt", []) + data.get("newDebt", [])
    ids = [item["id"] for item in all_debt]
    assert len(ids) == len(set(ids)), f"Duplicate IDs found: {[x for x in ids if ids.count(x) > 1]}"


def test_all_statuses_valid():
    """All debt items must have a valid status."""
    data = load_register()
    all_debt = data.get("inheritedDebt", []) + data.get("newDebt", [])
    for item in all_debt:
        assert item["status"] in VALID_STATUSES, f"Invalid status '{item['status']}' for {item['id']}"


def test_required_fields_present():
    """All debt items must have required fields."""
    data = load_register()
    all_debt = data.get("inheritedDebt", []) + data.get("newDebt", [])
    for item in all_debt:
        for field in REQUIRED_FIELDS:
            assert field in item, f"Missing field '{field}' in {item.get('id', 'unknown')}"


def test_summary_counts_match_items():
    """Summary counts must match actual item counts."""
    data = load_register()
    all_debt = data.get("inheritedDebt", []) + data.get("newDebt", [])
    summary = data.get("summary", {})

    by_status = {}
    for item in all_debt:
        status = item["status"]
        by_status[status] = by_status.get(status, 0) + 1

    total = len(all_debt)
    closed = by_status.get("CLOSED", 0)
    open_count = by_status.get("OPEN", 0)
    in_progress = by_status.get("IN_PROGRESS", 0)
    blocked = by_status.get("BLOCKED", 0)
    ready = by_status.get("READY_FOR_VERIFICATION", 0)
    reopened = by_status.get("REOPENED", 0)
    accepted = by_status.get("ACCEPTED_RISK", 0)

    # Check total equation
    assert total == closed + open_count + in_progress + blocked + ready + reopened + accepted, \
        f"Total {total} != sum of status counts ({closed + open_count + in_progress + blocked + ready + reopened + accepted})"

    # Check reported counts
    assert summary.get("closedDebtCount", -1) == closed, \
        f"closedDebtCount mismatch: reported={summary.get('closedDebtCount')}, actual={closed}"
    assert summary.get("openP0DebtCount", -1) == sum(
        1 for item in all_debt if item["severity"] == "P0" and item["status"] not in ("CLOSED", "ACCEPTED_RISK")
    ), "openP0DebtCount mismatch"


def test_closed_items_have_evidence():
    """Closed items must have closure evidence."""
    data = load_register()
    all_debt = data.get("inheritedDebt", []) + data.get("newDebt", [])
    for item in all_debt:
        if item["status"] == "CLOSED":
            evidence = item.get("closureEvidence")
            assert evidence is not None and evidence != "", \
                f"Closed item {item['id']} has no closureEvidence"


def test_open_items_no_closure_date():
    """Open items must not have closureDate."""
    data = load_register()
    all_debt = data.get("inheritedDebt", []) + data.get("newDebt", [])
    for item in all_debt:
        if item["status"] != "CLOSED":
            assert item.get("closureDate") is None, \
                f"Non-closed item {item['id']} has closureDate set"


def test_p0_not_accepted_risk_without_reference():
    """P0 items cannot be ACCEPTED_RISK without explicit decision reference."""
    data = load_register()
    all_debt = data.get("inheritedDebt", []) + data.get("newDebt", [])
    for item in all_debt:
        if item["severity"] == "P0" and item["status"] == "ACCEPTED_RISK":
            # Must have explicit decision reference in evidence
            assert "ADR" in item.get("evidence", "") or "decision" in item.get("evidence", "").lower(), \
                f"P0 item {item['id']} is ACCEPTED_RISK without decision reference"


def test_debt_gate_blocked_when_p0_open():
    """Debt gate must be BLOCKED when P0 debt is open."""
    data = load_register()
    all_debt = data.get("inheritedDebt", []) + data.get("newDebt", [])
    open_p0 = any(
        item["severity"] == "P0" and item["status"] not in ("CLOSED", "ACCEPTED_RISK")
        for item in all_debt
    )
    if open_p0:
        assert data["summary"]["debtGate"] != "PASS", \
            "Debt gate is PASS but P0 debt is open"
