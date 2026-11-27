# BASELINE Report — UAT & Final Acceptance Session (2026-11-16, session 4)

## 1. Resume point (Phase A)

| Item | Value |
| --- | --- |
| `origin/main` at session start | `0988c4f6058f` (PR #1535 — workflow + SQL inventory doc only, zero production code) |
| PR #1536 | **open, not merged**, head `2a9ec15ca9cd`, base `main`, docs-only (`docs/subscriptions/final-closure/`) |
| Local tree | full clone of PR head `2a9ec15c` (branch `docs/subscriptions-final-closure`), clean |
| Docs verified (A.3) | all five present and intact: `REQUIREMENTS-TRACEABILITY.md`, `CLOSURE-REPORT.md`, `EVIDENCE-MANIFEST-SESSION2.md`, `EVIDENCE-MANIFEST-SESSION3.md`, `HUMAN-UAT-PACK.md` |
| Prior sessions' verified state | backend 694 unique / 679 first-pass / 15 gated-skip re-runs / 0 effective skips / 0 F / 0 E; Vitest 1426/1426; standard browser suite 330 exec = 282 passed / 48 credential-gated skips / 0 failed; subscription E2E 1/1; all previously hash-verified (session-3 audit, 13/13) |

## 2. Environment at session start (A.5)

The execution sandbox was **fully recycled** between sessions: `/home/z/my-project` lost the SNAD clone, the PostgreSQL data directory, JDK, Maven, Chrome extraction, scripts and the local evidence archives. Only `download/` survived (this time even the evidence zips were purged). Recovered:

- GitHub state intact (PR #1536 head `2a9ec15c`, all five docs committed).
- Fresh rebuild performed this session: SNAD clone, PostgreSQL **17.11** (host-native zonky binaries) bootstrapped with the exact repo role contract (`sanad` restricted LOGIN non-inheriting `sanad_callback_replay_cleanup`; `sanad_subscription_test` inheriting `sanad`), JDK 21.0.x (Temurin), Maven 3.9.9, Google Chrome **157.0.8103.78** (official .deb, user-space), Node 24.22.3 (system), Playwright 1.61.1.

## 3. Evidence-bundle integrity (A.4)

| Evidence | Status |
| --- | --- |
| Committed docs + manifests (all numbers, SHA-256s, environment stamps) | **INTACT** on GitHub at PR head `2a9ec15c` — the durable record |
| Raw session-1/2/3 artifact bundle (Surefire XMLs, logs, screenshots, HTML reports) | **LOST** with the recycled sandbox (was sandbox-local only). Their SHA-256s remain recorded in `EVIDENCE-MANIFEST-SESSION2.md` §3 / `EVIDENCE-MANIFEST-SESSION3.md` §3 as verifiable references. |

## 4. Re-execution decision (A.6)

- Backend Surefire batches (694) and Vitest (1426) and the standard browser suite (330 exec) are **NOT re-run**: results stand on the committed manifests; no technical reason to doubt them.
- Re-executed fresh this session (documented necessity — missing acceptance coverage + staging proof): staging provisioning (Flyway + seeds + role contract), Subscription Authenticated E2E, **UAT-01…19 automated execution** (Phase C), the mandatory credential-gated Playwright cases (Phase D), and security acceptance checks (RBAC / cross-tenant / ledgers / audit).

## 5. Gaps carried into Phase B/C

1. No staging existed at session start → rebuilt (Phase B).
2. Raw prior evidence bundles lost → the committed manifests are the historical record; all new evidence of this session is archived under `test-evidence/uat-20261116/` and committed docs carry its SHA-256 manifest.
3. Human UAT remains the open acceptance gate (Phase G) — the agent executes UAT-01…19 as **automated UAT evidence only**, never as human approval.
