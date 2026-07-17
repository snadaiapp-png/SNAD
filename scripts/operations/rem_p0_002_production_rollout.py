#!/usr/bin/env python3
"""Deploy the exact main SHA to Vercel Production and run REM-P0-002 acceptance."""
# Operational trigger: CRM-006 production closure rerun (2026-07-17).
from __future__ import annotations

import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

EVIDENCE = Path("evidence")
SUMMARY = EVIDENCE / "rem-p0-002-rollout-summary.json"


class RolloutFailure(RuntimeError):
    pass


def required(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise RolloutFailure(f"missing required environment value: {name}")
    return value


def request_json(method: str, url: str, token: str, payload: dict[str, Any] | None = None, timeout: int = 60) -> dict[str, Any]:
    data = None if payload is None else json.dumps(payload, separators=(",", ":")).encode("utf-8")
    headers = {"Authorization": f"Bearer {token}", "Accept": "application/json"}
    if data is not None:
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            body = response.read()
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        raise RolloutFailure(f"Vercel API {method} failed with HTTP {error.code}: {body[:500]}") from error
    except (urllib.error.URLError, TimeoutError, OSError) as error:
        raise RolloutFailure(f"Vercel API {method} network failure: {type(error).__name__}") from error
    try:
        value = json.loads(body.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise RolloutFailure("Vercel API returned invalid JSON") from error
    if not isinstance(value, dict):
        raise RolloutFailure("Vercel API response must be an object")
    return value


def list_production_deployments(token: str, project_id: str, team_id: str) -> list[dict[str, Any]]:
    query = urllib.parse.urlencode({"projectId": project_id, "target": "production", "limit": "100", "teamId": team_id})
    payload = request_json("GET", f"https://api.vercel.com/v6/deployments?{query}", token)
    deployments = payload.get("deployments", [])
    return deployments if isinstance(deployments, list) else []


def deployment_state(deployment: dict[str, Any]) -> str:
    return str(deployment.get("readyState") or deployment.get("state") or "")


def deployment_sha(deployment: dict[str, Any]) -> str:
    meta = deployment.get("meta")
    return str(meta.get("githubCommitSha") or "") if isinstance(meta, dict) else ""


def find_exact(deployments: list[dict[str, Any]], release_sha: str, deployment_id: str | None = None) -> dict[str, Any] | None:
    matches = []
    for deployment in deployments:
        if deployment_sha(deployment) != release_sha or deployment.get("target") != "production":
            continue
        if deployment_id and str(deployment.get("uid") or deployment.get("id") or "") != deployment_id:
            continue
        matches.append(deployment)
    matches.sort(key=lambda item: int(item.get("createdAt") or item.get("created") or 0))
    return matches[-1] if matches else None


def deploy_exact_sha(token: str, project_id: str, team_id: str, release_sha: str) -> dict[str, Any]:
    existing = find_exact(list_production_deployments(token, project_id, team_id), release_sha)
    if existing and deployment_state(existing) == "READY":
        return existing

    project = request_json("GET", f"https://api.vercel.com/v9/projects/{project_id}?teamId={urllib.parse.quote(team_id)}", token)
    project_name = str(project.get("name") or "")
    if not project_name:
        raise RolloutFailure("Vercel project name is missing")

    created = request_json(
        "POST",
        f"https://api.vercel.com/v13/deployments?teamId={urllib.parse.quote(team_id)}",
        token,
        {
            "name": project_name,
            "project": project_id,
            "target": "production",
            "gitSource": {"type": "github", "org": "snadaiapp-png", "repo": "SNAD", "ref": "main", "sha": release_sha},
            "gitMetadata": {
                "remoteUrl": "https://github.com/snadaiapp-png/SNAD",
                "commitRef": "main",
                "commitSha": release_sha,
                "dirty": "false",
                "ci": "true",
                "ciType": "github-actions",
            },
            "meta": {"githubCommitSha": release_sha, "remediation": "REM-P0-002"},
        },
    )
    deployment_id = str(created.get("id") or created.get("uid") or "")
    if not deployment_id:
        raise RolloutFailure("Vercel did not return a deployment ID")

    deadline = time.monotonic() + 25 * 60
    while time.monotonic() < deadline:
        deployment = find_exact(list_production_deployments(token, project_id, team_id), release_sha, deployment_id)
        if deployment:
            state = deployment_state(deployment)
            if state == "READY":
                return deployment
            if state in {"ERROR", "CANCELED"}:
                raise RolloutFailure(f"Vercel deployment failed with state {state}")
        time.sleep(10)
    raise RolloutFailure("Vercel deployment did not become READY within the rollout deadline")


def fetch_public_json(url: str, timeout: int = 30) -> dict[str, Any]:
    request = urllib.request.Request(url, headers={"Accept": "application/json", "User-Agent": "SANAD-REM-P0-002-Rollout/1.0"})
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            if response.status != 200:
                raise RolloutFailure(f"runtime check returned HTTP {response.status}")
            body = response.read()
    except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError, OSError) as error:
        raise RolloutFailure(f"runtime check failed: {type(error).__name__}") from error
    try:
        value = json.loads(body.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise RolloutFailure("runtime check returned invalid JSON") from error
    if not isinstance(value, dict):
        raise RolloutFailure("runtime check response must be an object")
    return value


def verify_backend_routing(base_url: str, release_sha: str) -> None:
    deadline = time.monotonic() + 3 * 60
    last: dict[str, Any] | None = None
    while time.monotonic() < deadline:
        try:
            last = fetch_public_json(f"{base_url}/api/system/backend-status")
            if last.get("configured") is True and last.get("reachable") is True and last.get("statusCode") == 200:
                break
        except RolloutFailure:
            pass
        time.sleep(5)
    if not last or last.get("configured") is not True or last.get("reachable") is not True or last.get("statusCode") != 200:
        raise RolloutFailure("production backend routing did not satisfy configured/reachable/200")
    evidence = {"release_sha": release_sha, **last, "result": "PASS"}
    (EVIDENCE / "rem-p0-002-backend-routing.json").write_text(json.dumps(evidence, indent=2, sort_keys=True), encoding="utf-8")


def run_synthetic(base_url: str) -> None:
    env = os.environ.copy()
    env["PRODUCTION_WEB_BASE_URL"] = base_url
    env["AUTH_SYNTHETIC_ITERATIONS"] = "5"
    env["AUTH_SYNTHETIC_EVIDENCE_PATH"] = str(EVIDENCE / "rem-p0-002-initial-synthetic.json")
    completed = subprocess.run([sys.executable, "scripts/operations/bff_auth_session_synthetic.py"], env=env, check=False)
    if completed.returncode != 0:
        raise RolloutFailure("initial five-journey production authentication synthetic failed")


def main() -> int:
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    release_sha = required("GITHUB_SHA")
    base_url = required("PRODUCTION_WEB_BASE_URL").rstrip("/")
    summary: dict[str, Any] = {"schema_version": "1.0", "finding": "REM-P0-002", "release_sha": release_sha, "status": "running"}
    try:
        token = required("VERCEL_TOKEN")
        project_id = required("VERCEL_PROJECT_ID")
        team_id = required("VERCEL_TEAM_ID")
        required("AUTH_SMOKE_TENANT_ID")
        required("AUTH_SMOKE_EMAIL")
        required("AUTH_SMOKE_PASSWORD")

        deployment = deploy_exact_sha(token, project_id, team_id, release_sha)
        deployment_id = str(deployment.get("uid") or deployment.get("id") or "")
        deployment_url = str(deployment.get("url") or "")
        if deployment_sha(deployment) != release_sha or deployment_state(deployment) != "READY" or deployment.get("target") != "production":
            raise RolloutFailure("exact Vercel production deployment metadata did not pass")

        deployment_evidence = {
            "schema_version": "1.0",
            "finding": "REM-P0-002",
            "release_sha": release_sha,
            "deployment_id": deployment_id,
            "deployment_url": deployment_url,
            "target": deployment.get("target"),
            "ready_state": deployment_state(deployment),
            "deployed_commit_sha": deployment_sha(deployment),
            "result": "PASS",
        }
        (EVIDENCE / "rem-p0-002-vercel-deployment.json").write_text(json.dumps(deployment_evidence, indent=2, sort_keys=True), encoding="utf-8")

        verify_backend_routing(base_url, release_sha)
        run_synthetic(base_url)

        summary.update({"status": "passed", "deployment_id": deployment_id, "deployment_url": deployment_url, "production_url": base_url, "initial_synthetic_iterations": 5})
        SUMMARY.write_text(json.dumps(summary, indent=2, sort_keys=True), encoding="utf-8")
        print("REM-P0-002 EXACT-SHA PRODUCTION ROLLOUT PASSED")
        print(f"ReleaseSHA={release_sha} DeploymentID={deployment_id}")
        return 0
    except (RolloutFailure, ValueError) as error:
        summary.update({"status": "failed", "failure": str(error)})
        SUMMARY.write_text(json.dumps(summary, indent=2, sort_keys=True), encoding="utf-8")
        print(f"REM-P0-002 PRODUCTION ROLLOUT FAILURE: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
