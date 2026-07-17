#!/usr/bin/env python3
"""Synthetic login, refresh, identity and logout journey through the SANAD BFF."""
from __future__ import annotations

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


class SyntheticFailure(RuntimeError):
    pass


def require_env(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise SyntheticFailure(f"missing required environment value: {name}")
    return value


def validated_base_url(raw: str) -> str:
    value = raw.rstrip("/")
    parsed = urllib.parse.urlparse(value)
    if parsed.scheme != "https" or not parsed.netloc or parsed.username or parsed.password:
        raise SyntheticFailure("PRODUCTION_WEB_BASE_URL must be a credential-free HTTPS origin")
    if parsed.path not in ("", "/") or parsed.query or parsed.fragment:
        raise SyntheticFailure("PRODUCTION_WEB_BASE_URL must not include a path, query or fragment")
    return value


def request_json(
    opener: urllib.request.OpenerDirector,
    method: str,
    url: str,
    *,
    origin: str,
    payload: dict[str, Any] | None = None,
    access_token: str | None = None,
    timeout_seconds: float = 25.0,
) -> tuple[int, dict[str, str], bytes, float]:
    data = None if payload is None else json.dumps(payload, separators=(",", ":")).encode("utf-8")
    headers = {
        "Accept": "application/json",
        "Origin": origin,
        "X-Request-Id": f"auth-synthetic-{uuid.uuid4()}",
        "User-Agent": "SANAD-BFF-Auth-Synthetic/1.0",
    }
    if data is not None:
        headers["Content-Type"] = "application/json"
    if access_token:
        headers["Authorization"] = f"Bearer {access_token}"

    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    started = time.monotonic()
    try:
        with opener.open(request, timeout=timeout_seconds) as response:
            body = response.read()
            elapsed_ms = round((time.monotonic() - started) * 1000, 2)
            return response.status, {key.lower(): value for key, value in response.headers.items()}, body, elapsed_ms
    except urllib.error.HTTPError as error:
        body = error.read()
        elapsed_ms = round((time.monotonic() - started) * 1000, 2)
        return error.code, {key.lower(): value for key, value in error.headers.items()}, body, elapsed_ms
    except (urllib.error.URLError, TimeoutError, OSError) as error:
        elapsed_ms = round((time.monotonic() - started) * 1000, 2)
        raise SyntheticFailure(f"network failure after {elapsed_ms}ms: {type(error).__name__}") from error


def parse_json(body: bytes, step: str) -> dict[str, Any]:
    try:
        value = json.loads(body.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise SyntheticFailure(f"{step}: response was not valid JSON") from error
    if not isinstance(value, dict):
        raise SyntheticFailure(f"{step}: response JSON must be an object")
    return value


def expect_status(step: str, actual: int, expected: int) -> None:
    if actual != expected:
        raise SyntheticFailure(f"{step}: expected HTTP {expected}, received HTTP {actual}")


def record_step(
    evidence: dict[str, Any],
    iteration: int,
    name: str,
    status: int,
    elapsed_ms: float,
    headers: dict[str, str],
) -> None:
    evidence["steps"].append(
        {
            "iteration": iteration,
            "name": name,
            "status": status,
            "duration_ms": elapsed_ms,
            "request_id": headers.get("x-request-id") or headers.get("x-correlation-id"),
            "bff_attempts": headers.get("x-sanad-bff-attempts"),
            "bff_error": headers.get("x-sanad-bff-error"),
        }
    )


def main() -> int:
    output_path = Path(os.environ.get("AUTH_SYNTHETIC_EVIDENCE_PATH", "auth-session-synthetic-evidence.json"))
    evidence: dict[str, Any] = {
        "schema_version": "1.0",
        "status": "running",
        "started_at_epoch": int(time.time()),
        "steps": [],
    }

    try:
        base_url = validated_base_url(require_env("PRODUCTION_WEB_BASE_URL"))
        tenant_id = require_env("AUTH_SMOKE_TENANT_ID")
        email = require_env("AUTH_SMOKE_EMAIL")
        password = require_env("AUTH_SMOKE_PASSWORD")
        iterations = int(os.environ.get("AUTH_SYNTHETIC_ITERATIONS", "1"))
        if iterations < 1 or iterations > 5:
            raise SyntheticFailure("AUTH_SYNTHETIC_ITERATIONS must be between 1 and 5")

        origin = base_url
        auth_base = f"{base_url}/api/platform/api/v1/auth"
        evidence["target_host"] = urllib.parse.urlparse(base_url).netloc
        evidence["iterations"] = iterations

        for iteration in range(1, iterations + 1):
            cookie_jar = http.cookiejar.CookieJar()
            opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cookie_jar))

            status, headers, _, elapsed = request_json(
                opener,
                "GET",
                f"{auth_base}/me",
                origin=origin,
            )
            record_step(evidence, iteration, "anonymous-me", status, elapsed, headers)
            expect_status("anonymous-me", status, 401)

            status, headers, body, elapsed = request_json(
                opener,
                "POST",
                f"{auth_base}/login",
                origin=origin,
                payload={"email": email, "password": password, "tenantId": tenant_id},
            )
            record_step(evidence, iteration, "login", status, elapsed, headers)
            expect_status("login", status, 200)
            login = parse_json(body, "login")
            access_token = login.get("accessToken")
            user = login.get("user")
            if not isinstance(access_token, str) or not access_token:
                raise SyntheticFailure("login: accessToken missing")
            if not isinstance(user, dict) or user.get("tenantId") != tenant_id:
                raise SyntheticFailure("login: tenant binding mismatch")
            if not any(cookie.name == "sanad_refresh" and cookie.value for cookie in cookie_jar):
                raise SyntheticFailure("login: HttpOnly refresh cookie was not stored")

            status, headers, body, elapsed = request_json(
                opener,
                "GET",
                f"{auth_base}/me",
                origin=origin,
                access_token=access_token,
            )
            record_step(evidence, iteration, "authenticated-me", status, elapsed, headers)
            expect_status("authenticated-me", status, 200)
            me = parse_json(body, "authenticated-me")
            if me.get("tenantId") != tenant_id or me.get("status") != "ACTIVE":
                raise SyntheticFailure("authenticated-me: identity or status mismatch")

            status, headers, body, elapsed = request_json(
                opener,
                "POST",
                f"{auth_base}/refresh",
                origin=origin,
                payload={},
            )
            record_step(evidence, iteration, "refresh", status, elapsed, headers)
            expect_status("refresh", status, 200)
            refresh = parse_json(body, "refresh")
            rotated_access_token = refresh.get("accessToken")
            if not isinstance(rotated_access_token, str) or not rotated_access_token:
                raise SyntheticFailure("refresh: rotated accessToken missing")

            status, headers, _, elapsed = request_json(
                opener,
                "GET",
                f"{auth_base}/me",
                origin=origin,
                access_token=rotated_access_token,
            )
            record_step(evidence, iteration, "restored-me", status, elapsed, headers)
            expect_status("restored-me", status, 200)

            status, headers, _, elapsed = request_json(
                opener,
                "POST",
                f"{auth_base}/logout",
                origin=origin,
                access_token=rotated_access_token,
            )
            record_step(evidence, iteration, "logout", status, elapsed, headers)
            expect_status("logout", status, 204)

            if any(cookie.name == "sanad_refresh" and not cookie.is_expired() for cookie in cookie_jar):
                raise SyntheticFailure("logout: refresh cookie remained active")

            status, headers, _, elapsed = request_json(
                opener,
                "POST",
                f"{auth_base}/refresh",
                origin=origin,
                payload={},
            )
            record_step(evidence, iteration, "post-logout-refresh", status, elapsed, headers)
            expect_status("post-logout-refresh", status, 401)

        evidence["status"] = "passed"
        evidence["completed_at_epoch"] = int(time.time())
        output_path.write_text(json.dumps(evidence, indent=2, sort_keys=True), encoding="utf-8")
        print("REM-P0-002 BFF AUTH SESSION SYNTHETIC PASSED")
        print(f"Iterations={iterations} Steps={len(evidence['steps'])}")
        return 0
    except (SyntheticFailure, ValueError) as error:
        evidence["status"] = "failed"
        evidence["completed_at_epoch"] = int(time.time())
        evidence["failure"] = str(error)
        output_path.write_text(json.dumps(evidence, indent=2, sort_keys=True), encoding="utf-8")
        print(f"REM-P0-002 SYNTHETIC FAILURE: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
