"""
SANAD — R12B Orchestrator Tests
================================
EXEC-PROMPT-010R12C Section 12: tests for scripts/ops/r12a_dispatch_and_attest.py.

Covers:
  - pending/queued timeout (15 min default)
  - in_progress timeout (max_poll_minutes)
  - exit code 124 (Maven attempt timeout) — handled by NVD workflow, not here
  - active NVD run detection (duplicate dispatch prevention)
  - successful terminal NVD run
  - failed terminal NVD run
  - cancelled terminal NVD run
  - timeout report contents

These tests use a stub GitHubClient that returns canned run payloads,
so no network access is required.
"""
from __future__ import annotations

import sys
import time
from pathlib import Path

import pytest

# Add the repo root to sys.path so we can import the orchestrator
REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT))

from scripts.ops.r12a_dispatch_and_attest import (  # noqa: E402
    GitHubClient,
    poll_until_terminal,
    detect_active_nvd_run,
    _print_timeout_report,
)


# ---------- Stub client ----------

class StubClient:
    """A minimal stub that returns canned run payloads in sequence."""

    def __init__(self, run_sequence: list[dict]):
        self.run_sequence = run_sequence
        self._index = 0
        self.calls = 0

    def get_run(self, run_id: int) -> dict:
        self.calls += 1
        if self._index < len(self.run_sequence):
            payload = self.run_sequence[self._index]
            self._index += 1
            return payload
        # Return the last payload indefinitely
        return self.run_sequence[-1]

    def _request(self, method: str, path: str, **kwargs) -> dict:
        # Used by detect_active_nvd_run
        if "actions/workflows" in path and "runs" in path:
            return {"workflow_runs": self.run_sequence}
        return {}


# ---------- Tests: pending timeout ----------

def test_pending_timeout_raises_after_window(monkeypatch):
    """A run that stays queued longer than pending_timeout_minutes must raise."""
    # Force time.time() to advance fast
    real_time = time.time()
    times = iter([real_time, real_time + 60, real_time + 600, real_time + 900, real_time + 1200])

    monkeypatch.setattr(time, "time", lambda: next(times, real_time + 10000))
    monkeypatch.setattr(time, "sleep", lambda s: None)

    client = StubClient([
        {"id": 1, "status": "queued", "conclusion": None, "html_url": "u", "updated_at": "t"},
        {"id": 1, "status": "queued", "conclusion": None, "html_url": "u", "updated_at": "t"},
        {"id": 1, "status": "queued", "conclusion": None, "html_url": "u", "updated_at": "t"},
        {"id": 1, "status": "queued", "conclusion": None, "html_url": "u", "updated_at": "t"},
    ])

    with pytest.raises(TimeoutError, match="did not start within 1 minutes"):
        poll_until_terminal(
            client, 1, poll_interval=1, max_poll_minutes=10, label="test",
            pending_timeout_minutes=1,
        )


def test_pending_then_in_progress_then_completed(monkeypatch):
    """A run that transitions queued → in_progress → completed returns the final payload."""
    real_time = time.time()
    times = iter([real_time, real_time + 30, real_time + 60, real_time + 90, real_time + 120])

    monkeypatch.setattr(time, "time", lambda: next(times, real_time + 10000))
    monkeypatch.setattr(time, "sleep", lambda s: None)

    client = StubClient([
        {"id": 1, "status": "queued", "conclusion": None, "html_url": "u", "updated_at": "t"},
        {"id": 1, "status": "in_progress", "conclusion": None, "html_url": "u", "updated_at": "t"},
        {"id": 1, "status": "in_progress", "conclusion": None, "html_url": "u", "updated_at": "t"},
        {"id": 1, "status": "completed", "conclusion": "success", "html_url": "u", "updated_at": "t"},
    ])

    result = poll_until_terminal(
        client, 1, poll_interval=1, max_poll_minutes=10, label="test",
        pending_timeout_minutes=5,
    )
    assert result["status"] == "completed"
    assert result["conclusion"] == "success"


# ---------- Tests: in_progress timeout ----------

def test_in_progress_timeout_raises_after_window(monkeypatch):
    """A run that stays in_progress longer than max_poll_minutes must raise."""
    real_time = time.time()
    # Advance time: pending → in_progress (timer starts) → in_progress → in_progress (exceeds window)
    times = iter([
        real_time,                     # queued
        real_time + 30,                # in_progress (start timer at t0)
        real_time + 30 + 120,          # in_progress, 120s elapsed
        real_time + 30 + 600,          # in_progress, 600s elapsed (exceeds 5 min = 300s)
    ])

    monkeypatch.setattr(time, "time", lambda: next(times, real_time + 10000))
    monkeypatch.setattr(time, "sleep", lambda s: None)

    client = StubClient([
        {"id": 1, "status": "queued", "conclusion": None, "html_url": "u", "updated_at": "t"},
        {"id": 1, "status": "in_progress", "conclusion": None, "html_url": "u", "updated_at": "t"},
        {"id": 1, "status": "in_progress", "conclusion": None, "html_url": "u", "updated_at": "t"},
        {"id": 1, "status": "in_progress", "conclusion": None, "html_url": "u", "updated_at": "t"},
    ])

    with pytest.raises(TimeoutError, match="did not reach terminal state within 5 minutes"):
        poll_until_terminal(
            client, 1, poll_interval=1, max_poll_minutes=5, label="test",
            pending_timeout_minutes=10,
        )


