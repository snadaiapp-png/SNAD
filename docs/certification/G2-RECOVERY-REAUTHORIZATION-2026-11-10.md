# G2 Recovery Re-Authorization Record

- **Record ID:** G2-REAUTH-20261110-1492
- **Issued:** 2026-11-10
- **Repository:** snadaiapp-png/SNAD (main @ e7c876c2f8d0e9fe972c4ede56e4d2188e91b405, PR #1491 squash merge)
- **Project manager (full delegation):** snadaiapp-png (id 294245491)
- **Independent reviewer:** abdulrhmansenan1985-creator (id 301111696)

## Purpose

Re-authorize the G2 Recovery Release Dispatch path on the current main baseline so the
exact-SHA recovery release can run against main @ e7c876c. The previous authorization
window (ca40478, certificate G1-CERT-20261110-1482) closed when main advanced: the
fail-closed main-head gates correctly refused both reruns (dispatch run 42663769184
attempt 2: "Recovery not eligible: triggering SHA is not current main"; production-release
run 42674957020 attempt 2: "Requested commit is not the current main head").

## Authorization chain

| # | Element | Evidence | Status |
|---|---------|----------|--------|
| 1 | G1 security certification | Certificate G1-CERT-20261110-1482 (PR #1490, merged as ca40478): durable encrypted backup + full ACL/RLS fail-closed equivalence + independent approval, 3/3 MET | CARRIED FORWARD |
| 2 | Repo operator gate | `G1_SECURITY_APPROVED=true` (repo variable, created by the independent reviewer 2026-11-09T16:06:54Z, unchanged) | ACTIVE |
| 3 | Operator instruction | Explicit project-manager instruction dated 2026-11-10 to execute the new G2 cycle on the e7c876c baseline | GIVEN |

## Baseline delta (ca40478 -> e7c876c)

PR #1491 only: staged Vercel production promotion workflow hardening
(`.github/workflows/vercel-main-production-reconcile.yml`,
`.github/workflows/vercel-reconcile-safety-tests.yml`,
`tests/ci/test_release_gate_topology.py`,
`tests/ci/test_vercel_main_reconcile_workflow.py`). Zero backend/runtime code changes.
The backend image for e7c876c is already published and attested
(Publish Render Backend Image run 42714247466, success 2026-11-09T22:42Z).

## Operational trigger

Render service `sanad-backend` (srv-d8ragqkm0tmc73bviqq0, starter plan, not suspended)
has been unresponsive since its last live deployment (2026-10-28T00:09Z): four direct
`/actuator/health` probes timed out (40s/95s/170s/60s budgets, HTTP 000 with completed
TLS handshake) and the trailing 2h service log window is empty. The recovery release
performs an exact-SHA Render deployment which replaces the wedged instance.

## Scope and limits

- Evidence-only commit: no application code, database, Flyway, or payment changes.
- `rollback_on_failure=false` per the dispatch contract.
- Production environment approval by the independent reviewer remains mandatory.
- This marker is valid for the exact main SHA of this commit only.

## Effect

This commit message carries the authorization marker
`PRODUCTION-RECOVERY-G2-AUTHORIZED` for the exact main SHA of this commit, enabling the
G2 Recovery Release Dispatch path (Production environment approval by the independent
reviewer, then exact-SHA recovery release without rollback).
