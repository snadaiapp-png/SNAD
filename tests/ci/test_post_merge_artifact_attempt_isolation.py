#!/usr/bin/env python3
"""Regression contract for attempt-scoped Post-Merge artifacts.

Never combine artifacts from different GitHub Actions run attempts or
silence missing mandatory verification evidence.
"""
from pathlib import Path
import importlib.util
import json
import shutil

import yaml

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW_PATH = ROOT / ".github/workflows/post-merge-verification.yml"
ATTEMPT = "-att${{ github.run_attempt }}"
OUTPUT_ONLY = {
    "pmv-surefire-reports",
    "hrm-surefire-reports",
    "verification-manifest-${{ github.run_id }}",
}


def _jobs():
    return yaml.safe_load(WORKFLOW_PATH.read_text(encoding="utf-8"))["jobs"]


def _artifacts(kind):
    result = []
    for job_id, job in _jobs().items():
        for step in job.get("steps", []):
            uses = str(step.get("uses", ""))
            if uses.startswith("actions/" + kind + "-artifact@"):
                result.append((job_id, step))
    return result


def _name(step):
    return step["with"]["name"]


def test_01_expected_artifact_coverage():
    assert len(_artifacts("upload")) == 12
    assert len(_artifacts("download")) == 9


def test_02_every_upload_and_download_is_attempt_scoped():
    for kind in ("upload", "download"):
        for job, step in _artifacts(kind):
            assert _name(step).endswith(ATTEMPT), (job, step["name"])


def test_03_upload_failures_remain_hard_failures():
    for job, step in _artifacts("upload"):
        assert step["with"].get("if-no-files-found") == "error", job
        assert step.get("continue-on-error") not in (True, "true", "${{ true }}"), job
        assert str(step.get("if", "")).strip() in ("always()", "${{ always() }}"), job


def test_04_downloads_match_exactly_same_attempt_upload_names():
    uploads = [_name(s) for _, s in _artifacts("upload")]
    downloads = [_name(s) for _, s in _artifacts("download")]
    assert len(set(uploads)) == len(uploads)
    assert len(set(downloads)) == len(downloads)
    assert set(downloads) == set(uploads) - {x + ATTEMPT for x in OUTPUT_ONLY}


def test_05_all_five_verification_jobs_publish_consumed_fragments():
    job_fragments = {
        "frontend-verification": "pmv-fragment-frontend",
        "backend-compile": "pmv-fragment-backend-compile",
        "postgres-direct-integration": "pmv-fragment-pg-direct",
        "hrm-focused-security-rls": "pmv-fragment-hrm",
        "security-governance-scans": "pmv-fragment-security",
    }
    for job, prefix in job_fragments.items():
        assert any(
            j == job and _name(step) == prefix + ATTEMPT
            for j, step in _artifacts("upload")
        )
        assert any(
            j == "final-evidence-aggregation" and _name(step) == prefix + ATTEMPT
            for j, step in _artifacts("download")
        )


def test_06_backend_and_hrm_outcomes_are_not_removed():
    jobs = _jobs()
    pg = jobs["postgres-direct-integration"]["steps"]
    hrm = jobs["hrm-focused-security-rls"]["steps"]
    assert any(
        step.get("id") == "fragment" and "'backend_tests':" in step.get("run", "")
        for step in pg
    )
    assert any(
        step.get("id") == "fragment" and "'hrm_focused_tests':" in step.get("run", "")
        for step in hrm
    )
    assert any(
        step.get("id") == "upload-reports"
        and _name(step) == "hrm-surefire-reports" + ATTEMPT
        for step in hrm
    )


def test_07_manifest_records_attempt_and_keeps_final_validator():
    source = WORKFLOW_PATH.read_text(encoding="utf-8")
    assert source.count('"workflowRunAttempt"') == 1
    assert source.count("'workflowRunAttempt'") == 1
    job = _jobs()["final-evidence-aggregation"]
    scripts = [str(s.get("run", "")) for s in job["steps"]]
    assert any("validate_post_merge_evidence.py" in s for s in scripts)
    assert any("PMV_FINAL_GATE=PASS" in s for s in scripts)


def test_08_incident_replay_missing_pg_hrm_outcomes_fails_closed():
    test_path = ROOT / "tests/ci/test_validate_post_merge_evidence.py"
    spec = importlib.util.spec_from_file_location("pmv_validator_test_fixture", test_path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    fixture = module.TestPostMergeEvidenceValidator(
        methodName="test_all_valid_returns_zero"
    )
    fixture.setUp()
    try:
        fixture._write_all_valid()
        manifest = fixture._base_manifest()
        for key in ("backend_tests", "hrm_focused_tests"):
            manifest["checks"].pop(key)
        fixture.manifest.write_text(json.dumps(manifest), encoding="utf-8")
        assert fixture._run() == 1
    finally:
        shutil.rmtree(fixture.tmpdir, ignore_errors=True)
