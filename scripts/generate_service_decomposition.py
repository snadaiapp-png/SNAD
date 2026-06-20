#!/usr/bin/env python3
"""Generate SANAD Executor #24 service decomposition artifacts.

Outputs:
- sanad-service-catalog.csv
- sanad-service-catalog.json
- sanad-api-catalog.csv
- sanad-event-catalog.csv
- sanad-domain-ownership.csv
- sanad-service-dependency-matrix.csv
- sanad-service-decomposition-summary.json
"""

from __future__ import annotations

import csv
import json
from collections import Counter, defaultdict
from pathlib import Path

OUTPUT_DIR = Path(__file__).resolve().parents[1] / "generated" / "executor-24"

DOMAINS = [
    ("CORE", "SaaS Core", "Core Platform", "Core Squad", ["tenant", "identity", "subscription", "module", "notification", "configuration"]),
    ("SEC", "Security Governance", "Core Platform", "Platform Squad", ["policy", "audit", "risk", "consent", "access-review", "compliance"]),
    ("WF", "Workflow Automation", "Intelligence & Automation", "Intelligence Squad", ["process", "task", "rule", "approval", "sla", "orchestration"]),
    ("AI", "AI Core and Agents", "Intelligence & Automation", "Intelligence Squad", ["agent", "prompt", "knowledge", "recommendation", "prediction", "ai-governance"]),
    ("CRM", "Customer Relationship Management", "Business Applications", "Experience Squad", ["account", "contact", "lead", "opportunity", "activity", "case"]),
    ("ERP", "ERP Operations", "Business Applications", "Core Squad", ["procurement", "inventory", "project", "asset", "contract", "service-order"]),
    ("ACC", "Accounting and Finance", "Business Applications", "Core Squad", ["ledger", "invoice", "payment", "tax", "bank", "closing"]),
    ("HRM", "Human Resources", "Business Applications", "Experience Squad", ["employee", "recruitment", "attendance", "leave", "payroll", "performance"]),
    ("ECX", "Ecommerce and CX", "Commerce", "Experience Squad", ["storefront", "catalog", "cart", "checkout", "order", "loyalty"]),
    ("POS", "POS and Industry Engine", "Commerce", "Experience Squad", ["terminal", "sale", "shift", "industry-template", "pricing", "fulfillment"]),
    ("DATA", "Enterprise Data and Analytics", "Data Platform", "Intelligence Squad", ["event-stream", "warehouse", "metric", "dashboard", "data-quality", "insight"]),
    ("OPS", "Platform Operations", "Delivery Excellence", "Platform Squad", ["release", "quality", "environment", "observability", "incident", "support"]),
    ("MKT", "Partner Marketplace", "Ecosystem", "Experience Squad", ["partner", "app-listing", "integration", "commission", "developer", "review"]),
]

SERVICE_PATTERNS = [
    ("command", "Owns write model, validations and lifecycle transitions"),
    ("query", "Owns read model, search and projection views"),
    ("workflow", "Owns process automation, approvals and SLA control"),
    ("integration", "Owns inbound and outbound adapters and synchronization"),
    ("analytics", "Owns metrics, insights and analytical projections"),
    ("admin", "Owns configuration, audit views and operational administration"),
]

API_PATTERNS = [
    ("POST", "create", "Create a new aggregate instance"),
    ("GET", "get", "Retrieve an aggregate instance"),
    ("PATCH", "update", "Update mutable aggregate attributes"),
    ("POST", "transition", "Execute lifecycle transition or approval action"),
    ("GET", "search", "Search and filter aggregate records"),
]

EVENT_PATTERNS = [
    ("Created", "Published after a new aggregate is committed"),
    ("Updated", "Published after material business data changes"),
    ("StatusChanged", "Published after lifecycle transition"),
    ("Approved", "Published after workflow approval"),
    ("Rejected", "Published after workflow rejection"),
    ("Archived", "Published when record leaves the active operational set"),
]

CSV_SERVICE_FIELDS = [
    "Service ID", "Domain", "Subdomain", "Bounded Context", "Service Name", "Service Type",
    "Description", "Owner Squad", "System of Record", "Database Ownership", "Primary Aggregate",
    "Criticality", "MVP", "Deployment Unit", "Sync Dependencies", "Async Dependencies",
    "Security Boundary", "Data Classification", "SLO Target", "Backlog References",
]

API_FIELDS = [
    "API ID", "Service ID", "Domain", "Method", "Path", "Purpose", "Auth Scope", "Version", "Idempotency", "Rate Limit Tier",
]

EVENT_FIELDS = [
    "Event ID", "Producer Service ID", "Domain", "Event Name", "Topic", "Schema Version", "Description", "Consumers", "Retention Class",
]

