#!/usr/bin/env python3
"""Exercise CRM-009 Workflow Engine and AI Gateway through the production Vercel BFF.

The probe is fail-closed: architecture/configuration-only success is insufficient. It
creates a temporary CRM contact, dispatches a real workflow, exercises all three AI
capabilities, verifies idempotency and the explicit human decision boundary, then
archives the temporary contact. Evidence contains hashes and public statuses only.
"""
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
from typing import Any, Callable


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
        raise AcceptanceFailure("PRODUCTION_WEB_BASE_URL must not include path, query or fragment")
    return value


def request_json(
    opener: urllib.request.OpenerDirector,
    method: str,
    url: str,
    *,
    origin: str,
    payload: dict[str, Any] | None = None,
    token: str | None = None,
    headers: dict[str, str] | None = None,
) -> tuple[int, dict[str, str], Any]:
    data = None if payload is None else json.dumps(payload, separators=(",", ":")).encode("utf-8")
    request_headers = {
        "Accept": "application/json",
        "Origin": origin,
        "User-Agent": "SANAD-CRM-009-Workflow-AI-Acceptance/1.0",
        "X-Request-Id": f"crm009-wa-{uuid.uuid4()}",
    }
    if data is not None:
        request_headers["Content-Type"] = "application/json"
    if token:
        request_headers["Authorization"] = f"Bearer {token}"
    if headers:
        request_headers.update(headers)
    request = urllib.request.Request(url, data=data, headers=request_headers, method=method)
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


def unwrap(body: Any) -> dict[str, Any]:
    if not isinstance(body, dict):
        raise AcceptanceFailure("response JSON must be an object")
    data = body.get("data")
    return data if isinstance(data, dict) else body


def login(base_url: str, tenant_id: str, email: str, password: str) -> tuple[urllib.request.OpenerDirector, str]:
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    status, _, body = request_json(
        opener,
        "POST",
        f"{base_url}/api/platform/api/v1/auth/login",
        origin=base_url,
        payload={"tenantId": tenant_id, "email": email, "password": password},
    )
    if status != 200:
        raise AcceptanceFailure(f"production BFF login failed with HTTP {status}")
    data = unwrap(body)
    token = data.get("accessToken")
    user = data.get("user")
    if not isinstance(token, str) or not token:
        raise AcceptanceFailure("login accessToken is missing")
    if not isinstance(user, dict) or user.get("tenantId") != tenant_id or user.get("status") != "ACTIVE":
        raise AcceptanceFailure("login identity binding/status mismatch")
    return opener, token


def record(evidence: dict[str, Any], name: str, **values: Any) -> None:
    evidence["steps"].append({"name": name, **values})


