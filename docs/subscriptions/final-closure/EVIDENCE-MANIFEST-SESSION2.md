# Session-2 Evidence Manifest (2026-11-16) — PR #1536

Execution HEAD: `5f7db7f497b3e44e36e5c18c7c26e1d96d469875` (this branch, clean tree; docs-only descendant of `4c9d7e68ca1636135549077650a896aaf4fe236d`).
Environment: host-native PostgreSQL 17.11, OpenJDK 21, Node 24, branded **Google Chrome 157.0.8103.78** at the canonical `/opt/google/chrome/chrome` path (user-namespace bind-mount of the official `google-chrome-stable` .deb; no repo config changed, no acceptance criterion changed, no test disabled).
Holds unchanged: `G1=HOLD`, `P0_SECURITY_HOLD=ACTIVE`, `PRODUCTION_RELEASE=NO-GO`, `PAYMENTS=OFF`.

## 1. Test accounting — unique vs re-runs vs skipped (corrected)

### Backend (Surefire XML, 81 unique classes, independently re-audited from archived XML)

| Batch | As-run rows | Failures | Errors | Skipped |
| --- | --- | --- | --- | --- |
| batch1-unit (28 classes) | 191 | 0 | 0 | 0 |
| batch2a1-pg-lifecycle (10) | 108 | 0 | 0 | 0 |
| batch2a2-pg-lifecycle (10) | 100 | 0 | 0 | 0 |
| batch2b1-pg-catalog (8) | 89 | 0 | 0 | 0 |
| batch2b2-pg-read (12) | 65 | 0 | 0 | 0 |
| batch3-pg-security (9) | 72 | 0 | 0 | **15** |
| batch3b-pg-acceptance-rbac (1 — re-run of the 15 gated acceptance tests under the exact CI `pg-acceptance` contract) | 15 | 0 | 0 | 0 |
| batch4-ws6-ws7-domains (3) | 54 | 0 | 0 | 0 |
| **Total** | **694** | **0** | **0** | **15** |

- **Unique scheduled: 694** (no class runs in more than one batch — verified 0 duplicates).
- **First-pass executed: 679**; the 15 `RbacAccessCheckPostgresAcceptanceTest` cases were **skipped (not counted as success)** in batch3 and were **re-run as batch3b under the exact CI contract → 15/15 green, 0 skipped**.
- Re-run extra rows beyond unique: **0** (batch3b is a re-run of the same 15 unique cases, replacing their skipped first pass).
- Effective final state: **694 unique / 694 executed-green / 0 failures / 0 errors / 0 effective skips.**

### Frontend gates (re-executed at this HEAD on 2026-11-16)

| Gate | Result | Unique tests | Skipped |
| --- | --- | --- | --- |
| `tsc --noEmit` | PASS | — | — |
| Vitest | PASS | **171 files / 1426 tests / 0 failed** | **0** |
| `next build` + `validate:integrity` | PASS (twice: with `NEXT_PUBLIC_API_BASE_URL` for the subscription E2E job parity, and without it for the standard-suite job parity) | — | — |
| gitleaks current-tree (`--no-git`) | **0 leaks** (session-1 evidence; docs-only diff since) | — | — |

### Playwright — standard suite (CI job-1 parity: frontend-only, no backend, no API env)

| Metric | Value |
| --- | --- |
| Projects | 6 (`ar-rtl-light`, `ar-rtl-dark`, `ar-rtl-system`, `en-ltr-light`, `en-ltr-dark`, `en-ltr-system`) |
| Unique cases | **55** (×6 projects = **330 executions**) |
| Passed | **282** (0 failed) |
| Skipped | **48 executions = 8 unique cases × 6 projects**, all credential-gated (`test.skip(!E2E_EXEC…)` / `test.skip(!CRM_TENANT_A_…)`) — these credentials are absent from CI job 1 by design (they belong to the dedicated authenticated jobs), so the skip set is CI-identical. **Not counted as success.** |
| Failed | **0** |
| Duration | ~3.2 min per shard (3 shards, same tests as one CI run; shards are disjoint — no double-counting) |

### Playwright — Subscription Authenticated E2E (CI job-3 parity: real PostgreSQL + Spring Boot + Next.js)

