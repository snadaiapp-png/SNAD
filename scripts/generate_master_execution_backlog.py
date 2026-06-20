#!/usr/bin/env python3
"""Generate the complete SANAD Master Execution Backlog (#23).

Outputs:
- sanad-master-execution-backlog.csv
- sanad-master-execution-backlog.json
- sanad-mvp-backlog.csv
- sanad-backlog-summary.json
- sanad-sprint-plan.csv
- sanad-resource-matrix.csv

The generator is deterministic and produces a Jira/Azure DevOps/GitHub Projects
import-ready hierarchy with:
Program -> Epic -> Feature -> Story -> Task.
"""

from __future__ import annotations

import csv
import json
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

OUTPUT_DIR = Path(__file__).resolve().parents[1] / "generated" / "executor-23"

MIN_COUNTS = {
    "Epic": 150,
    "Feature": 800,
    "Story": 4000,
    "Task": 15000,
}

SPRINT_LENGTH_WEEKS = 2
SPRINT_COUNT = 96
SQUADS = ["Core", "Experience", "Intelligence", "Platform"]
SQUAD_CAPACITY_SP = 80

PLATFORMS = [
    (1, "Strategy & Feasibility", "STR", "Foundation", "Product Strategy Lead", 1),
    (2, "Enterprise Architecture", "ARCH", "Foundation", "Enterprise Architect", 1),
    (3, "Technology & Standards", "TECH", "Foundation", "Principal Engineer", 1),
    (4, "SaaS Core Platform", "SAAS", "Core Platform", "SaaS Platform Lead", 2),
    (5, "Infrastructure & DevOps", "DEVOPS", "Core Platform", "Platform Engineering Lead", 2),
    (6, "Security Governance & Compliance", "SEC", "Core Platform", "Security Lead", 2),
    (7, "Workflow Engine", "WF", "Intelligence & Automation", "Workflow Lead", 3),
    (8, "AI Core & Agent Ecosystem", "AI", "Intelligence & Automation", "AI Platform Lead", 3),
    (9, "CRM Platform", "CRM", "Business Applications", "CRM Product Lead", 4),
    (10, "ERP Core Platform", "ERP", "Business Applications", "ERP Product Lead", 4),
    (11, "Accounting Platform", "ACC", "Business Applications", "Finance Product Lead", 4),
    (12, "HRM Platform", "HRM", "Business Applications", "HR Product Lead", 4),
    (13, "Ecommerce & Customer Experience", "ECX", "Commerce", "Commerce Product Lead", 5),
    (14, "POS & Industry Engine", "POS", "Commerce", "Industry Solutions Lead", 5),
    (15, "QA & Release Management", "QA", "Delivery Excellence", "QA Lead", 6),
    (16, "Product Backlog & Delivery Planning", "PLAN", "Delivery Excellence", "Delivery Manager", 1),
    (17, "Master Product Backlog", "MPB", "Delivery Excellence", "Product Operations Lead", 1),
    (18, "MVP Planning", "MVP", "Delivery Excellence", "MVP Program Lead", 2),
    (19, "Go-Live & Commercial Launch", "GL", "Commercialization", "Launch Director", 6),
    (20, "Scale Growth & Global Expansion", "GROW", "Commercialization", "Growth Director", 7),
    (21, "Partner Ecosystem & Marketplace", "PART", "Ecosystem", "Ecosystem Director", 7),
    (22, "Enterprise Data Analytics & Intelligence", "DATA", "Data Platform", "Data Platform Lead", 7),
]

EPIC_THEMES = [
    "Domain Foundation and Master Data",
    "Core Transactions and Operational Lifecycle",
    "User Experience, Workflow and Collaboration",
    "APIs, Events and Ecosystem Integration",
    "Analytics, Intelligence and Decision Support",
    "Security, Compliance and Auditability",
    "Reliability, Administration and Scale Operations",
]

