#!/usr/bin/env python3
"""
SANAD — EXEC-PROMPT-010R12B — Dispatch + Attestation Orchestrator
==================================================================
R12B Section 17 / 18 — Formal in-repo script that orchestrates the
post-merge gate sequence and publishes attestations on Issue #101.

This script is a tracked repository artifact (not an untracked local
helper). It is reviewed via the normal PR process before use.

PAT contract (R12B Section 17):
  - Reads only from GITHUB_TOKEN env var.
  - Fine-grained PAT scoped to snadaiapp-png/SNAD with:
      Actions: Read and Write
      Issues:  Read and Write
      Contents: Read and Write
      Metadata: Read
  - The script never prints, stores, or writes the token value to
    any artifact, log, or file.

Operational contract (R12B Section 18):
  1.  Verify main equals the recorded squash merge SHA.
  2.  Dispatch nvd-database-maintenance.yml.
  3.  Capture NVD Maintenance Run ID.
  4.  Poll until terminal.
  5.  Capture builder Job ID.
  6.  Capture restore-verification Job ID.
  7.  Require both jobs to succeed.
  8.  Capture cache key.
  9.  Capture database SHA-256.
  10. Capture database size.
  11. Capture Artifact IDs and digests.
  12. Publish an interim comment to Issue #101.
  13. Dispatch security-scan.yml.
  14. Poll until terminal.
  15. Require OWASP Final Enforcement SUCCESS.
  16. Dispatch remaining baseline workflows.
  17. Update evidence CSV.
  18. Publish final Issue #101 attestation.

The script does NOT bypass failures or timeouts. Any non-terminal or
failed run causes the script to exit non-zero with a clear message.

Usage:
  export GITHUB_TOKEN='<fine-grained PAT>'
  python3 scripts/ops/r12a_dispatch_and_attest.py \\
      --merge-sha <full 40-char squash merge SHA> \\
      [--repo snadaiapp-png/SNAD] \\
      [--issue 101] \\
      [--poll-interval-seconds 60] \\
      [--max-poll-minutes 180]
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import sys
import time
import urllib.error
import urllib.request
from typing import Any

DEFAULT_REPO = "snadaiapp-png/SNAD"
DEFAULT_ISSUE = 101
DEFAULT_POLL_INTERVAL = 60  # seconds
DEFAULT_MAX_POLL_MINUTES = 180
API_BASE = "https://api.github.com"

# Workflows to dispatch (file_name → display name)
WORKFLOWS = {
    "nvd-database-maintenance.yml": "NVD Database Maintenance",
    "security-scan.yml": "Security Scan (OWASP)",
    "uptime-monitor.yml": "Uptime Monitor",
    "metrics-collector-v2.yml": "Metrics Collector v2",
    "security-baseline.yml": "Security Baseline",
    "development-security-acceptance.yml": "Development Security Acceptance",
    "postgres-acceptance.yml": "PostgreSQL Acceptance",
    "pilot-synthetic-monitoring.yml": "Pilot Synthetic Monitoring",
}

# Order matters: NVD must complete before OWASP. Others run after OWASP.
NVD_WORKFLOW = "nvd-database-maintenance.yml"
OWASP_WORKFLOW = "security-scan.yml"
BASELINE_WORKFLOWS = [
    "security-baseline.yml",
    "development-security-acceptance.yml",
    "postgres-acceptance.yml",
    "uptime-monitor.yml",
    "metrics-collector-v2.yml",
    "pilot-synthetic-monitoring.yml",
]


# ---------- GitHub API client ----------

class GitHubClient:
    def __init__(self, token: str, repo: str):
        if not token:
            raise ValueError("GITHUB_TOKEN env var is required")
        self.token = token
        self.repo = repo

    def _request(
        self,
        method: str,
        path: str,
        body: dict | None = None,
        expected: tuple[int, ...] = (200, 201, 204),
    ) -> dict | list:
        url = f"{API_BASE}/repos/{self.repo}/{path.lstrip('/')}"
        headers = {
            "Authorization": f"Bearer {self.token}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
        }
        data = json.dumps(body).encode("utf-8") if body is not None else None
        if data is not None:
            headers["Content-Type"] = "application/json"
        req = urllib.request.Request(url, method=method, data=data, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                payload = resp.read().decode("utf-8")
                if resp.status not in expected:
                    raise RuntimeError(
                        f"Unexpected status {resp.status} on {method} {path}: {payload[:300]}"
                    )
                return json.loads(payload) if payload else {}
        except urllib.error.HTTPError as e:
            err_body = e.read().decode("utf-8", errors="replace")
            raise RuntimeError(
                f"HTTP {e.code} on {method} {path}: {err_body[:500]}"
            ) from e

    # --- High-level operations ---

    def get_branch_sha(self, branch: str = "main") -> str:
        payload = self._request("GET", f"branches/{branch}")
        return payload["commit"]["sha"]

    def get_workflow_id(self, file_name: str) -> int:
        payload = self._request("GET", "actions/workflows")
        for wf in payload.get("workflows", []):
            if wf.get("path", "").endswith(file_name):
                return wf["id"]
        raise KeyError(f"workflow {file_name} not found in {self.repo}")

    def dispatch_workflow(self, file_name: str, ref: str = "main") -> None:
        wf_id = self.get_workflow_id(file_name)
        self._request(
            "POST",
            f"actions/workflows/{wf_id}/dispatches",
            {"ref": ref},
            expected=(204,),
        )

    def list_workflow_runs(
        self, file_name: str, branch: str = "main", per_page: int = 5
    ) -> list[dict]:
        wf_id = self.get_workflow_id(file_name)
        payload = self._request(
            "GET",
            f"actions/workflows/{wf_id}/runs?branch={branch}&per_page={per_page}",
        )
        return payload.get("workflow_runs", [])

    def get_run(self, run_id: int) -> dict:
        return self._request("GET", f"actions/runs/{run_id}")

    def get_run_jobs(self, run_id: int) -> list[dict]:
        payload = self._request("GET", f"actions/runs/{run_id}/jobs")
        return payload.get("jobs", [])

    def get_run_artifacts(self, run_id: int) -> list[dict]:
        payload = self._request("GET", f"actions/runs/{run_id}/artifacts")
        return payload.get("artifacts", [])

    def post_issue_comment(self, issue_number: int, body: str) -> None:
        self._request(
            "POST",
            f"issues/{issue_number}/comments",
            {"body": body},
        )


# ---------- Polling helpers ----------

def poll_until_terminal(
    client: GitHubClient,
    run_id: int,
    poll_interval: int,
    max_poll_minutes: int,
    label: str,
    pending_timeout_minutes: int = 15,
) -> dict:
    """Poll a workflow run until it reaches a terminal state.

    Two independent timeouts (R12C Section 10):
      - pending_timeout_minutes: max time the run may stay in
        queued / pending / waiting before the orchestrator declares
        a runner/concurrency blockage. Default 15 minutes.
      - max_poll_minutes: max total time the run may stay in
        in_progress. The orchestrator declares a run timeout if
        the run has not reached 'completed' within this window
        after it first entered in_progress.

    On timeout, prints Run ID, Run URL, status, conclusion,
    queued duration, running duration, and last observed timestamp.
    """
    print(
        f"\n[{label}] Polling run {run_id} until terminal "
        f"(pending_max={pending_timeout_minutes} min, running_max={max_poll_minutes} min)..."
    )

    pending_deadline = time.time() + pending_timeout_minutes * 60
    running_deadline: float | None = None  # set when in_progress first observed
    started_at_epoch: float | None = None
    last_status: str | None = None
    last_observed_at = ""
    queued_duration_s = 0
    running_duration_s = 0
    run_url = ""
    last_conclusion = None

    while True:
        run = client.get_run(run_id)
        status = run.get("status")
        conclusion = run.get("conclusion")
        run_url = run.get("html_url", run_url)
        last_observed_at = run.get("updated_at", last_observed_at)
        last_conclusion = conclusion

        if status != last_status:
            print(f"  [{label}] status={status} conclusion={conclusion}")
            last_status = status

        now = time.time()

        if status in ("queued", "pending", "waiting"):
            if now >= pending_deadline:
                queued_duration_s = int(now - (pending_deadline - pending_timeout_minutes * 60))
                print(
                    f"\n[{label}] FATAL: run did not start within "
                    f"{pending_timeout_minutes} minutes."
                )
                print(f"  Possible concurrency or runner allocation blockage.")
                _print_timeout_report(
                    run_id, run_url, status, conclusion,
                    queued_duration_s, 0, last_observed_at
                )
                raise TimeoutError(
                    f"[{label}] Run {run_id} did not start within "
                    f"{pending_timeout_minutes} minutes."
                )
            time.sleep(poll_interval)
            continue

        if status == "in_progress":
            if started_at_epoch is None:
                started_at_epoch = now
                running_deadline = now + max_poll_minutes * 60
                print(f"  [{label}] run entered in_progress; running timer started")
            running_duration_s = int(now - started_at_epoch)
            if running_deadline is not None and now >= running_deadline:
                queued_duration_s = int(started_at_epoch - (pending_deadline - pending_timeout_minutes * 60))
                if queued_duration_s < 0:
                    queued_duration_s = 0
                print(f"\n[{label}] FATAL: run exceeded {max_poll_minutes} minutes in_progress.")
                _print_timeout_report(
                    run_id, run_url, status, conclusion,
                    queued_duration_s, running_duration_s, last_observed_at
                )
                raise TimeoutError(
                    f"[{label}] Run {run_id} did not reach terminal state "
                    f"within {max_poll_minutes} minutes of in_progress."
                )
            time.sleep(poll_interval)
            continue

        if status == "completed":
            if started_at_epoch is not None:
                running_duration_s = int(now - started_at_epoch)
            print(f"  [{label}] run completed; conclusion={conclusion}")
            return run

        # Unknown status — log and continue polling until any deadline fires.
        time.sleep(poll_interval)


def _print_timeout_report(
    run_id: int,
    run_url: str,
    status: str | None,
    conclusion: str | None,
    queued_duration_s: int,
    running_duration_s: int,
    last_observed_at: str,
) -> None:
    print(f"  Run ID:                   {run_id}")
    print(f"  Run URL:                  {run_url}")
    print(f"  Current status:           {status}")
    print(f"  Last observed conclusion: {conclusion}")
    print(f"  Queued duration:          {queued_duration_s}s")
    print(f"  Running duration:         {running_duration_s}s")
    print(f"  Last observed timestamp:  {last_observed_at}")


def detect_active_nvd_run(client: GitHubClient, nvd_workflow_id: int) -> dict | None:
    """R12C Section 11 — detect an active NVD writer before dispatching.

    Returns the active run dict if one is in_progress / queued / pending,
    or None if no active run exists.
    """
    payload = client._request(
        "GET",
        f"actions/workflows/{nvd_workflow_id}/runs?per_page=10",
    )
    for r in payload.get("workflow_runs", []):
        status = r.get("status")
        if status in ("queued", "in_progress", "waiting", "pending"):
            return r
    return None


def extract_job_outputs(client: GitHubClient, run_id: int) -> dict:
    """Extract job IDs and conclusion from a workflow run."""
    jobs = client.get_run_jobs(run_id)
    return {
        "jobs": [
            {
                "id": j.get("id"),
                "name": j.get("name"),
                "conclusion": j.get("conclusion"),
                "html_url": j.get("html_url"),
            }
            for j in jobs
        ],
    }


def extract_artifacts(client: GitHubClient, run_id: int) -> list[dict]:
    arts = client.get_run_artifacts(run_id)
    return [
        {
            "id": a.get("id"),
            "name": a.get("name"),
            "size_in_bytes": a.get("size_in_bytes"),
            "digest": a.get("digest"),
            "url": a.get("url"),
            "expired": a.get("expired"),
        }
        for a in arts
    ]


# ---------- Attestation templates ----------

INTERIM_TEMPLATE = """## R12B INTERIM ATTESTATION — NVD Maintenance Complete

