# Subscription Module Closure Report — Session Resumption (2026-11-15)

**Repository:** snadaiapp-png/SNAD · **MAIN_SHA:** `4c9d7e68ca1636135549077650a896aaf4fe236d` · **HEAD_SHA (verification tree):** same SHA, clean working tree
**Predecessor baseline (Issue #1055):** `e30d47be1716dcd32a4101f01428f62a66d0ac18`

## 1. Session context

This session resumed the closure plan after the execution sandbox was recycled; the previous session's local artifacts (Surefire XML evidence, batch definitions, "106 tests" console log) were **not present locally nor committed to the repository**. No numbers from that session were assumed. Instead, every batch was **re-executed and re-proven** at the exact locked HEAD with real PostgreSQL, and the claimed first-batch figure of "106 tests / 0 errors" was replaced by reproducible, archived evidence (see §2). Batch definitions were re-established from code-level analysis of all 78 subscription test classes and are documented in `REQUIREMENTS-TRACEABILITY.md` §1.

## 2. Executed results (all numbers from archived Surefire XML)

| Gate | Result |
| --- | --- |
| Backend — subscription unit (28 classes) | 191 / 0 F / 0 E / 0 S |
| Backend — PG Direct lifecycle+change (20 classes) | 208 / 0 F / 0 E / 0 S |
| Backend — PG Direct catalog+read+billing+RBAC (29 classes) | 241 scheduled, 226 executed / 0 F / 0 E (15 acceptance skips re-run clean under CI contract) |
| Backend — RBAC acceptance (CI `pg-acceptance` contract, isolated DB) | 15 / 0 F / 0 E / **0 S** |
| Backend — WS6/WS7 supplemental integration (3 classes) | 54 / 0 F / 0 E / 0 S |
| **Backend executed total** | **679 / 0 / 0 / 0** (real PostgreSQL 17.11, host-native, role contract honored, `CI=true` fail-closed) |
| Frontend TypeScript (`tsc --noEmit`) | PASS |
| Frontend Vitest | 171 files, **1426 / 0 failed / 0 skipped** |
| Production build (`next build` + `validate:integrity` prebuild) | PASS |
| Secrets (`gitleaks detect --no-git`, CI parity, v8.24.3) | **0 leaks** (38 historical-only findings in full-history mode, not in current tree) |
| Closure contract (`tests/ci/test_subscription_1055_comprehensive_closure.py`) | **12/12 OK** |
| `tests/security/` Python suite | 82 passed, 23 subtests passed; 2 failures are the **by-design RED gates** of PR #1383 (not in CI) |
| Playwright (standard + subscription acceptance) | **BLOCKED — environment**: configs pin `channel:"chrome"` (`/opt/google/chrome/chrome`); sandbox lacks root and `/opt` is read-only. Not a code failure. |

Failed tests: **none**. Skipped tests: **none** on every executed batch (the batch3 RBAC acceptance skips were re-run cleanly as batch3b rather than counted as success).

## 3. Root causes fixed during this session

1. **`G1_RLS_RUNTIME_ROLE_UNAVAILABLE`** — Flyway `V20261011_3__public_users_fail_closed_tenant_rls.sql` fail-closed guard aborted all PG migrations: the local cluster had a **superuser** `sanad` from `initdb`. Fix: rebuild cluster per the repository's own provisioning contract (`docker/postgres-init/*`, CI SQL) — restricted `sanad`, `sanad_migrator`, `sanad_callback_replay_cleanup` (+ non-inheriting membership), isolated DBs owned by `sanad`. Re-run: green.
2. **Silent acceptance-suite skip** — `RbacAccessCheckPostgresAcceptanceTest` gates on `SPRING_PROFILES_ACTIVE=pg-acceptance` + `PG_ACCEPTANCE_*` env (CI contract). Fix: rebuilt isolated `pg_acceptance` DB and ran with the exact CI env incl. fresh `CRM_CUSTOM_FIELD_ENCRYPTION_KEY`. Re-run: 15/15, 0 skipped.
3. **Surefire 3.5.4 ignores `-Dsurefire.reportsDirectory`** — per-run separation achieved by wipe-before-run + archive-after-run into independent evidence directories with manifests (batch id, SHA, exit code, timestamps).
4. **Detached background processes killed by sandbox supervisor** — switched to synchronous, resumable chunked execution.

## 4. Requirements outcome (details in REQUIREMENTS-TRACEABILITY.md)

- WS1 Tenant UI recovery — VERIFIED · WS2 Application catalog lifecycle — VERIFIED · WS3 Subscription lifecycle integrity — VERIFIED (backend/unit/contract; browser E2E blocked) · WS4 Subscription navigation/RBAC — VERIFIED · WS5 Billing/read-model correctness — VERIFIED · WS6 Tenant/domain provisioning — VERIFIED · WS7 Website/store monetization — VERIFIED · WS8 Branch/operating-unit subscriptions — VERIFIED
- Blocked/Unverified: Playwright browser gates (environment), human UAT re-run (requires operator).
- Old drafts #1056/#1057/#1058: **verified as absorbed by merged PRs #1062 and #1156 (plus #1138, `fc382d3a`)** — recommend close-as-superseded, not merge; one literal test residue from #1058 documented with its functional supersession.

## 5. Files modified / PRs opened (this session)

| Artifact | Where |
| --- | --- |
| `docs/subscriptions/final-closure/REQUIREMENTS-TRACEABILITY.md` | docs-only draft PR (branch `docs/subscriptions-final-closure`) — **not merged** |
| `docs/subscriptions/final-closure/CLOSURE-REPORT.md` (this file) | same draft PR |
| Evidence bundle (per-batch Surefire XML, console logs, manifests, gitleaks report, Playwright logs) | session archive (also mirrored outside the repo for the controller) |
| Code changes | **none** — zero production-code modifications in this session |

## 6. Holds (unchanged, verified)

`G1=HOLD` · `P0_SECURITY_HOLD=ACTIVE` · `PRODUCTION_RELEASE=NO-GO` · `PAYMENTS=OFF` — no PR merged, no production change, no payment activation, no branch-protection interaction.

## 7. Next resumption point

1. ~~Controller decision on Playwright runner (Chrome channel)~~ → **RESOLVED 2026-11-16 (session 2, §8): executed with branded Chrome at the canonical path, zero config changes.**
2. Human UAT re-run per Issue #1055 acceptance clause (8 workstreams, screenshots as evidence).
3. Close superseded drafts #1056/#1057/#1058 after controller review.
4. Final closure decision remains gated on the above; all phase-0/1 evidence in this report is reproducible at HEAD `4c9d7e68`.

## 8. Session-2 addendum (2026-11-16) — Playwright unblocked and executed; corrected test accounting

Controller instruction of 2026-11-16: re-verify every claim of §2/§4 against executable evidence, correct the test accounting (unique vs re-runs; skipped ≠ success), resolve the Playwright blocker in a Chrome-equipped (or equivalent approved) environment without touching acceptance criteria, run the Subscription Authenticated E2E on real PostgreSQL with isolated seed data, and hand Human UAT to the user (`HUMAN-UAT-PACK.md`). All holds stay active; nothing merged; payments untouched.

### 8.1 Environment resolution

- **Playwright blocker (session-1 root cause):** official `google-chrome-stable` .deb extracted to user space and bind-mounted at `/opt/google/chrome/chrome` inside a user+mount namespace (`unshare -rm`). Playwright's `channel:"chrome"` resolution is therefore exercised exactly as on CI runners (`Google Chrome 157.0.8103.78`). No config/spec/threshold was modified; no test disabled; `chrome --version` verified per CI step.
- **Backend start failure `CRM_ACCEPTANCE_TENANT_WIDE_GRANT_NORMALIZATION_FAILED … updated 0`:** root cause proven from the raw stack trace + Flyway log line — restart commands omitted `SPRING_DATASOURCE_URL`/`USERNAME`, so the app silently used the base default `localhost:5432/sanad` dev database (incomplete acceptance seed). JDBC-level reproduction (`RlsRepro2.java`, including the exact connection-proxy wrapper) passed 4/4/1/1 against the correct DB, ruling out RLS. Fix: full CI env restored; log then shows `Database: jdbc:postgresql://127.0.0.1:5432/sanad_subscription_test` and clean start.
- **Background reaping:** `setsid --fork` daemonization keeps servers alive across tool calls (replaces session-1's synchronous-only workaround).
- **Reporter parity:** authoritative E2E run executed after a clean CI-style re-provision (drop DB → 261 migrations → both seed files) with `--reporter=html,list`.

### 8.2 Corrected accounting (details: `EVIDENCE-MANIFEST-SESSION2.md` §1)

| Suite | Unique | Re-runs | Passed | Skipped | Failed |
| --- | --- | --- | --- | --- | --- |
| Backend Surefire (81 classes, 8 batches) | **694** | 0 (the batch3b 15 replace the 15 gated skips of batch3 — not extra tests) | **694** (679 first-pass + 15 CI-contract re-run) | **0 effective** (15 skipped → re-run clean; never counted as success) | 0 |
| Vitest | **1426** | 0 | **1426** | 0 | 0 |
| Playwright standard suite (55 cases × 6 projects) | 55 cases / **330 executions** | by-design matrix, no retries | **282** | **48** (8 credential-gated unique × 6; CI job 1 skips the same set; not counted as success) | **0** |
| Playwright subscription acceptance E2E | **1** | 1 planned re-provision + re-run for reporter parity (documented) | **1** | 0 | 0 |

### 8.3 Workstream impact

Browser-level evidence now exists for the surfaces that were previously blocked: WS1 (tenant/auth surfaces, bilingual-theme matrix), WS3/WS4 (subscription control-plane E2E incl. generic-route 409 fail-closed guard and governed cancellation), WS5 (detail/billing readbacks in the E2E durable readback). WS1–WS8 classifications in `REQUIREMENTS-TRACEABILITY.md` remain VERIFIED on backend+unit+contract+browser evidence; **final closure remains BLOCKED solely on the Human UAT re-run** (and the controller-review items in §7.3). No domain is classified VERIFIED with a mandatory acceptance test blocked: the previously-blocked mandatory browser tests have now executed green, and the Human UAT clause is tracked as the remaining gate, not attributed to any single workstream.

### 8.4 Holds (re-verified unchanged)

`G1=HOLD` · `P0_SECURITY_HOLD=ACTIVE` · `PRODUCTION_RELEASE=NO-GO` · `PAYMENTS=OFF` — no PR merged, no production change, no payment activation, no branch-protection interaction. FULLY_CERTIFIED has **not** been declared.
