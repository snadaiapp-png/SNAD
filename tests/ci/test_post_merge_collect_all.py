#!/usr/bin/env python3
"""Structural contract for Post-Merge Main Verification.

PMV must collect independent verification failures instead of allowing the first
failed check to hide every later check. Individual repository-verification steps
record their real ``outcome`` while execution continues where dependencies allow;
the truthful manifest and final fail-closed gate remain the closure authority.
"""
from pathlib import Path

import yaml


REPO_ROOT = Path(__file__).resolve().parent.parent.parent
WORKFLOW = REPO_ROOT / ".github" / "workflows" / "post-merge-verification.yml"

COLLECT_ALL_STEP_IDS = (
    "frontend-deps",
    "backend-deps",
    "frontend-lint",
    "frontend-typecheck",
    "frontend-tests",
    "frontend-build",
    "sds-compliance",
    "logo-governance",
    "brand-name",
    "i18n-keys",
    "performance-budget",
    "provision-db",
    "db-role-contract",
    "crm-key",
    "backend-compile",
    "backend-tests",
    "workflow-security",
    "secret-scan",
    "smoke-backend",
    "smoke-frontend",
)

MANIFEST_KEYS = {
    "frontend-deps": "frontend_deps",
    "backend-deps": "backend_deps",
    "frontend-lint": "frontend_lint",
    "frontend-typecheck": "frontend_typecheck",
    "frontend-tests": "frontend_tests",
    "frontend-build": "frontend_build",
    "sds-compliance": "sds_compliance",
    "logo-governance": "logo_governance",
    "brand-name": "brand_name",
    "i18n-keys": "i18n_keys",
    "performance-budget": "performance_budget",
    "provision-db": "provision_db",
    "db-role-contract": "db_role_contract",
    "crm-key": "crm_key",
    "backend-compile": "backend_compile",
    "backend-tests": "backend_tests",
    "workflow-security": "workflow_security",
    "secret-scan": "secret_scan",
    "smoke-backend": "smoke_backend",
    "smoke-frontend": "smoke_frontend",
}


def _workflow():
    return yaml.safe_load(WORKFLOW.read_text(encoding="utf-8"))


def _steps():
    return _workflow()["jobs"]["verify-main"]["steps"]


def _steps_by_id():
    return {step.get("id"): step for step in _steps() if step.get("id")}


def _if(step_id: str) -> str:
    return str(_steps_by_id()[step_id].get("if", ""))


def test_01_pmv_verification_steps_collect_failures_instead_of_fail_fast():
    steps = _steps_by_id()
    for step_id in COLLECT_ALL_STEP_IDS:
        assert step_id in steps, f"PMV collect-all step is missing id={step_id}"
        assert steps[step_id].get("continue-on-error") is True, (
            f"PMV step {step_id} must set continue-on-error: true so a failure "
            "is recorded without hiding independent later verification steps"
        )


def test_02_manifest_records_every_collect_all_outcome():
    script = _steps_by_id()["manifest"]["run"]
    for step_id in COLLECT_ALL_STEP_IDS:
        assert f"steps.{step_id}.outcome" in script, (
            f"verification manifest must record outcome for {step_id}"
        )


def test_03_manifest_and_final_gate_always_run():
    steps = _steps_by_id()
    assert str(steps["manifest"].get("if", "")).strip() == "always()"
    assert str(steps["final-gate"].get("if", "")).strip() == "always()"


def test_04_final_gate_never_soft_fails():
    gate = _steps_by_id()["final-gate"]
    assert gate.get("continue-on-error") is not True
    script = gate["run"]
    assert "MANIFEST_RESULT" in script
    assert "exit 1" in script
    assert "validate_post_merge_evidence.py" in script
    assert "|| true" not in script


def test_05_manifest_is_fail_closed_for_failure_skip_cancel_and_missing():
    script = _steps_by_id()["manifest"]["run"]
    for token in ("failure", "skipped", "cancelled", "missing"):
        assert token in script.lower(), f"manifest must classify {token} as non-success"
    assert "criticalFailures" in script
    assert "failedChecks" in script
    assert "skippedChecks" in script
    assert "cancelledChecks" in script