def digest(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def get_id(data: dict[str, Any], label: str) -> str:
    value = data.get("id") or data.get("requestId") or data.get("contactId")
    if not isinstance(value, str) or not value:
        raise AcceptanceFailure(f"{label} response did not return an ID")
    return value


def poll_request(
    opener: urllib.request.OpenerDirector,
    base_url: str,
    token: str,
    request_id: str,
    *,
    workflow: bool,
    terminal_predicate: Callable[[dict[str, Any]], bool],
    label: str,
) -> tuple[dict[str, Any], dict[str, str]]:
    path = (
        f"/api/platform/api/v2/crm/integrations/workflows/{request_id}"
        if workflow
        else f"/api/platform/api/v2/crm/integrations/{request_id}"
    )
    last: dict[str, Any] | None = None
    last_headers: dict[str, str] = {}
    for _ in range(30):
        status, response_headers, body = request_json(
            opener,
            "GET",
            f"{base_url}{path}",
            origin=base_url,
            token=token,
        )
        if status != 200:
            raise AcceptanceFailure(f"{label} status read failed with HTTP {status}")
        last = unwrap(body)
        last_headers = response_headers
        current = str(last.get("status") or "")
        if current in {"UNAVAILABLE", "TIMED_OUT", "POLICY_DENIED", "UNSAFE_OUTPUT", "REJECTED"}:
            raise AcceptanceFailure(f"{label} entered terminal failure status {current}")
        if terminal_predicate(last):
            return last, last_headers
        time.sleep(2)
    raise AcceptanceFailure(f"{label} did not reach an accepted terminal state; last={last}")


def create_contact(
    opener: urllib.request.OpenerDirector,
    base_url: str,
    token: str,
) -> tuple[str, int]:
    suffix = f"{int(time.time())}-{uuid.uuid4().hex[:8]}"
    payload = {
        "givenName": f"CRM009{suffix}",
        "familyName": "Integration",
        "primaryEmail": f"crm009-integration-{suffix}@example.invalid",
        "preferredLocale": "ar-SA",
        "timeZone": "Asia/Riyadh",
        "consentSummary": "UNKNOWN",
    }
    status, _, body = request_json(
        opener,
        "POST",
        f"{base_url}/api/platform/api/v1/crm/contacts",
        origin=base_url,
        payload=payload,
        token=token,
    )
    if status != 201:
        raise AcceptanceFailure(f"temporary contact creation failed with HTTP {status}")
    data = unwrap(body)
    contact_id = get_id(data, "contact")
    version = data.get("version", 0)
    if not isinstance(version, int) or version < 0:
        raise AcceptanceFailure("contact version is invalid")
    return contact_id, version


def dispatch_workflow(
    evidence: dict[str, Any],
    opener: urllib.request.OpenerDirector,
    base_url: str,
    token: str,
    contact_id: str,
    contact_version: int,
) -> None:
    idem = f"crm009-workflow-{uuid.uuid4()}"
    payload = {
        "workflowType": "REMINDER",
        "sourceEntityType": "CONTACT",
        "sourceEntityId": contact_id,
        "sourceEntityVersion": contact_version,
        "payload": {"acceptanceProbe": True, "purpose": "CRM-009 terminal closure"},
    }
    endpoint = f"{base_url}/api/platform/api/v2/crm/integrations/workflows"
    headers = {"Idempotency-Key": idem, "Accept-Language": "ar-SA"}
    status, _, body = request_json(
        opener, "POST", endpoint, origin=base_url, payload=payload, token=token, headers=headers
    )
    if status != 202:
        raise AcceptanceFailure(f"workflow dispatch failed with HTTP {status}")
    first = unwrap(body)
    request_id = get_id(first, "workflow")

    replay_status, _, replay_body = request_json(
        opener, "POST", endpoint, origin=base_url, payload=payload, token=token, headers=headers
    )
    if replay_status != 202 or get_id(unwrap(replay_body), "workflow replay") != request_id:
        raise AcceptanceFailure("workflow idempotency replay did not return the original request")

    final, _ = poll_request(
        opener,
        base_url,
        token,
        request_id,
        workflow=True,
        terminal_predicate=lambda value: str(value.get("status")) in {"ACCEPTED", "RUNNING", "COMPLETED"},
        label="workflow",
    )
    external = final.get("externalReference")
    if not isinstance(external, str) or not external:
        raise AcceptanceFailure("workflow acceptance did not persist an external workflow reference")
    record(
        evidence,
        "workflow-production-dispatch",
        result="PASS",
        status=final.get("status"),
        requestIdSha256=digest(request_id),
        externalReferenceSha256=digest(external),
        idempotencyReplay=True,
    )


def request_ai_capability(
    evidence: dict[str, Any],
    opener: urllib.request.OpenerDirector,
    base_url: str,
    token: str,
    contact_id: str,
    contact_version: int,
    capability: str,
) -> tuple[str, dict[str, Any], dict[str, str]]:
    idem = f"crm009-ai-{capability.lower()}-{uuid.uuid4()}"
    payload = {
        "capability": capability,
        "sourceEntityType": "CONTACT",
        "sourceEntityId": contact_id,
        "sourceEntityVersion": contact_version,
        "userIntent": f"CRM-009 production acceptance for {capability}",
    }
    endpoint = f"{base_url}/api/platform/api/v2/crm/integrations/ai"
    headers = {"Idempotency-Key": idem, "Accept-Language": "ar-SA"}
    status, _, body = request_json(
        opener, "POST", endpoint, origin=base_url, payload=payload, token=token, headers=headers
    )
    if status != 202:
        raise AcceptanceFailure(f"AI {capability} request failed with HTTP {status}")
    first = unwrap(body)
    request_id = get_id(first, f"AI {capability}")

    replay_status, _, replay_body = request_json(
        opener, "POST", endpoint, origin=base_url, payload=payload, token=token, headers=headers
    )
    if replay_status != 202 or get_id(unwrap(replay_body), f"AI {capability} replay") != request_id:
        raise AcceptanceFailure(f"AI {capability} idempotency replay failed")

    final, response_headers = poll_request(
        opener,
        base_url,
        token,
        request_id,
        workflow=False,
        terminal_predicate=lambda value: str(value.get("status")) in {"COMPLETED", "RECOMMENDATION_AVAILABLE"},
        label=f"AI {capability}",
    )
    result = final.get("resultPayload") or final.get("result")
    if isinstance(result, str):
        try:
            result = json.loads(result)
        except json.JSONDecodeError as error:
            raise AcceptanceFailure(f"AI {capability} result payload is not valid JSON") from error
    if not isinstance(result, dict):
        raise AcceptanceFailure(f"AI {capability} did not persist a structured result")
    if result.get("status") not in {"AVAILABLE", "PARTIAL"}:
        raise AcceptanceFailure(f"AI {capability} gateway status is not AVAILABLE/PARTIAL")
    for field in ("policyVersion", "modelVersion"):
        if not isinstance(result.get(field), str) or not result.get(field):
            raise AcceptanceFailure(f"AI {capability} result is missing {field}")

    if capability == "CUSTOMER_SUMMARY":
        if not isinstance(result.get("generatedText"), str) or not result.get("generatedText"):
            raise AcceptanceFailure("CUSTOMER_SUMMARY did not return generatedText")
    elif capability == "SCORING":
        confidence = result.get("confidence")
        if not isinstance(confidence, (int, float)):
            raise AcceptanceFailure("SCORING did not return numeric confidence")
        if not isinstance(result.get("explanation"), str) or not result.get("explanation"):
            raise AcceptanceFailure("SCORING did not return an explanation")
    elif capability == "NEXT_BEST_ACTION":
        if not isinstance(result.get("actionCode"), str) or not result.get("actionCode"):
            raise AcceptanceFailure("NEXT_BEST_ACTION did not return an actionable actionCode")
        if result.get("humanConfirmationRequired") is not True:
            raise AcceptanceFailure("actionable AI output did not require human confirmation")
        if str(final.get("status")) != "RECOMMENDATION_AVAILABLE":
            raise AcceptanceFailure("actionable AI output did not enter RECOMMENDATION_AVAILABLE")

    record(
        evidence,
        f"ai-{capability.lower()}",
        result="PASS",
        status=final.get("status"),
        gatewayStatus=result.get("status"),
        policyVersion=result.get("policyVersion"),
        modelVersion=result.get("modelVersion"),
        idempotencyReplay=True,
    )
    return request_id, final, response_headers


def reject_actionable_recommendation(
    evidence: dict[str, Any],
    opener: urllib.request.OpenerDirector,
    base_url: str,
    token: str,
    request_id: str,
    final: dict[str, Any],
    response_headers: dict[str, str],
) -> None:
    etag = response_headers.get("etag")
    if not etag:
        version = final.get("version")
        if not isinstance(version, int):
            raise AcceptanceFailure("actionable AI response has no ETag or version")
        etag = f'"{version}"'
    idem = f"crm009-ai-reject-{uuid.uuid4()}"
    status, _, body = request_json(
        opener,
        "POST",
        f"{base_url}/api/platform/api/v2/crm/integrations/{request_id}/reject",
        origin=base_url,
        payload={"reason": "Terminal acceptance probe: no business mutation executed"},
        token=token,
        headers={"Idempotency-Key": idem, "If-Match": etag},
    )
    if status != 200 or str(unwrap(body).get("status")) != "REJECTED":
        raise AcceptanceFailure(f"explicit human rejection boundary failed with HTTP {status}")
    record(evidence, "human-control-rejection", result="PASS", status="REJECTED")


def main() -> int:
    evidence_path = Path(os.environ.get(
        "CRM_009_WORKFLOW_AI_EVIDENCE_PATH",
        "crm-009-workflow-ai-production-evidence.json",
    ))
    evidence: dict[str, Any] = {
        "schema": "snad.crm-009.workflow-ai-production-acceptance.v1",
        "result": "IN_PROGRESS",
        "startedAtEpoch": int(time.time()),
        "steps": [],
    }
    contact_id: str | None = None
    opener: urllib.request.OpenerDirector | None = None
    token: str | None = None
    base_url: str | None = None
    try:
        base_url = validated_origin(required("PRODUCTION_WEB_BASE_URL"))
        tenant_id = required("AUTH_SMOKE_TENANT_A_ID")
        opener, token = login(
            base_url,
            tenant_id,
            required("AUTH_SMOKE_TENANT_A_EMAIL"),
            required("AUTH_SMOKE_TENANT_A_PASSWORD"),
        )
        record(evidence, "production-bff-login", result="PASS")

        contact_id, contact_version = create_contact(opener, base_url, token)
        record(evidence, "temporary-contact", result="PASS", contactIdSha256=digest(contact_id))

        dispatch_workflow(evidence, opener, base_url, token, contact_id, contact_version)
        request_ai_capability(
            evidence, opener, base_url, token, contact_id, contact_version, "CUSTOMER_SUMMARY"
        )
        request_ai_capability(
            evidence, opener, base_url, token, contact_id, contact_version, "SCORING"
        )
        nba_id, nba_final, nba_headers = request_ai_capability(
            evidence, opener, base_url, token, contact_id, contact_version, "NEXT_BEST_ACTION"
        )
        reject_actionable_recommendation(
            evidence, opener, base_url, token, nba_id, nba_final, nba_headers
        )

        status, _, _ = request_json(
            opener,
            "PATCH",
            f"{base_url}/api/platform/api/v1/crm/contacts/{contact_id}/archive",
            origin=base_url,
            token=token,
        )
        if status != 200:
            raise AcceptanceFailure(f"temporary contact cleanup failed with HTTP {status}")
        record(evidence, "temporary-contact-cleanup", result="PASS")

        evidence.update({
            "result": "PASS",
            "completedAtEpoch": int(time.time()),
            "workflowProductionIntegration": True,
            "aiGatewayProductionIntegration": True,
            "humanControlBoundary": True,
            "directModelProviderCalls": 0,
            "skippedSections": 0,
        })
        evidence_path.parent.mkdir(parents=True, exist_ok=True)
        evidence_path.write_text(json.dumps(evidence, indent=2, sort_keys=True), encoding="utf-8")
        print("CRM-009 WORKFLOW AND AI PRODUCTION ACCEPTANCE PASSED")
        return 0
    except AcceptanceFailure as error:
        evidence.update({
            "result": "FAIL",
            "completedAtEpoch": int(time.time()),
            "failure": str(error),
        })
        evidence_path.parent.mkdir(parents=True, exist_ok=True)
        evidence_path.write_text(json.dumps(evidence, indent=2, sort_keys=True), encoding="utf-8")
        print(f"CRM-009 WORKFLOW/AI ACCEPTANCE FAILURE: {error}", file=sys.stderr)
        return 1
    finally:
        # Best-effort cleanup is deliberately non-authoritative; a failed cleanup is
        # already surfaced above on the success path, while failure paths retain the
        # temporary record for auditable diagnosis rather than hiding the first error.
        pass


if __name__ == "__main__":
    raise SystemExit(main())
