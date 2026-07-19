#!/usr/bin/env python3
"""Fail-closed production readiness probe for SNAD.

This probe verifies the complete production chain:
Vercel UI -> integration status -> BFF -> backend authentication -> backend Actuator.
It uses only the Python standard library so it can run locally and in GitHub Actions.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

DEFAULT_PRODUCTION_URL = "https://snad-app.vercel.app"
DEFAULT_ATTEMPTS = 18
DEFAULT_DELAY_SECONDS = 10.0
DEFAULT_TIMEOUT_SECONDS = 15.0


@dataclass(frozen=True)
class ProbeResult:
    name: str
    url: str
    expected: str
    actual: str
    passed: bool
    status_code: int | None
    checked_at: str
    detail: str | None = None


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def normalize_https_url(value: str) -> str:
    parsed = urllib.parse.urlparse(value.strip())
    if parsed.scheme != "https" or not parsed.netloc or parsed.username or parsed.password:
        raise ValueError("URL must be absolute HTTPS without embedded credentials")
    return value.rstrip("/")


def host_with_optional_port(url: str) -> str:
    parsed = urllib.parse.urlparse(url)
    if not parsed.hostname:
        raise ValueError("URL does not contain a hostname")
    return parsed.hostname if not parsed.port else f"{parsed.hostname}:{parsed.port}"


def request(url: str, timeout: float) -> tuple[int, bytes, dict[str, str]]:
    headers = {
        "Accept": "application/json,text/html;q=0.9,*/*;q=0.8",
        "User-Agent": "SNAD-Production-Readiness/1.0",
    }
    hostname = urllib.parse.urlparse(url).hostname or ""
    if "ngrok" in hostname.lower():
        headers["ngrok-skip-browser-warning"] = "true"

    req = urllib.request.Request(url, headers=headers, method="GET")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            return response.status, response.read(), {key.lower(): value for key, value in response.headers.items()}
    except urllib.error.HTTPError as error:
        return error.code, error.read(), {key.lower(): value for key, value in error.headers.items()}


def decode_json(body: bytes, name: str) -> dict[str, Any]:
    try:
        value = json.loads(body.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ValueError(f"{name} returned invalid JSON: {error}") from error
    if not isinstance(value, dict):
        raise ValueError(f"{name} returned a non-object JSON payload")
    return value


def result(
    *,
    name: str,
    url: str,
    expected: str,
    actual: str,
    passed: bool,
    status_code: int | None,
    detail: str | None = None,
) -> ProbeResult:
    return ProbeResult(
        name=name,
        url=url,
        expected=expected,
        actual=actual,
        passed=passed,
        status_code=status_code,
        checked_at=utc_now(),
        detail=detail,
    )


def run_once(production_url: str, timeout: float) -> list[ProbeResult]:
    checks: list[ProbeResult] = []

    root_url = f"{production_url}/"
    status, body, _ = request(root_url, timeout)
    html = body.decode("utf-8", errors="replace")
    required_markers = ("SNAD", "سند", 'lang="ar"', 'dir="rtl"')
    missing_markers = [marker for marker in required_markers if marker not in html]
    ui_passed = status == 200 and not missing_markers
    checks.append(
        result(
            name="production-ui",
            url=root_url,
            expected="HTTP 200 with SNAD/سند and Arabic RTL markers",
            actual=f"HTTP {status}; missing markers: {missing_markers or 'none'}",
            passed=ui_passed,
            status_code=status,
        )
    )
    if not ui_passed:
        return checks

    integration_url = f"{production_url}/api/system/backend-status"
    status, body, _ = request(integration_url, timeout)
    integration: dict[str, Any] = {}
    integration_error: str | None = None
    try:
        integration = decode_json(body, "backend-status")
    except ValueError as error:
        integration_error = str(error)

    integration_passed = (
        status == 200
        and integration_error is None
        and integration.get("configured") is True
        and integration.get("reachable") is True
        and integration.get("statusCode") == 200
        and "targetHost" not in integration
    )
    checks.append(
        result(
            name="frontend-backend-integration",
            url=integration_url,
            expected="HTTP 200; configured=true; reachable=true; statusCode=200; targetHost absent",
            actual=(
                f"HTTP {status}; configured={integration.get('configured')}; "
                f"reachable={integration.get('reachable')}; statusCode={integration.get('statusCode')}; "
                f"targetHostExposed={'targetHost' in integration}"
            ),
            passed=integration_passed,
            status_code=status,
            detail=integration_error,
        )
    )
    if not integration_passed:
        return checks

    auth_url = f"{production_url}/api/platform/api/v1/auth/me"
    status, body, _ = request(auth_url, timeout)
    auth_passed = status == 401
    checks.append(
        result(
            name="bff-authentication-chain",
            url=auth_url,
            expected="HTTP 401 without a session",
            actual=f"HTTP {status}",
            passed=auth_passed,
            status_code=status,
            detail=body.decode("utf-8", errors="replace")[:500] or None,
        )
    )
    if not auth_passed:
        return checks

    expected_host = os.environ.get("SNAD_BACKEND_EXPECTED_HOST", "").strip().lower()
    if not expected_host:
        checks.append(
            result(
                name="backend-host-policy",
                url=integration_url,
                expected="SNAD_BACKEND_EXPECTED_HOST configured",
                actual="Expected backend host is not configured",
                passed=False,
                status_code=None,
            )
        )
        return checks

    override_health_url = os.environ.get("SNAD_BACKEND_HEALTH_URL", "").strip()
    health_url = override_health_url or f"https://{expected_host}/actuator/health"

    try:
        health_url = normalize_https_url(health_url)
        health_host = host_with_optional_port(health_url).lower()
        host_passed = health_host == expected_host
        checks.append(
            result(
                name="backend-host-policy",
                url=health_url,
                expected=f"approved health host={expected_host}",
                actual=f"health host={health_host}",
                passed=host_passed,
                status_code=None,
            )
        )
        if not host_passed:
            return checks

        status, body, _ = request(health_url, timeout)
        health_error: str | None = None
        health: dict[str, Any] = {}
        try:
            health = decode_json(body, "backend Actuator health")
        except ValueError as error:
            health_error = str(error)
        checks.append(
            result(
                name="backend-actuator-health",
                url=health_url,
                expected="HTTP 200 with status=UP",
                actual=f"HTTP {status}; status={health.get('status')}",
                passed=status == 200 and health_error is None and health.get("status") == "UP",
                status_code=status,
                detail=health_error,
            )
        )
    except ValueError as error:
        checks.append(
            result(
                name="backend-host-policy",
                url=health_url,
                expected=f"approved HTTPS health host={expected_host}",
                actual=type(error).__name__,
                passed=False,
                status_code=None,
                detail=str(error),
            )
        )
    except (urllib.error.URLError, TimeoutError, OSError) as error:
        checks.append(
            result(
                name="backend-actuator-health",
                url=health_url,
                expected="Approved HTTPS host; HTTP 200 with status=UP",
                actual=type(error).__name__,
                passed=False,
                status_code=None,
                detail=str(error),
            )
        )

    return checks


def write_evidence(path: Path, production_url: str, attempt: int, checks: list[ProbeResult]) -> None:
    payload = {
        "schemaVersion": 1,
        "productionUrl": production_url,
        "attempt": attempt,
        "generatedAt": utc_now(),
        "passed": all(check.passed for check in checks),
        "checks": [asdict(check) for check in checks],
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def print_checks(attempt: int, checks: list[ProbeResult]) -> None:
    print(f"\nSNAD production readiness attempt {attempt}")
    for check in checks:
        marker = "PASS" if check.passed else "FAIL"
        print(f"[{marker}] {check.name}: {check.actual} (expected: {check.expected})")
        if check.detail and not check.passed:
            print(f"       detail: {check.detail}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--production-url",
        default=os.environ.get("SNAD_PRODUCTION_URL", DEFAULT_PRODUCTION_URL),
        help="SNAD production base URL (HTTPS)",
    )
    parser.add_argument("--attempts", type=int, default=DEFAULT_ATTEMPTS)
    parser.add_argument("--delay-seconds", type=float, default=DEFAULT_DELAY_SECONDS)
    parser.add_argument("--timeout-seconds", type=float, default=DEFAULT_TIMEOUT_SECONDS)
    parser.add_argument(
        "--evidence-file",
        type=Path,
        default=Path("production-readiness-evidence.json"),
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.attempts < 1:
        print("--attempts must be at least 1", file=sys.stderr)
        return 2
    if args.delay_seconds < 0 or args.timeout_seconds <= 0:
        print("Delay must be non-negative and timeout must be positive", file=sys.stderr)
        return 2

    try:
        production_url = normalize_https_url(args.production_url)
    except ValueError as error:
        print(str(error), file=sys.stderr)
        return 2

    last_checks: list[ProbeResult] = []
    for attempt in range(1, args.attempts + 1):
        try:
            last_checks = run_once(production_url, args.timeout_seconds)
        except (urllib.error.URLError, TimeoutError, OSError, ValueError) as error:
            last_checks = [
                result(
                    name="production-readiness-probe",
                    url=production_url,
                    expected="All production checks complete",
                    actual=type(error).__name__,
                    passed=False,
                    status_code=None,
                    detail=str(error),
                )
            ]

        print_checks(attempt, last_checks)
        write_evidence(args.evidence_file, production_url, attempt, last_checks)

        if all(check.passed for check in last_checks):
            print(f"\nSNAD production readiness gate passed. Evidence: {args.evidence_file}")
            return 0

        if attempt < args.attempts:
            time.sleep(args.delay_seconds)

    print(f"\nSNAD production readiness gate failed. Evidence: {args.evidence_file}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
