# G4 Closure — Test Results (all counts from actual reports/logs)

**Tested SHAs:** `952723e53bd8d3b434300bb7536f54b8bffffe20` (PR head, Run #40141730303) · `b1e22e20de17cadb17a875d8888a856980ff9419` (main head / merge commit, all other runs)
**Counting convention:** element-based (`<testcase>` element enumeration) — per the in-repo Gate G contract ("the testsuite `tests` attribute is unreliable under JUnit 5 nested/display-name containers"); attribute-based sums are reported alongside for transparency.

---

## 1. CI Run #40141730303 (pull_request, head `952723e5`) — result: SUCCESS

Jobs (live API, captured 2026-10-25T00:56:49Z):

| Job | Conclusion | Evidence |
|---|---|---|
| Maven Test Suite | success | artifacts `maven-test-console-log` (205,252,004 B) + `surefire-reports` (208,442,220 B), both head_sha-bound |
| CRM Integration Tests | success | artifact `crm-surefire-reports` (625,750 B) |
| PostgreSQL Acceptance Tests | success | artifact `pg-acceptance-surefire-reports` (27,430 B) |
| R0C-12 Canonical Gate G | **skipped** | see §4 — manual-dispatch-only by design; dispatched separately on `b1e22e20` |

### 1.1 Maven Test Suite reconciliation (raw artifacts, downloaded and parsed by this package)

| Measure | Value |
|---|---|
| Console final aggregate (`[WARNING] Tests run: …` line) | **4722 / 0F / 0E / 37S** — BUILD SUCCESS, total time 01:12 h, finished 2026-10-25T00:24:34Z |
| XML files in artifact | 637 (`TEST-*.xml`) |
| XML parse errors | **0** |
| Duplicate reports (classname#name keys) | **0** |
| Attribute-based XML sum | 4480 / 0F / 0E / 37S (undercounts — known Surefire attribute behavior for nested/display-name containers; parent suites carry `tests="0"` with testcase children) |
| **Element-based XML sum (canonical)** | **4722 / 0F / 0E / 37S** |
| Element vs console | **EXACT MATCH → SUREFIRE_RECONCILIATION = PASS** |
| Skips | exactly 5 classes: `CommerceOrderPostgresConcurrencyTest` 6, `RbacAccessCheckPostgresAcceptanceTest` 15, `ModuleRegistryUatPostgresAcceptanceTest` 10, `PlatformSessionInvalidationPostgresAcceptanceTest` 3, `PlatformUserPostgresAcceptanceTest` 3 → **UNEXPLAINED_SKIPS = 0**, **CRITICAL_SKIPS = 0** |
| Skip coverage | the same 37 tests executed GREEN in the same run's PostgreSQL Acceptance Tests job (below) |

### 1.2 PostgreSQL Acceptance Tests (same run) — 37/37, 0F/0E/0S

| Suite | Tests | F | E | S | Time |
|---|---|---|---|---|---|
| CommerceOrderPostgresConcurrencyTest | 6 | 0 | 0 | 0 | 7.417s |
| ModuleRegistryUatPostgresAcceptanceTest | 10 | 0 | 0 | 0 | 14.042s |
| PlatformSessionInvalidationPostgresAcceptanceTest | 3 | 0 | 0 | 0 | 2.584s |
| PlatformUserPostgresAcceptanceTest | 3 | 0 | 0 | 0 | 0.177s |
| RbacAccessCheckPostgresAcceptanceTest | 15 | 0 | 0 | 0 | 5.119s |

### 1.3 CRM Integration Tests (same run) — 85/85, 0F/0E/0S (16 suite files, element-based)

## 2. Runs executed on `b1e22e20` (all dispatched by this mandate or push-triggered; verdicts from live API + job logs)

| Run | Workflow | Trigger | Conclusion | Test counts (from logs) |
|---|---|---|---|---|
| `40147222757` | Post-Merge Main Verification (PMV = G4-T12 JOB A–F) | push (merge) | **SUCCESS — all six jobs** | JOB D HRM-focused: **1042/0F/0E/0S** (100 XML files, element-based; includes payroll family 46 and `HrRlsFailClosedIntegrationTest` 259) — see §3 |
| `40149954567` | HRM G4 Payroll Data Isolation PostgreSQL Direct | workflow_dispatch | **SUCCESS** | **2/2** — forced-RLS + no-bypass role contract; Tenant A/B known-ownership isolation |
| `40149956367` | HRM G4 T5 Payroll Lifecycle | workflow_dispatch | **SUCCESS** | **25/25** — PostgreSQL Direct lifecycle suite |
| `40149958050` | HRM G4 T6 Payroll Authorization | workflow_dispatch | **SUCCESS** | **13/13** — fail-closed capability authorization contracts |
| `40149959660` | HRM G4 T10 Authenticated Acceptance | workflow_dispatch | **SUCCESS** (2 jobs) | Playwright: **1 passed (5.7 s)** — "authenticated payroll operator: create, calculate, review, approve, export boundary; foreign tenant denied"; vitest contract: **2/2 passed** |
| `40149030737` | CI — dispatched for R0C-12 Canonical Gate G | workflow_dispatch | **SUCCESS — GATE_G_STATUS=PASS** (2026-10-25T02:12:33Z) | PostgreSQL Acceptance: success; CRM: success; Maven Test Suite: **success** (4722/0F/0E/37S on `b1e22e20`); R0C-12: **PASS** — SHA_BINDING=PASS, reconciliation PASS, UNEXPLAINED_SKIPS=0, CRITICAL_INVARIANTS=PASS, FAILED_CONDITIONS=NONE (verdict artifact `r0c12-canonical-gate-g-b1e22e2-run-40149030737`, id 12583049586) |

### 2.1 PMV JOB A–F (run `40147222757`) — JOB F verification-manifest: **result PASS**

JOB A frontend-verification ✓ · JOB B backend-compile ✓ · JOB C PostgreSQL Direct integration ✓ · JOB D HRM-focused security/RLS ✓ · JOB E security & governance scans ✓ · JOB F final evidence aggregation ✓

JOB F manifest artifact (`verification-manifest.json`, downloaded and verified): `exactMainSha=b1e22e20…`, `result=PASS`, 23/23 checks SUCCESS, `failedChecks=[]`, `skippedChecks=[]`, `missingChecks=[]`, actor `abdulrhmansenan1985-creator`. Secret-scan artifact: 6259 files scanned, **0 findings**, 0 errors.

### 2.2 Local supporting run (agent sandbox — supporting only, CI is authoritative)

Toolchain: Maven 3.9.16 (sha512-verified) + Temurin JDK 21.0.13. Worktree `b1e22e20`.
`PayrollLifecycleTest` 4 + `PayrollAuthorizationContractTest` 5 + `PayrollCalculationEngineTest` 9 = **18/18, 0F/0E/0S, BUILD SUCCESS**. (PostgreSQL is not available in the sandbox; PostgreSQL Direct evidence comes exclusively from CI runs above — per workflow governance, local canonical execution is forbidden.)

## 3. HRM-focused suite (PMV JOB D) payroll-relevant classes (from `hrm-surefire-reports` artifact)

| Class | Tests | F | E | S |
|---|---|---|---|---|
| PayrollLifecyclePostgresTest | 13 | 0 | 0 | 0 |
| PayrollLifecycleTest | 4 | 0 | 0 | 0 |
| PayrollAuthorizationContractTest | 5 | 0 | 0 | 0 |
| PayrollCalculationEngineTest | 9 | 0 | 0 | 0 |
| PayrollApiContractTest | 4 | 0 | 0 | 0 |
| PayrollAccountingExportContractTest | 5 | 0 | 0 | 0 |
| CanonicalHrPayrollInputAdapterTest | 3 | 0 | 0 | 0 |
| HrG4PayrollDataIsolationPostgresTest | 2 | 0 | 0 | 0 |
| HrG4PayrollSnapshotPostgresTest | 1 | 0 | 0 | 0 |
| HrRlsFailClosedIntegrationTest | 259 | 0 | 0 | 0 |
| HrApiV2AuthorizationTest | 6 | 0 | 0 | 0 |
| HrTenantContextRegressionTest | 4 | 0 | 0 | 0 |
| HrIdempotencyIntegrationTest | 12 | 0 | 0 | 0 |
| HrAuditOutboxAtomicityIntegrationTest | 18 | 0 | 0 | 0 |
| HrModuleBoundaryArchitectureTest | 8 | 0 | 0 | 0 |

Total JOB D: **1042 tests, 0F/0E/0S** (element-based; payroll-specific subtotal: 46).

## 4. R0C-12 Canonical Gate G — skip cause (proven, not inferred)

- **Run #40141730303** (pull_request): job record shows `conclusion=skipped`, **zero steps**, no runner, started=completed within the same second → job-level condition evaluated false at scheduling.
- **Workflow source** `.github/workflows/ci.yml` at `952723e5` (blob saved in package evidence): line 769 `if: github.event_name == 'workflow_dispatch'` — the job is **manual-dispatch-only by design** (Amendment #6: "This job is ADDITIVE and MANUAL-ONLY… LOCAL_Z_CANONICAL_EXECUTION = FORBIDDEN").
- **Authorized supported method used:** workflow_dispatch on `main@b1e22e20` → run `40149030737` (2026-10-25T01:02:10Z → 02:12:33Z). **GATE_G_STATUS=PASS.** The run executed the canonical contract (exact-SHA checkout binding, Temurin 21.0.12.1, Maven 3.9.16 sha512-verified, PostgreSQL 16.2 sha256-verified source build, least-privilege provisioning, single uninterrupted Maven invocation, XML reconciliation, skip classification, critical invariants, PG-acceptance companion 37/37) and uploads the SHA/run-bound verdict artifact (`A6-GATE-G-VERDICT.txt` + `SHA256SUMS-FINAL.txt`).
- Gate G execution history: exactly one prior dispatch success (run `39627497322`, 2026-10-21, `7dbd7b211`); no Gate G execution existed for `952723e5`/`b1e22e20` before this mandate.

## 5. Exclusions and limitations

- No test was excluded, skipped, weakened, or modified by this work. The 37 profile-gated skips are designed exclusions covered by the dedicated acceptance job (proven green).
- Production UI acceptance: intentionally not executed (gated — see §F blockers).
- `hrm-g4-t12-vercel-cli-preview.yml` (Vercel preview deployment): **not dispatched** — it deploys a preview artifact and requires the protected `VERCEL_TOKEN`; outside this mandate's deployment boundary (NO DEPLOY).
- Playwright report artifact retained (531,467-byte HTML report, `playwright-g4-payroll-report/index.html`); backend.log + frontend.log retained inside the T10 artifact.
- Job logs fetched for T5/T6/isolation/T10/Render/Y2 are preserved under the package evidence directory; the 2.35 GB Maven console remains available as run artifact (id 12581340701) with the bounded element-based summary reproduced in §1.1 and in evidence-manifest.json.