FEATURE_PATTERNS = [
    "Domain model, lifecycle and business rules",
    "Versioned APIs, commands, queries and events",
    "User journeys, approvals and exception handling",
    "Authorization, tenant isolation and audit controls",
    "Integration, synchronization and data quality",
    "Observability, administration and operational controls",
]

STORY_PATTERNS = [
    ("Define business behavior and readiness", "As a product owner, I need explicit rules, scope and readiness criteria so implementation is unambiguous."),
    ("Implement primary operational path", "As an authorized user, I need the primary end-to-end flow so the business outcome is completed."),
    ("Implement validation and exception paths", "As an operator, I need invalid and exceptional cases handled predictably so data integrity is protected."),
    ("Enforce tenancy, access and audit", "As a security owner, I need tenant isolation, authorization and audit evidence so access is controlled."),
    ("Verify quality, telemetry and documentation", "As an operations owner, I need tests, metrics, logs and runbooks so the capability is supportable."),
]

TASK_PATTERNS = [
    ("Schema and contracts", "Create or update schema, domain objects, validation rules, migrations and versioned API/event contracts.", "Backend Engineer", 1),
    ("Service implementation", "Implement application services, business rules, transaction handling, permissions and error mapping.", "Backend Engineer", 2),
    ("Experience and integration", "Implement UI or integration adapters, workflow hooks, localization and accessibility behavior.", "Full-stack Engineer", 2),
    ("Verification and evidence", "Add unit, integration, security and acceptance tests; update documentation, telemetry and evidence.", "QA Automation Engineer", 1),
]

MVP_PLATFORM_NUMBERS = {4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 18, 22}
MVP_EPIC_INDEXES = {1, 2, 3}
P0_PLATFORM_NUMBERS = {4, 5, 6, 7, 8, 11, 15, 18}

DEPENDENCIES = {
    2: ["SNAD-P01-E01"],
    3: ["SNAD-P02-E01"],
    4: ["SNAD-P02-E01", "SNAD-P03-E01"],
    5: ["SNAD-P03-E01", "SNAD-P06-E01"],
    6: ["SNAD-P02-E01", "SNAD-P03-E01"],
    7: ["SNAD-P04-E01", "SNAD-P05-E01", "SNAD-P06-E01"],
    8: ["SNAD-P04-E01", "SNAD-P05-E01", "SNAD-P06-E01", "SNAD-P07-E01"],
    9: ["SNAD-P04-E01", "SNAD-P06-E01", "SNAD-P07-E01", "SNAD-P08-E01"],
    10: ["SNAD-P04-E01", "SNAD-P06-E01", "SNAD-P07-E01"],
    11: ["SNAD-P10-E01", "SNAD-P04-E01", "SNAD-P06-E01", "SNAD-P07-E01"],
    12: ["SNAD-P04-E01", "SNAD-P06-E01", "SNAD-P07-E01", "SNAD-P11-E01"],
    13: ["SNAD-P04-E01", "SNAD-P06-E01", "SNAD-P07-E01", "SNAD-P08-E01", "SNAD-P09-E01"],
    14: ["SNAD-P10-E01", "SNAD-P11-E01", "SNAD-P13-E01"],
    15: ["SNAD-P03-E01", "SNAD-P05-E01", "SNAD-P06-E01"],
    16: ["SNAD-P01-E01"],
    17: ["SNAD-P16-E01"],
    18: ["SNAD-P17-E01"],
    19: ["SNAD-P15-E01", "SNAD-P18-E01"],
    20: ["SNAD-P19-E01", "SNAD-P22-E01"],
    21: ["SNAD-P04-E01", "SNAD-P06-E01", "SNAD-P13-E01", "SNAD-P20-E01"],
    22: ["SNAD-P04-E01", "SNAD-P05-E01", "SNAD-P06-E01", "SNAD-P08-E01"],
}

