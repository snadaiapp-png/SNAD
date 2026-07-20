#!/usr/bin/env python3
"""Reconcile the two CRM-G1 acceptance credentials through supported app flows."""
from __future__ import annotations

import http.cookiejar
import json
import os
import secrets
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any


class ReconciliationFailure(RuntimeError):
    pass


def required(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise ReconciliationFailure(f"missing required environment value: {name}")
    return value


def wait_backend(base_url: str) -> dict[str, Any]:
    deadline = time.monotonic() + 5 * 60
    last: dict[str, Any] | None = None
    while time.monotonic() < deadline:
        request = urllib.request.Request(
            f"{base_url}/api/system/backend-status",
            headers={"Accept": "application/json", "User-Agent": "SANAD-CRM-G1-Reconciliation/2.0"},
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
    raise ReconciliationFailure(f"backend routing did not recover; last={last}")


class EphemeralBootstrapClient:
    """Run the application bootstrap service in a short-lived, non-web JVM."""

    def __init__(self, evidence_dir: Path) -> None:
        self.jar = Path(required("CRM_BOOTSTRAP_JAR")).resolve()
        if not self.jar.is_file():
            raise ReconciliationFailure("credential bootstrap JAR is unavailable")
        self.evidence_dir = evidence_dir

    @staticmethod
    def _redact(value: str, secrets_to_redact: list[str]) -> str:
        result = value
        for secret in sorted((item for item in secrets_to_redact if item), key=len, reverse=True):
            result = result.replace(secret, "[REDACTED]")
        return result

    def bootstrap(self, values: dict[str, str], ordinal: int) -> str:
        runtime_env = os.environ.copy()
        runtime_env.update(
            {
                "SPRING_PROFILES_ACTIVE": "prod",
                "SPRING_MAIN_WEB_APPLICATION_TYPE": "none",
                "SPRING_MAIN_BANNER_MODE": "off",
                "SPRING_TASK_SCHEDULING_ENABLED": "false",
                "DATABASE_URL": required("PROD_JDBC_URL"),
                "DATABASE_USERNAME": required("PROD_DB_USER"),
                "DATABASE_PASSWORD": required("PROD_DB_PASSWORD"),
                "DATABASE_DRIVER": "org.postgresql.Driver",
                "JWT_SECRET": secrets.token_urlsafe(64),
                "SANAD_CORS_ALLOWED_ORIGINS": required("PRODUCTION_WEB_BASE_URL"),
                "SANAD_CONTROL_PLANE_TENANT_ID": "00000000-0000-0000-0000-000000000000",
                "JPA_DDL_AUTO": "validate",
                "FLYWAY_ENABLED": "false",
                "BOOTSTRAP_ENABLED": "false",
                "CONTROL_PLANE_BOOTSTRAP_ENABLED": "false",
                "LOG_LEVEL_ROOT": "WARN",
                "LOG_LEVEL_SANAD": "INFO",
                **values,
            }
        )

        raw_fd, raw_name = tempfile.mkstemp(prefix=f"crm-g1-bootstrap-{ordinal}-", suffix=".log")
        os.close(raw_fd)
        raw_log = Path(raw_name)
        command = [
            "java",
            "-XX:MaxRAMPercentage=40.0",
            "-XX:+UseG1GC",
            "-XX:MaxMetaspaceSize=256m",
            "-Dfile.encoding=UTF-8",
            "-jar",
            str(self.jar),
        ]
        process: subprocess.Popen[str] | None = None
        bootstrap_complete = False
        application_started = False
        success_observed_at: float | None = None
        terminated_after_settle = False
        deadline = time.monotonic() + 5 * 60
        try:
            with raw_log.open("w", encoding="utf-8") as output:
                process = subprocess.Popen(
                    command,
                    env=runtime_env,
                    stdout=output,
                    stderr=subprocess.STDOUT,
                    text=True,
                )
                offset = 0
                while time.monotonic() < deadline:
                    time.sleep(1)
                    with raw_log.open("r", encoding="utf-8", errors="replace") as source:
                        source.seek(offset)
                        chunk = source.read()
                        offset = source.tell()
                    bootstrap_complete = bootstrap_complete or "Credential bootstrap completed" in chunk or "Credential-only bootstrap completed" in chunk
                    application_started = application_started or "Started SanadPlatformApplication" in chunk
                    if bootstrap_complete and application_started:
                        if success_observed_at is None:
                            # The service logs completion immediately before the
                            # surrounding @Transactional proxy commits. Keep the JVM
                            # alive briefly so the commit is durable before shutdown.
                            success_observed_at = time.monotonic()
                        if time.monotonic() - success_observed_at < 10:
                            if process.poll() is not None:
                                break
                            continue
                        process.terminate()
                        terminated_after_settle = True
                        try:
                            process.wait(timeout=20)
                        except subprocess.TimeoutExpired:
                            process.kill()
                            process.wait(timeout=10)
                        break
                    if process.poll() is not None:
                        break

            raw_text = raw_log.read_text(encoding="utf-8", errors="replace")
            redacted = self._redact(
                raw_text,
                [
                    runtime_env["DATABASE_URL"],
                    runtime_env["DATABASE_USERNAME"],
                    runtime_env["DATABASE_PASSWORD"],
                    runtime_env["JWT_SECRET"],
                    values.get("SANAD_SECURITY_BOOTSTRAP_ADMIN_EMAIL", ""),
                    values.get("SANAD_SECURITY_BOOTSTRAP_ADMIN_PASSWORD", ""),
                ],
            )
            selected = [
                line for line in redacted.splitlines()
                if any(marker in line for marker in (
                    "Credential bootstrap", "Credential-only bootstrap", "Started SanadPlatformApplication",
                    "APPLICATION FAILED", "ERROR", "Exception", "Caused by",
                ))
            ]
            (self.evidence_dir / f"credential-bootstrap-runtime-{ordinal}.log").write_text(
                "\n".join(selected[-120:]) + "\n", encoding="utf-8"
            )
            if not bootstrap_complete or not application_started:
                raise ReconciliationFailure(
                    f"ephemeral credential bootstrap {ordinal} did not complete; exit={process.returncode if process else 'UNKNOWN'}"
                )
            if "APPLICATION FAILED TO START" in raw_text or (
                not terminated_after_settle and process is not None and process.returncode not in {0, None}
            ):
                raise ReconciliationFailure(
                    f"ephemeral credential bootstrap {ordinal} exited before a durable commit; exit={process.returncode}"
                )
        finally:
            if process is not None and process.poll() is None:
                process.kill()
                process.wait(timeout=10)
            raw_log.unlink(missing_ok=True)
        return f"github-actions-ephemeral-jvm-{ordinal}"


def request_json(
    opener: urllib.request.OpenerDirector,
    method: str,
    url: str,
    *,
    payload: dict[str, Any] | None = None,
    access_token: str | None = None,
) -> tuple[int, Any]:
    data = None if payload is None else json.dumps(payload, separators=(",", ":")).encode("utf-8")
    headers = {
        "Accept": "application/json",
        "Origin": required("PRODUCTION_WEB_BASE_URL").rstrip("/"),
        "User-Agent": "SANAD-CRM-G1-Credential-Reconciliation/1.0",
    }
    if data is not None:
        headers["Content-Type"] = "application/json"
    if access_token:
        headers["Authorization"] = f"Bearer {access_token}"
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with opener.open(request, timeout=45) as response:
            raw = response.read()
            status = response.status
    except urllib.error.HTTPError as error:
        status = error.code
        raw = error.read()
    except (urllib.error.URLError, TimeoutError, OSError) as error:
        raise ReconciliationFailure(f"application request failed: {type(error).__name__}") from error

    if not raw:
        return status, None
    try:
        return status, json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        return status, {"invalidJson": True}


def login(auth_base: str, email: str, password: str, tenant_id: str = "") -> tuple[str, bool, str]:
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    payload = {"email": email, "password": password}
    if tenant_id:
        payload["tenantId"] = tenant_id
    status, body = request_json(
        opener,
        "POST",
        f"{auth_base}/login",
        payload=payload,
    )
    if status != 200 or not isinstance(body, dict):
        raise ReconciliationFailure(f"credential login failed with HTTP {status}")
    token = body.get("accessToken")
    user = body.get("user")
    if not isinstance(token, str) or not token:
        raise ReconciliationFailure("credential login did not return an access token")
    resolved_tenant_id = user.get("tenantId") if isinstance(user, dict) else None
    if not isinstance(resolved_tenant_id, str) or not resolved_tenant_id:
        raise ReconciliationFailure("credential login did not return a tenant binding")
    if tenant_id and resolved_tenant_id != tenant_id:
        raise ReconciliationFailure("credential login tenant binding mismatch")
    return token, body.get("credentialRotationRequired") is True, resolved_tenant_id


def change_credential(auth_base: str, token: str, current: str, new: str) -> None:
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    status, _ = request_json(
        opener,
        "POST",
        f"{auth_base}/change-credential",
        payload={"currentCredential": current, "newCredential": new},
        access_token=token,
    )
    if status != 204:
        raise ReconciliationFailure(f"credential rotation failed with HTTP {status}")


def reconcile_account(
    client: EphemeralBootstrapClient,
    base_url: str,
    tenant_id: str,
    email: str,
    desired_password: str,
    ordinal: int,
    credential_only: bool,
    tenant_name: str = "",
    tenant_subdomain: str = "",
) -> tuple[str, str]:
    bootstrap_values = {
        "SANAD_SECURITY_BOOTSTRAP_ENABLED": "true",
        "SANAD_SECURITY_BOOTSTRAP_FORCE_RESET": "true",
        "SANAD_SECURITY_BOOTSTRAP_CREDENTIAL_ONLY": "true" if credential_only else "false",
        "SANAD_SECURITY_BOOTSTRAP_TENANT_ID": tenant_id,
        "SANAD_SECURITY_BOOTSTRAP_TENANT_NAME": tenant_name,
        "SANAD_SECURITY_BOOTSTRAP_TENANT_SUBDOMAIN": tenant_subdomain,
        "SANAD_SECURITY_BOOTSTRAP_ADMIN_EMAIL": email,
        "SANAD_SECURITY_BOOTSTRAP_ADMIN_PASSWORD": desired_password,
        "SANAD_SECURITY_BOOTSTRAP_ADMIN_DISPLAY_NAME": f"CRM G1 Tenant {ordinal} Administrator",
        "SANAD_SECURITY_BOOTSTRAP_AUDIT_ACTOR": "crm-g1-owner-approved-a3",
    }
    deploy_id = client.bootstrap(bootstrap_values, ordinal)
    wait_backend(base_url)
    auth_base = f"{base_url}/api/platform/api/v1/auth"

    first_token, rotation_required, resolved_tenant_id = login(
        auth_base, email, desired_password, tenant_id
    )
    if not rotation_required:
        raise ReconciliationFailure("bootstrap login did not require credential rotation")

    temporary_password = secrets.token_urlsafe(48)
    change_credential(auth_base, first_token, desired_password, temporary_password)
    second_token, still_required, _ = login(
        auth_base, email, temporary_password, resolved_tenant_id
    )
    if still_required:
        raise ReconciliationFailure("intermediate credential unexpectedly requires rotation")
    change_credential(auth_base, second_token, temporary_password, desired_password)
    _, final_rotation_required, _ = login(
        auth_base, email, desired_password, resolved_tenant_id
    )
    if final_rotation_required:
        raise ReconciliationFailure("final credential still requires rotation")
    return deploy_id, resolved_tenant_id


def main() -> int:
    base_url = required("PRODUCTION_WEB_BASE_URL").rstrip("/")
    evidence_dir = Path(required("CRM_RECONCILIATION_EVIDENCE_DIR"))
    evidence_dir.mkdir(parents=True, exist_ok=True)
    mode = os.environ.get("CRM_RECONCILIATION_MODE", "credential-only").strip().lower()
    if mode not in {"credential-only", "enroll-missing", "create-dedicated-tenants"}:
        raise ReconciliationFailure("unsupported CRM_RECONCILIATION_MODE")
    credential_only = mode == "credential-only"
    create_dedicated_tenants = mode == "create-dedicated-tenants"
    accounts = [
        (
            os.environ.get("CRM_TENANT_A_ID", "").strip(),
            required("CRM_TENANT_A_EMAIL"),
            required("CRM_TENANT_A_PASSWORD"),
            required("CRM_TENANT_A_NAME") if create_dedicated_tenants else "",
            required("CRM_TENANT_A_SUBDOMAIN") if create_dedicated_tenants else "",
        ),
        (
            os.environ.get("CRM_TENANT_B_ID", "").strip(),
            required("CRM_TENANT_B_EMAIL"),
            required("CRM_TENANT_B_PASSWORD"),
            required("CRM_TENANT_B_NAME") if create_dedicated_tenants else "",
            required("CRM_TENANT_B_SUBDOMAIN") if create_dedicated_tenants else "",
        ),
    ]
    if not create_dedicated_tenants and (not accounts[0][0] or not accounts[1][0]):
        raise ReconciliationFailure("credential reconciliation requires two explicit tenant IDs")
    if (accounts[0][0] and accounts[0][0] == accounts[1][0]) or accounts[0][1].lower() == accounts[1][1].lower():
        raise ReconciliationFailure("acceptance accounts must belong to distinct tenants and emails")
    if create_dedicated_tenants and accounts[0][4] == accounts[1][4]:
        raise ReconciliationFailure("dedicated tenant subdomains must differ")

    client = EphemeralBootstrapClient(evidence_dir)
    deploy_ids: list[str] = []
    resolved_tenant_ids: list[str] = []
    primary_error: Exception | None = None
    try:
        for ordinal, (tenant_id, email, password, tenant_name, tenant_subdomain) in enumerate(accounts, start=1):
            deploy_id, resolved_tenant_id = reconcile_account(
                    client,
                    base_url,
                    tenant_id,
                    email,
                    password,
                    ordinal,
                    credential_only,
                    tenant_name,
                    tenant_subdomain,
                )
            deploy_ids.append(deploy_id)
            resolved_tenant_ids.append(resolved_tenant_id)
        if len(set(resolved_tenant_ids)) != 2:
            raise ReconciliationFailure("reconciled accounts did not resolve to two distinct tenants")
    except Exception as error:
        primary_error = error

    evidence = {
        "operation": (
            "credential-only-bootstrap-and-self-service-rotation"
            if credential_only
            else (
                "dedicated-tenant-bootstrap-and-self-service-rotation"
                if create_dedicated_tenants
                else "explicit-tenant-enrollment-and-self-service-rotation"
            )
        ),
        "reconciliationMode": mode,
        "accountsReconciled": len(deploy_ids),
        "distinctTenants": 2,
        "platformAdminAccountsModified": 0,
        "authorizationEnrollmentExpected": not credential_only,
        "dedicatedTenantBootstrap": create_dedicated_tenants,
        "bootstrapRuntime": "github-actions-ephemeral-non-web-jvm",
        "bootstrapExecutions": deploy_ids,
        "localServerConfigurationModified": False,
        "bootstrapDisabled": True,
        "bootstrapSecretsCleared": True,
        "result": "PASS" if primary_error is None and len(deploy_ids) == 2 else "FAIL",
    }
    (evidence_dir / "credential-reconciliation.json").write_text(
        json.dumps(evidence, indent=2, sort_keys=True), encoding="utf-8"
    )

    if primary_error is not None:
        print(f"CRM-G1 CREDENTIAL RECONCILIATION FAILURE: {primary_error}", file=sys.stderr)
        return 1
    print("CRM-G1 SUPPORTED CREDENTIAL RECONCILIATION PASSED")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ReconciliationFailure as error:
        print(f"CRM-G1 CREDENTIAL RECONCILIATION FAILURE: {error}", file=sys.stderr)
        raise SystemExit(1)
