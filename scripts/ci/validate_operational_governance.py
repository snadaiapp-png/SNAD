#!/usr/bin/env python3
"""Fail-closed validation for SANAD SLA/SLO and incident operations."""
from __future__ import annotations
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / "docs/operations/reliability"

class ValidationError(Exception):
    pass

def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValidationError(message)

def text(path: Path) -> str:
    require(path.is_file(), f"missing file: {path.relative_to(ROOT)}")
    value = path.read_text(encoding="utf-8")
    require(value.strip(), f"empty file: {path.relative_to(ROOT)}")
    return value

def load_json(path: Path) -> dict:
    try:
        return json.loads(text(path))
    except json.JSONDecodeError as exc:
        raise ValidationError(f"invalid JSON {path.relative_to(ROOT)}: {exc}") from exc

def main() -> int:
    policy = text(BASE / "SLA-SLO-POLICY.md")
    incident = text(BASE / "INCIDENT-MANAGEMENT.md")
    escalation = text(BASE / "ON-CALL-ESCALATION.md")
    readme = text(BASE / "README.md")
    closure = text(ROOT / "docs/governance/P1-008-CLOSURE-DECISION-2026-07-17.md")
    issue_form = text(ROOT / ".github/ISSUE_TEMPLATE/incident.yml")
    monthly_workflow = text(ROOT / ".github/workflows/monthly-service-review.yml")

    for token in ("SLI", "SLO", "SLA", "Error budget", "99.95%", "99.90%", "99.50%",
                  "Deferred", "fifth business day", "budget exhausted"):
        require(token.lower() in policy.lower() or token.lower() in readme.lower(),
                f"policy token missing: {token}")

    for sev in ("SEV0", "SEV1", "SEV2", "SEV3"):
        require(sev in incident and sev in issue_form, f"missing severity: {sev}")

    for role in ("Incident Commander", "Technical Lead", "Communications Lead",
                 "Scribe", "Security Lead", "Service Owner"):
        require(role in incident, f"missing command role: {role}")

    for token in ("24×7", "5 minutes", "10 minutes", "Project Owner",
                  "Security Lead", "Financial"):
        require(token in escalation, f"escalation token missing: {token}")

    catalog = load_json(BASE / "service-level-catalog.json")
    require(catalog.get("schema_version") == "1.0", "catalog schema")
    require(catalog.get("timezone") == "Asia/Riyadh", "catalog timezone")
    services = catalog.get("services")
    require(isinstance(services, list) and len(services) >= 7, "service coverage")

    ids = set()
    tiers = set()
    for service in services:
        sid = service.get("id")
        require(isinstance(sid, str) and sid and sid not in ids, "unique service id")
        ids.add(sid)
        tier = service.get("tier")
        require(tier in (0, 1, 2), f"{sid}: invalid tier")
        tiers.add(tier)
        slo = service.get("availability_slo_percent")
        sla = service.get("external_sla_target_percent")
        require(isinstance(slo, (int, float)) and isinstance(sla, (int, float)),
                f"{sid}: target types")
        require(99.0 <= sla <= slo < 100, f"{sid}: targets")
        require(service.get("latency_target_ms", 0) > 0, f"{sid}: latency")
        for key in ("owner_role", "success_contract", "measurement_sources"):
            require(service.get(key), f"{sid}: missing {key}")

    require(tiers == {0, 1, 2}, "all service tiers required")
    require({"bff-api", "identity-session", "financial-integrity"} <= ids,
            "critical services missing")

    roster = load_json(BASE / "on-call-roster.json")
    require(roster.get("timezone") == "Asia/Riyadh", "roster timezone")
    require(roster.get("primary", {}).get("github"), "primary on-call")
    require(roster.get("secondary", {}).get("github"), "secondary on-call")
    require(roster.get("executive_escalation", {}).get("page_after_minutes") <= 10,
            "executive escalation timer")
    require(len(roster.get("specialist_escalations", [])) >= 3,
            "specialist escalation coverage")
    require(roster.get("residual_risk"), "roster residual risk")

    for name in ("INCIDENT-REPORT.md", "POST-INCIDENT-REVIEW.md",
                 "MONTHLY-SERVICE-REPORT.md"):
        value = text(BASE / "templates" / name)
        require("Owner" in value or "owner" in value, f"{name}: owner evidence")
        require("Evidence" in value or "evidence" in value, f"{name}: evidence")

    require("schedule:" in monthly_workflow and "cron:" in monthly_workflow,
            "monthly schedule missing")
    require("issues: write" in monthly_workflow, "monthly issue permission missing")
    require("P1-008: CLOSED" in closure, "closure decision missing")
    require("does not assert" in closure.lower(), "closure boundary missing")

    print("P1-008 OPERATIONAL GOVERNANCE VALIDATION PASSED")
    print(f"Services={len(services)} Tiers={sorted(tiers)}")
    print("Severities=SEV0,SEV1,SEV2,SEV3")
    print("On-call=PRIMARY+SECONDARY+EXECUTIVE+SPECIALISTS")
    print("Monthly reporting=SCHEDULED")
    return 0

if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValidationError, OSError, KeyError, TypeError, ValueError) as exc:
        print(f"P1-008 VALIDATION ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
