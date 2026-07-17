#!/usr/bin/env python3
"""Validate the final REM-P1-007 integrated business-process closure contract."""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CATALOG = ROOT / "docs/quality/e2e/business-process-catalog.json"
PLAN = ROOT / "docs/quality/e2e/REM-P1-007-EXECUTION-PLAN.md"
DECISION = ROOT / "docs/governance/REM-P1-007-CLOSURE-DECISION-2026-07-17.md"
CURRENT_STATUS = ROOT / "docs/governance/CURRENT-STATUS.json"
WORKFLOW = ROOT / ".github/workflows/business-process-e2e-validation.yml"
SALES_TEST = ROOT / "apps/sanad-platform/src/test/java/com/sanad/platform/e2e/SalesQualificationBusinessProcessE2ETest.java"
INTEGRATED_TEST = ROOT / "apps/sanad-platform/src/test/java/com/sanad/platform/e2e/IntegratedBusinessProcessesE2ETest.java"
POSTGRES_TEST = ROOT / "apps/sanad-platform/src/test/java/com/sanad/platform/e2e/IntegratedBusinessProcessesPostgresE2ETest.java"
SERVICE = ROOT / "apps/sanad-platform/src/main/java/com/sanad/platform/businessprocess/BusinessProcessService.java"
CONTROLLER = ROOT / "apps/sanad-platform/src/main/java/com/sanad/platform/businessprocess/BusinessProcessController.java"
MIGRATION = ROOT / "apps/sanad-platform/src/main/resources/db/migration/V20260717_4__create_business_process_e2e_backbone.sql"

EXPECTED_PROCESSES = {
    "SALES-ORDER-TO-CASH": [
        "Lead", "Qualification", "Account and Contact", "Opportunity", "Quotation",
        "Sales Order", "Inventory Reservation", "Delivery", "Invoice", "Ledger Posting",
        "Collection", "Analytics",
    ],
    "PROCUREMENT-PROCURE-TO-PAY": [
        "Purchase Request", "Approval", "Purchase Order", "Goods Receipt",
        "Supplier Invoice", "Ledger Posting", "Payment", "Reconciliation",
    ],
    "HR-HIRE-TO-PAY": [
        "Employee", "Contract", "Attendance", "Leave", "Payroll",
        "Ledger Posting", "Payment", "Analytics",
    ],
    "COMMERCE-ORDER-TO-REFUND": [
        "Customer Order", "Payment Authorization", "Inventory Reservation", "Shipment",
        "Invoice", "Return", "Refund", "Ledger Reconciliation", "Analytics",
    ],
}