| Item | Result |
| --- | --- |
| `subscription-executive-acceptance.spec.ts` (serial) | **1 passed / 0 failed / 0 skipped** (5.8 s) — UI login → list → detail → generic-route `CANCEL` 409 guard → governed cancellation → durable readback |
| Provisioning | fresh `sanad_subscription_test` DB, 261 Flyway migrations, `crm-acceptance-seed.sql` + `subscription-acceptance-seed.sql`, restricted role contract verified (`sanad_subscription_test=false\|false\|false\|false`; `sanad=true\|false\|false\|false\|false\|false`; RLS inheritance `true`), ephemeral password |
| Durable contracts (CI `PGOPTIONS` method) | `SUBSCRIPTION_FINAL_STATE=CANCELLED\|false` PASS · commands ledger = 1 PASS · events ledger = 1 PASS |
| Run protocol | run 1 (list reporter) PASSED 6.6 s; fixture then reset via the exact CI provisioning procedure (drop DB → migrate → seed) and the authoritative run 2 executed with `--reporter=html,list` → PASSED 5.8 s; durable contracts re-verified after each run |

## 2. Root causes fixed in session 2

1. **Playwright `channel:"chrome"` unavailability** (session-1 blocker) — resolved WITHOUT touching acceptance criteria or disabling tests: official `google-chrome-stable` .deb extracted to user space and bind-mounted at `/opt/google/chrome/chrome` inside a user+mount namespace (`unshare -rm`) so Playwright's CI-identical fixed-path resolution succeeds. `chrome --version` = Google Chrome 157.0.8103.78.
2. **`CRM_ACCEPTANCE_TENANT_WIDE_GRANT_NORMALIZATION_FAILED … but updated 0`** during backend start — root cause identified from the raw stack trace plus the Flyway connection line in the log: restart commands had omitted `SPRING_DATASOURCE_URL`/`SPRING_DATASOURCE_USERNAME`, so the app silently fell back to the base-config default `jdbc:postgresql://localhost:5432/sanad` (a dev database whose acceptance seed is incomplete). JDBC-level repro (`RlsRepro2.java`, proxy-wrapper replication) passed 4/4/1/1 against the correct DB, isolating the cause to the connection target, not RLS. Fix: restore the full CI env; app then connected to `sanad_subscription_test` (verified via `Database: jdbc:postgresql://127.0.0.1:5432/sanad_subscription_test` in the log) and started clean.
3. **Sandbox background-process reaping** — servers started with `nohup … &` were killed between tool calls (repeat of session-1 root cause 4). Fix: `setsid --fork` daemonization (parent exits immediately; child re-parented); PostgreSQL/Next/backend all survived across calls.
4. **CLI reporter overriding config** — `--reporter=list` suppressed the config's HTML reporter; re-run with `--reporter=html,list` after a clean re-provision to produce the CI-identical HTML report.

## 3. Artifact hashes (SHA-256, first 16 hex chars)

| Artifact | SHA-256 (prefix) |
| --- | --- |
| `e2e-subscription-authenticated/playwright-report/index.html` | `f2e5bc32ef24cbd7` |
| `e2e-subscription-authenticated/backend.log` | `8d542112573d6ad3` |
| `e2e-subscription-authenticated/frontend.log` | `75ce0ffff3fae0f4` |
| `standard-suite/junit-shard1.xml` | `a909491cf8470cf8` |
| `standard-suite/junit-shard2.xml` | `542f22259aedbdee` |
| `standard-suite/junit-shard3.xml` | `6188233d725a8b96` |
| `phase0-refresh-typecheck.log` | `de02d57b104220f1` |
| `phase0-refresh-vitest.log` | `5c517c6c028924de` |
| `phase0-e2e-flyway.log` | `dba1c07d1b5eb694` |
| `phase0-e2e-next-build.log` | `3f17da1ae1d20192` |
| `phase0-e2e-next-build-standard.log` | `75a778cfe54bab83` |
| `phase0-web/vitest.log` (session 1) | `41cbef5d2764b554` |
| `phase0-web/gitleaks-current-tree.json` | `37517e5f3dc66819` |

Full bundle: `subscription-closure-evidence-20261116.zip` (controller archive; also contains session-1 per-batch Surefire XML + console logs + manifests re-audited for this document).
