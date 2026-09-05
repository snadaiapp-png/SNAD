#!/usr/bin/env python3
"""Pure evaluator for the SNAD Workflow Y2 production evidence probe.

This module contains no network or database writes. The shell collector gathers
sanitized read-only facts; this evaluator turns them into deterministic gates.
"""

from __future__ import annotations

import argparse
import json
import re
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

EXPECTED_ENV_KEYS = [
    "APPLICATION_BASE_URL",
    "BOOTSTRAP_ENABLED",
    "CRM_CUSTOM_FIELD_ENCRYPTION_KEY",
    "DATABASE_DRIVER",
    "DATABASE_PASSWORD",
    "DATABASE_URL",
    "DATABASE_USERNAME",
    "FLYWAY_ENABLED",
    "FLYWAY_LOCATIONS",
    "FLYWAY_OUT_OF_ORDER",
    "FLYWAY_VALIDATE_ON_MIGRATE",
    "FLYWAY_BASELINE_ON_MIGRATE",
    "JPA_DDL_AUTO",
    "JWT_SECRET",
    "LAZY_INIT",
    "LOG_LEVEL_ROOT",
    "LOG_LEVEL_SANAD",
    "MANAGEMENT_ENDPOINTS",
    "SANAD_AI_GATEWAY_BASE_URL",
    "SANAD_CONTROL_PLANE_TENANT_ID",
    "SANAD_CORS_ALLOWED_ORIGINS",
    "SANAD_SERVICE_AUTH_JWT_SECRET",
    "SANAD_WORKFLOW_ENGINE_BASE_URL",
    "SECURITY_NOTIFICATION_ENDPOINT",
    "SECURITY_NOTIFICATION_FROM",
    "SECURITY_NOTIFICATION_PROVIDER",
    "SECURITY_NOTIFICATION_RESEND_API_KEY",
    "SERVER_PORT",
    "SHUTDOWN_TIMEOUT",
    "SPRING_DATASOURCE_PASSWORD",
    "SPRING_DATASOURCE_USERNAME",
    "SPRING_FLYWAY_OUT_OF_ORDER",
    "SPRING_FLYWAY_PASSWORD",
    "SPRING_FLYWAY_URL",
    "SPRING_FLYWAY_USER",
    "SPRING_PROFILES_ACTIVE",
    "DATABASE_POOL_MAX",
    "DATABASE_POOL_MIN",
    "DATABASE_POOL_TIMEOUT",
    "CONTROL_PLANE_ADMIN_EMAIL",
    "CONTROL_PLANE_ADMIN_PASSWORD",
    "CONTROL_PLANE_BOOTSTRAP_ENABLED",
    "CONTROL_PLANE_BOOTSTRAP_TOKEN",
    "CONTROL_PLANE_TENANT_ID",
]

REQUIRED_Y2_VERSIONS = [
    "20260902.1",
    "20260902.2",
    "20260902.3",
    "20260902.4",
    "20260902.5",
    "20260902.6",
    "20260902.7",
    "20260904.1",
]

Y2_CAPABILITIES = [
    "WORKFLOW.DESIGN",
    "WORKFLOW.VALIDATE",
    "WORKFLOW.PUBLISH",
    "WORKFLOW.START",
    "WORKFLOW.TASK_EXECUTE",
    "WORKFLOW.REASSIGN",
    "WORKFLOW.DELEGATE",
    "WORKFLOW.CANCEL",
    "WORKFLOW.INCIDENT_MANAGE",
    "WORKFLOW.MONITOR",
    "WORKFLOW.AUDIT_VIEW",
    "WORKFLOW.BREAK_GLASS",
    "WORKFLOW.SELF_APPROVAL_OVERRIDE",
]

Y2_TABLES = [
    "workflow_step_transitions",
    "workflow_work_items",
    "workflow_work_item_candidates",
    "workflow_branch_tokens",
    "workflow_business_calendars",
    "workflow_calendar_holidays",
    "workflow_delegations",
    "workflow_execution_attempts",
    "workflow_incidents",
    "workflow_event_inbox",
    "workflow_event_outbox",
    "workflow_notification_intents",
]