CSV_FIELDS = [
    "External ID", "Issue Type", "Summary", "Description", "Platform",
    "Portfolio", "Epic Name", "Epic Link", "Parent External ID",
    "Owner Role", "Owner Squad", "Priority", "Size", "Story Points",
    "Original Estimate Hours", "MVP", "Release", "Execution Wave",
    "Target Sprint", "Dependencies", "Acceptance Criteria",
    "Component", "Labels", "Definition of Ready", "Definition of Done",
]

DOR = (
    "Business goal defined; scope and exclusions stated; acceptance criteria testable; "
    "dependencies identified; estimate assigned; design and API impact reviewed; priority approved."
)
DOD = (
    "Code complete and reviewed; unit/integration/security tests pass; tenant isolation verified; "
    "documentation and contracts updated; observability added; deployment verified; QA/Product evidence attached."
)


@dataclass(frozen=True)
class Platform:
    number: int
    name: str
    code: str
    portfolio: str
    owner: str
    wave: int


def priority(platform_number: int, epic_index: int, mvp: bool) -> str:
    if platform_number in P0_PLATFORM_NUMBERS and mvp:
        return "P0"
    if mvp:
        return "P1"
    return "P2" if epic_index <= 5 else "P3"


def release_for(wave: int, mvp: bool) -> str:
    if mvp:
        return "R1-MVP"
    return {
        1: "R2-Foundation",
        2: "R2-Core",
        3: "R3-Intelligence",
        4: "R4-Business",
        5: "R5-Commerce",
        6: "R6-GoLive",
        7: "R7-Scale",
    }[wave]


def squad_for(platform: Platform, epic_index: int) -> str:
    if platform.number in {5, 6, 15}:
        return "Platform"
    if platform.number in {7, 8, 22}:
        return "Intelligence"
    if platform.number in {9, 10, 11, 12, 13, 14, 19, 20, 21}:
        return "Experience" if epic_index in {3, 4} else "Core"
    return SQUADS[(platform.number + epic_index) % len(SQUADS)]