class ValidationError(RuntimeError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValidationError(message)


def text(path: Path) -> str:
    require(path.is_file(), f"missing required file: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def require_tokens(path: Path, tokens: tuple[str, ...]) -> None:
    content = text(path)
    for token in tokens:
        require(token in content, f"missing control in {path.relative_to(ROOT)}: {token}")


def validate_process(process: dict) -> None:
    process_id = process.get("id")
    require(process_id in EXPECTED_PROCESSES, f"unknown process: {process_id}")
    required = EXPECTED_PROCESSES[process_id]
    require(process.get("required_steps") == required, f"required step drift: {process_id}")
    require(process.get("status") == "FULLY_VERIFIED", f"process is not fully verified: {process_id}")
    require(process.get("verified_steps") == required, f"verified steps incomplete: {process_id}")
    require(process.get("blocked_steps") == [], f"blocked steps remain: {process_id}")
    require(process.get("closure_ready") is True, f"closure_ready false: {process_id}")
    require(process.get("automated_test") == "com.sanad.platform.e2e.IntegratedBusinessProcessesE2ETest",
            f"H2 HTTP test identity drift: {process_id}")
    require(process.get("postgresql_test") == "com.sanad.platform.e2e.IntegratedBusinessProcessesPostgresE2ETest",
            f"PostgreSQL test identity drift: {process_id}")
    require(process.get("evidence_artifact") == "business-process-e2e-evidence",
            f"evidence artifact drift: {process_id}")
    require(process.get("financial_reconciliation") is True, f"financial proof missing: {process_id}")
    require(process.get("inventory_reconciliation") is True, f"inventory proof missing: {process_id}")
    require(process.get("analytics_reconciliation") is True, f"analytics proof missing: {process_id}")
    require(len(process.get("owner_roles", [])) >= 2, f"accountable owners missing: {process_id}")


def main() -> int:
    catalog = json.loads(text(CATALOG))
    require(catalog.get("schema_version") == "2.0", "invalid final catalog schema")
    require(catalog.get("finding") == "REM-P1-007", "wrong finding")
    require(catalog.get("status") == "CLOSED", "finding is not closed in catalog")
    require(catalog.get("closure_authorized") is True, "closure not authorized")
    require(catalog.get("broad_commercial_go_live_authorized") is False,
            "REM-P1-007 must not authorize broad go-live")

    policy = catalog.get("evidence_policy", {})
    required_policy = (
        "exact_sha_required", "real_application_paths_required", "tenant_isolation_required",
        "authorization_required", "audit_required", "rollback_required",
        "financial_assertions_required_when_applicable",
        "inventory_assertions_required_when_applicable", "workflow_approval_required_when_applicable",
        "analytics_consistency_required", "postgresql_execution_required",
    )
    for flag in required_policy:
        require(policy.get(flag) is True, f"evidence policy disabled: {flag}")
    require(policy.get("mock_only_evidence_allowed") is False, "mock-only evidence is allowed")

    processes = catalog.get("processes")
    require(isinstance(processes, list) and len(processes) == 4, "four processes are required")
    require({item.get("id") for item in processes} == set(EXPECTED_PROCESSES), "process coverage incomplete")
    for process in processes:
        validate_process(process)

    acceptance = catalog.get("acceptance", {})
    require(acceptance.get("project_owner_direction") == "FINAL_CLOSURE_REQUESTED",
            "project owner closure direction missing")
    require(acceptance.get("qa_release_acceptance") == "EXACT_SHA_CI_REQUIRED",
            "QA exact-SHA acceptance missing")

    closure_gate = catalog.get("closure_gate", {})
    require(closure_gate and all(value is True for value in closure_gate.values()),
            "closure gate is not fully enforced")

    require_tokens(SALES_TEST, (
        "provesLeadToWonOpportunityWithGovernedCrossCuttingEvidence",
        "platform_audit_logs", "crm_timeline_events", "isForbidden()", "isNotFound()", "idempotent",
    ))
    require_tokens(INTEGRATED_TEST, (
        "provesAllFourProcessesWithFinancialInventoryWorkflowAuditAnalyticsAndRollback",
        "/api/v1/business-process-e2e/{processCode}/execute",
        "financialReconciled", "inventoryReconciled", "analyticsConsistent",
        "procure-rollback", "isUnprocessableEntity()", "isForbidden()", "isNotFound()",
    ))
    require_tokens(POSTGRES_TEST, (
        "IntegratedBusinessProcessesPostgresE2ETest", "PostgreSQLContainer",
        "postgres:16-alpine", "executesAllGovernedProcessesAgainstRealPostgres",
    ))
    require_tokens(SERVICE, (
        "SALES-ORDER-TO-CASH", "PROCUREMENT-PROCURE-TO-PAY", "HR-HIRE-TO-PAY",
        "COMMERCE-ORDER-TO-REFUND", "@Transactional", "addJournal", "reserve(", "ship(",
        "receive(", "returnInventory(", "financialReconciled", "inventoryReconciled",
        "analyticsConsistent", "PlatformAuditWriter",
    ))
    require_tokens(CONTROLLER, (
        "@RequireCapability(\"BUSINESS_PROCESS.EXECUTE\")",
        "@RequireCapability(\"BUSINESS_PROCESS.READ\")",
        "/api/v1/business-process-e2e",
    ))
    require_tokens(MIGRATION, (
        "bp_process_runs", "bp_process_steps", "bp_inventory_balances", "bp_inventory_movements",
        "bp_ledger_entries", "bp_payment_events", "bp_workflow_approvals",
        "bp_analytics_snapshots", "BUSINESS_PROCESS.READ", "BUSINESS_PROCESS.EXECUTE",
    ))
    require_tokens(WORKFLOW, (
        "Business Process E2E Validation", "validate_business_process_e2e.py",
        "SalesQualificationBusinessProcessE2ETest,IntegratedBusinessProcessesE2ETest,IntegratedBusinessProcessesPostgresE2ETest",
        "actions/upload-artifact@v4", "business-process-e2e-evidence", "github.sha",
    ))
    require_tokens(PLAN, (
        "Status:** `CLOSED`", "ALL_PROCESSES_FULLY_VERIFIED: TRUE",
        "POSTGRESQL_EXECUTION: REQUIRED_AND_TESTED", "REM-P1-007: CLOSED",
        "BROAD_COMMERCIAL_GO_LIVE: NOT_APPROVED",
    ))
    require_tokens(DECISION, (
        "REM-P1-007: CLOSED", "Project Owner", "PostgreSQL 16",
        "does not approve broad commercial go-live",
    ))

    current = json.loads(text(CURRENT_STATUS))
    require("REM-P1-007" in current.get("closed_findings", {}), "closure missing from current status")
    require("REM-P1-007" not in current.get("open_findings", {}), "finding remains open")
    require("REM-P1-007" not in current.get("remediation_in_progress_findings", {}),
            "finding remains in remediation-in-progress")
    require(current.get("commercial_go_live") == "NOT_APPROVED", "commercial boundary changed")

    print("REM-P1-007 FINAL BUSINESS PROCESS E2E CLOSURE VALIDATION PASSED")
    print("Processes=4 FullyVerified=4 BlockedSteps=0 PostgreSQLRequired=true")
    print("ClosureAuthorized=true BroadCommercialGoLive=false")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValidationError, OSError, KeyError, ValueError, json.JSONDecodeError) as exc:
        print(f"REM-P1-007 FINAL CLOSURE VALIDATION ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