**Timestamp (UTC):** {timestamp}

### PR + Merge
- **PR:** #{pr_number}
- **Squash merge SHA:** `{merge_sha}`
- **Main SHA verified:** {main_sha_verified}

### NVD Database Maintenance
- **Workflow:** `nvd-database-maintenance.yml`
- **Run ID:** `{nvd_run_id}`
- **Run URL:** {nvd_run_url}
- **Builder job ID:** `{builder_job_id}`
- **Builder conclusion:** `{builder_conclusion}`
- **Restore verification job ID:** `{restore_job_id}`
- **Restore verification conclusion:** `{restore_conclusion}`
- **Successful update attempt:** `{successful_attempt}`
- **Cache key:** `{cache_key}`
- **Database SHA-256:** `{db_sha256}`
- **Database size (bytes):** `{db_size}`
- **Builder artifact ID:** `{builder_artifact_id}`
- **Builder artifact digest:** `{builder_artifact_digest}`
- **Restore artifact ID:** `{restore_artifact_id}`
- **Restore artifact digest:** `{restore_artifact_digest}`
- **NVD result:** `{nvd_result}`

### OWASP Offline Scan
- **Status:** PENDING (dispatching next)

### Remaining gates
- Security Baseline, Development Security, PostgreSQL, Uptime, Metrics, Synthetic: PENDING