def sprint_for(platform: Platform, epic_index: int, feature_index: int) -> int:
    wave_start = {1: 1, 2: 9, 3: 21, 4: 33, 5: 49, 6: 65, 7: 77}[platform.wave]
    offset = ((epic_index - 1) * 2 + (feature_index - 1) // 3)
    return min(SPRINT_COUNT, wave_start + offset)


def row(**values: object) -> dict[str, object]:
    base = {field: "" for field in CSV_FIELDS}
    base.update(values)
    return base


def generate_rows() -> Iterable[dict[str, object]]:
    for raw in PLATFORMS:
        platform = Platform(*raw)
        platform_deps = DEPENDENCIES.get(platform.number, [])
        previous_epic = ""
        for epic_index, theme in enumerate(EPIC_THEMES, 1):
            epic_id = f"SNAD-P{platform.number:02d}-E{epic_index:02d}"
            epic_mvp = platform.number in MVP_PLATFORM_NUMBERS and epic_index in MVP_EPIC_INDEXES
            epic_deps = list(platform_deps)
            if previous_epic:
                epic_deps.append(previous_epic)
            epic_priority = priority(platform.number, epic_index, epic_mvp)
            squad = squad_for(platform, epic_index)
            epic_sprint = sprint_for(platform, epic_index, 1)
            yield row(
                **{
                    "External ID": epic_id,
                    "Issue Type": "Epic",
                    "Summary": f"{platform.name} — {theme}",
                    "Description": (
                        f"Deliver {theme.lower()} for {platform.name} as a multi-tenant, "
                        "API-first, workflow-first, AI-ready and security-by-design capability."
                    ),
                    "Platform": f"{platform.number:02d} - {platform.name}",
                    "Portfolio": platform.portfolio,
                    "Epic Name": epic_id,
                    "Owner Role": platform.owner,
                    "Owner Squad": squad,
                    "Priority": epic_priority,
                    "Size": "XXL",
                    "Story Points": 180,
                    "Original Estimate Hours": 1440,
                    "MVP": "Yes" if epic_mvp else "No",
                    "Release": release_for(platform.wave, epic_mvp),
                    "Execution Wave": f"Wave {platform.wave}",
                    "Target Sprint": f"S{epic_sprint:03d}",
                    "Dependencies": ";".join(epic_deps),
                    "Acceptance Criteria": (
                        f"All six features in {theme} are accepted; all P0/P1 stories are Done; "
                        "cross-tenant access is denied; API/event contracts are versioned; "
                        "security, quality, observability and operational evidence are complete."
                    ),
                    "Component": platform.code,
                    "Labels": f"sanad,p{platform.number:02d},{platform.code.lower()},epic,wave-{platform.wave}",
                    "Definition of Ready": DOR,
                    "Definition of Done": DOD,
                }
            )

            previous_feature = ""
            for feature_index, feature_pattern in enumerate(FEATURE_PATTERNS, 1):
                feature_id = f"{epic_id}-F{feature_index:02d}"
                feature_mvp = epic_mvp and feature_index <= 4
                feature_sprint = sprint_for(platform, epic_index, feature_index)
                feature_deps = [previous_feature] if previous_feature else [*epic_deps]
                yield row(
                    **{
                        "External ID": feature_id,
                        "Issue Type": "Feature",
                        "Summary": f"{theme} — {feature_pattern}",
                        "Description": (
                            f"Design and deliver {feature_pattern.lower()} for the "
                            f"{theme.lower()} scope of {platform.name}."
                        ),
                        "Platform": f"{platform.number:02d} - {platform.name}",
                        "Portfolio": platform.portfolio,
                        "Epic Link": epic_id,
                        "Parent External ID": epic_id,
                        "Owner Role": platform.owner,
                        "Owner Squad": squad,
                        "Priority": priority(platform.number, epic_index, feature_mvp),
                        "Size": "XL",
                        "Story Points": 30,
                        "Original Estimate Hours": 240,
                        "MVP": "Yes" if feature_mvp else "No",
                        "Release": release_for(platform.wave, feature_mvp),
                        "Execution Wave": f"Wave {platform.wave}",
                        "Target Sprint": f"S{feature_sprint:03d}",
                        "Dependencies": ";".join(filter(None, feature_deps)),
                        "Acceptance Criteria": (
                            f"{feature_pattern} is usable end-to-end for {platform.name}; "
                            "valid paths succeed, invalid paths fail safely, tenant isolation and "
                            "authorization are enforced, audit events and telemetry are emitted, and automated tests pass."
                        ),
                        "Component": platform.code,
                        "Labels": f"sanad,p{platform.number:02d},{platform.code.lower()},feature,wave-{platform.wave}",
                        "Definition of Ready": DOR,
                        "Definition of Done": DOD,
                    }
                )

                previous_story = ""
                for story_index, (story_title, story_description) in enumerate(STORY_PATTERNS, 1):
                    story_id = f"{feature_id}-S{story_index:02d}"
                    story_mvp = feature_mvp and story_index <= 4
                    story_deps = [previous_story] if previous_story else [*feature_deps]
                    yield row(
                        **{
                            "External ID": story_id,
                            "Issue Type": "Story",
                            "Summary": f"{feature_pattern} — {story_title}",
                            "Description": (
                                f"{story_description} Context: {platform.name} / {theme} / {feature_pattern}."
                            ),
                            "Platform": f"{platform.number:02d} - {platform.name}",
                            "Portfolio": platform.portfolio,
                            "Epic Link": epic_id,
                            "Parent External ID": feature_id,
                            "Owner Role": platform.owner,
                            "Owner Squad": squad,
                            "Priority": priority(platform.number, epic_index, story_mvp),
                            "Size": "L",
                            "Story Points": 6,
                            "Original Estimate Hours": 48,
                            "MVP": "Yes" if story_mvp else "No",
                            "Release": release_for(platform.wave, story_mvp),
                            "Execution Wave": f"Wave {platform.wave}",
                            "Target Sprint": f"S{feature_sprint:03d}",
                            "Dependencies": ";".join(filter(None, story_deps)),
                            "Acceptance Criteria": (
                                f"Given an authorized user in a valid tenant, when {story_title.lower()} "
                                f"is executed for {feature_pattern.lower()}, then the expected business result is persisted "
                                "and returned; unauthorized or cross-tenant access is rejected; validation and error codes are "
                                "deterministic; audit, logs and metrics are emitted; contract, regression and security tests pass."
                            ),
                            "Component": platform.code,
                            "Labels": f"sanad,p{platform.number:02d},{platform.code.lower()},story,wave-{platform.wave}",
                            "Definition of Ready": DOR,
                            "Definition of Done": DOD,
                        }
                    )

                    previous_task = ""
                    for task_index, (task_title, task_description, task_owner, task_points) in enumerate(TASK_PATTERNS, 1):
                        task_id = f"{story_id}-T{task_index:02d}"
                        task_deps = [previous_task] if previous_task else [*story_deps]
                        yield row(
                            **{
                                "External ID": task_id,
                                "Issue Type": "Task",
                                "Summary": f"{story_title} — {task_title}",
                                "Description": (
                                    f"{task_description} Scope: {platform.name} / {theme} / "
                                    f"{feature_pattern} / {story_title}."
                                ),
                                "Platform": f"{platform.number:02d} - {platform.name}",
                                "Portfolio": platform.portfolio,
                                "Epic Link": epic_id,
                                "Parent External ID": story_id,
                                "Owner Role": task_owner,
                                "Owner Squad": squad,
                                "Priority": priority(platform.number, epic_index, story_mvp),
                                "Size": {1: "XS", 2: "S", 3: "M", 5: "L", 8: "XL", 13: "XXL"}[task_points],
                                "Story Points": task_points,
                                "Original Estimate Hours": task_points * 8,
                                "MVP": "Yes" if story_mvp else "No",
                                "Release": release_for(platform.wave, story_mvp),
                                "Execution Wave": f"Wave {platform.wave}",
                                "Target Sprint": f"S{feature_sprint:03d}",
                                "Dependencies": ";".join(filter(None, task_deps)),
                                "Acceptance Criteria": (
                                    "Implementation is reviewed; automated tests pass; no unresolved critical/high security "
                                    "finding exists; tenant isolation and authorization are verified where applicable; "
                                    "documentation, traceability, telemetry and acceptance evidence are updated."
                                ),
                                "Component": platform.code,
                                "Labels": f"sanad,p{platform.number:02d},{platform.code.lower()},task,wave-{platform.wave}",
                                "Definition of Ready": DOR,
                                "Definition of Done": DOD,
                            }
                        )
                        previous_task = task_id
                    previous_story = story_id
                previous_feature = feature_id
            previous_epic = epic_id


def write_csv(path: Path, rows: list[dict[str, object]]) -> None:
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=CSV_FIELDS)
        writer.writeheader()
        writer.writerows(rows)


