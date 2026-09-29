# HR G2 Recovery Diagnostic Authorization

Status: PENDING REVIEW

Purpose: authorize one governed diagnostic recovery cycle after PR #1200 instrumentation merged to `main`.

The diagnostic cycle is limited to the existing governed recovery chain and must preserve:

- exact-SHA binding,
- PostgreSQL Direct governance,
- no Docker/Testcontainers,
- no direct production database mutation,
- no credential mutation unless the governed 401 credential-drift signature is proven,
- fail-closed recovery-surface shutdown,
- sanitized Render API diagnostics only.

The next authorized merge commit must include the marker `PRODUCTION-RECOVERY-G2-AUTHORIZED` so the existing `Publish Render Backend Image` -> `G2 Recovery Release Dispatch` -> `SANAD Production Release` -> `G2 Production Credential Reconciliation` chain can execute on the current main SHA.

Acceptance condition for this diagnostic authorization:

1. instrumented recovery-close diagnostics are present on main,
2. exact-head CI succeeds,
3. independent review is approved,
4. recovery-close logs expose HTTP status and sanitized response body without secrets,
5. the recovery endpoint is proven disabled with HTTP 404 before any final production release is accepted.

This document authorizes diagnostics and containment only. It does not declare HR G2 operational closure.