OWNERSHIP_FIELDS = ["Domain", "Bounded Context", "Owner Squad", "Product Owner", "Tech Owner", "Primary Data Objects", "Decision Rights"]
DEPENDENCY_FIELDS = ["Service ID", "Depends On", "Dependency Type", "Reason", "Runtime Criticality", "Fallback Strategy"]

MVP_DOMAINS = {"CORE", "SEC", "WF", "AI", "CRM", "ERP", "ACC", "ECX", "DATA", "OPS"}
P0_DOMAINS = {"CORE", "SEC", "WF", "ACC", "OPS"}


def slug(value: str) -> str:
    return value.lower().replace(" ", "-").replace("&", "and")


def service_rows():
    for domain_code, domain_name, portfolio, squad, aggregates in DOMAINS:
        for index, aggregate in enumerate(aggregates, 1):
            context = f"{domain_name} / {aggregate.title()} Context"
            for pattern_index, (service_type, description) in enumerate(SERVICE_PATTERNS, 1):
                service_id = f"SNAD-SVC-{domain_code}-{index:02d}-{service_type.upper()}"
                is_mvp = domain_code in MVP_DOMAINS and index <= 4 and service_type in {"command", "query", "workflow", "integration"}
                criticality = "P0" if domain_code in P0_DOMAINS and is_mvp else ("P1" if is_mvp else "P2")
                dependencies_sync = []
                dependencies_async = []
                if domain_code != "CORE":
                    dependencies_sync.append("SNAD-SVC-CORE-01-QUERY")
                if domain_code not in {"SEC", "CORE"}:
                    dependencies_async.append("SNAD-SVC-SEC-02-ADMIN")
                if service_type == "workflow" and domain_code != "WF":
                    dependencies_sync.append("SNAD-SVC-WF-01-COMMAND")
                if service_type == "analytics" and domain_code != "DATA":
                    dependencies_async.append("SNAD-SVC-DATA-01-INTEGRATION")
                yield {
                    "Service ID": service_id,
                    "Domain": domain_name,
                    "Subdomain": aggregate.title(),
                    "Bounded Context": context,
                    "Service Name": f"{domain_name} {aggregate.title()} {service_type.title()} Service",
                    "Service Type": service_type,
                    "Description": f"{description} for {aggregate} in {domain_name}.",
                    "Owner Squad": squad,
                    "System of Record": "Yes" if service_type == "command" else "No",
                    "Database Ownership": "Own database/schema" if service_type == "command" else "Projection/read model only",
                    "Primary Aggregate": aggregate,
                    "Criticality": criticality,
                    "MVP": "Yes" if is_mvp else "No",
                    "Deployment Unit": f"{slug(domain_code)}-{slug(aggregate)}-{service_type}",
                    "Sync Dependencies": ";".join(dependencies_sync),
                    "Async Dependencies": ";".join(dependencies_async),
                    "Security Boundary": "Tenant + Organization + RBAC/ABAC + audit policy",
                    "Data Classification": "Confidential" if domain_code in {"ACC", "HRM", "SEC"} else "Internal",
                    "SLO Target": "99.95" if criticality in {"P0", "P1"} else "99.90",
                    "Backlog References": f"SNAD-P{domain_to_platform(domain_code):02d}-E01;SNAD-P{domain_to_platform(domain_code):02d}-E04;SNAD-P{domain_to_platform(domain_code):02d}-E06",
                }


def domain_to_platform(domain_code: str) -> int:
    mapping = {
        "CORE": 4, "SEC": 6, "WF": 7, "AI": 8, "CRM": 9, "ERP": 10,
        "ACC": 11, "HRM": 12, "ECX": 13, "POS": 14, "DATA": 22,
        "OPS": 15, "MKT": 21,
    }
    return mapping[domain_code]


def api_rows(services: list[dict[str, str]]):
    for svc in services:
        aggregate = slug(svc["Primary Aggregate"])
        service_slug = slug(svc["Service Name"])
        for index, (method, action, purpose) in enumerate(API_PATTERNS, 1):
            if svc["Service Type"] not in {"command", "query", "admin"} and action in {"create", "update", "transition"}:
                continue
            yield {
                "API ID": f"{svc['Service ID']}-API-{index:02d}",
                "Service ID": svc["Service ID"],
                "Domain": svc["Domain"],
                "Method": method,
                "Path": f"/api/v1/{service_slug}/{aggregate}/{action}",
                "Purpose": purpose,
                "Auth Scope": f"{slug(svc['Domain'])}:{aggregate}:{action}",
                "Version": "v1",
                "Idempotency": "Required" if method in {"POST", "PATCH"} else "Not required",
                "Rate Limit Tier": "critical" if svc["Criticality"] in {"P0", "P1"} else "standard",
            }