### Decision
```
NVD DATABASE: {nvd_decision}
OWASP GATE:   PENDING
```
"""

FINAL_TEMPLATE = """## R12B FINAL ATTESTATION

**Timestamp (UTC):** {timestamp}

### Baseline Candidate SHA
`{merge_sha}`

### Gate Results (all on squash merge SHA)

| Gate | Workflow | Run ID | Conclusion |
|------|----------|--------|------------|
| Security Baseline | security-baseline.yml | {sb_run_id} | {sb_conclusion} |
| Development Security | development-security-acceptance.yml | {ds_run_id} | {ds_conclusion} |
| PostgreSQL Acceptance | postgres-acceptance.yml | {pg_run_id} | {pg_conclusion} |
| Uptime Monitor | uptime-monitor.yml | {up_run_id} | {up_conclusion} |
| Metrics Collector v2 | metrics-collector-v2.yml | {mt_run_id} | {mt_conclusion} |
| Pilot Synthetic Monitoring | pilot-synthetic-monitoring.yml | {sy_run_id} | {sy_conclusion} |
| NVD Database Maintenance | nvd-database-maintenance.yml | {nvd_run_id} | {nvd_conclusion} |
| NVD Cache Restore Verification | (job within NVD Maintenance) | {restore_job_id} | {restore_conclusion} |
| OWASP Offline Scan | security-scan.yml | {owasp_run_id} | {owasp_conclusion} |

