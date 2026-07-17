#!/usr/bin/env python3
"""Validate the REM-P1-007 business-process E2E evidence contract.

This validator is intentionally fail-closed about closure claims while allowing
an honest incremental remediation state. It proves that executable evidence is
real, traceable and classified without allowing a partial CRM slice to be
misrepresented as complete cross-module Order-to-Cash proof.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CATALOG_PATH = ROOT / "docs" / "quality" / "e2e" / "business-process-catalog.json"
PLAN_PATH = ROOT / "docs" / "quality" / "e2e" / "REM-P1-007-EXECUTION-PLAN.md"
TEST_PATH = (
    ROOT
    / "apps"
    / "sanad-platform"
    / "src"
    / "test"
    / "java"
    / "com"
    / "sanad"
    / "platform"
    / "e2e"
    / "SalesQualificationBusinessProcessE2ETest.java"
)
WORKFLOW_PATH = ROOT / ".github" / "workflows" / "business-process-e2e-validation.yml"
CURRENT_STATUS_PATH = ROOT / "docs" / "governance" / "CURRENT-STATUS.json"

EXPECTED_PROCESSES = {
    "SALES-ORDER-TO-CASH": [
        "Lead",
        "Qualification",
        "Account and Contact",
        "Opportunity",
        "Quotation",
        "Sales Order",
        "Inventory Reservation",
        "Delivery",
        "Invoice",
        "Ledger Posting",
        "Collection",
        "Analytics",
    ],
    "PROCUREMENT-PROCURE-TO-PAY": [
        "Purchase Request",
        "Approval",
        "Purchase Order",
        "Goods Receipt",
        "Supplier Invoice",
        "Ledger Posting",
        "Payment",
        "Reconciliation",
    ],
    "HR-HIRE-TO-PAY": [
        "Employee",
        "Contract",
        "Attendance",
        "Leave",
        "Payroll",
        "Ledger Posting",
        "Payment",
        "Analytics",
    ],
    "COMMERCE-ORDER-TO-REFUND": [
        "Customer Order",
        "Payment Authorization",
        "Inventory Reservation",
        "Shipment",
        "Invoice",
        "Return",
        "Refund",
        "Ledger Reconciliation",
        "Analytics",
    ],
}

REQUIRED_POLICY_FLAGS = (
    "exact_sha_required",
    "real_application_paths_required",
    "tenant_isolation_required",
    "authorization_required",
    "audit_required",
    "rollback_required",
    "financial_assertions_required_when_applicable",
    "inventory_assertions_required_when_applicable",
    "analytics_consistency_required",
)

REQUIRED_TEST_TOKENS = (
    "class SalesQualificationBusinessProcessE2ETest",
    "/api/v1/crm/leads",
    "/api/v1/crm/leads/{id}/convert",
    "/api/v1/crm/opportunities/{id}/stage",
    "platform_audit_logs",
    "crm_timeline_events",
    "isForbidden()",
    "isNotFound()",
    "opportunitiesBeforeRejectedMutation",
    "idempotent",
    "openOpportunities",
)

REQUIRED_WORKFLOW_TOKENS = (
    "Business Process E2E Validation",
    "validate_business_process_e2e.py",
    "SalesQualificationBusinessProcessE2ETest",
    "actions/upload-artifact@v4",
    "business-process-e2e-evidence",
    "github.sha",
)


class ValidationError(RuntimeError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValidationError(message)


def read_text(path: Path) -> str:
    require(path.is_file(), f"missing required file: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def validate_process(process: dict, allowed_statuses: set[str]) -> None:
    process_id = process.get("id")
    require(process_id in EXPECTED_PROCESSES, f"unknown process id: {process_id}")
    require(process.get("status") in allowed_statuses, f"invalid status for {process_id}")

    required_steps = process.get("required_steps")
    verified_steps = process.get("verified_steps")
    blocked_steps = process.get("blocked_steps")
    require(required_steps == EXPECTED_PROCESSES[process_id], f"required steps drift: {process_id}")
    require(isinstance(verified_steps, list), f"verified_steps missing: {process_id}")
    require(isinstance(blocked_steps, list), f"blocked_steps missing: {process_id}")
    require(len(required_steps) == len(set(required_steps)), f"duplicate required step: {process_id}")
    require(set(verified_steps).issubset(set(required_steps)), f"unknown verified step: {process_id}")
    require(set(blocked_steps).issubset(set(required_steps)), f"unknown blocked step: {process_id}")
    require(not set(verified_steps).intersection(blocked_steps), f"step both verified and blocked: {process_id}")
    require(set(verified_steps).union(blocked_steps) == set(required_steps), f"unclassified step: {process_id}")
    require(process.get("closure_ready") is False, f"premature closure_ready claim: {process_id}")
    require(len(process.get("owner_roles", [])) >= 2, f"insufficient accountable owners: {process_id}")

    status = process["status"]
    if status == "NOT_EXECUTABLE":
        require(not verified_steps, f"NOT_EXECUTABLE process has verified steps: {process_id}")
        require(blocked_steps == required_steps, f"NOT_EXECUTABLE process must block all steps: {process_id}")
        require(process.get("automated_test") is None, f"NOT_EXECUTABLE process has test claim: {process_id}")
        require(process.get("evidence_artifact") is None, f"NOT_EXECUTABLE process has artifact claim: {process_id}")
    elif status == "PARTIAL_VERIFIED":
        require(verified_steps, f"PARTIAL_VERIFIED has no evidence: {process_id}")
        require(blocked_steps, f"PARTIAL_VERIFIED has no remaining gap: {process_id}")
        require(process.get("automated_test"), f"partial process missing automated test: {process_id}")
        require(process.get("evidence_artifact"), f"partial process missing artifact: {process_id}")
    elif status == "FULLY_VERIFIED":
        require(not blocked_steps, f"FULLY_VERIFIED process has blocked steps: {process_id}")
        require(verified_steps == required_steps, f"FULLY_VERIFIED steps incomplete: {process_id}")
        require(process.get("automated_test"), f"fully verified process missing automated test: {process_id}")
        require(process.get("evidence_artifact"), f"fully verified process missing artifact: {process_id}")


def main() -> int:
    catalog = json.loads(read_text(CATALOG_PATH))
    require(catalog.get("schema_version") == "1.0", "invalid catalog schema")
    require(catalog.get("finding") == "REM-P1-007", "catalog controls the wrong finding")
    require(catalog.get("status") == "REMEDIATION_IN_PROGRESS", "invalid remediation status")
    require(catalog.get("closure_authorized") is False, "REM-P1-007 cannot be closed by partial evidence")

    policy = catalog.get("evidence_policy", {})
    for flag in REQUIRED_POLICY_FLAGS:
        require(policy.get(flag) is True, f"evidence policy flag not enforced: {flag}")
    require(policy.get("mock_only_evidence_allowed") is False, "mock-only evidence must be prohibited")

    allowed_statuses = set(catalog.get("status_values", []))
    require(
        allowed_statuses == {"NOT_EXECUTABLE", "PARTIAL_VERIFIED", "FULLY_VERIFIED"},
        "invalid process status vocabulary",
    )

    processes = catalog.get("processes")
    require(isinstance(processes, list), "process list missing")
    require(len(processes) == 4, "exactly four governed business processes are required")
    process_ids = [item.get("id") for item in processes]
    require(set(process_ids) == set(EXPECTED_PROCESSES), "required business-process coverage is incomplete")
    require(len(process_ids) == len(set(process_ids)), "duplicate business process id")

    for process in processes:
        validate_process(process, allowed_statuses)

    sales = next(item for item in processes if item["id"] == "SALES-ORDER-TO-CASH")
    require(sales["status"] == "PARTIAL_VERIFIED", "sales foundation slice must remain partial")
    require(
        sales["verified_steps"] == ["Lead", "Qualification", "Account and Contact", "Opportunity"],
        "sales verified slice drifted",
    )
    require(
        sales["automated_test"] == "com.sanad.platform.e2e.SalesQualificationBusinessProcessE2ETest",
        "sales test identity drifted",
    )

    closure_gate = catalog.get("closure_gate", {})
    for flag, value in closure_gate.items():
        require(value is True, f"closure gate must remain fail-closed: {flag}")

    test_text = read_text(TEST_PATH)
    for token in REQUIRED_TEST_TOKENS:
        require(token in test_text, f"business-process test control missing: {token}")

    workflow_text = read_text(WORKFLOW_PATH)
    for token in REQUIRED_WORKFLOW_TOKENS:
        require(token in workflow_text, f"business-process workflow control missing: {token}")

    plan_text = read_text(PLAN_PATH)
    for token in (
        "REM-P1-007",
        "REMEDIATION_IN_PROGRESS",
        "SalesQualificationBusinessProcessE2ETest",
        "Quotation",
        "Ledger Posting",
        "Procure to Pay",
        "Hire to Pay",
        "Commerce Order to Refund",
        "No closure is authorized",
    ):
        require(token in plan_text, f"execution plan control missing: {token}")

    current_status = json.loads(read_text(CURRENT_STATUS_PATH))
    require("REM-P1-007" in current_status.get("open_findings", {}), "REM-P1-007 disappeared from open findings")
    in_progress = current_status.get("remediation_in_progress_findings", {})
    require("REM-P1-007" in in_progress, "REM-P1-007 remediation progress is not recorded")
    require(
        in_progress["REM-P1-007"].get("closure_authorized") is False,
        "current status prematurely authorizes REM-P1-007 closure",
    )

    fully_verified = sum(item["status"] == "FULLY_VERIFIED" for item in processes)
    partial = sum(item["status"] == "PARTIAL_VERIFIED" for item in processes)
    not_executable = sum(item["status"] == "NOT_EXECUTABLE" for item in processes)

    print("REM-P1-007 BUSINESS PROCESS E2E GOVERNANCE VALIDATION PASSED")
    print(f"Processes={len(processes)} FullyVerified={fully_verified} Partial={partial} NotExecutable={not_executable}")
    print("Executable foundation=Sales Lead -> Qualified -> Converted -> Won")
    print("ClosureAuthorized=false")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValidationError, OSError, KeyError, ValueError, json.JSONDecodeError) as exc:
        print(f"REM-P1-007 BUSINESS PROCESS E2E VALIDATION ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
