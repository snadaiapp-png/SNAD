# Session-3 Evidence Manifest (2026-11-16) — PR #1536

Execution HEAD: `679ad47e198fcf6c45e680dc3c4dc0caffc45b1d` (branch `docs/subscriptions-final-closure`, clean tree; docs-only descendant of `4c9d7e68ca1636135549077650a896aaf4fe236d`; the only new commit on `origin/main` since, `0988c4f6` = PR #1535, adds a workflow file + an SQL inventory document — zero production code, so the PR baseline stays valid and GitHub reports the PR mergeable).
Scope of this session: (1) independent audit of every session-1/2 claim against the archived evidence, (2) closure of the two evidence gaps found, (3) fresh CI-parity re-run of the Subscription Authenticated E2E with full artifact capture.
Holds unchanged: `G1=HOLD`, `P0_SECURITY_HOLD=ACTIVE`, `PRODUCTION_RELEASE=NO-GO`, `PAYMENTS=OFF`. No PR merged. FULLY_CERTIFIED not declared.

## 1. Claim-by-claim audit results (all against archived artifacts, re-parsed independently)

| Claim (session 1/2 docs) | Independent re-check | Verdict |
| --- | --- | --- |
| Backend 694 unique scheduled / 679 first-pass / 0 F / 0 E / 15 gated skips | Re-parsed all 81 Surefire XML files in the 20261116 archive: batch totals 191/108/100/89/65/72(15 skip)/15/54 = 694 rows, 0 failures, 0 errors, 15 skips confined to `RbacAccessCheckPostgresAcceptanceTest` in batch3; batch3b re-runs the same class under the CI `pg-acceptance` contract (15/15, 0 skip); no class appears in more than one batch (0 duplicates); per-batch manifests carry HEAD `4c9d7e68…`, timestamps, `mvn_exit=0` | CONFIRMED |
| Skipped never counted as success | batch3's 15 skips were replaced by the batch3b CI-contract re-run; standard-suite 48 skips (8 credential-gated cases × 6 projects) excluded from passed counts everywhere | CONFIRMED |
| Vitest 171 files / 1426 tests / 0 failed / 0 skipped | `phase0-refresh-vitest.log` tail: `Test Files 171 passed (171)`, `Tests 1426 passed (1426)`, 112.31 s | CONFIRMED |
| `tsc --noEmit` PASS | `phase0-refresh-typecheck.log` shows the command with no error output | CONFIRMED |
| `next build` PASS (both env variants) | `phase0-e2e-next-build.log` and `phase0-e2e-next-build-standard.log` both end in the full route table (`ƒ Proxy (Middleware)` footer) | CONFIRMED |
| Flyway 261 migrations on the E2E database | `phase0-e2e-flyway.log`: `Successfully applied 261 migrations to schema "public", now at version v20261113.1` | CONFIRMED |
| Standard Playwright suite 330 executions = 282 passed / 48 skipped / 0 failed | Re-parsed `junit-shard1/2/3.xml`: 110+110+110 rows = 330, 0 failures, 0 errors, 48 skipped; 55 unique cases; exactly 8 unique fully-skipped cases (`auth-login-v2.spec.ts` ×1, `crm-tags-production-verification.spec.ts` ×7) × 6 projects | CONFIRMED |
| Subscription E2E session-2 run 1 passed (HTML report) | Parsed embedded report zip of archived `e2e-subscription-authenticated/playwright-report/index.html`: `stats total=1 expected=1 unexpected=0 flaky=0 skipped=0 ok=true`; test `list → detail → governed cancellation → durable readback`, duration 5773 ms; backend log line `Database: jdbc:postgresql://127.0.0.1:5432/sanad_subscription_test (PostgreSQL 17.11)` | CONFIRMED |
| Artifact hashes in session-2 manifest §3 | Recomputed SHA-256 for all 13 listed artifacts: 13/13 match | CONFIRMED |
| WS absorption (#1056/#1057/#1058 → #1062/#1138/#1156/`fc382d3a`) | Spot-checked key files at `4c9d7e68`: `components/sds/Modal.tsx`, `components/sds/__tests__/Modal.test.tsx`, `app/executive/tenants/tenant-management.test.tsx`, `app/executive/subscriptions/[id]/subscription-lifecycle-actions.test.tsx`, `subscription/catalog/ApplicationCatalogService.java`, `subscription/api/LifecycleControllerCommandBoundaryTest.java` — all present in main | CONFIRMED (spot level) |
| Issue #1055 eight domains + acceptance clause | Fetched issue #1055 from GitHub API: the eight numbered scope items and the acceptance clause match the traceability document's WS1–WS8 one-for-one | CONFIRMED |
| Session-2 "durable contracts verified" | **GAP**: outputs existed only in console transcript, not as an archived artifact | CLOSED by session-3 re-run (§3) |
| Session-2 "chrome --version verified per CI step" | **GAP**: version string not present in any archived log | CLOSED by session-3 re-run (§3) |
| Holds still active | Governance docs at HEAD (`SANAD-DEVELOPMENT-FIRST-DELIVERY-2026-11-15.md`): `P0_SECURITY_HOLD=ACTIVE · G1=HOLD · CAB=NOT APPROVED · PRODUCTION_RELEASE=NO-GO · PAYMENTS=OFF` | CONFIRMED |

## 2. Test accounting (final, corrected — unchanged from session-2 numbers, now triple-checked)

| Suite | Unique | Re-runs | Passed | Skipped | Failed |
| --- | --- | --- | --- | --- | --- |
| Backend Surefire (81 classes, 8 batches, real PostgreSQL 17.11) | **694** | 15 (batch3b = CI-contract re-run of the 15 gated acceptance cases; replaces their skipped first pass, not extra tests) | **694** | **0 effective** (15 gated skips → re-run clean; never counted as success) | 0 |
| Vitest | **1426** | 0 | **1426** | 0 | 0 |
| Playwright standard suite (55 cases × 6 projects) | 55 cases / **330 executions** | by-design locale×theme matrix, no retries | **282** | **48** (8 credential-gated unique × 6; CI-identical skip set; not counted as success) | **0** |
| Playwright subscription E2E (session 2 + session 3) | **1** | 1 planned re-provision re-run (session 2) + 1 full CI-parity re-run (session 3, §3) | **1 per run** | 0 | 0 |

## 3. Session-3 CI-parity re-run of the Subscription Authenticated E2E (2026-11-15T17:29–17:35Z)

Full re-execution of the CI job `subscription-authenticated` contract, from an empty database, at HEAD `679ad47e198f…`:

| Step | Result |
| --- | --- |
| Chrome at canonical path | `google-chrome-stable` official .deb extracted user-space, bind-mounted at `/opt/google/chrome/chrome` inside a user+mount namespace; `/opt/google/chrome/chrome --version` → **Google Chrome 157.0.8103.78** (archived in `environment.txt`) |
| DB re-provision | drop + recreate `sanad_subscription_test` (ephemeral password); role contract `false\|false\|false\|false` and `true\|false\|false\|false\|false\|false` asserted; `GRANT sanad TO sanad_subscription_test WITH INHERIT TRUE, SET FALSE` re-applied (`inherited=true`, `role_guard=false\|false`) |
| Flyway | `Successfully applied 261 migrations … v20261113.1` (CI command, classpath locations) |
| Seeds | `crm-acceptance-seed.sql` + `subscription-acceptance-seed.sql`, `ON_ERROR_STOP=1`, both rc=0 |
| Seeded contract | `ACTIVE\|33333333-3333-4333-8333-333333333333\|33333333-3333-4333-8333-333333333334` — PASS |
| Backend | Spring Boot jar healthy ~27 s; log line `Database: jdbc:postgresql://127.0.0.1:5432/sanad_subscription_test (PostgreSQL 17.11)` |
| Smoke login | CI assertion PASS (`accessToken` present, `tenantId=11111111-1111-4111-8111-111111111111`) |
| Frontend | `next build` with `NEXT_PUBLIC_API_BASE_URL` PASS; `next start` on 127.0.0.1:3001, `GET /` → 200 |
| E2E run (CI command, exactly once) | `npx playwright test --config=playwright.subscription-acceptance.config.ts --reporter=html,list` → **1 passed (6.8 s)**, `stats: total=1 expected=1 unexpected=0 flaky=0 skipped=0 ok=true`; channel `chrome`, `screenshot: only-on-failure`, `trace: retain-on-failure` — untouched |
| Durable contracts (archived this time) | `SUBSCRIPTION_FINAL_STATE=CANCELLED\|false` PASS · commands ledger = 1 PASS · events ledger = 1 PASS (+ detail rows in `durable-contracts.txt`) |
| Evidence screenshots | 5 PNGs captured with branded Chrome through the real UI (login, filled form, post-login, subscriptions list showing `Tenant A (Acceptance)` / CANCELLED state, subscription detail full-page) — evidence-only capture, not tests; no suite/config file touched |

### Session-3 artifact hashes (SHA-256, full)

| Artifact | SHA-256 |
| --- | --- |
| `backend.log` | `dcbcec3d34069d2fe0ce1c158eeeea985ac786b8cc5fdc4cbd58394ee847331f` |
| `frontend.log` | `3833a5bbf2d533106b92b12d05cfe9153a06a08cd32be7780bff2f0e49ea011e` |
| `frontend-build.log` | `cab343c532d260acab3741df265100916129f684e342fbbd666dee5db959b3bd` |
| `provision.log` | `6a48380683132a35359ea5c14aadc2987e86487a99f70755e78e0b93551853ba` |
| `playwright-run.log` | `755f05fc2c49aaf97c84426b39e7adb936d0a0b5ab1391b1ff9b4dbb5a1c30f5` |
| `playwright-report/index.html` | `b7ae9162c716703bf7ed6ee03a2fcb4deb49d7aa691248672005cf654c0f8eb6` |
| `durable-contracts.txt` | `e96cae1cec9484be6fd0dddbdc55f390fafe7339a58a87506576f9bb5f6fea1d` |
| `smoke-login.json` | `4ba21f6feee2f6e246fd9a2061557a8e4bb1e1db9177575d7eeb2c880b2ebc62` |
| `environment.txt` | `7e32ff326fe514689d896e75a1a98ce5695524a270eca0f34d3ede8f148620a5` |
| `screenshots/01-login-page.png` | `a23a6110225364d8e106d166fe17c49f3891147089cc85c81fb3aaa7dbc4ff9f` |
| `screenshots/02-login-filled.png` | `e968bd76b24f1377ce9096de2afed036957a2f684daf50d7c26c4c02f723ba43` |
| `screenshots/03-post-login.png` | `b1ca17ee9c00b4214fc1dd7744722d34c3bcac452f10a4a1791d686d99c42703` |
| `screenshots/04-subscriptions-list.png` | `2695019b85fb09ea80ed421b073e1df359c0a737bbd06f58f2a7b90bf3033760` |
| `screenshots/05-subscription-detail.png` | `4c81543fe4368dd4a3078681917d0490522ddd23c040a9b23a6efffab55588d9` |

Environment (full): PostgreSQL 17.11 host-native (no Docker/Testcontainers/H2/mocks), OpenJDK 21.0.13, Node v24.22.3, Playwright 1.61.1, Google Chrome 157.0.8103.78 (channel `chrome`, canonical path), Maven 3.9.9.

## 4. Remaining gates (unchanged)

1. **Human UAT re-run** — `HUMAN-UAT-PACK.md` (19 scenarios, WS1–WS8) is ready and awaits the human operator; this session does not execute or claim it. Final closure stays blocked on it.
2. Controller review items from CLOSURE-REPORT §7.3 (superseded drafts #1056/#1057/#1058 disposition).
3. Phase 2 does **not** start: Phase 0/1 evidence is complete and triple-checked, but the phase-gate closure requires the Human UAT clause; holds remain active.