# ---------- Tests: terminal states ----------

def test_completed_success_returns_payload(monkeypatch):
    monkeypatch.setattr(time, "time", lambda: 1000.0)
    monkeypatch.setattr(time, "sleep", lambda s: None)

    client = StubClient([
        {"id": 1, "status": "completed", "conclusion": "success", "html_url": "u", "updated_at": "t"},
    ])
    result = poll_until_terminal(client, 1, poll_interval=1, max_poll_minutes=5, label="test")
    assert result["conclusion"] == "success"


def test_completed_failure_returns_payload(monkeypatch):
    monkeypatch.setattr(time, "time", lambda: 1000.0)
    monkeypatch.setattr(time, "sleep", lambda s: None)

    client = StubClient([
        {"id": 1, "status": "completed", "conclusion": "failure", "html_url": "u", "updated_at": "t"},
    ])
    result = poll_until_terminal(client, 1, poll_interval=1, max_poll_minutes=5, label="test")
    assert result["conclusion"] == "failure"


def test_completed_cancelled_returns_payload(monkeypatch):
    monkeypatch.setattr(time, "time", lambda: 1000.0)
    monkeypatch.setattr(time, "sleep", lambda s: None)

    client = StubClient([
        {"id": 1, "status": "completed", "conclusion": "cancelled", "html_url": "u", "updated_at": "t"},
    ])
    result = poll_until_terminal(client, 1, poll_interval=1, max_poll_minutes=5, label="test")
    assert result["conclusion"] == "cancelled"


# ---------- Tests: detect_active_nvd_run ----------

def test_detect_active_nvd_run_with_in_progress():
    """An in_progress run must be detected and returned."""
    client = StubClient([
        {"id": 100, "status": "in_progress", "conclusion": None, "html_url": "u", "head_sha": "abc"},
    ])
    active = detect_active_nvd_run(client, nvd_workflow_id=123)
    assert active is not None
    assert active["id"] == 100
    assert active["status"] == "in_progress"


def test_detect_active_nvd_run_with_queued():
    client = StubClient([
        {"id": 100, "status": "queued", "conclusion": None, "html_url": "u", "head_sha": "abc"},
    ])
    active = detect_active_nvd_run(client, nvd_workflow_id=123)
    assert active is not None
    assert active["status"] == "queued"


def test_detect_active_nvd_run_with_pending():
    client = StubClient([
        {"id": 100, "status": "pending", "conclusion": None, "html_url": "u", "head_sha": "abc"},
    ])
    active = detect_active_nvd_run(client, nvd_workflow_id=123)
    assert active is not None


def test_detect_active_nvd_run_returns_none_when_all_terminal():
    """When all runs are completed, no active run should be detected."""
    client = StubClient([
        {"id": 1, "status": "completed", "conclusion": "success", "html_url": "u", "head_sha": "abc"},
        {"id": 2, "status": "completed", "conclusion": "failure", "html_url": "u", "head_sha": "abc"},
        {"id": 3, "status": "completed", "conclusion": "cancelled", "html_url": "u", "head_sha": "abc"},
    ])
    active = detect_active_nvd_run(client, nvd_workflow_id=123)
    assert active is None


def test_detect_active_nvd_run_returns_first_active():
    """When multiple runs exist, the first active one should be returned."""
    client = StubClient([
        {"id": 1, "status": "completed", "conclusion": "success", "html_url": "u", "head_sha": "abc"},
        {"id": 2, "status": "in_progress", "conclusion": None, "html_url": "u", "head_sha": "def"},
        {"id": 3, "status": "queued", "conclusion": None, "html_url": "u", "head_sha": "ghi"},
    ])
    active = detect_active_nvd_run(client, nvd_workflow_id=123)
    assert active is not None
    assert active["id"] == 2  # first active, not the queued one


# ---------- Tests: timeout report ----------

def test_print_timeout_report_outputs_all_fields(capsys):
    _print_timeout_report(
        run_id=12345,
        run_url="https://github.com/example/repo/actions/runs/12345",
        status="in_progress",
        conclusion=None,
        queued_duration_s=60,
        running_duration_s=600,
        last_observed_at="2026-06-26T12:00:00Z",
    )
    captured = capsys.readouterr()
    assert "12345" in captured.out
    assert "https://github.com/example/repo/actions/runs/12345" in captured.out
    assert "in_progress" in captured.out
    assert "60s" in captured.out
    assert "600s" in captured.out
    assert "2026-06-26T12:00:00Z" in captured.out