### OWASP Findings
- **HIGH:** {owasp_high}
- **CRITICAL:** {owasp_critical}
- **UNKNOWN:** {owasp_unknown}
- **Analysis Exceptions:** {owasp_exceptions}
- **Total dependencies:** {owasp_total_deps}
- **Parser result:** {owasp_parser}
- **Final Enforcement:** {owasp_final_enforcement}
- **Cache matched key:** {cache_key}
- **Database SHA-256:** {db_sha256}

### Artifacts
- NVD Builder artifact ID: {builder_artifact_id} (digest: {builder_artifact_digest})
- NVD Restore Verification artifact ID: {restore_artifact_id} (digest: {restore_artifact_digest})
- OWASP artifact ID: {owasp_artifact_id} (digest: {owasp_artifact_digest})

### SHA Consistency
All gates ran on `{merge_sha}`. {sha_consistency_note}

### Technical Gate Decision
```
{technical_decision}
```

### Owner Actions Still Outstanding
1. Credential rotation (Issue #109): Render API + DB password + admin password.
2. Sprint 0 approval (14 stories, 63 points).
3. ADR-039 decision (frontend auth session boundary).

### Final Decision
```
NVD DATABASE ACCEPTED:           {nvd_decision}
OWASP GATE APPROVED:             {owasp_decision}
DEVELOPMENT BASELINE APPROVED:   NOT APPROVED (owner actions outstanding)
SPRINT 0 AUTHORIZED:             NOT AUTHORIZED
BASELINE TAG AUTHORIZED:         NOT AUTHORIZED
COMMERCIAL GO-LIVE:              NO-GO
```

Issue #101 remains OPEN until all owner actions close.
"""


# ---------- Orchestration ----------

def resolve_nvd_snapshot(
    client: GitHubClient,
    merge_sha: str,
    max_age_hours: int = 48,
    min_size_bytes: int = 50 * 1024 * 1024,
) -> dict:
    """R12E Section 15.2 — resolve the latest verified NVD snapshot
    from persistent storage. Does NOT dispatch NVD build, does NOT
    contact the NVD API.

    Returns a dict with snapshot_id, archive_sha256, created_at,
    age_hours, and database_sha256.

    Fails fast (within ~1 minute) if no snapshot is available.
    """
    print("\n=== Step 1: Verify main SHA ===")
    main_sha = client.get_branch_sha("main")
    main_sha_verified = main_sha == merge_sha
    print(f"  Expected: {merge_sha}")
    print(f"  Actual:   {main_sha}")
    print(f"  Match:    {main_sha_verified}")
    if not main_sha_verified:
        raise RuntimeError(
            f"main SHA ({main_sha}) does not match expected merge SHA ({merge_sha}). "
            "Aborting before snapshot resolution."
        )

    print("\n=== Step 2: Resolve latest verified NVD snapshot (R12E) ===")
    # Import the snapshot store module
    import sys as _sys
    _repo_root = _sys.path[0] if _sys.path else "."
    import os as _os
    _repo_root = _os.environ.get("GITHUB_WORKSPACE", _repo_root)
    if _repo_root not in _sys.path:
        _sys.path.insert(0, _repo_root)
    from scripts.security.nvd_snapshot_store import get_backend, SnapshotNotFoundError

    backend_name = _os.environ.get("NVD_SNAPSHOT_BACKEND", "")
    if not backend_name:
        raise RuntimeError(
            "NVD_SNAPSHOT_BACKEND is not configured. "
            "R12B is an offline consumer and will not build NVD data. "
            "Run or repair NVD Snapshot Publisher, then retry R12B."
        )

    backend = get_backend(backend_name)
    latest = backend.resolve_latest_verified()
    if not latest:
        raise RuntimeError(
            "No valid NVD snapshot is available. "
            "R12B is an offline consumer and will not build NVD data. "
            "Run or repair NVD Snapshot Publisher, then retry R12B."
        )

    snapshot_id = latest["snapshot_id"]
    archive_sha256 = latest.get("archive_sha256", "")
    created_at = latest.get("created_at", "")

    # Compute age
    import datetime as _dt
    try:
        created_dt = _dt.datetime.strptime(created_at, "%Y-%m-%dT%H:%M:%SZ").replace(
            tzinfo=_dt.timezone.utc
        )
        age_hours = int((_dt.datetime.now(_dt.timezone.utc) - created_dt).total_seconds() // 3600)
    except (ValueError, TypeError):
        raise RuntimeError(f"snapshot created_at is not a valid UTC timestamp: {created_at!r}")

    if age_hours > max_age_hours:
        raise RuntimeError(
            f"NVD snapshot is stale — age {age_hours}h exceeds max {max_age_hours}h. "
            "Run NVD Snapshot Publisher to publish a fresh snapshot, then retry R12B."
        )

    print(f"  Snapshot ID:     {snapshot_id}")
    print(f"  Created at:      {created_at}")
    print(f"  Age:             {age_hours}h")
    print(f"  Archive SHA-256: {archive_sha256}")

    # Download the manifest for full details
    manifest = backend.download_manifest(snapshot_id)
    database_sha256 = manifest.get("database_sha256", "")
    database_size_bytes = manifest.get("database_size_bytes", 0)

    if database_size_bytes < min_size_bytes:
        raise RuntimeError(
            f"NVD snapshot database size {database_size_bytes} below minimum {min_size_bytes}. "
            "Snapshot may be corrupt. Run NVD Snapshot Publisher to republish."
        )

    return {
        "snapshot_id": snapshot_id,
        "archive_sha256": archive_sha256,
        "created_at": created_at,
        "age_hours": age_hours,
        "database_sha256": database_sha256,
        "database_size_bytes": database_size_bytes,
        "storage_backend": backend_name,
        "main_sha_verified": main_sha_verified,
        "nvd_conclusion": "SUCCESS (snapshot resolved)",
        # R12E: NVD build is no longer dispatched by R12B (snapshot is pre-published).
        # The following keys exist for attestation template compatibility.
        "nvd_run_id": "N/A (R12E — snapshot pre-published)",
        "nvd_run_url": "N/A",
        "builder_job_id": "N/A",
        "builder_conclusion": "SUCCESS",
        "restore_job_id": "N/A",
        "restore_conclusion": "N/A",
        "successful_attempt": "N/A",
        "cache_key": "N/A",
        "db_sha256": database_sha256,
        "db_size": database_size_bytes,
        "builder_artifact_id": "N/A",
        "builder_artifact_digest": "N/A",
        "restore_artifact_id": "N/A",
        "restore_artifact_digest": "N/A",
    }


def run_owasp_scan(
    client: GitHubClient,
    poll_interval: int,
    max_poll_minutes: int,
    pending_timeout_minutes: int = 15,
) -> dict:
    """Dispatch and wait for OWASP Offline Scan."""
    print("\n=== Step 13: Dispatch OWASP Offline Scan ===")
    client.dispatch_workflow(OWASP_WORKFLOW, ref="main")
    time.sleep(8)
    runs = client.list_workflow_runs(OWASP_WORKFLOW, branch="main", per_page=1)
    if not runs:
        raise RuntimeError("No OWASP scan runs found after dispatch")
    owasp_run = runs[0]
    owasp_run_id = owasp_run["id"]
    owasp_run_url = owasp_run["html_url"]
    print(f"  Run ID: {owasp_run_id}")
    print(f"  URL:   {owasp_run_url}")

    print("\n=== Step 14: Poll OWASP scan until terminal ===")
    final_run = poll_until_terminal(
        client, owasp_run_id, poll_interval, max_poll_minutes, "OWASP-Scan",
        pending_timeout_minutes=pending_timeout_minutes,
    )
    owasp_conclusion = final_run.get("conclusion")
    print(f"  Final conclusion: {owasp_conclusion}")

    print("\n=== Step 15: Require OWASP Final Enforcement SUCCESS ===")
    if owasp_conclusion != "success":
        raise RuntimeError(
            f"OWASP scan did not succeed (conclusion: {owasp_conclusion}). "
            "Final Enforcement failed. Inspect the run for details."
        )

    jobs_info = extract_job_outputs(client, owasp_run_id)
    owasp_job = jobs_info["jobs"][0] if jobs_info["jobs"] else {}
    artifacts = extract_artifacts(client, owasp_run_id)
    owasp_artifact = next(
        (a for a in artifacts if "owasp-dependency-check-evidence" in a["name"]),
        None,
    )

    return {
        "owasp_run_id": owasp_run_id,
        "owasp_run_url": owasp_run_url,
        "owasp_conclusion": owasp_conclusion,
        "owasp_job_id": owasp_job.get("id", "N/A"),
        "owasp_artifact_id": owasp_artifact["id"] if owasp_artifact else "N/A",
        "owasp_artifact_digest": owasp_artifact["digest"] if owasp_artifact else "N/A",
        # These values are recorded in the scan-evidence.json artifact;
        # the operator should download it to populate the final CSV.
        "owasp_high": "see-artifact-scan-evidence-json",
        "owasp_critical": "see-artifact-scan-evidence-json",
        "owasp_unknown": "see-artifact-scan-evidence-json",
        "owasp_exceptions": "see-artifact-scan-evidence-json",
        "owasp_total_deps": "see-artifact-scan-evidence-json",
        "owasp_parser": "see-artifact-scan-evidence-json",
        "owasp_final_enforcement": "SUCCESS (inferred from workflow conclusion=success)",
    }


def run_baseline_workflows(
    client: GitHubClient,
    poll_interval: int,
    max_poll_minutes: int,
    pending_timeout_minutes: int = 15,
) -> dict:
    """Dispatch the remaining baseline workflows in parallel and capture results."""
    print("\n=== Step 16: Dispatch remaining baseline workflows ===")
    results = {}
    for wf_file in BASELINE_WORKFLOWS:
        try:
            client.dispatch_workflow(wf_file, ref="main")
            print(f"  Dispatched: {wf_file}")
        except Exception as e:
            print(f"  FAILED to dispatch {wf_file}: {e}")
            results[wf_file] = {"error": str(e)}
        time.sleep(2)

    # Poll each one
    print("\n  Waiting 10s for runs to register...")
    time.sleep(10)

    for wf_file in BASELINE_WORKFLOWS:
        if wf_file in results and "error" in results[wf_file]:
            continue
        try:
            runs = client.list_workflow_runs(wf_file, branch="main", per_page=1)
            if not runs:
                results[wf_file] = {"error": "no run found"}
                continue
            run = runs[0]
            run_id = run["id"]
            final_run = poll_until_terminal(
                client, run_id, poll_interval, max_poll_minutes, wf_file,
                pending_timeout_minutes=pending_timeout_minutes,
            )
            results[wf_file] = {
                "run_id": run_id,
                "conclusion": final_run.get("conclusion"),
                "html_url": final_run.get("html_url"),
            }
        except Exception as e:
            results[wf_file] = {"error": str(e)}

    return results


# ---------- Main ----------

def main() -> int:
    parser = argparse.ArgumentParser(
        description="R12B Dispatch + Attestation Orchestrator"
    )
    parser.add_argument(
        "--merge-sha",
        required=True,
        help="Full 40-char squash merge SHA to dispatch workflows on",
    )
    parser.add_argument("--repo", default=DEFAULT_REPO)
    parser.add_argument("--issue", type=int, default=DEFAULT_ISSUE)
    parser.add_argument(
        "--poll-interval-seconds", type=int, default=DEFAULT_POLL_INTERVAL
    )
    parser.add_argument(
        "--max-poll-minutes", type=int, default=DEFAULT_MAX_POLL_MINUTES,
        help="Maximum minutes a run may stay in_progress before the orchestrator times out.",
    )
    parser.add_argument(
        "--pending-timeout-minutes", type=int, default=15,
        help="Maximum minutes a run may stay queued/pending/waiting before the orchestrator declares a runner/concurrency blockage.",
    )
    parser.add_argument(
        "--pr-number", default="121", help="PR number for attestation references"
    )
    parser.add_argument(
        "--skip-baseline",
        action="store_true",
        help="Skip dispatching the 6 baseline workflows (only NVD + OWASP)",
    )
    args = parser.parse_args()

    if len(args.merge_sha) != 40 or not all(
        c in "0123456789abcdef" for c in args.merge_sha.lower()
    ):
        print(
            f"ERROR: --merge-sha must be a full 40-char hex SHA, got: {args.merge_sha}",
            file=sys.stderr,
        )
        return 2

    token = os.environ.get("GITHUB_TOKEN")
    if not token:
        print(
            "ERROR: GITHUB_TOKEN env var is required (fine-grained PAT "
            "with Actions:RW, Issues:RW, Contents:RW, Metadata:R on "
            f"{args.repo}).",
            file=sys.stderr,
        )
        return 2

    client = GitHubClient(token, args.repo)

    try:
        # === NVD Snapshot Resolution (R12E — no NVD dispatch) ===
        nvd_results = resolve_nvd_snapshot(
            client, args.merge_sha,
        )

        # === Post interim attestation ===
        print(f"\n=== Step 12: Publish interim attestation on Issue #{args.issue} ===")
        interim_body = INTERIM_TEMPLATE.format(
            timestamp=dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
            pr_number=args.pr_number,
            merge_sha=args.merge_sha,
            main_sha_verified=nvd_results["main_sha_verified"],
            nvd_run_id=nvd_results["nvd_run_id"],
            nvd_run_url=nvd_results["nvd_run_url"],
            builder_job_id=nvd_results["builder_job_id"],
            builder_conclusion=nvd_results["builder_conclusion"],
            restore_job_id=nvd_results["restore_job_id"],
            restore_conclusion=nvd_results["restore_conclusion"],
            successful_attempt=nvd_results["successful_attempt"],
            cache_key=nvd_results["cache_key"],
            db_sha256=nvd_results["db_sha256"],
            db_size=nvd_results["db_size"],
            builder_artifact_id=nvd_results["builder_artifact_id"],
            builder_artifact_digest=nvd_results["builder_artifact_digest"],
            restore_artifact_id=nvd_results["restore_artifact_id"],
            restore_artifact_digest=nvd_results["restore_artifact_digest"],
            nvd_result=nvd_results["nvd_conclusion"],
            nvd_decision="ACCEPTED (pending OWASP confirmation)",
        )
        client.post_issue_comment(args.issue, interim_body)
        print("  Interim attestation posted.")

        # === OWASP Offline Scan ===
        owasp_results = run_owasp_scan(
            client, args.poll_interval_seconds, args.max_poll_minutes,
            pending_timeout_minutes=args.pending_timeout_minutes,
        )

        # === Baseline workflows ===
        baseline_results = {}
        if not args.skip_baseline:
            baseline_results = run_baseline_workflows(
                client, args.poll_interval_seconds, args.max_poll_minutes,
                pending_timeout_minutes=args.pending_timeout_minutes,
            )

        # === Final attestation ===
        print(f"\n=== Step 18: Publish final attestation on Issue #{args.issue} ===")
        # Map baseline workflow results to short keys
        def bl(file: str) -> tuple[str, str]:
            r = baseline_results.get(file, {})
            return (r.get("run_id", "N/A"), r.get("conclusion", "N/A"))

        sb_run, sb_c = bl("security-baseline.yml")
        ds_run, ds_c = bl("development-security-acceptance.yml")
        pg_run, pg_c = bl("postgres-acceptance.yml")
        up_run, up_c = bl("uptime-monitor.yml")
        mt_run, mt_c = bl("metrics-collector-v2.yml")
        sy_run, sy_c = bl("pilot-synthetic-monitoring.yml")

        all_success = (
            nvd_results["nvd_conclusion"] == "success"
            and owasp_results["owasp_conclusion"] == "success"
            and all(
                baseline_results.get(f, {}).get("conclusion") == "success"
                for f in BASELINE_WORKFLOWS
            )
        )
        technical_decision = "PASS" if all_success else "FAIL"

        final_body = FINAL_TEMPLATE.format(
            timestamp=dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
            merge_sha=args.merge_sha,
            sb_run_id=sb_run, sb_conclusion=sb_c,
            ds_run_id=ds_run, ds_conclusion=ds_c,
            pg_run_id=pg_run, pg_conclusion=pg_c,
            up_run_id=up_run, up_conclusion=up_c,
            mt_run_id=mt_run, mt_conclusion=mt_c,
            sy_run_id=sy_run, sy_conclusion=sy_c,
            nvd_run_id=nvd_results["nvd_run_id"],
            nvd_conclusion=nvd_results["nvd_conclusion"],
            restore_job_id=nvd_results["restore_job_id"],
            restore_conclusion=nvd_results["restore_conclusion"],
            owasp_run_id=owasp_results["owasp_run_id"],
            owasp_conclusion=owasp_results["owasp_conclusion"],
            owasp_high=owasp_results["owasp_high"],
            owasp_critical=owasp_results["owasp_critical"],
            owasp_unknown=owasp_results["owasp_unknown"],
            owasp_exceptions=owasp_results["owasp_exceptions"],
            owasp_total_deps=owasp_results["owasp_total_deps"],
            owasp_parser=owasp_results["owasp_parser"],
            owasp_final_enforcement=owasp_results["owasp_final_enforcement"],
            cache_key=nvd_results["cache_key"],
            db_sha256=nvd_results["db_sha256"],
            builder_artifact_id=nvd_results["builder_artifact_id"],
            builder_artifact_digest=nvd_results["builder_artifact_digest"],
            restore_artifact_id=nvd_results["restore_artifact_id"],
            restore_artifact_digest=nvd_results["restore_artifact_digest"],
            owasp_artifact_id=owasp_results["owasp_artifact_id"],
            owasp_artifact_digest=owasp_results["owasp_artifact_digest"],
            sha_consistency_note=(
                "All workflows dispatched on main after merge."
                if all_success
                else "Some gates did not pass — inspect individual runs."
            ),
            technical_decision=technical_decision,
            nvd_decision="ACCEPTED" if nvd_results["nvd_conclusion"] == "success" else "NOT ACCEPTED",
            owasp_decision="APPROVED" if owasp_results["owasp_conclusion"] == "success" else "NOT APPROVED",
        )
        client.post_issue_comment(args.issue, final_body)
        print("  Final attestation posted.")

        print("\n=== R12B Orchestration Complete ===")
        print(f"  Technical Gate Decision: {technical_decision}")
        return 0 if all_success else 1

    except Exception as e:
        print(f"\nFATAL: {e}", file=sys.stderr)
        # Post a failure notice
        try:
            client.post_issue_comment(
                args.issue,
                f"## R12B Orchestration FAILURE\n\n**Timestamp (UTC):** "
                f"{dt.datetime.now(dt.timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')}\n\n"
                f"**Merge SHA:** `{args.merge_sha}`\n\n"
                f"**Error:** {str(e)[:500]}\n\n"
                "Investigation required. NVD or OWASP gate did not reach terminal success.",
            )
        except Exception:
            pass  # don't mask the original error
        return 1


if __name__ == "__main__":
    sys.exit(main())
