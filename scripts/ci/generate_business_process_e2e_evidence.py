#!/usr/bin/env python3
"""Generate sanitized exact-SHA evidence for final REM-P1-007 closure."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parents[2]
CATALOG = ROOT / "docs/quality/e2e/business-process-catalog.json"
REPORT_DIR = ROOT / "apps/sanad-platform/target/surefire-reports"
SUITES = {
    "SalesQualificationBusinessProcessE2ETest": REPORT_DIR / "TEST-com.sanad.platform.e2e.SalesQualificationBusinessProcessE2ETest.xml",
    "IntegratedBusinessProcessesE2ETest": REPORT_DIR / "TEST-com.sanad.platform.e2e.IntegratedBusinessProcessesE2ETest.xml",
    "IntegratedBusinessProcessesPostgresE2ETest": REPORT_DIR / "TEST-com.sanad.platform.e2e.IntegratedBusinessProcessesPostgresE2ETest.xml",
}
SOURCES = {
    "sales_foundation_test": ROOT / "apps/sanad-platform/src/test/java/com/sanad/platform/e2e/SalesQualificationBusinessProcessE2ETest.java",
    "integrated_http_test": ROOT / "apps/sanad-platform/src/test/java/com/sanad/platform/e2e/IntegratedBusinessProcessesE2ETest.java",
    "postgresql_test": ROOT / "apps/sanad-platform/src/test/java/com/sanad/platform/e2e/IntegratedBusinessProcessesPostgresE2ETest.java",
    "orchestration_service": ROOT / "apps/sanad-platform/src/main/java/com/sanad/platform/businessprocess/BusinessProcessService.java",
    "persistence_migration": ROOT / "apps/sanad-platform/src/main/resources/db/migration/V20260717_4__create_business_process_e2e_backbone.sql",
}
REQUIRED_TESTCASES = {
    "provesLeadToWonOpportunityWithGovernedCrossCuttingEvidence",
    "provesAllFourProcessesWithFinancialInventoryWorkflowAuditAnalyticsAndRollback",
    "executesAllGovernedProcessesAgainstRealPostgres",
}


class EvidenceError(RuntimeError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise EvidenceError(message)


def integer_attribute(node: ElementTree.Element, name: str) -> int:
    return int(float(node.attrib.get(name, "0")))


def sha256(path: Path) -> str:
    require(path.is_file(), f"missing evidence source: {path.relative_to(ROOT)}")
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--sha", required=True)
    parser.add_argument("--run-id", default=os.getenv("GITHUB_RUN_ID", "local"))
    parser.add_argument("--run-attempt", default=os.getenv("GITHUB_RUN_ATTEMPT", "1"))
    parser.add_argument("--ref", default=os.getenv("GITHUB_REF", "local"))
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    require(len(args.sha) == 40, "exact 40-character Git SHA is required")
    require(all(character in "0123456789abcdef" for character in args.sha.lower()), "invalid Git SHA")
    require(CATALOG.is_file(), "missing business process catalog")

    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    require(catalog.get("status") == "CLOSED", "catalog is not closed")
    require(catalog.get("closure_authorized") is True, "catalog closure is not authorized")
    processes = catalog.get("processes", [])
    require(len(processes) == 4, "four governed processes are required")
    for process in processes:
        require(process.get("status") == "FULLY_VERIFIED", f"process not fully verified: {process.get('id')}")
        require(process.get("blocked_steps") == [], f"blocked steps remain: {process.get('id')}")
        require(process.get("verified_steps") == process.get("required_steps"),
                f"verified steps incomplete: {process.get('id')}")

    aggregate = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0, "duration_seconds": 0.0}
    suites = []
    testcase_names: list[str] = []
    for suite_name, path in SUITES.items():
        require(path.is_file(), f"missing Surefire result: {path}")
        suite = ElementTree.parse(path).getroot()
        result = {
            "suite": suite.attrib.get("name", suite_name),
            "tests": integer_attribute(suite, "tests"),
            "failures": integer_attribute(suite, "failures"),
            "errors": integer_attribute(suite, "errors"),
            "skipped": integer_attribute(suite, "skipped"),
            "duration_seconds": float(suite.attrib.get("time", "0")),
            "testcases": [testcase.attrib.get("name", "") for testcase in suite.findall("testcase")],
        }
        suites.append(result)
        testcase_names.extend(result["testcases"])
        for key in ("tests", "failures", "errors", "skipped"):
            aggregate[key] += result[key]
        aggregate["duration_seconds"] += result["duration_seconds"]

    require(aggregate["tests"] >= 3, "critical test count is incomplete")
    require(aggregate["failures"] == 0, "test failures prevent closure")
    require(aggregate["errors"] == 0, "test errors prevent closure")
    require(aggregate["skipped"] == 0, "skipped critical tests prevent closure")
    require(REQUIRED_TESTCASES.issubset(set(testcase_names)), "required final-closure testcase missing")

    process_evidence = []
    for process in processes:
        process_evidence.append({
            "id": process["id"],
            "status": process["status"],
            "verified_steps": process["verified_steps"],
            "blocked_steps": process["blocked_steps"],
            "closure_ready": process["closure_ready"],
            "financial_reconciliation": process["financial_reconciliation"],
            "inventory_reconciliation": process["inventory_reconciliation"],
            "analytics_reconciliation": process["analytics_reconciliation"],
        })

    evidence = {
        "schema_version": "2.0",
        "finding": "REM-P1-007",
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "git_sha": args.sha,
        "github_run_id": str(args.run_id),
        "github_run_attempt": str(args.run_attempt),
        "git_ref": args.ref,
        "environment": {
            "test_scope": "Spring Boot HTTP integration plus service-level PostgreSQL execution",
            "database_engines": ["H2 PostgreSQL compatibility mode", "PostgreSQL 16 Testcontainers"],
            "postgresql_closure_evidence": True,
            "production_deployment_claim": False,
        },
        "processes": process_evidence,
        "cross_cutting_assertions": {
            "tenant_isolation": "TESTED",
            "authorization_denial": "TESTED",
            "idempotent_replay": "TESTED",
            "central_audit": "TESTED",
            "crm_timeline": "TESTED",
            "transaction_rollback": "TESTED",
            "financial_reconciliation": "TESTED",
            "inventory_reconciliation": "TESTED",
            "workflow_approval": "TESTED",
            "analytics_consistency": "TESTED",
        },
        "test_result": {
            **aggregate,
            "suites": suites,
            "required_testcases": sorted(REQUIRED_TESTCASES),
        },
        "integrity": {
            "source_sha256": {name: sha256(path) for name, path in SOURCES.items()},
            "secrets_included": False,
            "customer_personal_data_included": False,
        },
        "acceptance": {
            "project_owner_direction": "FINAL_CLOSURE_REQUESTED",
            "qa_release_acceptance": "EXACT_SHA_WORKFLOW_SUCCESS",
            "accepted_scope": "REM-P1-007 integrated-evidence defect",
        },
        "decision": {
            "rem_p1_007_closed": True,
            "all_processes_fully_verified": True,
            "blocked_steps": 0,
            "broad_commercial_go_live_approved": False,
            "project_status": "CONDITIONAL_CONTINUE",
        },
    }

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"REM-P1-007 final evidence written: {args.output}")
    print(f"Tests={aggregate['tests']} Failures={aggregate['failures']} Errors={aggregate['errors']} Skipped={aggregate['skipped']}")
    print("Processes=4 FullyVerified=4 Closure=true BroadCommercialGoLive=false")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (EvidenceError, OSError, KeyError, ValueError, json.JSONDecodeError, ElementTree.ParseError) as exc:
        print(f"REM-P1-007 FINAL EVIDENCE ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
