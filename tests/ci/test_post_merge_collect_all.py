#!/usr/bin/env python3
"""Structural contract for Post-Merge Main Verification (jobs A-F architecture).

PMV must collect independent verification failures instead of allowing the first
failed check to hide every later check. Individual repository-verification steps
record their real ``outcome`` while execution continues where dependencies allow;
per-job outcome fragments are merged into the truthful manifest, and the final
fail-closed gate (JOB F + validate_post_merge_evidence.py) remains the only
closure authority.

v20260907.1 (HRM-G0 reconciliation): the former single monolithic job was
redesigned into parallel jobs A-F. This contract now enforces the SAME
collect-all guarantees across the job chain, and adds the PostgreSQL
governance rule: host-native PostgreSQL only — no service containers.
"""
from pathlib import Path

import yaml


REPO_ROOT = Path(__file__).resolve().parent.parent.parent
WORKFLOW = REPO_ROOT / ".github" / "workflows" / "post-merge-verification.yml"

# JOB -> collect-all verification step ids owned by that job.
JOB_COLLECT_ALL_STEP_IDS = {
    "frontend-verification": (
        "frontend-deps",
        "frontend-lint",
        "frontend-typecheck",
        "frontend-tests",
        "frontend-build",
        "smoke-frontend",
    ),
    "backend-compile": (
        "backend-deps",
        "backend-compile",
        "backend-test-compile",
    ),
    "postgres-direct-integration": (
        "host-native-pg",
        "provision-db",
        "db-role-contract",
        "crm-key",
        "backend-tests",
        "smoke-backend",
    ),
    "hrm-focused-security-rls": (
        "hrm-focused-tests",
    ),
    "security-governance-scans": (
        "sds-compliance",
        "logo-governance",
        "brand-name",
        "i18n-keys",
        "workflow-security",
        "secret-scan",
        "performance-budget",
    ),
}

ALL_COLLECT_ALL_STEP_IDS = tuple(
    step_id
    for step_ids in JOB_COLLECT_ALL_STEP_IDS.values()
    for step_id in step_ids
)

# Stable public manifest keys — one per collect-all step (outcome mapping in
# each job's "Emit outcome fragment" step).
STEP_TO_MANIFEST_KEY = {
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
    "backend-test-compile": "backend_test_compile",
    "backend-tests": "backend_tests",
    "workflow-security": "workflow_security",
    "secret-scan": "secret_scan",
    "smoke-backend": "smoke_backend",
    "smoke-frontend": "smoke_frontend",
    "host-native-pg": "postgresql_host_native",
    "hrm-focused-tests": "hrm_focused_tests",
}

AGGREGATION_JOB = "final-evidence-aggregation"


def _workflow():
    return yaml.safe_load(WORKFLOW.read_text(encoding="utf-8"))


def _jobs():
    return _workflow()["jobs"]


def _job(job_id: str):
    return _jobs()[job_id]


def _steps(job_id: str):
    return _job(job_id)["steps"]


def _steps_by_id(job_id: str):
    return {step.get("id"): step for step in _steps(job_id) if step.get("id")}


def _step(step_id: str):
    for job_id, step_ids in JOB_COLLECT_ALL_STEP_IDS.items():
        if step_id in step_ids:
            return _steps_by_id(job_id)[step_id]
    raise KeyError(f"step id={step_id} not mapped to a job")


def _if(step_id: str) -> str:
    return str(_step(step_id).get("if", ""))


def _job_of(step_id: str) -> str:
    for job_id, step_ids in JOB_COLLECT_ALL_STEP_IDS.items():
        if step_id in step_ids:
            return job_id
    raise KeyError(step_id)


def _job_steps_text(job_id: str) -> str:
    import json
    return json.dumps(_steps(job_id), default=str)


def test_01_pmv_verification_steps_collect_failures_instead_of_fail_fast():
    for job_id, step_ids in JOB_COLLECT_ALL_STEP_IDS.items():
        steps = _steps_by_id(job_id)
        for step_id in step_ids:
            assert step_id in steps, f"PMV collect-all step is missing id={step_id} (job {job_id})"
            assert steps[step_id].get("continue-on-error") is True, (
                f"PMV step {step_id} (job {job_id}) must set continue-on-error: true "
                "so a failure is recorded without hiding independent later verification steps"
            )


def test_02_every_collect_all_outcome_is_recorded_in_a_fragment():
    for job_id, step_ids in JOB_COLLECT_ALL_STEP_IDS.items():
        text = _job_steps_text(job_id)
        for step_id in step_ids:
            assert f"steps.{step_id}.outcome" in text, (
                f"job {job_id} must record outcome of {step_id} in its outcome fragment"
            )