def write_json(path: Path, rows: list[dict[str, object]]) -> None:
    path.write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8")


def write_sprint_plan(path: Path, rows: list[dict[str, object]]) -> None:
    points_by_sprint = defaultdict(int)
    mvp_points_by_sprint = defaultdict(int)
    for item in rows:
        if item["Issue Type"] != "Task":
            continue
        sprint = item["Target Sprint"]
        points_by_sprint[sprint] += int(item["Story Points"])
        if item["MVP"] == "Yes":
            mvp_points_by_sprint[sprint] += int(item["Story Points"])

    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        fields = [
            "Sprint", "Duration Weeks", "Squads", "Capacity SP",
            "Planned Task SP", "MVP Task SP", "Capacity Status",
        ]
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        for index in range(1, SPRINT_COUNT + 1):
            sprint = f"S{index:03d}"
            planned = points_by_sprint[sprint]
            capacity = len(SQUADS) * SQUAD_CAPACITY_SP
            writer.writerow({
                "Sprint": sprint,
                "Duration Weeks": SPRINT_LENGTH_WEEKS,
                "Squads": len(SQUADS),
                "Capacity SP": capacity,
                "Planned Task SP": planned,
                "MVP Task SP": mvp_points_by_sprint[sprint],
                "Capacity Status": "Within baseline" if planned <= capacity else "Requires rebalancing",
            })