REQUIRED_COLUMNS = [
    "workflow_definitions.definition_family_id",
    "workflow_definitions.engine_generation",
    "workflow_definitions.publication_state",
    "workflow_definitions.published_by",
    "workflow_definitions.published_at",
    "workflow_definitions.validated_at",
    "workflow_definitions.definition_checksum",
    "workflow_definitions.schema_version",
    "workflow_instances.engine_generation",
    "workflow_instances.definition_family_id",
    "workflow_instances.definition_version_id",
    "workflow_instances.parent_instance_id",
    "workflow_instances.trigger_type",
    "workflow_instances.trigger_id",
    "workflow_instances.idempotency_key",
    "workflow_instances.causation_id",
    "workflow_instances.context_json",
    "workflow_instances.context_schema_version",
    "workflow_approval_requests.requested_from_employee_id",
    "workflow_approval_requests.approval_policy",
    "workflow_approval_requests.self_approval_policy",
    "workflow_approval_requests.policy_snapshot",
]

MIGRATION_DIRS = [
    Path("apps/sanad-platform/src/main/resources/db/migration"),
    Path("apps/sanad-platform/src/main/resources/db/vendor/postgresql"),
]


def assert_http_method(method: str) -> None:
    if method.upper() != "GET":
        raise ValueError(f"production evidence probe only permits HTTP GET, got {method}")


def assert_read_only_sql(sql: str) -> None:
    cleaned = re.sub(r"(?m)^\s*--.*$", "", sql).strip()
    forbidden = re.compile(
        r"\b(INSERT|UPDATE|DELETE|MERGE|ALTER|CREATE|DROP|TRUNCATE|GRANT|REVOKE|CALL|DO|COPY)\b",
        re.IGNORECASE,
    )
    if forbidden.search(cleaned):
        raise ValueError("production evidence probe SQL contains a mutating statement")

    # Permit one read-only statement only. A trailing semicolon is harmless,
    # but `SELECT ...; UPDATE ...` must never pass merely because SELECT came first.
    statements = [statement.strip() for statement in cleaned.split(";") if statement.strip()]
    if len(statements) != 1:
        raise ValueError("production evidence probe permits exactly one read-only SQL statement")

    statement = statements[0]
    first = statement.split(None, 1)[0].upper() if statement else ""
    if first in {"SELECT", "SHOW"}:
        return
    if first == "WITH" and re.search(r"\bSELECT\b", statement, re.IGNORECASE):
        return
    raise ValueError(
        f"production evidence probe only permits read-only SELECT/SHOW/CTE SQL, got {first or 'EMPTY'}"
    )


def version_key(version: str) -> tuple[int, ...]:
    value = str(version).strip()
    if not re.fullmatch(r"\d+(?:\.\d+)*", value):
        raise ValueError(f"unsupported non-numeric Flyway version: {value}")
    return tuple(int(part) for part in value.split("."))


def discover_repository_versions(repo_root: Path) -> tuple[list[str], list[str]]:
    versions: list[str] = []
    for relative_dir in MIGRATION_DIRS:
        directory = repo_root / relative_dir
        if not directory.exists():
            continue
        for path in sorted(directory.glob("V*.sql")):
            stem = path.name
            if "__" not in stem:
                continue
            raw = stem[1:].split("__", 1)[0]
            normalized = raw.replace("_", ".")
            version_key(normalized)
            versions.append(normalized)

    counts = Counter(versions)
    duplicates = sorted((v for v, count in counts.items() if count > 1), key=version_key)
    return sorted(set(versions), key=version_key), duplicates


def _evaluate_environment(snapshot: dict[str, Any]) -> dict[str, Any]:
    expected = set(EXPECTED_ENV_KEYS)
    items = snapshot.get("renderEnv", [])
    present = {
        str(item.get("key"))
        for item in items
        if item.get("key") and bool(item.get("present"))
    }
    all_keys = {str(item.get("key")) for item in items if item.get("key")}
    missing = sorted(expected - present)
    extra = sorted(all_keys - expected)
    return {
        "status": "PASS" if not missing else "FAIL",
        "expected": len(EXPECTED_ENV_KEYS),
        "present": len(expected & present),
        "missing": missing,
        "extra": extra,
        "contractDrift": bool(extra),
    }