def test_03_manifest_and_final_gate_always_run():
    steps = _steps_by_id(AGGREGATION_JOB)
    assert str(steps["manifest"].get("if", "")).strip() == "always()"
    assert str(steps["final-gate"].get("if", "")).strip() == "always()"


def test_04_final_gate_never_soft_fails():
    gate = _steps_by_id(AGGREGATION_JOB)["final-gate"]
    assert gate.get("continue-on-error") is not True
    script = gate["run"]
    assert "MANIFEST_RESULT" in script
    assert "exit 1" in script
    assert "validate_post_merge_evidence.py" in script
    assert "|| true" not in script


def test_05_manifest_is_fail_closed_for_failure_skip_cancel_and_missing():
    script = _steps_by_id(AGGREGATION_JOB)["manifest"]["run"]
    for token in ("failure", "skipped", "cancelled", "missing"):
        assert token in script.lower(), f"manifest must classify {token} as non-success"
    assert "criticalFailures" in script
    assert "failedChecks" in script
    assert "skippedChecks" in script
    assert "cancelledChecks" in script
    assert "missingChecks" in script


def test_06_manifest_keeps_first_failure_and_all_failures():
    script = _steps_by_id(AGGREGATION_JOB)["manifest"]["run"]
    assert "failedGate" in script
    assert "criticalFailures" in script
    assert "[0]" in script, "failedGate must preserve the first critical failure"


def test_07_manifest_uses_stable_public_keys_for_all_checks():
    for job_id in JOB_COLLECT_ALL_STEP_IDS:
        text = _job_steps_text(job_id)
        for step_id in JOB_COLLECT_ALL_STEP_IDS[job_id]:
            manifest_key = STEP_TO_MANIFEST_KEY[step_id]
            assert manifest_key in text, (
                f"fragment of job {job_id} missing stable manifest key {manifest_key} "
                f"for step {step_id}"
            )


def test_08_aggregation_merges_all_job_fragments():
    script = _steps_by_id(AGGREGATION_JOB)["manifest"]["run"]
    assert "pmv-fragment.json" in script, "manifest must consume per-job fragments"
    for job_id in JOB_COLLECT_ALL_STEP_IDS:
        for step in _steps(job_id):
            if step.get("id") == "fragment":
                assert "pmv-fragment.json" in step["run"]


def test_09_frontend_checks_depend_on_frontend_dependencies_only():
    for step_id in ("frontend-lint", "frontend-typecheck", "frontend-tests", "frontend-build"):
        condition = _if(step_id)
        assert "frontend-deps" in condition
        assert "backend-" not in condition
        assert "provision-db" not in condition


def test_10_performance_budget_is_self_provisioned_with_a_frontend_build():
    step = _step("performance-budget")
    script = step["run"]
    assert "npm run build" in script, (
        "performance budget must build the frontend inside JOB E so it does "
        "not silently depend on another job's unshared build artifacts"
    )


def test_11_backend_compile_depends_on_backend_dependencies_not_frontend():
    condition = _if("backend-compile")
    assert "backend-deps" in condition
    assert "frontend-" not in condition


def test_12_backend_tests_require_host_native_database_key_prerequisites():
    condition = _if("backend-tests")
    for prerequisite in (
        "host-native-pg",
        "provision-db",
        "db-role-contract",
        "crm-key",
    ):
        assert prerequisite in condition
    assert "frontend-" not in condition


def test_13_database_role_contract_depends_on_provisioning():
    assert "provision-db" in _if("db-role-contract")


def test_14_security_checks_remain_independent_of_application_failures():
    for step_id in ("workflow-security", "secret-scan"):
        condition = _if(step_id)
        assert condition.strip() in ("${{ !cancelled() }}", "!cancelled()"), (
            f"{step_id} must run unconditionally (collect-all), got: {condition}"
        )


def test_15_backend_smoke_is_dependency_aware_but_not_blocked_by_test_failure():
    condition = _if("smoke-backend")
    for prerequisite in (
        "host-native-pg",
        "provision-db",
        "db-role-contract",
        "crm-key",
    ):
        assert prerequisite in condition
    assert "backend-tests" not in condition
    assert "frontend-" not in condition


def test_16_frontend_smoke_requires_built_frontend_only():
    condition = _if("smoke-frontend")
    assert "frontend-deps" in condition
    assert "frontend-build" in condition
    assert "backend-" not in condition