def write_resource_matrix(path: Path) -> None:
    records = [
        ("Core", "SaaS, ERP, Accounting, HRM and shared domain services", "Product Lead; Tech Lead; 4 Backend; 2 Full-stack; 1 QA; 0.5 UX", 80),
        ("Experience", "CRM, Ecommerce, POS, partner and launch experiences", "Product Lead; Tech Lead; 2 Backend; 4 Full-stack; 1 QA; 1 UX", 80),
        ("Intelligence", "Workflow, AI, data, analytics and automation", "Product Lead; Tech Lead; 3 Backend/Data; 2 AI/ML; 1 Full-stack; 1 QA", 80),
        ("Platform", "Infrastructure, security, DevOps, QA platform and reliability", "Platform Lead; Security Lead; 3 Platform; 2 SRE; 1 QA Automation", 80),
    ]
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(["Squad", "Primary Scope", "Baseline Composition", "Capacity SP per Sprint"])
        writer.writerows(records)


def validate(rows: list[dict[str, object]]) -> dict[str, object]:
    counts = Counter(str(item["Issue Type"]) for item in rows)
    for issue_type, minimum in MIN_COUNTS.items():
        actual = counts[issue_type]
        if actual < minimum:
            raise SystemExit(f"{issue_type} count {actual} is below required minimum {minimum}")

    ids = [str(item["External ID"]) for item in rows]
    if len(ids) != len(set(ids)):
        raise SystemExit("Duplicate External ID detected")

    id_set = set(ids)
    broken_parents = [
        item["External ID"] for item in rows
        if item["Parent External ID"] and item["Parent External ID"] not in id_set
    ]
    if broken_parents:
        raise SystemExit(f"Broken parent references: {broken_parents[:10]}")

    task_points = sum(int(item["Story Points"]) for item in rows if item["Issue Type"] == "Task")
    mvp_tasks = [item for item in rows if item["Issue Type"] == "Task" and item["MVP"] == "Yes"]
    return {
        "total_items": len(rows),
        "counts": dict(counts),
        "platforms": len(PLATFORMS),
        "execution_waves": 7,
        "releases": sorted({str(item["Release"]) for item in rows}),
        "task_story_points": task_points,
        "mvp_task_count": len(mvp_tasks),
        "mvp_task_story_points": sum(int(item["Story Points"]) for item in mvp_tasks),
        "sprints": SPRINT_COUNT,
        "squad_count": len(SQUADS),
        "squad_capacity_sp_per_sprint": SQUAD_CAPACITY_SP,
        "portfolio_capacity_sp_per_sprint": len(SQUADS) * SQUAD_CAPACITY_SP,
        "validation": "PASSED",
    }


def main() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    rows = list(generate_rows())
    summary = validate(rows)

    write_csv(OUTPUT_DIR / "sanad-master-execution-backlog.csv", rows)
    write_json(OUTPUT_DIR / "sanad-master-execution-backlog.json", rows)

    mvp_rows = [item for item in rows if item["MVP"] == "Yes"]
    write_csv(OUTPUT_DIR / "sanad-mvp-backlog.csv", mvp_rows)
    write_sprint_plan(OUTPUT_DIR / "sanad-sprint-plan.csv", rows)
    write_resource_matrix(OUTPUT_DIR / "sanad-resource-matrix.csv")

    (OUTPUT_DIR / "sanad-backlog-summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
