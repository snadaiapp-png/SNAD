#!/usr/bin/env python3
import copy
import importlib.util
import json
import subprocess
import tempfile
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
ENGINE_PATH = REPO_ROOT / "scripts/production/workflow_y2_evidence.py"
SHELL_PATH = REPO_ROOT / "scripts/production/verify-workflow-y2-production-evidence.sh"
WORKFLOW_PATH = REPO_ROOT / ".github/workflows/workflow-y2-production-evidence.yml"

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

Y2_VERSIONS = [
    "20260902.1", "20260902.2", "20260902.3", "20260902.4",
    "20260902.5", "20260902.6", "20260902.7", "20260904.1",
]

Y2_CAPABILITIES = [
    "WORKFLOW.DESIGN", "WORKFLOW.VALIDATE", "WORKFLOW.PUBLISH",
    "WORKFLOW.START", "WORKFLOW.TASK_EXECUTE", "WORKFLOW.REASSIGN",
    "WORKFLOW.DELEGATE", "WORKFLOW.CANCEL", "WORKFLOW.INCIDENT_MANAGE",
    "WORKFLOW.MONITOR", "WORKFLOW.AUDIT_VIEW", "WORKFLOW.BREAK_GLASS",
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


def load_engine():
    if not ENGINE_PATH.exists():
        raise AssertionError(
            f"production evidence engine must exist at {ENGINE_PATH}; "
            "tests intentionally precede implementation"
        )
    spec = importlib.util.spec_from_file_location("workflow_y2_evidence", ENGINE_PATH)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


def passing_snapshot():
    repo_versions = ["1", "2", "3", "4", "9", "15", *Y2_VERSIONS]
    return {
        "mainSha": "deadbeef",
        "renderEnv": [{"key": key, "present": True} for key in EXPECTED_ENV_KEYS],
        "repositoryVersions": repo_versions,
        "dbHistory": [
            {
                "installedRank": rank,
                "version": version,
                "type": "SQL",
                "success": True,
                "checksum": rank,
            }
            for rank, version in enumerate(repo_versions, start=1)
        ],
        "schema": {
            "tables": {
                table: {
                    "exists": True,
                    "tenantId": True,
                    "rls": True,
                    "forceRls": False,
                    "tenantPolicy": True,
                }
                for table in Y2_TABLES
            },
            "columns": REQUIRED_COLUMNS[:],
        },
        "capabilities": [
            {"code": code, "status": "ACTIVE"} for code in Y2_CAPABILITIES
        ],
        "adminBindings": {
            "activeTenants": 2,
            "activeTenantsWithAdmin": 2,
            "activeTenantsWithCompleteY2AdminBinding": 2,
            "incompleteBindings": 0,
        },
    }


class WorkflowY2EvidenceBehaviorTest(unittest.TestCase):
    def evaluate(self, mutate=None):
        engine = load_engine()
        snapshot = passing_snapshot()
        if mutate:
            mutate(snapshot)
        return engine.evaluate_snapshot(snapshot)

    def test_01_exact_44_environment_contract_passes(self):
        result = self.evaluate()
        self.assertEqual("PASS", result["renderEnvironment"]["status"])
        self.assertEqual(44, result["renderEnvironment"]["expected"])
        self.assertEqual([], result["renderEnvironment"]["missing"])

    def test_02_missing_environment_key_fails_with_exact_name(self):
        missing = "SPRING_FLYWAY_USER"

        def mutate(snapshot):
            snapshot["renderEnv"] = [
                item for item in snapshot["renderEnv"] if item["key"] != missing
            ]

        result = self.evaluate(mutate)
        self.assertEqual("FAIL", result["renderEnvironment"]["status"])
        self.assertEqual([missing], result["renderEnvironment"]["missing"])
        self.assertEqual("FAIL", result["result"])

    def test_03_unexpected_environment_key_is_contract_drift(self):
        def mutate(snapshot):
            snapshot["renderEnv"].append({"key": "UNEXPECTED_KEY", "present": True})

        result = self.evaluate(mutate)
        self.assertEqual(["UNEXPECTED_KEY"], result["renderEnvironment"]["extra"])
        self.assertTrue(result["renderEnvironment"]["contractDrift"])
        self.assertEqual("CONTRACT_DRIFT", result["result"])

    def test_04_middle_migration_gap_is_detected(self):
        def mutate(snapshot):
            snapshot["repositoryVersions"] = ["1", "2", "3", "4"]
            snapshot["dbHistory"] = [
                {"installedRank": 1, "version": "1", "type": "SQL", "success": True, "checksum": 1},
                {"installedRank": 2, "version": "2", "type": "SQL", "success": True, "checksum": 2},
                {"installedRank": 3, "version": "4", "type": "SQL", "success": True, "checksum": 4},
            ]

        result = self.evaluate(mutate)
        self.assertEqual(["3"], result["database"]["repoMissingInDb"])
        self.assertEqual("FAIL", result["database"]["status"])

    def test_05_legacy_v9_does_not_beat_20260904_1_lexically(self):
        result = self.evaluate()
        self.assertEqual("20260904.1", result["database"]["latestVersion"])

    def test_06_failed_migration_fails_gate(self):
        def mutate(snapshot):
            snapshot["dbHistory"].append(
                {
                    "installedRank": 999,
                    "version": "20260905.1",
                    "type": "SQL",
                    "success": False,
                    "checksum": 999,
                }
            )

        result = self.evaluate(mutate)
        self.assertEqual(1, result["database"]["failedMigrations"])
        self.assertEqual("FAIL", result["database"]["status"])

    def test_07_duplicate_active_successful_version_fails_gate(self):
        def mutate(snapshot):
            snapshot["dbHistory"].append(
                {
                    "installedRank": 999,
                    "version": "20260902.3",
                    "type": "SQL",
                    "success": True,
                    "checksum": 999,
                }
            )

        result = self.evaluate(mutate)
        self.assertEqual(["20260902.3"], result["database"]["duplicateVersions"])
        self.assertEqual("FAIL", result["database"]["status"])

    def test_08_delete_marker_is_classified_not_counted_as_active_duplicate(self):
        def mutate(snapshot):
            snapshot["repositoryVersions"] = [
                version for version in snapshot["repositoryVersions"] if version != "15"
            ]
            snapshot["dbHistory"].append(
                {
                    "installedRank": 999,
                    "version": "15",
                    "type": "DELETE",
                    "success": True,
                    "checksum": None,
                }
            )

        result = self.evaluate(mutate)
        self.assertIn("15", result["database"]["deleteMarkers"])
        self.assertNotIn("15", result["database"]["duplicateVersions"])
        self.assertNotIn("15", result["database"]["dbSuccessNotInRepo"])

    def test_09_missing_required_y2_migration_fails_y2_history(self):
        def mutate(snapshot):
            snapshot["dbHistory"] = [
                row for row in snapshot["dbHistory"] if row["version"] != "20260902.5"
            ]

        result = self.evaluate(mutate)
        self.assertEqual("FAIL", result["workflowY2"]["migrationHistory"])
        self.assertIn("20260902.5", result["workflowY2"]["missingMigrations"])

    def test_10_missing_definition_family_id_fails_schema(self):
        def mutate(snapshot):
            snapshot["schema"]["columns"].remove(
                "workflow_definitions.definition_family_id"
            )

        result = self.evaluate(mutate)
        self.assertEqual("FAIL", result["workflowY2"]["schemaSentinels"])
        self.assertIn(
            "workflow_definitions.definition_family_id",
            result["workflowY2"]["missingColumns"],
        )

    def test_11_missing_self_approval_override_fails_capability_gate(self):
        def mutate(snapshot):
            snapshot["capabilities"] = [
                item
                for item in snapshot["capabilities"]
                if item["code"] != "WORKFLOW.SELF_APPROVAL_OVERRIDE"
            ]

        result = self.evaluate(mutate)
        self.assertEqual("FAIL", result["workflowY2"]["capabilities"])
        self.assertIn(
            "WORKFLOW.SELF_APPROVAL_OVERRIDE",
            result["workflowY2"]["missingCapabilities"],
        )

    def test_12_incomplete_admin_binding_fails_gate(self):
        def mutate(snapshot):
            snapshot["adminBindings"]["activeTenantsWithCompleteY2AdminBinding"] = 1
            snapshot["adminBindings"]["incompleteBindings"] = 1

        result = self.evaluate(mutate)
        self.assertEqual("FAIL", result["workflowY2"]["adminBindings"])

    def test_13_rls_disabled_on_y2_tenant_table_fails_gate(self):
        def mutate(snapshot):
            snapshot["schema"]["tables"]["workflow_work_items"]["rls"] = False

        result = self.evaluate(mutate)
        self.assertEqual("FAIL", result["workflowY2"]["rls"])
        self.assertIn("workflow_work_items", result["workflowY2"]["rlsFailures"])

    def test_14_mutating_sql_is_rejected_behaviorally(self):
        engine = load_engine()
        with self.assertRaises(ValueError):
            engine.assert_read_only_sql("UPDATE workflow_work_items SET status='COMPLETED'")

    def test_15_mutating_http_method_is_rejected_behaviorally(self):
        engine = load_engine()
        for method in ("POST", "PUT", "PATCH", "DELETE"):
            with self.subTest(method=method):
                with self.assertRaises(ValueError):
                    engine.assert_http_method(method)

    def test_shell_contract_contains_no_production_mutation_commands(self):
        self.assertTrue(
            SHELL_PATH.exists(),
            f"production shell must exist at {SHELL_PATH}",
        )
        source = SHELL_PATH.read_text()
        forbidden = [
            "curl -X POST", "curl -X PUT", "curl -X PATCH", "curl -X DELETE",
            "flyway migrate", "flyway repair", "flyway clean",
            "gh issue", "gh pr",
        ]
        for needle in forbidden:
            with self.subTest(needle=needle):
                self.assertNotIn(needle, source)

    def test_16_workflow_contract_is_read_only_and_has_no_governance_writes(self):
        self.assertTrue(
            WORKFLOW_PATH.exists(),
            f"production evidence workflow must exist at {WORKFLOW_PATH}",
        )
        source = WORKFLOW_PATH.read_text()
        self.assertIn("permissions:\n  contents: read", source)
        forbidden = [
            "issues: write",
            "pull-requests: write",
            "deployments: write",
            "gh issue",
            "gh pr",
            "flyway migrate",
            "flyway repair",
            "curl -X POST",
            "curl -X PUT",
            "curl -X PATCH",
            "curl -X DELETE",
        ]
        for needle in forbidden:
            with self.subTest(needle=needle):
                self.assertNotIn(needle, source)


if __name__ == "__main__":
    unittest.main(verbosity=2)