def test_17_fail_closed_evidence_placeholders_exist_before_verification():
    # JOB A: frontend smoke placeholders + vitest log
    job_a_steps = _steps("frontend-verification")
    ids = [step.get("id") for step in job_a_steps]
    assert "evidence-placeholders" in ids
    assert ids.index("evidence-placeholders") < ids.index("frontend-deps")
    script = _steps_by_id("frontend-verification")["evidence-placeholders"]["run"]
    for name in ("frontend-smoke-metadata.json", "vitest.log"):
        assert name in script
    assert "PREREQUISITE_FAILED" in script
    # JOB C: backend smoke placeholders
    job_c_steps = _steps("postgres-direct-integration")
    ids_c = [step.get("id") for step in job_c_steps]
    assert ids_c.index("host-native-pg") < ids_c.index("backend-tests")
    smoke = _steps_by_id("postgres-direct-integration")["smoke-backend"]["run"]
    for name in ("backend-health.json", "backend-smoke-metadata.json"):
        assert name in smoke


def test_18_evidence_uploads_always_run():
    checks = [
        ("frontend-verification", "fragment"),
        ("frontend-verification", "upload-vitest"),
        ("frontend-verification", "upload-frontend-evidence"),
        ("backend-compile", "fragment"),
        ("postgres-direct-integration", "fragment"),
        ("postgres-direct-integration", "upload-reports"),
        ("postgres-direct-integration", "upload-backend-evidence"),
        ("hrm-focused-security-rls", "fragment"),
        ("hrm-focused-security-rls", "upload-reports"),
        ("security-governance-scans", "fragment"),
        ("security-governance-scans", "upload-secret-report"),
        (AGGREGATION_JOB, "upload-manifest"),
    ]
    for job_id, step_id in checks:
        steps = _steps_by_id(job_id)
        assert step_id in steps, f"missing upload/fragment step {step_id} in {job_id}"
        condition = str(steps[step_id].get("if", "")).strip()
        assert condition in ("always()", "${{ always() }}"), (
            f"upload step {step_id} in {job_id} must always run, got: {condition}"
        )


def test_19_final_gate_is_the_only_closure_authority_and_needs_all_jobs():
    job = _job(AGGREGATION_JOB)
    needs = job.get("needs") or []
    for job_id in JOB_COLLECT_ALL_STEP_IDS:
        assert job_id in needs, f"aggregation must need {job_id}"
    condition = str(job.get("if", "")).strip()
    assert "cancelled()" in condition, (
        "aggregation must run even when upstream jobs fail (!cancelled()), "
        "so the gate closes truthfully instead of silently skipping"
    )
    gate = _steps_by_id(AGGREGATION_JOB)["final-gate"]
    assert gate.get("continue-on-error") is not True


def test_20_no_job_level_continue_on_error_can_bypass_final_gate():
    for job_id in list(JOB_COLLECT_ALL_STEP_IDS) + [AGGREGATION_JOB]:
        job = _job(job_id)
        assert job.get("continue-on-error") is not True, (
            f"job {job_id} must not set job-level continue-on-error"
        )


def test_21_no_postgres_service_containers_anywhere_in_pmw():
    jobs = _jobs()
    for job_id, job in jobs.items():
        assert "services" not in job, (
            f"job {job_id} must not define PostgreSQL service containers — "
            "PMV requires host-native PostgreSQL only"
        )
    text = WORKFLOW.read_text(encoding="utf-8")
    assert "image: postgres" not in text


def test_22_host_native_postgresql_is_proven_and_fails_closed():
    for job_id in ("postgres-direct-integration", "hrm-focused-security-rls"):
        steps = _steps_by_id(job_id)
        assert "host-native-pg" in steps, f"{job_id} must prove host-native PostgreSQL"
        script = steps["host-native-pg"]["run"]
        assert "psql --version" in script
        assert "pg_isready" in script
        assert "ENVIRONMENT_BLOCKER=HOST_NATIVE_POSTGRESQL_UNAVAILABLE" in script
        assert "systemctl start postgresql" in script
    # The backend test lane requires the host-native proof step.
    assert "host-native-pg" in _if("backend-tests")
    assert "host-native-pg" in _if("hrm-focused-tests")


def test_23_hr_focused_suite_has_fail_loud_no_op_guard():
    script = _step("hrm-focused-tests")["run"]
    assert "failIfNoTests=true" in script
    assert "com.sanad.platform.hr." in script


def test_24_aggregation_fails_when_fragments_are_missing():
    for job_id in JOB_COLLECT_ALL_STEP_IDS:
        found = any(
            step.get("id") == "fragment" for step in _steps(job_id)
        )
        assert found, f"job {job_id} must emit an outcome fragment"
    script = _steps_by_id(AGGREGATION_JOB)["manifest"]["run"]
    assert "'missing'" in script or '"missing"' in script, (
        "manifest must mark outcomes from missing fragments as missing (fail-closed)"
    )
