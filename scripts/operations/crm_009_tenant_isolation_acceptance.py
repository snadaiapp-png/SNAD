#!/usr/bin/env python3
"""Fail-closed CRM-009 production tenant-isolation acceptance through the Vercel BFF."""
from __future__ import annotations

import hashlib
import http.cookiejar
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path
from typing import Any


class AcceptanceFailure(RuntimeError):
    pass


def required(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise AcceptanceFailure(f"missing required environment value: {name}")
    return value


def validated_origin(raw: str) -> str:
    value = raw.rstrip("/")
    parsed = urllib.parse.urlparse(value)
    if parsed.scheme != "https" or not parsed.netloc or parsed.username or parsed.password:
        raise AcceptanceFailure("PRODUCTION_WEB_BASE_URL must be a credential-free HTTPS origin")
    if parsed.path not in ("", "/") or parsed.query or parsed.fragment:
        raise AcceptanceFailure("PRODUCTION_WEB_BASE_URL must not include a path, query or fragment")
    return value


def request_json(
    opener: urllib.request.OpenerDirector,
    method: str,
    url: str,
    *,
    origin: str,
    payload: dict[str, Any] | None = None,
    token: str | None = None,
) -> tuple[int, dict[str, str], Any]:
    data = None if payload is None else json.dumps(payload, separators=(",", ":")).encode("utf-8")
    headers = {
        "Accept": "application/json",
        "Origin": origin,
        "User-Agent": "SANAD-CRM-009-Tenant-Isolation/1.0",
        "X-Request-Id": f"crm009-isolation-{uuid.uuid4()}",
    }
    if data is not None:
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with opener.open(request, timeout=45) as response:
            raw = response.read()
            status = response.status
            response_headers = {key.lower(): value for key, value in response.headers.items()}
    except urllib.error.HTTPError as error:
        status = error.code
        raw = error.read()
        response_headers = {key.lower(): value for key, value in error.headers.items()}
    except (urllib.error.URLError, TimeoutError, OSError) as error:
        raise AcceptanceFailure(f"network failure: {type(error).__name__}") from error

    if not raw:
        return status, response_headers, None
    try:
        return status, response_headers, json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        return status, response_headers, {"invalidJson": True}


def login(base_url: str, tenant_id: str, email: str, password: str) -> tuple[urllib.request.OpenerDirector, str]:
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    status, _, body = request_json(
        opener,
        "POST",
        f"{base_url}/api/platform/api/v1/auth/login",
        origin=base_url,
        payload={"tenantId": tenant_id, "email": email, "password": password},
    )
    if status != 200 or not isinstance(body, dict):
        raise AcceptanceFailure(f"login failed with HTTP {status}")
    data = body.get("data") if isinstance(body.get("data"), dict) else body
    token = data.get("accessToken") if isinstance(data, dict) else None
    user = data.get("user") if isinstance(data, dict) else None
    resolved_tenant = user.get("tenantId") if isinstance(user, dict) else None
    if not isinstance(token, str) or not token:
        raise AcceptanceFailure("login did not return an access token")
    if resolved_tenant != tenant_id:
        raise AcceptanceFailure("login tenant binding mismatch")
    if isinstance(user, dict) and user.get("status") != "ACTIVE":
        raise AcceptanceFailure("login identity is not ACTIVE")
    return opener, token


def digest(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def main() -> int:
    evidence_path = Path(os.environ.get(
        "CRM_009_TENANT_ISOLATION_EVIDENCE_PATH",
        "crm-009-tenant-isolation-evidence.json",
    ))
    evidence: dict[str, Any] = {
        "schema": "snad.crm-009.tenant-isolation.v1",
        "result": "IN_PROGRESS",
        "startedAtEpoch": int(time.time()),
        "steps": [],
    }
    try:
        base_url = validated_origin(required("PRODUCTION_WEB_BASE_URL"))
        tenant_a = required("AUTH_SMOKE_TENANT_A_ID")
        tenant_b = required("AUTH_SMOKE_TENANT_B_ID")
        if tenant_a == tenant_b:
            raise AcceptanceFailure("acceptance tenant IDs must be distinct")

        opener_a, token_a = login(
            base_url,
            tenant_a,
            required("AUTH_SMOKE_TENANT_A_EMAIL"),
            required("AUTH_SMOKE_TENANT_A_PASSWORD"),
        )
        opener_b, token_b = login(
            base_url,
            tenant_b,
            required("AUTH_SMOKE_TENANT_B_EMAIL"),
            required("AUTH_SMOKE_TENANT_B_PASSWORD"),
        )
        evidence["steps"].append({"name": "two-tenant-login", "result": "PASS"})

        suffix = f"{int(time.time())}-{uuid.uuid4().hex[:8]}"
        contact_payload = {
            "givenName": f"CRM009{suffix}",
            "familyName": "Isolation",
            "primaryEmail": f"crm009-isolation-{suffix}@example.invalid",
            "preferredLocale": "ar-SA",
            "timeZone": "Asia/Riyadh",
            "consentSummary": "UNKNOWN",
        }
        status, _, body = request_json(
            opener_a,
            "POST",
            f"{base_url}/api/platform/api/v1/crm/contacts",
            origin=base_url,
            payload=contact_payload,
            token=token_a,
        )
        if status != 201 or not isinstance(body, dict):
            raise AcceptanceFailure(f"Tenant A contact creation failed with HTTP {status}")
        data = body.get("data") if isinstance(body.get("data"), dict) else body
        contact_id = data.get("id") or data.get("contactId")
        if not isinstance(contact_id, str) or not contact_id:
            raise AcceptanceFailure("contact creation did not return an ID")
        evidence["steps"].append({"name": "tenant-a-create", "http": status, "result": "PASS"})

        status, _, body = request_json(
            opener_a,
            "GET",
            f"{base_url}/api/platform/api/v1/crm/contacts/{contact_id}",
            origin=base_url,
            token=token_a,
        )
        if status != 200 or not isinstance(body, dict):
            raise AcceptanceFailure(f"Tenant A contact read failed with HTTP {status}")
        evidence["steps"].append({"name": "tenant-a-read", "http": status, "result": "PASS"})

        status, _, _ = request_json(
            opener_b,
            "GET",
            f"{base_url}/api/platform/api/v1/crm/contacts/{contact_id}",
            origin=base_url,
            token=token_b,
        )
        if status not in {403, 404}:
            raise AcceptanceFailure(
                f"cross-tenant contact read was not rejected; received HTTP {status}"
            )
        evidence["steps"].append({
            "name": "tenant-b-cross-read-rejected",
            "http": status,
            "result": "PASS",
        })

        status, _, _ = request_json(
            opener_a,
            "PATCH",
            f"{base_url}/api/platform/api/v1/crm/contacts/{contact_id}/archive",
            origin=base_url,
            token=token_a,
        )
        if status != 200:
            raise AcceptanceFailure(f"Tenant A cleanup/archive failed with HTTP {status}")
        evidence["steps"].append({"name": "tenant-a-cleanup", "http": status, "result": "PASS"})

        evidence.update({
            "result": "PASS",
            "completedAtEpoch": int(time.time()),
            "tenantAIdSha256": digest(tenant_a),
            "tenantBIdSha256": digest(tenant_b),
            "contactIdSha256": digest(contact_id),
            "crossTenantReadRejected": True,
            "skippedSections": 0,
        })
        evidence_path.parent.mkdir(parents=True, exist_ok=True)
        evidence_path.write_text(json.dumps(evidence, indent=2, sort_keys=True), encoding="utf-8")
        print("CRM-009 TENANT ISOLATION ACCEPTANCE PASSED")
        return 0
    except AcceptanceFailure as error:
        evidence.update({
            "result": "FAIL",
            "completedAtEpoch": int(time.time()),
            "failure": str(error),
        })
        evidence_path.parent.mkdir(parents=True, exist_ok=True)
        evidence_path.write_text(json.dumps(evidence, indent=2, sort_keys=True), encoding="utf-8")
        print(f"CRM-009 TENANT ISOLATION FAILURE: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
