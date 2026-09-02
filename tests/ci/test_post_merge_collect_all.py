#!/usr/bin/env python3
"""Structural contract for Post-Merge Main Verification.

PMV must collect independent verification failures instead of allowing the first
failed check to skip every later check. Individual verification steps therefore
record a failing ``outcome`` while execution continues; the truthful manifest
and the final fail-closed gate remain the only closure authority.
"""
from pathlib import Path

import yaml


REPO_ROOT = Path(__file__).resolve().parent.parent.parent
WORKFLOW = REPO_ROOT / ".github" / "workflows" / "post-merge-verification.yml"

# Environment/bootstrap actions are intentionally excluded: if checkout or a
# toolchain setup action cannot run there is no meaningful repository matrix to
# collect. Everything below is a repository verification or an auditable local
# prerequisite whose outcome belongs in the closure evidence.
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

MANIFEST_REQUIRED_IDS = (
    "provision-db",
    "db-role-contract",
    "crm-key",
)


def _workflow():
    return yaml.safe_load(WORKFLOW.read_text(encoding="utf-8"))


def _steps():
    document = _workflow()
    return document["jobs"]["verify-main"]["steps"]


def _steps_by_id():
    return {step.get("id"): step for step in _steps() if step.get("id")}


def test_pmv_verification_steps_collect_failures_instead_of_fail_fast():
    steps = _steps_by_id()
    for step_id in COLLECT_ALL_STEP_IDS:
        assert step_id in steps, f"PMV collect-all step is missing id={step_id}"
        assert steps[step_id].get("continue-on-error") is True, (
            f"PMV step {step_id} must set continue-on-error: true so a failure "
            "is recorded without hiding later verification steps"
        )


def test_pmv_manifest_records_auditable_local_prerequisite_outcomes():
    manifest = _steps_by_id()["manifest"]
    script = manifest["run"]
    for step_id in MANIFEST_REQUIRED_IDS:
        assert f"steps.{step_id}.outcome" in script, (
            f"verification manifest must record outcome for {step_id}"
        )


def test_pmv_final_gate_remains_always_fail_closed():
    steps = _steps_by_id()
    manifest = steps["manifest"]
    final_gate = steps["final-gate"]

    assert str(manifest.get("if", "")).strip() == "always()"
    assert str(final_gate.get("if", "")).strip() == "always()"

    final_script = final_gate["run"]
    assert "MANIFEST_RESULT" in final_script
    assert 'exit 1' in final_script
    assert "validate_post_merge_evidence.py" in final_script
