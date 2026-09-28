#!/usr/bin/env python3
"""One-shot G2 credential reconciliation through the production application API."""

import json
import os
import sys
import urllib.error
import urllib.request


def required(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise RuntimeError(f"{name} is required")
    return value


def request(method: str, url: str, payload=None, token=None):
    data = None if payload is None else json.dumps(payload).encode()
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            body = response.read().decode()
            return response.status, json.loads(body) if body else {}
    except urllib.error.HTTPError as exc:
        body = exc.read().decode()
        try:
            parsed = json.loads(body) if body else {}
        except json.JSONDecodeError:
            parsed = {}
        return exc.code, parsed


def login(auth_api, tenant_id, email, password):
    return request("POST", f"{auth_api}/login", {
        "email": email,
        "password": password,
        "tenantId": tenant_id,
    })


def main() -> int:
    base_url = required("BASE_URL").rstrip("/")
    tenant_id = required("G2_TENANT_ID")
    admin_email = required("G2_ADMIN_EMAIL")
    admin_password = required("G2_ADMIN_PASSWORD")
    identities = [
        ("EMPLOYEE", required("G2_EMPLOYEE_EMAIL"), required("G2_EMPLOYEE_PASSWORD")),
        ("MANAGER", required("G2_MANAGER_EMAIL"), required("G2_MANAGER_PASSWORD")),
        ("HR", required("G2_HR_EMAIL"), required("G2_HR_PASSWORD")),
    ]
    auth_api = f"{base_url}/api/v1/auth"
    api = f"{base_url}/api/v1"

    status, admin = login(auth_api, tenant_id, admin_email, admin_password)
    if status != 200 or not admin.get("accessToken"):
        raise RuntimeError(f"tenant administrator login failed: HTTP {status}")
    if admin.get("user", {}).get("tenantId") != tenant_id:
        raise RuntimeError("tenant administrator binding mismatch")
    access_token = admin["accessToken"]

    status, me = request("GET", f"{auth_api}/me", token=access_token)
    if status != 200 or "USER.WRITE" not in me.get("capabilities", []):
        raise RuntimeError("tenant administrator lacks USER.WRITE")

    resolved = []
    for label, email, password in identities:
        status, users = request("GET", f"{api}/users?tenantId={tenant_id}&email={email}", token=access_token)
        if status != 200 or not isinstance(users, list):
            raise RuntimeError(f"{label} user lookup failed: HTTP {status}")
        matches = [u for u in users if u.get("email") == email and u.get("status") == "ACTIVE"]
        if not matches:
            raise RuntimeError(f"{label} ACTIVE identity not found in governed tenant")
        resolved.append((label, email, password, matches[0]["id"]))

    for label, email, password, user_id in resolved:
        status, body = login(auth_api, tenant_id, email, password)
        already_reconciled = (
            status == 200
            and body.get("accessToken")
            and body.get("user", {}).get("tenantId") == tenant_id
            and body.get("credentialRotationRequired") is False
        )
        if not already_reconciled:
            status, _ = request(
                "POST",
                f"{auth_api}/admin-reconcile-credential/{user_id}",
                {"credential": password},
                access_token,
            )
            if status != 204:
                raise RuntimeError(f"{label} reconciliation failed: HTTP {status}")

    for label, email, password, _ in resolved:
        status, body = login(auth_api, tenant_id, email, password)
        if status != 200:
            raise RuntimeError(f"{label} LOGIN_HTTP={status}")
        if not body.get("accessToken"):
            raise RuntimeError(f"{label} access token missing")
        if body.get("user", {}).get("tenantId") != tenant_id:
            raise RuntimeError(f"{label} tenant binding mismatch")
        if body.get("user", {}).get("status") != "ACTIVE":
            raise RuntimeError(f"{label} account not ACTIVE")
        if body.get("credentialRotationRequired") is not False:
            raise RuntimeError(f"{label} credential rotation remains required")
        print(f"{label}: LOGIN_HTTP=200 TENANT_BINDING=PASS STATUS=ACTIVE ROTATION_REQUIRED=false")

    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
