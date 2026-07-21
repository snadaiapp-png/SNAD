#!/usr/bin/env python3
"""Fail closed when REM-P0-002 BFF/auth/session reliability controls regress."""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


class ValidationError(RuntimeError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValidationError(message)


def text(path: str) -> str:
    target = ROOT / path
    require(target.is_file(), f"missing file: {path}")
    value = target.read_text(encoding="utf-8")
    require(bool(value.strip()), f"empty file: {path}")
    return value


def main() -> int:
    route = text("apps/web/app/api/platform/[...path]/route.ts")
    route_test = text("apps/web/app/api/platform/[...path]/route.test.ts")
    provider = text("apps/web/lib/auth/auth-provider.tsx")
    single_flight = text("apps/web/lib/auth/single-flight.ts")
    single_flight_test = text("apps/web/lib/auth/single-flight.test.ts")
    synthetic = text("scripts/operations/bff_auth_session_synthetic.py")
    synthetic_workflow = text(".github/workflows/bff-auth-session-synthetic.yml")
    runbook = text("docs/operations/reliability/AUTH-SESSION-RELIABILITY.md")
    current = json.loads(text("docs/governance/CURRENT-STATUS.json"))

    for token in (
        "BACKEND_REQUEST_TIMEOUT_MS is an end-to-end BFF budget",
        "RETRYABLE_UPSTREAM_STATUSES",
        "isIdempotent",
        "upstream-timeout",
        "clearRefreshCookie",
        "path === LOGOUT_PATH",
        "path === REFRESH_PATH",
        "x-request-id",
    ):
        require(token in route, f"BFF reliability control missing: {token}")

    for token in (
        "retries one transient 503",
        "does not retry a state-changing login request",
        "clears the local refresh cookie even when upstream logout fails",
        "classifies upstream timeouts as a bounded 504",
    ):
        require(token in route_test, f"BFF regression test missing: {token}")

    require("class SingleFlight" in single_flight, "single-flight implementation missing")
    require("shares one operation across concurrent callers" in single_flight_test, "single-flight concurrency test missing")
    require("releases a failed operation" in single_flight_test, "single-flight recovery test missing")

    for token in (
        "SingleFlight<AuthResponse>",
        "sessionGenerationRef",
        "refreshEnabledRef",
        "Stale session refresh result",
        "runRefresh",
    ):
        require(token in provider, f"session rotation control missing: {token}")

    for token in (
        "anonymous-me",
        "login",
        "authenticated-me",
        "refresh",
        "restored-me",
        "logout",
        "post-logout-refresh",
        "sanad_refresh",
    ):
        require(token in synthetic, f"synthetic journey step missing: {token}")

    require("schedule:" in synthetic_workflow and "cron:" in synthetic_workflow, "hourly schedule missing")
    require("environment: Production" in synthetic_workflow, "Production environment protection missing")

    for token in (
        "DEDICATED_TENANT_ID: ${{ secrets.AUTH_SMOKE_TENANT_A_ID }}",
        "DEDICATED_EMAIL: ${{ secrets.AUTH_SMOKE_TENANT_A_EMAIL }}",
        "DEDICATED_PASSWORD: ${{ secrets.AUTH_SMOKE_TENANT_A_PASSWORD }}",
        "CRM_EMAIL: ${{ secrets.CRM_TENANT_A_EMAIL }}",
        "CRM_PASSWORD: ${{ secrets.CRM_TENANT_A_PASSWORD }}",
        "dedicated_count",
        "Partial AUTH_SMOKE_TENANT_A identity is forbidden",
        "CRM identity must resolve to exactly one active tenant",
        "CRM identity must not be a platform administrator",
        "IDENTITY_SOURCE=\"CRM_LIMITED\"",
        "python3 scripts/operations/bff_auth_session_synthetic.py",
    ):
        require(token in synthetic_workflow, f"atomic least-privilege identity control missing: {token}")

    for forbidden in (
        "BOOTSTRAP_TENANT_ID",
        "CONTROL_PLANE_ADMIN_EMAIL",
        "CONTROL_PLANE_ADMIN_PASSWORD",
        "SANAD_ADMIN_EMAIL",
        "SANAD_ADMIN_PASSWORD",
        "|| secrets.",
        "GITHUB_ENV",
    ):
        require(forbidden not in synthetic_workflow, f"synthetic identity fallback or cross-step secret transport is forbidden: {forbidden}")

    require("platform_admin" in synthetic_workflow, "platform-admin rejection check missing")
    require("actions/upload-artifact@v4" in synthetic_workflow, "sanitized evidence artifact missing")
    require("Open or update authentication incident" in synthetic_workflow, "failure incident automation missing")
    require("Close recovered authentication incident" in synthetic_workflow, "recovery incident automation missing")

    for token in (
        "72 consecutive hourly cycles",
        "REM-P0-002: OPEN",
        "APPLICATION CONTROLS: IMPLEMENTED",
        "REM-P0-001 DEPENDENCY: OPEN",
        "BROAD COMMERCIAL GO-LIVE: NOT APPROVED",
    ):
        require(token in runbook, f"closure boundary missing: {token}")

    open_findings = current.get("open_findings", {})
    require("REM-P0-002" in open_findings, "REM-P0-002 was closed without production evidence")
    require("REM-P0-001" in open_findings, "REM-P0-001 dependency disappeared")
    require(current.get("commercial_go_live") == "NOT_APPROVED", "commercial boundary changed")

    print("REM-P0-002 AUTH SESSION RELIABILITY VALIDATION PASSED")
    print("BFF=BOUNDED_RETRY+CORRELATION+COOKIE_SAFETY")
    print("SESSION=SINGLE_FLIGHT+STALE_RESULT_REJECTION")
    print("SYNTHETIC=HOURLY_LOGIN+ME+REFRESH+LOGOUT+ATOMIC_LEAST_PRIVILEGE_IDENTITY")
    print("FINDING=OPEN_PENDING_OBSERVATION_AND_REM-P0-001")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValidationError, OSError, ValueError, json.JSONDecodeError) as error:
        print(f"REM-P0-002 VALIDATION ERROR: {error}", file=sys.stderr)
        raise SystemExit(1)