# ---------- Tests: GitHubClient token guard ----------

def test_github_client_requires_token():
    """GitHubClient must refuse to construct without a token."""
    with pytest.raises(ValueError, match="GITHUB_TOKEN"):
        GitHubClient(token="", repo="snadaiapp-png/SNAD")


# ---------- Tests: NVD workflow YAML structure ----------

# R12E: The old NVD database maintenance workflow is DEPRECATED.
# These tests now check the NVD Snapshot Publisher instead.

def test_nvd_publisher_workflow_requires_self_hosted_runner():
    """R12E: the publisher must run on self-hosted runner, not ubuntu-latest."""
    import yaml
    wf_path = REPO_ROOT / ".github" / "workflows" / "nvd-snapshot-publisher.yml"
    with wf_path.open() as f:
        d = yaml.safe_load(f)
    for jn, jd in d["jobs"].items():
        runs_on = jd.get("runs-on", "")
        if isinstance(runs_on, list):
            assert "self-hosted" in runs_on, f"{jn} must run on self-hosted"
            assert "snad-nvd-publisher" in runs_on, f"{jn} must have snad-nvd-publisher label"
        elif isinstance(runs_on, str):
            assert "self-hosted" in runs_on, f"{jn} must run on self-hosted"
        else:
            assert False, f"{jn} runs-on is not a list or string: {runs_on}"


def test_nvd_publisher_uses_single_update_pass():
    """R12E: publisher runs one update-only pass, not a retry loop."""
    wf_path = REPO_ROOT / ".github" / "workflows" / "nvd-snapshot-publisher.yml"
    content = wf_path.read_text(encoding="utf-8")
    assert "update-only" in content
    # No retry loop (no `for ATTEMPT in 1 2 3`)
    assert "for ATTEMPT in 1 2 3" not in content


def test_nvd_publisher_does_not_force_results_per_page():
    """R12E: do not force nvdApiResultsPerPage; use NVD default."""
    wf_path = REPO_ROOT / ".github" / "workflows" / "nvd-snapshot-publisher.yml"
    content = wf_path.read_text(encoding="utf-8")
    assert "nvdApiResultsPerPage" not in content


def test_nvd_publisher_job_timeout_is_240():
    import yaml
    wf_path = REPO_ROOT / ".github" / "workflows" / "nvd-snapshot-publisher.yml"
    with wf_path.open() as f:
        d = yaml.safe_load(f)
    for jn, jd in d["jobs"].items():
        assert jd["timeout-minutes"] == 240, f"{jn} timeout should be 240"


def test_nvd_database_maintenance_is_deprecated():
    """R12E: old NVD workflow must have no triggers."""
    import yaml
    wf_path = REPO_ROOT / ".github" / "workflows" / "nvd-database-maintenance.yml"
    with wf_path.open() as f:
        d = yaml.safe_load(f)
    on_key = d.get("on", d.get(True, {}))
    # on: {} means no triggers
    assert on_key == {} or on_key is None, f"NVD database maintenance must have no triggers, got: {on_key}"


def test_nvd_database_maintenance_name_includes_deprecated():
    wf_path = REPO_ROOT / ".github" / "workflows" / "nvd-database-maintenance.yml"
    content = wf_path.read_text(encoding="utf-8")
    assert "DEPRECATED" in content


def test_validator_accepts_odc_trace_db():
    """The validator must not flag odc.trace.db as a temp file."""
    validator_path = REPO_ROOT / "scripts" / "ci" / "validate_nvd_database.py"
    content = validator_path.read_text(encoding="utf-8")
    assert '"odc.trace.db"' not in content


# ---------- Tests: R12B orchestrator YAML ----------

def test_r12b_orchestrator_job_timeout_is_120():
    """R12E: R12B timeout reduced to 120 minutes (no NVD build)."""
    import yaml
    wf_path = REPO_ROOT / ".github" / "workflows" / "r12b-acceptance-orchestrator.yml"
    with wf_path.open() as f:
        d = yaml.safe_load(f)
    assert d["jobs"]["orchestrate"]["timeout-minutes"] == 120


def test_r12b_orchestrator_does_not_dispatch_nvd():
    """R12E: R12B must not dispatch NVD build."""
    wf_path = REPO_ROOT / ".github" / "workflows" / "r12b-acceptance-orchestrator.yml"
    content = wf_path.read_text(encoding="utf-8")
    # The orchestrator should not reference NVD_WORKFLOW dispatch
    assert "nvd-database-maintenance" not in content


def test_r12b_orchestrator_installs_storage_deps():
    """R12E: R12B must install storage backend dependencies."""
    wf_path = REPO_ROOT / ".github" / "workflows" / "r12b-acceptance-orchestrator.yml"
    content = wf_path.read_text(encoding="utf-8")
    assert "NVD_SNAPSHOT_BACKEND" in content
    assert "Install storage backend dependencies" in content
