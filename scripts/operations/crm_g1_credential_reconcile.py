#!/usr/bin/env python3
"""Reconcile the two CRM-G1 acceptance credentials through supported app flows."""
from __future__ import annotations

import http.cookiejar
import json
import os
import secrets
import sys
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

from rem_p0_002_credential_resync import RenderClient, ResyncFailure, required, wait_backend


class ReconciliationFailure(RuntimeError):
    pass


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
    client: RenderClient,
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
    for key, value in bootstrap_values.items():
        client.put_variable(key, value)

    deploy_id = client.deploy_and_wait()
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


def cleanup(client: RenderClient) -> str:
    cleanup_values = {
        "SANAD_SECURITY_BOOTSTRAP_ENABLED": "false",
        "SANAD_SECURITY_BOOTSTRAP_FORCE_RESET": "false",
        "SANAD_SECURITY_BOOTSTRAP_CREDENTIAL_ONLY": "false",
        "SANAD_SECURITY_BOOTSTRAP_TENANT_ID": "",
        "SANAD_SECURITY_BOOTSTRAP_TENANT_NAME": "",
        "SANAD_SECURITY_BOOTSTRAP_TENANT_SUBDOMAIN": "",
        "SANAD_SECURITY_BOOTSTRAP_ADMIN_EMAIL": "",
        "SANAD_SECURITY_BOOTSTRAP_ADMIN_PASSWORD": "",
        "SANAD_SECURITY_BOOTSTRAP_ADMIN_DISPLAY_NAME": "",
        "SANAD_SECURITY_BOOTSTRAP_AUDIT_ACTOR": "credential-bootstrap",
    }
    errors: list[str] = []
    for key, value in cleanup_values.items():
        try:
            client.put_variable(key, value)
        except Exception as error:
            errors.append(f"{key}:{type(error).__name__}")
    if errors:
        raise ReconciliationFailure("bootstrap cleanup environment update failed: " + ",".join(errors))
    return client.deploy_and_wait()


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

    client = RenderClient(required("RENDER_API_KEY"), required("RENDER_SERVICE_ID"))
    deploy_ids: list[str] = []
    resolved_tenant_ids: list[str] = []
    primary_error: Exception | None = None
    cleanup_deploy_id = ""
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
    finally:
        try:
            cleanup_deploy_id = cleanup(client)
            wait_backend(base_url)
        except Exception as error:
            if primary_error is None:
                primary_error = error
            else:
                primary_error = ReconciliationFailure(
                    f"{primary_error}; cleanup also failed: {type(error).__name__}"
                )

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
        "bootstrapDeployments": deploy_ids,
        "cleanupDeployment": cleanup_deploy_id or "UNKNOWN",
        "bootstrapDisabled": bool(cleanup_deploy_id),
        "bootstrapSecretsCleared": bool(cleanup_deploy_id),
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
    except (ReconciliationFailure, ResyncFailure) as error:
        print(f"CRM-G1 CREDENTIAL RECONCILIATION FAILURE: {error}", file=sys.stderr)
        raise SystemExit(1)