def event_rows(services: list[dict[str, str]]):
    command_services = [svc for svc in services if svc["Service Type"] == "command"]
    service_ids_by_domain = defaultdict(list)
    for svc in services:
        service_ids_by_domain[svc["Domain"]].append(svc["Service ID"])
    for svc in command_services:
        aggregate = svc["Primary Aggregate"].title().replace("-", "")
        consumers = []
        consumers.extend(service_ids_by_domain[svc["Domain"]][:3])
        if svc["Domain"] != "Enterprise Data and Analytics":
            consumers.append("SNAD-SVC-DATA-01-INTEGRATION")
        for index, (suffix, description) in enumerate(EVENT_PATTERNS, 1):
            event_name = f"{aggregate}{suffix}"
            yield {
                "Event ID": f"{svc['Service ID']}-EVT-{index:02d}",
                "Producer Service ID": svc["Service ID"],
                "Domain": svc["Domain"],
                "Event Name": event_name,
                "Topic": f"sanad.{slug(svc['Domain'])}.{slug(svc['Primary Aggregate'])}.{slug(suffix)}",
                "Schema Version": "1.0.0",
                "Description": description,
                "Consumers": ";".join(sorted(set(consumers))),
                "Retention Class": "audit-retained" if svc["Criticality"] in {"P0", "P1"} else "operational-retained",
            }


def ownership_rows():
    for domain_code, domain_name, _portfolio, squad, aggregates in DOMAINS:
        yield {
            "Domain": domain_name,
            "Bounded Context": f"{domain_name} bounded context group",
            "Owner Squad": squad,
            "Product Owner": f"{domain_name} Product Owner",
            "Tech Owner": f"{domain_name} Technical Owner",
            "Primary Data Objects": ";".join(aggregates),
            "Decision Rights": "Owns domain model, APIs, events, data schema, SLOs, backlog decomposition and production readiness for the bounded context.",
        }


def dependency_rows(services: list[dict[str, str]]):
    for svc in services:
        for dep in filter(None, svc["Sync Dependencies"].split(";")):
            yield {
                "Service ID": svc["Service ID"],
                "Depends On": dep,
                "Dependency Type": "Synchronous API",
                "Reason": "Runtime authorization, lookup or orchestration dependency",
                "Runtime Criticality": "High" if svc["Criticality"] in {"P0", "P1"} else "Medium",
                "Fallback Strategy": "Fail closed for security; cached read model for non-security lookup where approved",
            }
        for dep in filter(None, svc["Async Dependencies"].split(";")):
            yield {
                "Service ID": svc["Service ID"],
                "Depends On": dep,
                "Dependency Type": "Asynchronous Event",
                "Reason": "Audit, analytics, synchronization or operational projection",
                "Runtime Criticality": "Medium",
                "Fallback Strategy": "Queue retry, dead-letter topic, replay from event store",
            }


def write_csv(path: Path, fields: list[str], rows: list[dict[str, str]]) -> None:
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)


def main() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    services = list(service_rows())
    apis = list(api_rows(services))
    events = list(event_rows(services))
    owners = list(ownership_rows())
    deps = list(dependency_rows(services))

    write_csv(OUTPUT_DIR / "sanad-service-catalog.csv", CSV_SERVICE_FIELDS, services)
    write_csv(OUTPUT_DIR / "sanad-api-catalog.csv", API_FIELDS, apis)
    write_csv(OUTPUT_DIR / "sanad-event-catalog.csv", EVENT_FIELDS, events)
    write_csv(OUTPUT_DIR / "sanad-domain-ownership.csv", OWNERSHIP_FIELDS, owners)
    write_csv(OUTPUT_DIR / "sanad-service-dependency-matrix.csv", DEPENDENCY_FIELDS, deps)
    (OUTPUT_DIR / "sanad-service-catalog.json").write_text(json.dumps(services, ensure_ascii=False, indent=2), encoding="utf-8")

    service_ids = [svc["Service ID"] for svc in services]
    if len(service_ids) != len(set(service_ids)):
        raise SystemExit("Duplicate service IDs detected")
    service_set = set(service_ids)
    for dep in deps:
        if dep["Depends On"] not in service_set:
            raise SystemExit(f"Unknown dependency {dep['Depends On']}")

    summary = {
        "domains": len(DOMAINS),
        "bounded_contexts": len(DOMAINS) * 6,
        "services": len(services),
        "apis": len(apis),
        "events": len(events),
        "dependency_edges": len(deps),
        "mvp_services": sum(1 for svc in services if svc["MVP"] == "Yes"),
        "critical_services": dict(Counter(svc["Criticality"] for svc in services)),
        "validation": "PASSED",
    }
    (OUTPUT_DIR / "sanad-service-decomposition-summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