def _effective_db_history(rows: list[dict[str, Any]]) -> dict[str, Any]:
    grouped: dict[str, list[dict[str, Any]]] = defaultdict(list)
    failed = 0
    for row in rows:
        version = row.get("version")
        if version in (None, ""):
            continue
        version = str(version)
        version_key(version)
        normalized = dict(row)
        normalized["version"] = version
        normalized["installedRank"] = int(row.get("installedRank", 0))
        normalized["type"] = str(row.get("type") or "").upper()
        normalized["success"] = bool(row.get("success"))
        if not normalized["success"]:
            failed += 1
        grouped[version].append(normalized)

    active: set[str] = set()
    delete_markers: set[str] = set()
    duplicates: set[str] = set()
    null_checksums: set[str] = set()

    for version, history in grouped.items():
        history.sort(key=lambda item: item["installedRank"])
        last_delete_rank = max(
            (
                item["installedRank"]
                for item in history
                if item["success"] and item["type"] == "DELETE"
            ),
            default=-1,
        )
        active_success = [
            item
            for item in history
            if item["success"]
            and item["type"] not in {"DELETE", "BASELINE", "SCHEMA_BASELINE"}
            and item["installedRank"] > last_delete_rank
        ]
        if active_success:
            active.add(version)
            if len(active_success) > 1:
                duplicates.add(version)
            if any(item.get("checksum") is None for item in active_success):
                null_checksums.add(version)
        elif last_delete_rank >= 0:
            delete_markers.add(version)

    return {
        "active": active,
        "failed": failed,
        "duplicates": duplicates,
        "deleteMarkers": delete_markers,
        "nullChecksums": null_checksums,
    }


def _evaluate_database(snapshot: dict[str, Any]) -> dict[str, Any]:
    repo_versions = [str(v) for v in snapshot.get("repositoryVersions", [])]
    for version in repo_versions:
        version_key(version)
    repo_counts = Counter(repo_versions)
    repo_duplicates = sorted(
        (v for v, count in repo_counts.items() if count > 1),
        key=version_key,
    )
    repo_set = set(repo_versions)

    history = _effective_db_history(list(snapshot.get("dbHistory", [])))
    active = history["active"]

    repo_missing = sorted(repo_set - active, key=version_key)
    db_not_repo = sorted(active - repo_set, key=version_key)
    duplicates = sorted(history["duplicates"], key=version_key)
    delete_markers = sorted(history["deleteMarkers"], key=version_key)
    null_checksums = sorted(history["nullChecksums"], key=version_key)
    latest = max(active, key=version_key) if active else None

    status = "PASS"
    if (
        repo_duplicates
        or history["failed"] > 0
        or duplicates
        or repo_missing
        or db_not_repo
        or null_checksums
    ):
        status = "FAIL"

    return {
        "status": status,
        "readOnly": bool(snapshot.get("databaseReadOnly", True)),
        "failedMigrations": history["failed"],
        "repositoryDuplicateVersions": repo_duplicates,
        "duplicateVersions": duplicates,
        "repoMissingInDb": repo_missing,
        "dbSuccessNotInRepo": db_not_repo,
        "deleteMarkers": delete_markers,
        "nullChecksums": null_checksums,
        "latestVersion": latest,
        "_activeVersions": active,
    }


