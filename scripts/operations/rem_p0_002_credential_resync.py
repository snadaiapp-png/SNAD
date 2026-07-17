#!/usr/bin/env python3
"""One-time, fail-closed production credential resynchronization for REM-P0-002."""
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
FAILURE_STATES = {"build_failed", "update_failed", "canceled", "deactivated"}
ACTIVE_STATES = {"created", "queued", "build_in_progress", "update_in_progress", "pre_deploy_in_progress"}


class ResyncFailure(RuntimeError):
    pass


def required(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise ResyncFailure(f"missing required environment value: {name}")
    return value


class RenderClient:
    def __init__(self, api_key: str, service_id: str) -> None:
        self.api_key = api_key
        self.base = f"https://api.render.com/v1/services/{urllib.parse.quote(service_id)}"

    def request(self, method: str, path: str, payload: dict[str, Any] | None = None, timeout: int = 60) -> tuple[int, Any]:
        data = None if payload is None else json.dumps(payload, separators=(",", ":")).encode("utf-8")
        headers = {"Authorization": f"Bearer {self.api_key}", "Accept": "application/json"}
        if data is not None:
            headers["Content-Type"] = "application/json"
        request = urllib.request.Request(f"{self.base}{path}", data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:
                status = response.status
                raw = response.read()
        except urllib.error.HTTPError as error:
            status = error.code
            raw = error.read()
        except (urllib.error.URLError, TimeoutError, OSError) as error:
            raise ResyncFailure(f"Render API network failure during {method} {path}: {type(error).__name__}") from error
        if not raw:
            body: Any = None
        else:
            try:
                body = json.loads(raw.decode("utf-8"))
            except (UnicodeDecodeError, json.JSONDecodeError):
                body = {"invalidJson": True}
        return status, body

    @staticmethod
    def error_summary(body: Any) -> str:
        if isinstance(body, dict):
            error = body.get("error")
            if isinstance(error, dict):
                return f"{error.get('code', '')} {error.get('message', '')}".strip()
        return ""

    def put_variable(self, key: str, value: str) -> None:
        status, body = self.request("PUT", f"/env-vars/{urllib.parse.quote(key)}", {"value": value}, timeout=30)
        if status not in {200, 201}:
            raise ResyncFailure(f"Render env update failed for {key}: HTTP {status} {self.error_summary(body)}")

    def list_deploys(self) -> list[dict[str, Any]]:
        status, body = self.request("GET", "/deploys?limit=50", timeout=30)
        if status != 200 or not isinstance(body, list):
            raise ResyncFailure(f"Render deploy list failed: HTTP {status} {self.error_summary(body)}")
        result: list[dict[str, Any]] = []
        for item in body:
            if isinstance(item, dict):
                deploy = item.get("deploy", item)
                if isinstance(deploy, dict):
                    result.append(deploy)
        return result

    def latest_deploy_id(self) -> str:
        deploys = sorted(self.list_deploys(), key=lambda item: str(item.get("createdAt") or ""), reverse=True)
        return str(deploys[0].get("id") or "") if deploys else ""

    def trigger_deploy(self) -> str:
        before_id = self.latest_deploy_id()
        status, body = self.request("POST", "/deploys", {}, timeout=60)
        if status == 201 and isinstance(body, dict):
            deploy_id = str(body.get("id") or (body.get("deploy") or {}).get("id") or "")
            if deploy_id:
                return deploy_id
            raise ResyncFailure("Render 201 response did not contain a deployment ID")
        if status != 202:
            raise ResyncFailure(f"Render deploy trigger failed: HTTP {status} {self.error_summary(body)}")

        deadline = time.monotonic() + 25 * 60
        while time.monotonic() < deadline:
            deploys = sorted(self.list_deploys(), key=lambda item: str(item.get("createdAt") or ""), reverse=True)
            for deploy in deploys:
                deploy_id = str(deploy.get("id") or "")
                if deploy_id and deploy_id != before_id:
                    return deploy_id
            time.sleep(10)
        raise ResyncFailure("queued Render deployment did not become discoverable")

    def wait_live(self, deploy_id: str) -> None:
        deadline = time.monotonic() + 30 * 60
        while time.monotonic() < deadline:
            for deploy in self.list_deploys():
                if str(deploy.get("id") or "") != deploy_id:
                    continue
                status = str(deploy.get("status") or "")
                if status == "live":
                    return
                if status in FAILURE_STATES:
                    raise ResyncFailure(f"Render deployment {deploy_id} failed with state {status}")
                break
            time.sleep(10)
        raise ResyncFailure(f"Render deployment {deploy_id} did not become live")

    def deploy_and_wait(self) -> str:
        deploy_id = self.trigger_deploy()
        self.wait_live(deploy_id)
        return deploy_id


def wait_backend(base_url: str) -> dict[str, Any]:
    deadline = time.monotonic() + 5 * 60
    last: dict[str, Any] | None = None
    while time.monotonic() < deadline:
        request = urllib.request.Request(
            f"{base_url}/api/system/backend-status",
            headers={"Accept": "application/json", "User-Agent": "SANAD-REM-P0-002-Resync/2.0"},
        )
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                body = json.loads(response.read().decode("utf-8"))
                if isinstance(body, dict):
                    last = body
                    if body.get("configured") is True and body.get("reachable") is True and body.get("statusCode") == 200:
                        return body
        except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError, OSError, json.JSONDecodeError):
            pass
        time.sleep(5)
    raise ResyncFailure(f"backend routing did not recover; last={last}")


def run_synthetic(base_url: str) -> None:
    env = os.environ.copy()
    env["PRODUCTION_WEB_BASE_URL"] = base_url
    env["AUTH_SYNTHETIC_ITERATIONS"] = "5"
    env["AUTH_SYNTHETIC_EVIDENCE_PATH"] = str(EVIDENCE / "rem-p0-002-post-resync-synthetic-v3.json")
    result = subprocess.run([sys.executable, "scripts/operations/bff_auth_session_synthetic.py"], env=env, check=False)
    if result.returncode != 0:
        raise ResyncFailure("five production authentication journeys failed after credential synchronization")


def write_json(name: str, value: dict[str, Any]) -> None:
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    (EVIDENCE / name).write_text(json.dumps(value, indent=2, sort_keys=True), encoding="utf-8")


def main() -> int:
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    client = RenderClient(required("RENDER_API_KEY"), required("RENDER_SERVICE_ID"))
    tenant_id = required("AUTH_SMOKE_TENANT_ID")
    email = required("AUTH_SMOKE_EMAIL")
    password = required("AUTH_SMOKE_PASSWORD")
    base_url = required("PRODUCTION_WEB_BASE_URL").rstrip("/")

    reset_deploy_id = ""
    cleanup_deploy_id = ""
    cleanup_success = False
    primary_error: Exception | None = None
    try:
        values = {
            "SANAD_SECURITY_BOOTSTRAP_ENABLED": "true",
            "SANAD_SECURITY_BOOTSTRAP_FORCE_RESET": "true",
            "SANAD_SECURITY_BOOTSTRAP_TENANT_ID": tenant_id,
            "SANAD_SECURITY_BOOTSTRAP_ADMIN_EMAIL": email,
            "SANAD_SECURITY_BOOTSTRAP_ADMIN_PASSWORD": password,
            "SANAD_SECURITY_BOOTSTRAP_ADMIN_DISPLAY_NAME": "SANAD Synthetic Operator",
            "SANAD_SECURITY_BOOTSTRAP_AUDIT_ACTOR": "rem-p0-002-credential-resync-v3",
        }
        for key, value in values.items():
            client.put_variable(key, value)
        reset_deploy_id = client.deploy_and_wait()
        write_json(
            "rem-p0-002-credential-resync-v3.json",
            {
                "finding": "REM-P0-002",
                "operation": "credential-bootstrap-force-reset",
                "deployId": reset_deploy_id,
                "tenantId": tenant_id,
                "auditActor": "rem-p0-002-credential-resync-v3",
                "result": "PASS",
            },
        )
    except Exception as error:  # cleanup must run for any failure
        primary_error = error
    finally:
        cleanup_errors: list[str] = []
        for key in ("SANAD_SECURITY_BOOTSTRAP_ENABLED", "SANAD_SECURITY_BOOTSTRAP_FORCE_RESET"):
            try:
                client.put_variable(key, "false")
            except Exception as error:
                cleanup_errors.append(f"{key}:{error}")
        if not cleanup_errors:
            try:
                cleanup_deploy_id = client.deploy_and_wait()
                cleanup_success = True
            except Exception as error:
                cleanup_errors.append(str(error))
        write_json(
            "rem-p0-002-bootstrap-cleanup-v3.json",
            {
                "bootstrapEnabled": False,
                "forceReset": False,
                "cleanupDeployId": cleanup_deploy_id or "UNKNOWN",
                "cleanupDeployLive": cleanup_success,
                "cleanupErrors": cleanup_errors,
            },
        )
        if cleanup_errors and primary_error is None:
            primary_error = ResyncFailure("bootstrap cleanup failed: " + "; ".join(cleanup_errors))

    if primary_error is not None:
        print(f"REM-P0-002 CREDENTIAL RESYNC FAILURE: {primary_error}", file=sys.stderr)
        return 1

    routing = wait_backend(base_url)
    write_json("rem-p0-002-post-resync-routing-v3.json", {**routing, "result": "PASS"})
    run_synthetic(base_url)
    write_json(
        "rem-p0-002-resync-summary-v3.json",
        {
            "finding": "REM-P0-002",
            "status": "passed",
            "resetDeployId": reset_deploy_id,
            "cleanupDeployId": cleanup_deploy_id,
            "bootstrapDisabled": True,
            "syntheticIterations": 5,
        },
    )
    print("REM-P0-002 PRODUCTION CREDENTIAL RESYNCHRONIZATION PASSED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
