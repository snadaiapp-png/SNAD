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


def get_all_debt():
    """Get all debt items from the register."""
    data = load_register()
    return data.get("inheritedDebt", []) + data.get("newDebt", [])


def test_register_exists():
    """The debt register JSON file must exist."""
    assert REGISTER_PATH.is_file(), f"Debt register not found at {REGISTER_PATH}"


def test_register_is_valid_json():
    """The debt register must be valid JSON."""
    data = load_register()
    assert isinstance(data, dict)


def test_unique_ids():
    """All debt IDs must be unique across inherited and new debt."""
    all_debt = get_all_debt()
    ids = [item["id"] for item in all_debt]
    assert len(ids) == len(set(ids)), f"Duplicate IDs found: {[x for x in ids if ids.count(x) > 1]}"


def test_all_statuses_valid():
    """All debt items must have a valid status."""
    all_debt = get_all_debt()
    for item in all_debt:
        assert item["status"] in VALID_STATUSES, f"Invalid status '{item['status']}' for {item['id']}"


def test_required_fields_present():
    """All debt items must have required fields."""
    all_debt = get_all_debt()
    for item in all_debt:
        for field in REQUIRED_FIELDS:
            assert field in item, f"Missing field '{field}' in {item.get('id', 'unknown')}"


def test_all_items_have_severity():
    """All debt items must have a severity (priority)."""
    all_debt = get_all_debt()
    for item in all_debt:
        assert "severity" in item and item["severity"], f"Missing severity in {item['id']}"


def test_all_items_have_target_closure_stage():
    """All debt items must have a targetClosureStage."""
    all_debt = get_all_debt()
    for item in all_debt:
        assert "targetClosureStage" in item and item["targetClosureStage"], \
            f"Missing targetClosureStage in {item['id']}"


def test_summary_counts_match_items():
    """Summary counts must match actual item counts and sum to total."""
    data = load_register()
    all_debt = get_all_debt()
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
    calculated_total = closed + open_count + in_progress + blocked + ready + reopened + accepted
    assert total == calculated_total, \
        f"Total {total} != sum of status counts ({calculated_total})"

    # Check reported total
    assert summary.get("totalDebtCount", -1) == total, \
        f"totalDebtCount mismatch: reported={summary.get('totalDebtCount')}, actual={total}"

    # Check individual counts
    assert summary.get("closedDebtCount", -1) == closed, \
        f"closedDebtCount mismatch: reported={summary.get('closedDebtCount')}, actual={closed}"
    assert summary.get("openDebtCount", -1) == open_count, \
        f"openDebtCount mismatch: reported={summary.get('openDebtCount')}, actual={open_count}"
    assert summary.get("blockedDebtCount", -1) == blocked, \
        f"blockedDebtCount mismatch: reported={summary.get('blockedDebtCount')}, actual={blocked}"
    assert summary.get("readyForVerificationDebtCount", -1) == ready, \
        f"readyForVerificationDebtCount mismatch: reported={summary.get('readyForVerificationDebtCount')}, actual={ready}"
    assert summary.get("inProgressDebtCount", -1) == in_progress, \
        f"inProgressDebtCount mismatch: reported={summary.get('inProgressDebtCount')}, actual={in_progress}"

    # Check P0 count
    open_p0 = sum(1 for item in all_debt if item["severity"] == "P0" and item["status"] not in ("CLOSED", "ACCEPTED_RISK"))
    assert summary.get("openP0DebtCount", -1) == open_p0, \
        f"openP0DebtCount mismatch: reported={summary.get('openP0DebtCount')}, actual={open_p0}"


def test_closed_items_have_evidence():
    """Closed items must have closure evidence."""
    all_debt = get_all_debt()
    for item in all_debt:
        if item["status"] == "CLOSED":
            evidence = item.get("closureEvidence")
            assert evidence is not None and evidence != "", \
                f"Closed item {item['id']} has no closureEvidence"


def test_open_items_no_closure_date():
    """Non-closed items must not have closureDate."""
    all_debt = get_all_debt()
    for item in all_debt:
        if item["status"] != "CLOSED":
            assert item.get("closureDate") is None, \
                f"Non-closed item {item['id']} has closureDate set"


def test_open_items_no_closed_commit_sha():
    """Non-closed items must not have closedCommitSha."""
    all_debt = get_all_debt()
    for item in all_debt:
        if item["status"] != "CLOSED":
            assert item.get("closedCommitSha") is None, \
                f"Non-closed item {item['id']} has closedCommitSha set"


def test_p0_not_accepted_risk_without_reference():
    """P0 items cannot be ACCEPTED_RISK without explicit decision reference."""
    all_debt = get_all_debt()
    for item in all_debt:
        if item["severity"] == "P0" and item["status"] == "ACCEPTED_RISK":
            assert "ADR" in item.get("evidence", "") or "decision" in item.get("evidence", "").lower(), \
                f"P0 item {item['id']} is ACCEPTED_RISK without decision reference"


def test_debt_gate_blocked_when_p0_open():
    """Debt gate must be BLOCKED when P0 debt is open."""
    data = load_register()
    all_debt = get_all_debt()
    open_p0 = any(
        item["severity"] == "P0" and item["status"] not in ("CLOSED", "ACCEPTED_RISK")
        for item in all_debt
    )
    if open_p0:
        assert data["summary"]["debtGate"] != "PASS", \
            "Debt gate is PASS but P0 debt is open"


def test_cd_01_p1_002_about_maven_wrapper_in_ci():
    """CD-01-P1-002 must be about Maven Wrapper usage in CI."""
    all_debt = get_all_debt()
    item = next((i for i in all_debt if i["id"] == "CD-01-P1-002"), None)
    assert item is not None, "CD-01-P1-002 not found"
    title_lower = item["title"].lower()
    assert "maven" in title_lower or "mvnw" in title_lower, \
        f"CD-01-P1-002 title should mention Maven Wrapper: '{item['title']}'"
    assert "ci" in title_lower or "workflow" in title_lower, \
        f"CD-01-P1-002 title should mention CI: '{item['title']}'"


def test_cd_01_p1_009_about_remote_ci_not_executed():
    """CD-01-P1-009 must be about remote CI not being executed."""
    all_debt = get_all_debt()
    item = next((i for i in all_debt if i["id"] == "CD-01-P1-009"), None)
    assert item is not None, "CD-01-P1-009 not found"
    title_lower = item["title"].lower()
    assert "remote" in title_lower or "ci" in title_lower, \
        f"CD-01-P1-009 title should mention remote CI: '{item['title']}'"
    assert "not" in title_lower or "not executed" in title_lower, \
        f"CD-01-P1-009 title should indicate not-executed: '{item['title']}'"


def test_no_duplicate_functional_descriptions():
    """No two debt items should have the same functional description (duplicate debt)."""
    all_debt = get_all_debt()
    titles = [item["title"] for item in all_debt]
    # Check for exact duplicates
    duplicates = [t for t in titles if titles.count(t) > 1]
    assert len(duplicates) == 0, f"Duplicate titles found: {set(duplicates)}"


def test_remote_ci_debt_blocks_final_pass():
    """If remote CI debt is open, final status cannot be PASS."""
    data = load_register()
    all_debt = get_all_debt()
    remote_ci_open = any(
        "remote" in item.get("title", "").lower() and "ci" in item.get("title", "").lower()
        and item["status"] not in ("CLOSED", "ACCEPTED_RISK")
        for item in all_debt
    )
    if remote_ci_open:
        assert data["summary"]["status"] != "PASS", \
            "Final status is PASS but remote CI debt is open"
