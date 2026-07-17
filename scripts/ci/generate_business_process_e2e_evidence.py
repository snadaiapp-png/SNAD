#!/usr/bin/env python3
"""Generate sanitized REM-P1-007 evidence from Surefire results."""
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
CATALOG = ROOT / "docs" / "quality" / "e2e" / "business-process-catalog.json"
DEFAULT_XML = (
    ROOT
    / "apps"
    / "sanad-platform"
    / "target"
    / "surefire-reports"
    / "TEST-com.sanad.platform.e2e.SalesQualificationBusinessProcessE2ETest.xml"
)


class EvidenceError(RuntimeError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise EvidenceError(message)


def integer_attribute(node: ElementTree.Element, name: str) -> int:
    value = node.attrib.get(name, "0")
    return int(float(value))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--sha", required=True)
    parser.add_argument("--run-id", default=os.getenv("GITHUB_RUN_ID", "local"))
    parser.add_argument("--run-attempt", default=os.getenv("GITHUB_RUN_ATTEMPT", "1"))
    parser.add_argument("--ref", default=os.getenv("GITHUB_REF", "local"))
    parser.add_argument("--xml", type=Path, default=DEFAULT_XML)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    require(len(args.sha) == 40, "exact 40-character Git SHA is required")
    require(all(character in "0123456789abcdef" for character in args.sha.lower()), "invalid Git SHA")
    require(args.xml.is_file(), f"missing Surefire result: {args.xml}")
    require(CATALOG.is_file(), "missing business process catalog")

    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    sales = next(
        process for process in catalog["processes"]
        if process["id"] == "SALES-ORDER-TO-CASH"
    )
    require(sales["status"] == "PARTIAL_VERIFIED", "sales evidence must remain partial")
    require(catalog["closure_authorized"] is False, "partial evidence cannot authorize closure")

    suite = ElementTree.parse(args.xml).getroot()
    tests = integer_attribute(suite, "tests")
    failures = integer_attribute(suite, "failures")
    errors = integer_attribute(suite, "errors")
    skipped = integer_attribute(suite, "skipped")
    duration_seconds = float(suite.attrib.get("time", "0"))

    testcase_names = [
        testcase.attrib.get("name", "")
        for testcase in suite.findall("testcase")
    ]
    require(tests >= 1, "zero-test evidence is prohibited")
    require(failures == 0, "test failures prevent evidence acceptance")
    require(errors == 0, "test errors prevent evidence acceptance")
    require(skipped == 0, "skipped critical business-process tests are prohibited")
    require(
        "provesLeadToWonOpportunityWithGovernedCrossCuttingEvidence" in testcase_names,
        "required business-process testcase missing",
    )

    source_digest = hashlib.sha256(
        (
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
        ).read_bytes()
    ).hexdigest()

    evidence = {
        "schema_version": "1.0",
        "finding": "REM-P1-007",
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "git_sha": args.sha,
        "github_run_id": str(args.run_id),
        "github_run_attempt": str(args.run_attempt),
        "git_ref": args.ref,
        "environment": {
            "test_scope": "Spring Boot HTTP integration",
            "database": "H2 PostgreSQL compatibility profile",
            "production_claim": False,
            "postgresql_closure_evidence": False,
        },
        "process": {
            "id": sales["id"],
            "status": sales["status"],
            "verified_steps": sales["verified_steps"],
            "blocked_steps": sales["blocked_steps"],
            "closure_ready": False,
        },
        "cross_cutting_assertions": {
            "tenant_isolation": "TESTED",
            "authorization_denial": "TESTED",
            "idempotent_conversion": "TESTED",
            "audit": "TESTED",
            "timeline": "TESTED",
            "analytics_consistency": "TESTED",
            "rejected_mutation_rollback": "TESTED",
            "financial_reconciliation": "NOT_APPLICABLE_TO_CURRENT_SLICE",
            "inventory_reconciliation": "NOT_APPLICABLE_TO_CURRENT_SLICE",
        },
        "test_result": {
            "suite": suite.attrib.get("name"),
            "tests": tests,
            "failures": failures,
            "errors": errors,
            "skipped": skipped,
            "duration_seconds": duration_seconds,
            "testcases": testcase_names,
        },
        "integrity": {
            "test_source_sha256": source_digest,
            "secrets_included": False,
            "customer_personal_data_included": False,
        },
        "decision": {
            "phase_1_foundation_verified": True,
            "rem_p1_007_closed": False,
            "broad_commercial_go_live_approved": False,
        },
    }

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(evidence, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"REM-P1-007 evidence written: {args.output}")
    print(f"Tests={tests} Failures={failures} Errors={errors} Skipped={skipped}")
    print("Closure=false")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (EvidenceError, OSError, KeyError, ValueError, json.JSONDecodeError, ElementTree.ParseError) as exc:
        print(f"REM-P1-007 EVIDENCE ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
