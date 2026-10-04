#!/usr/bin/env python3
"""Reconcile SNAD Vercel Production to the exact current main SHA.

This is an incident-recovery control. It creates a production deployment from
GitHub main using an immutable SHA, verifies Vercel deployment metadata, then
proves the public production alias serves the same release and the critical HRM
G3 routes. It fails closed on any identity or route mismatch.
"""

from __future__ import annotations

import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any


class ReconcileFailure(RuntimeError):
    pass


def required(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise ReconcileFailure(f"missing required environment value: {name}")
    return value


def request_json(
    method: str,
    url: str,
    token: str | None = None,
    payload: dict[str, Any] | None = None,
    timeout: int = 60,
) -> dict[str, Any]:
    data = None if payload is None else json.dumps(payload, separators=(",", ":")).encode("utf-8")
    headers = {"Accept": "application/json", "User-Agent": "SNAD-Vercel-Main-Reconcile/1.0"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if data is not None:
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            body = response.read()
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")[:800]
        raise ReconcileFailure(f"{method} {url} failed HTTP {error.code}: {detail}") from error
    except (urllib.error.URLError, TimeoutError, OSError) as error:
        raise ReconcileFailure(f"{method} {url} failed: {type(error).__name__}") from error
    try:
        value = json.loads(body.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ReconcileFailure(f"{method} {url} returned invalid JSON") from error
    if not isinstance(value, dict):
        raise ReconcileFailure(f"{method} {url} returned a non-object JSON payload")
    return value


def fetch_text(url: str, timeout: int = 45) -> tuple[int, str, dict[str, str]]:
    req = urllib.request.Request(
        url,
        headers={
            "Accept": "application/json,text/html;q=0.9,*/*;q=0.8",
            "User-Agent": "SNAD-Vercel-Main-Reconcile/1.0",
            "Cache-Control": "no-cache",
        },
        method="GET",
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            return (
                response.status,
                response.read().decode("utf-8", errors="replace"),
                {k.lower(): v for k, v in response.headers.items()},
            )
    except urllib.error.HTTPError as error:
        return (
            error.code,
            error.read().decode("utf-8", errors="replace"),
            {k.lower(): v for k, v in error.headers.items()},
        )


def deployment_sha(deployment: dict[str, Any]) -> str:
    meta = deployment.get("meta")
    return str(meta.get("githubCommitSha") or "") if isinstance(meta, dict) else ""


def deployment_state(deployment: dict[str, Any]) -> str:
    return str(deployment.get("readyState") or deployment.get("state") or "")


def create_exact_main_deployment(
    token: str,
    project_id: str,
    team_id: str,
    repository: str,
    release_sha: str,
) -> str:
    project = request_json(
        "GET",
        f"https://api.vercel.com/v9/projects/{urllib.parse.quote(project_id)}?teamId={urllib.parse.quote(team_id)}",
        token,
    )
    project_name = str(project.get("name") or "")
    if not project_name:
        raise ReconcileFailure("Vercel project name is missing")

    owner, repo = repository.split("/", 1)
    created = request_json(
        "POST",
        f"https://api.vercel.com/v13/deployments?teamId={urllib.parse.quote(team_id)}",
        token,
        {
            "name": project_name,
            "project": project_id,
            "target": "production",
            "gitSource": {
                "type": "github",
                "org": owner,
                "repo": repo,
                "ref": "main",
                "sha": release_sha,
            },
            "gitMetadata": {
                "remoteUrl": f"https://github.com/{repository}",
                "commitRef": "main",
                "commitSha": release_sha,
                "dirty": "false",
                "ci": "true",
                "ciType": "github-actions",
            },
            "meta": {
                "githubCommitSha": release_sha,
                "githubCommitRef": "main",
                "incident": "HRM-G3-PRODUCTION-ROUTE-RECOVERY",
            },
        },
    )
    deployment_id = str(created.get("id") or created.get("uid") or "")
    if not deployment_id:
        raise ReconcileFailure("Vercel did not return a deployment ID")
    return deployment_id


def wait_for_deployment(
    token: str,
    team_id: str,
    deployment_id: str,
    release_sha: str,
) -> dict[str, Any]:
    deadline = time.monotonic() + 25 * 60
    while time.monotonic() < deadline:
        deployment = request_json(
            "GET",
            f"https://api.vercel.com/v13/deployments/{urllib.parse.quote(deployment_id)}?teamId={urllib.parse.quote(team_id)}",
            token,
        )
        state = deployment_state(deployment)
        observed_sha = deployment_sha(deployment)
        target = str(deployment.get("target") or "")
        print(
            f"deployment={deployment_id} state={state or 'UNKNOWN'} "
            f"target={target or 'UNKNOWN'} sha={observed_sha or 'UNKNOWN'}"
        )
        if state == "READY":
            if target != "production":
                raise ReconcileFailure(f"deployment target mismatch: {target}")
            if observed_sha != release_sha:
                raise ReconcileFailure(
                    f"deployment SHA mismatch: expected={release_sha} actual={observed_sha or 'UNKNOWN'}"
                )
            return deployment
        if state in {"ERROR", "CANCELED"}:
            raise ReconcileFailure(f"Vercel deployment failed with state {state}")
        time.sleep(10)
    raise ReconcileFailure("Vercel deployment did not become READY before timeout")


def verify_public_release(base_url: str, release_sha: str) -> dict[str, Any]:
    deadline = time.monotonic() + 5 * 60
    last: dict[str, Any] | None = None
    while time.monotonic() < deadline:
        try:
            last = request_json("GET", f"{base_url}/api/system/release", timeout=30)
            if (
                last.get("commitSha") == release_sha
                and last.get("commitRef") == "main"
                and last.get("environment") in {"production", "prod"}
            ):
                return last
        except ReconcileFailure:
            pass
        time.sleep(5)
    raise ReconcileFailure(
        "production release identity did not converge to exact main "
        f"(last={json.dumps(last, sort_keys=True) if last else 'none'})"
    )


def verify_routes(base_url: str) -> list[dict[str, Any]]:
    paths = (
        "/",
        "/hr/employees",
        "/hr/assignments",
        "/hr/performance/goals",
        "/hr/performance/reviews",
    )
    soft_404_markers = ("page not found", "does not exist or has been moved")
    evidence: list[dict[str, Any]] = []
    for path in paths:
        status, body, headers = fetch_text(f"{base_url}{path}")
        normalized = body.lower()
        matched_soft_404 = [marker for marker in soft_404_markers if marker in normalized]
        matched_path = headers.get("x-matched-path")
        passed = status == 200 and not matched_soft_404
        evidence.append(
            {
                "path": path,
                "status": status,
                "xMatchedPath": matched_path,
                "soft404Markers": matched_soft_404,
                "passed": passed,
            }
        )
        print(
            f"route={path} status={status} "
            f"x-matched-path={matched_path or 'none'} soft404={matched_soft_404 or 'none'}"
        )
        if not passed:
            raise ReconcileFailure(f"production route failed after reconcile: {path} HTTP {status}")
    return evidence


def main() -> int:
    evidence_path = Path(os.environ.get("VERCEL_RECONCILE_EVIDENCE", "vercel-main-reconcile-evidence.json"))
    summary: dict[str, Any] = {"schemaVersion": 1, "result": "FAIL"}
    try:
        release_sha = required("GITHUB_SHA")
        repository = required("GITHUB_REPOSITORY")
        github_ref = required("GITHUB_REF")
        if github_ref != "refs/heads/main":
            raise ReconcileFailure(f"reconcile must execute from refs/heads/main, got {github_ref}")

        token = required("VERCEL_TOKEN")
        project_id = required("VERCEL_PROJECT_ID")
        team_id = required("VERCEL_TEAM_ID")
        base_url = os.environ.get("PRODUCTION_WEB_BASE_URL", "https://snad-app.vercel.app").rstrip("/")

        deployment_id = create_exact_main_deployment(token, project_id, team_id, repository, release_sha)
        deployment = wait_for_deployment(token, team_id, deployment_id, release_sha)
        release = verify_public_release(base_url, release_sha)
        routes = verify_routes(base_url)

        summary = {
            "schemaVersion": 1,
            "result": "PASS",
            "releaseSha": release_sha,
            "deploymentId": deployment_id,
            "deploymentUrl": deployment.get("url"),
            "deploymentTarget": deployment.get("target"),
            "deploymentState": deployment_state(deployment),
            "deploymentSha": deployment_sha(deployment),
            "productionUrl": base_url,
            "releaseIdentity": release,
            "routes": routes,
        }
        evidence_path.parent.mkdir(parents=True, exist_ok=True)
        evidence_path.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        print("SNAD VERCEL MAIN PRODUCTION RECONCILE = PASS")
        return 0
    except (ReconcileFailure, ValueError) as error:
        summary["failure"] = str(error)
        evidence_path.parent.mkdir(parents=True, exist_ok=True)
        evidence_path.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        print(f"SNAD VERCEL MAIN PRODUCTION RECONCILE = FAIL: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