def test_06_manifest_keeps_first_failure_and_all_failures():
    script = _steps_by_id()["manifest"]["run"]
    assert "failedGate" in script
    assert "criticalFailures" in script
    assert "[0]" in script, "failedGate must preserve the first critical failure"


def test_07_manifest_uses_stable_public_keys_for_all_checks():
    script = _steps_by_id()["manifest"]["run"]
    for manifest_key in MANIFEST_KEYS.values():
        assert manifest_key in script, f"manifest missing key {manifest_key}"


def test_08_frontend_checks_depend_on_frontend_dependencies_only():
    for step_id in ("frontend-lint", "frontend-typecheck", "frontend-tests", "frontend-build"):
        condition = _if(step_id)
        assert "frontend-deps" in condition
        assert "backend-" not in condition


def test_09_performance_budget_waits_for_frontend_build():
    assert "frontend-build" in _if("performance-budget")


def test_10_backend_compile_depends_on_backend_dependencies_not_frontend():
    condition = _if("backend-compile")
    assert "backend-deps" in condition
    assert "frontend-" not in condition


def test_11_backend_tests_require_database_key_and_compile_prerequisites():
    condition = _if("backend-tests")
    for prerequisite in (
        "backend-deps",
        "provision-db",
        "db-role-contract",
        "crm-key",
        "backend-compile",
    ):
        assert prerequisite in condition
    assert "frontend-" not in condition


def test_12_database_role_contract_depends_on_provisioning():
    assert "provision-db" in _if("db-role-contract")


def test_13_security_checks_remain_independent_of_application_failures():
    for step_id in ("workflow-security", "secret-scan"):
        condition = _if(step_id)
        assert "frontend-" not in condition
        assert "backend-" not in condition
        assert "provision-db" not in condition


def test_14_backend_smoke_is_dependency_aware_but_not_blocked_by_test_failure():
    condition = _if("smoke-backend")
    for prerequisite in (
        "backend-deps",
        "provision-db",
        "db-role-contract",
        "crm-key",
        "backend-compile",
    ):
        assert prerequisite in condition
    assert "backend-tests" not in condition
    assert "frontend-" not in condition


def test_15_frontend_smoke_requires_built_frontend_only():
    condition = _if("smoke-frontend")
    assert "frontend-deps" in condition
    assert "frontend-build" in condition
    assert "backend-" not in condition


def test_16_fail_closed_evidence_placeholders_exist_before_verification():
    steps = _steps()
    ids = [step.get("id") for step in steps]
    placeholder_index = ids.index("evidence-placeholders")
    first_collect_index = min(ids.index(step_id) for step_id in COLLECT_ALL_STEP_IDS)
    assert placeholder_index < first_collect_index
    script = _steps_by_id()["evidence-placeholders"]["run"]
    for name in (
        "secret-scan-report.json",
        "backend-health.json",
        "backend-smoke-metadata.json",
        "frontend-smoke-metadata.json",
        "vitest.log",
    ):
        assert name in script
    assert "PREREQUISITE_FAILED" in script


def test_17_evidence_uploads_always_run():
    steps = _steps_by_id()
    for step_id in (
        "upload-manifest",
        "upload-secret-report",
        "upload-vitest-log",
        "upload-backend-evidence",
        "upload-frontend-evidence",
    ):
        assert str(steps[step_id].get("if", "")).strip() == "always()"


def test_18_final_gate_checks_all_required_artifact_uploads():
    script = _steps_by_id()["final-gate"]["run"]
    for step_id in (
        "upload-manifest",
        "upload-secret-report",
        "upload-vitest-log",
        "upload-backend-evidence",
        "upload-frontend-evidence",
    ):
        assert f"steps.{step_id}.outcome" in script


def test_19_no_job_level_continue_on_error_can_bypass_final_gate():
    job = _workflow()["jobs"]["verify-main"]
    assert job.get("continue-on-error") is not True
    assert _steps_by_id()["final-gate"].get("continue-on-error") is not True