def _evaluate_workflow_y2(snapshot: dict[str, Any], database: dict[str, Any]) -> dict[str, Any]:
    active_versions = database["_activeVersions"]
    missing_migrations = sorted(
        set(REQUIRED_Y2_VERSIONS) - active_versions,
        key=version_key,
    )
    migration_status = "PASS" if not missing_migrations else "FAIL"

    schema = snapshot.get("schema", {})
    tables = schema.get("tables", {})
    present_columns = set(schema.get("columns", []))

    missing_tables = sorted(
        table
        for table in Y2_TABLES
        if not bool(tables.get(table, {}).get("exists"))
    )
    missing_columns = sorted(set(REQUIRED_COLUMNS) - present_columns)

    rls_failures = sorted(
        table
        for table in Y2_TABLES
        if not (
            bool(tables.get(table, {}).get("exists"))
            and bool(tables.get(table, {}).get("tenantId"))
            and bool(tables.get(table, {}).get("rls"))
            and bool(tables.get(table, {}).get("tenantPolicy"))
        )
    )
    force_rls_disabled = sorted(
        table
        for table in Y2_TABLES
        if bool(tables.get(table, {}).get("exists"))
        and not bool(tables.get(table, {}).get("forceRls"))
    )

    schema_status = "PASS" if not missing_tables and not missing_columns else "FAIL"
    rls_status = "PASS" if not rls_failures else "FAIL"

    capabilities = {
        str(item.get("code")): str(item.get("status") or "")
        for item in snapshot.get("capabilities", [])
        if item.get("code")
    }
    missing_capabilities = sorted(
        code
        for code in Y2_CAPABILITIES
        if capabilities.get(code) != "ACTIVE"
    )
    capability_status = "PASS" if not missing_capabilities else "FAIL"

    bindings = snapshot.get("adminBindings", {})
    active_tenants = int(bindings.get("activeTenants", 0))
    with_admin = int(bindings.get("activeTenantsWithAdmin", 0))
    complete = int(bindings.get("activeTenantsWithCompleteY2AdminBinding", 0))
    incomplete = int(bindings.get("incompleteBindings", 0))
    admin_status = (
        "PASS"
        if incomplete == 0 and active_tenants == with_admin == complete
        else "FAIL"
    )

    return {
        "migrationHistory": migration_status,
        "missingMigrations": missing_migrations,
        "schemaSentinels": schema_status,
        "missingTables": missing_tables,
        "missingColumns": missing_columns,
        "capabilities": capability_status,
        "missingCapabilities": missing_capabilities,
        "adminBindings": admin_status,
        "adminBindingCounts": {
            "activeTenants": active_tenants,
            "activeTenantsWithAdmin": with_admin,
            "activeTenantsWithCompleteY2AdminBinding": complete,
            "incompleteBindings": incomplete,
        },
        "rls": rls_status,
        "rlsFailures": rls_failures,
        "forceRlsDisabled": force_rls_disabled,
    }


def evaluate_snapshot(snapshot: dict[str, Any]) -> dict[str, Any]:
    environment = _evaluate_environment(snapshot)
    database = _evaluate_database(snapshot)
    workflow = _evaluate_workflow_y2(snapshot, database)

    hard_failure = (
        environment["status"] != "PASS"
        or database["status"] != "PASS"
        or workflow["migrationHistory"] != "PASS"
        or workflow["schemaSentinels"] != "PASS"
        or workflow["capabilities"] != "PASS"
        or workflow["adminBindings"] != "PASS"
        or workflow["rls"] != "PASS"
    )

    if hard_failure:
        overall = "FAIL"
    elif environment["contractDrift"]:
        overall = "CONTRACT_DRIFT"
    else:
        overall = "PASS"

    database.pop("_activeVersions", None)
    return {
        "result": overall,
        "mainSha": str(snapshot.get("mainSha") or ""),
        "renderEnvironment": environment,
        "database": database,
        "workflowY2": workflow,
    }


def _load_snapshot(path: Path, repo_root: Path | None) -> dict[str, Any]:
    snapshot = json.loads(path.read_text())
    if "repositoryVersions" not in snapshot:
        if repo_root is None:
            raise ValueError("repo root is required when repositoryVersions are not supplied")
        versions, duplicates = discover_repository_versions(repo_root)
        snapshot["repositoryVersions"] = versions
        if duplicates:
            snapshot["repositoryVersions"] = versions + duplicates
    return snapshot


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--snapshot", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--repo-root", type=Path)
    args = parser.parse_args()

    snapshot = _load_snapshot(args.snapshot, args.repo_root)
    result = evaluate_snapshot(snapshot)
    args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n")
    print(json.dumps(result, indent=2, sort_keys=True))

    if result["result"] == "PASS":
        return 0
    if result["result"] == "CONTRACT_DRIFT":
        return 3
    return 2


if __name__ == "__main__":
    raise SystemExit(main())