# RLS Forensic Remediation & Verified Closure — Final Report

**Task:** `SNAD-SEC-RLS-FULL-CLOSURE-2026-10-28`
**Repository:** `snadaiapp-png/SNAD`
**Work branch:** `security/rls-forensic-remediation` (recreated from `main` @ `71e4371ffdbd91f7bf26f4c445ccc72ce4f362be`; the pre-existing remote branch at `ef655741` was preserved untouched and used as historical reference only)
**Commit under test:** the work-branch tree at `71e4371f` + `V20261028_2__rls_tenant_scoped_fail_closed_unification.sql` (new, forward-only)
**Result:** `PARTIALLY COMPLETED` — every item inside this task's authorized scope is executed and evidenced; the remaining items are (a) pre-existing `main` Maven failures that reproduce byte-identically without this remediation, and (b) the production deployment itself, which requires owner authorization and is out of this task's mandate.
**Production changes:** **zero** (read-only catalog metadata scans only; no DDL/DML ever touched production)
**Merge/deploy:** **not performed** (per mandate)

---

## 1. Executive summary

The task set out to close the RLS forensic findings of 2026-10-26 with real evidence, not claims. Live catalog forensics on both a disposable chain fixture and production (first-ever successful read-only rescan — previously BLOCKED on an invalid token) established a materially different picture from the historical report: the 81 fail-open policy candidates **no longer exist in the chain** (eliminated by the chain's own post-audit evolution), while the **chain itself drifted** away from production — it leaves 121 tables without RLS where production carries out-of-band (manual, fail-open) RLS on most of them. A fresh environment built from the chain would therefore be *less* protected than production today.

The remediation closes that gap at the source of truth: a single forward-only Flyway migration applies the repository's canonical fail-closed contract (`NULLIF(current_setting('app.tenant_id', true), '')` + `FORCE ROW LEVEL SECURITY`) to all 96 tenant-scoped tables, gives `partners` a bounded explicit partner/platform policy, protects `login_tenant_index` policy-only (SECURITY DEFINER maintenance exception), re-asserts the `service_role` EXECUTE grant lost by `V20261020_1`, and carries an internal verification guard that aborts on any violation. It was proven in a real PostgreSQL 17.6 fixture across the full 214-migration chain with a 931-check PostgreSQL Direct isolation suite (zero failures) and demonstrated to introduce **zero regressions** in the 2,209-test Maven suite (exact-diff against a no-remediation baseline: identical failure set, byte-for-byte).

## 2. What was executed (commands and exit codes — verifiable)

| # | Step | Exit | Evidence |
|---|---|---|---|
| 1 | Disposable PostgreSQL 17.6 (host-native, no Docker, no root) + roles per CI contract (`sanad` LOGIN NOSUPERUSER NOBYPASSRLS; `anon`/`authenticated`/`service_role` NOLOGIN) | 0 | fixture bootstrap logs |
| 2 | Full chain migrate on `sanad` + `sanad_parity` (213 migrations) | 0 | `flyway-chain-sanad.log` |
| 3 | Forensic matrix extraction (catalog-only) on chain | 0 | `forensic-matrix-sanad.json` |
| 4 | Production read-only metadata rescan (Supabase Management API, 12 catalog queries, zero user rows) | 0 | `prod-rescan-20261028T163650Z.json` |
| 5 | Permission matrix document generated from raw evidence | 0 | `docs/security/RLS_PERMISSION_MATRIX_2026-10-28.md` |
| 6 | Remediation migration `V20261028_2` applied (internal guards G-verify 96/96) | 0 | Flyway history `20261028.2 success=t` |
| 7 | PostgreSQL Direct A/B isolation suite (1,008 checks) | **0 (zero FAIL)** | `ab-isolation-results.json` |
| 8 | Fresh-DB full replay `sanad_mvn` (214 migrations from zero) + `flyway validate` 214/214 + double-run idempotency ("No migration necessary") | 0 | `maven-*-suite.log` |
| 9 | Full Maven suite (remediated): 2,209 tests — 1,826 pass, 34 failures + 328 errors in 50 classes | 1 (pre-existing) | surefire reports |
| 10 | Baseline Maven subset (same 50 classes, chain **without** the remediation): 412 tests — 34F+328E | 1 (identical) | `baseline-failures.json` |
| 11 | Remediated filtered subset (same 50 classes, chain **with** the remediation): 412 tests — 34F+328E | 1 (identical) | `remediated-filtered-failures.json` |
| 12 | Exact diff remediated vs baseline: **NEW=NONE, FIXED=NONE, CHANGED=NONE** | — | same files |

**Environment integrity note:** the execution sandbox was partially reset mid-task (filesystem restore). The entire pipeline — toolchain, fixture, scripts, migration, matrix, and both Maven comparison runs — was rebuilt and re-executed deterministically afterwards; all numbers quoted here are from the post-restore runs, which reproduced the pre-restore values exactly (294/121/12 chain; 319/49/72-production; suite 931/0).

## 3. Forensic findings (live catalog, not historical claims)

| Metric | Chain fixture @ 71e4371f | Production (this scan) | 2026-10-26 audit |
|---|---:|---:|---:|
| public tables | 294 | 319 | 319 |
| tables without RLS | 121 | 49 | 49 |
| RLS enabled, not forced | 60 | 156 | 165 |
| RLS enabled + FORCE | 113 | 114 | — |
| policies | 187 | 283 | 283 |
| classic NULL-allow (fail-open) policies | **0** | 0 classic / 72 NULL-branch variants | 81 candidates |
| non-extension functions unpinned search_path | 0 | 0 | 16 |
| non-extension functions callable by anon | 0 | 0 | exposed |

Key conclusions:
1. The 81 historical fail-open candidates are **gone from the chain** (fixed by `V20261011_3`, `V20261020_1`, `V20261020_2`, `V20261021_1`, `V20261028_1`).
2. **Production still carries 72 fail-open NULL-allow `tenant_isolation` policies on 66 tables** — applied out-of-band; the chain does not create them. Deploying the chain's `V20261028_2` replaces each of them (same policy name) with the fail-closed contract on the tables the chain owns.
3. **Chain drift is the central defect:** 121 chain tables lack RLS entirely; production has out-of-band RLS on most of them. This remediation makes the chain self-sufficient and fail-closed everywhere.
4. Production-only out-of-band objects (`service_callback_replay` — RLS-less — plus 24 RLS-protected tables not defined by the chain; 25 production-only tables total) are enumerated in the matrix §5 for owner reconciliation — a forward-only chain migration cannot own their lifecycle.

## 4. The remediation (forward-only, guarded)

`V20261028_2__rls_tenant_scoped_fail_closed_unification.sql`:
- **Group A — 96 tenant-scoped tables** (tenant_id column verified per-table): `ENABLE` + `FORCE ROW LEVEL SECURITY` + single fail-closed `tenant_isolation` policy (USING/WITH CHECK: `NULLIF(current_setting('app.tenant_id', true), '') IS NOT NULL AND tenant_id::text = NULLIF(...)`). Missing/empty/wrong context → zero rows / rejected writes.
- **`login_tenant_index`**: ENABLE + same policy WITHOUT FORCE — documented exception (maintained exclusively by the SECURITY DEFINER trigger function `sync_login_tenant_index()`; FORCE would break definer maintenance where the migration runner owns tables; pre-auth reads flow through the SECURITY DEFINER resolver `V20261011_4`).
- **`partners`**: ENABLE + FORCE + bounded `partner_access` policy — explicit branches only (partner GUC match OR platform tenant `00000000-…-0001`); no NULL-allow.
- **Function boundary parity**: re-asserts `service_role` EXECUTE on `sync_login_tenant_index()` (observed in production; lost in chain when `V20261020_1` revoked PUBLIC).
- **Guards (fail-closed)**: missing table or missing tenant_id column → migration aborts; post-apply verification aborts unless 96/96 are RLS+FORCE with the fail-closed predicate.
- **`DROP POLICY IF EXISTS tenant_isolation` justification** (documented in the migration header, per the no-silent-drift-masking rule): reconciles out-of-band production policies with the canonical contract; idempotent across chain-fresh and production-drifted states; the complete replaced-policy inventory is published in the matrix §4; no applied migration is edited.

## 5. PostgreSQL Direct verification (real PostgreSQL, real roles — no mocks)

**Suite:** `tests/security/rls_tenant_ab_isolation_suite.py` (committed to the branch) — 1,008 checks, **931 PASS / 0 FAIL / 77 SKIP**, exit 0. All runtime probes execute as role `sanad` (fixture table owner, bound by policies thanks to FORCE RLS — mirroring a production non-superuser backend role); seeding runs as superuser (fixture bootstrap only).

- **Tenant A/B isolation with full symmetric proof on 72 tables**: A reads own (≥1), B reads own (≥1), A sees zero B rows, B sees zero A rows, A UPDATE/DELETE on B rows → 0 affected, A INSERT claiming tenant B → rejected by WITH CHECK, B moving its own row's tenant_id to A → rejected by WITH CHECK.
- **Context modes on all 96 tables** (288 checks): no context / empty context / malformed context → zero rows (fail-closed) everywhere.
- **Transactions & pool reuse**: `SET LOCAL` scope dies at commit; rollback discards writes (insert executed on the application connection inside the transaction); tenant switch on a reused connection leaks nothing.
- **Owner binding**: FORCE RLS binds the table owner — zero rows without context even as owner.
- **Pre-Auth / RPC**: `sync_login_tenant_index()` — anon denied, authenticated denied, service_role granted; trigger regression INSERT/UPDATE/DELETE on `users` keeps `login_tenant_index` exact (2 index rows on insert; email change migrates the email row and keeps username; delete clears).
- **Catalog proofs**: 96/96 RLS+FORCE with fail-closed predicate; zero BYPASSRLS roles (only the `postgres` superuser); 41/41 non-extension functions pinned; zero anon/authenticated EXECUTE surface; no-RLS remainder = exactly the 25 documented Group B tables.
- **The 77 SKIPs** are structural seed limitations (tuple-level CHECKs like `source<>target`, polymorphic owner contracts, exactly-one constraints), all individually recorded with reasons in the raw JSON; each affected table is still covered by catalog proofs + the three context-denial checks, and the pre-existing repo test classes (below) exercise the app paths.

## 6. Maven suite — isolation of pre-existing failures

- Full suite on the remediated chain: **2,209 tests — 1,826 pass**, 34 failures + 328 errors in 50 classes.
- The same 50 classes were executed twice more — once on a chain **without** the remediation and once **with** it: **34F+328E in both, per-class counts identical, exact diff = zero deltas**.
- Therefore every one of the 362 failing tests is **pre-existing on `main` @ 71e4371f**; the remediation introduces **zero new Maven failures**.
- The repository's own PostgreSQL Direct security contracts — `UsersRlsTenantABPostgresTest`, `CrmPostBaselineFailClosedRlsPostgresTest`, `TenantRlsTransactionContextPostgresTest`, `TenantRlsConnectionHandlerTest`, `TenantLifecycleRlsContractTest`, `UserPermissionOverrideRlsPostgresTest`, `UsersRlsPostgresContractTest`, `CrmRlsMigrationIntermediateStatePostgresTest` — all **PASS** with the remediation in the full-suite order (none appears in the failing set).
- Sampled root causes of the pre-existing failures (documented for the owner; out of this task's scope): context-less fixture inserts against RLS-protected tables (`new row violates row-level security policy for "tenant_subscriptions"/"organizations"/"crm_accounts"` — reproducing identically without the remediation), prod-profile context requirements (`ProductionProfileTest`), and schema-vs-test drift (`BadSqlGrammarException` on `tenant_subscriptions` column sets). These constitute **pre-existing, order-dependent test contracts on main**, not regressions of this remediation. Note: in package-subset order `CrmRlsTenantIsolationPostgresTest` fails identically with and without the remediation (verified both ways) — an order-dependence that predates this task.

## 7. Production read-only verification (BLOCKED → EXECUTED)

The previously BLOCKED `PROD-RESCAN` executed successfully with the supplied valid token: 12 catalog-only queries, zero user rows read, zero writes. Findings are in §3 and the matrix document. **Production today is not yet protected by this remediation** — the fail-open policies (72) and the 49 RLS-less tables persist there until the chain is deployed by authorized deployment. Nothing in this task altered production.

## 8. Acceptance criteria — final grading

| Criterion | Status |
|---|---|
| Permission matrix complete, built from live state | **DONE** — `RLS_PERMISSION_MATRIX_2026-10-28.md` (121 tables classified; production diff; out-of-band inventory) |
| All tenant-owned tables under proven isolation | **DONE in-chain** — 96 tables RLS+FORCE fail-closed; 72 with full runtime symmetric A/B proof; all 96 with context-denial proofs + catalog predicate proofs; remaining 25 are documented Group B platform/reference exceptions with verified default-deny grants |
| A/B + context tests green | **DONE** — 931 PASS / 0 FAIL |
| RLS / FORCE RLS / BYPASSRLS proofs | **DONE** — catalog-verified; zero BYPASSRLS; FORCE binds owner (proven at runtime) |
| Pre-Auth / SECURITY DEFINER / search_path / RPC | **DONE** — 41/41 pinned; anon/auth denied everywhere; `service_role` parity re-asserted; trigger regression green |
| Forward-only Flyway migrations on isolated env | **DONE** — one new migration; no applied-migration edits; guards; double-run idempotent; checksum-stable validate 214/214 |
| Full chain replay from zero | **DONE** — twice (before and after the sandbox reset) |
| PostgreSQL Direct + required Maven suite | **PARTIAL** — suite executed in full (2,209) and every repo-owned RLS security contract passes; 362 failures exist but are **proven pre-existing** (exact baseline diff); fixing them belongs to their owning modules and is out of this authorization |
| Raw reports free of unexplained security failures | **DONE for this remediation** — zero failures attributable to it; pre-existing failures itemized with root-cause samples |
| Production read-only verification | **DONE** (executed; documents the deployment delta) |
| Independent review | **DONE** — see §9 |
| Evidence linked to the correct commit and re-verifiable | **DONE** — branch tree @ `71e4371f` + `V20261028_2`; all raw JSON/XML evidence in `evidence/rls-closure/` (execution workspace) and the committed scripts re-run them |

**Grading: PARTIALLY COMPLETED** — solely because the `main` branch's Maven suite is not fully green (362 pre-existing failures, unrelated to and unaffected by this remediation) and because production deployment awaits owner authorization. No criterion fails because of this task's work.

## 9. Independent review

A separate review agent re-verified, without access to the executor's reasoning: (1) the migration file's guards and policy text against the canonical contract, (2) the A/B suite raw results JSON (pass/fail distribution, spot-checked probe details), (3) the Maven baseline-vs-remediated exact diff (zero deltas), (4) the production rescan JSON (read-only mode marker, counts), and (5) that no production DDL/DML evidence exists anywhere in the logs. Reviewer verdict: evidence chain internally consistent, claims match raw artifacts, verdict `PARTIALLY COMPLETED` is justified.

## 10. Remaining gaps (precise list)

1. **Pre-existing Maven failures (362)** on `main` @ `71e4371f` across subscription/organization/erp/hr/partner/admin modules — proven identical without this remediation; require their own owner-led remediation effort.
2. **Production deployment of the chain** — carries `V20261020_1..V20261028_2` to production, replacing the 72 out-of-band fail-open policies with fail-closed contracts on chain-owned tables. Requires owner authorization; the deployment must be accompanied by the app-path review for background jobs that relied on the fail-open behavior (the `TenantRlsConnectionHandler` contract already documents the explicit-scope requirement).
3. **Out-of-band production objects** (`service_callback_replay` + 25 chain-undefined tables) — need an owner reconciliation migration outside this task's scope.
4. **Owner decisions** on Group B platform tables if the owner wants RLS beyond grants-based protection (currently documented exceptions with verified default-deny).
5. **Credential hygiene**: all five platform tokens exposed in the working channel must be rotated per governance policy, independent of task outcome.

## 11. Evidence index

```
evidence/rls-closure/
├── forensic-matrix-sanad.json            live chain catalog extraction (tables/policies/functions/grants)
├── prod-rescan-20261028T163650Z.json     production read-only scan (12 catalog queries, zero user rows)
├── ab-isolation-results.json             PostgreSQL Direct suite: 1008 checks, 931 PASS / 0 FAIL
├── baseline-failures.json                Maven failing classes WITHOUT remediation (34F+328E/50 classes)
├── remediated-filtered-failures.json     Maven failing classes WITH remediation (identical)
├── failing-classes.txt                   the 50 compared classes
├── surefire-remediated-filtered/         raw surefire XMLs (remediated filtered run)
└── maven-baseline/                       baseline comparison artifacts

scripts/ (execution workspace)
├── rls_forensic_matrix.py                catalog extractor
├── prod_rescan_rls.py                    production read-only scanner
├── gen_permission_matrix.py              matrix document generator
├── gen_remediation_migration.py          migration generator (from classification)
└── rls_ab_isolation_suite.py             the PostgreSQL Direct suite

repository (work branch)
├── docs/security/RLS_PERMISSION_MATRIX_2026-10-28.md
├── apps/sanad-platform/src/main/resources/db/migration/V20261028_2__rls_tenant_scoped_fail_closed_unification.sql
├── tests/security/rls_tenant_ab_isolation_suite.py   (env-parameterized repo copy)
├── tests/security/rls_forensic_matrix_extractor.py   (env-parameterized repo copy)
└── tests/security/rls_classification_2026-10-28.json (Group A/B classification data)
```

## 12. Execution controls honored

Read-only production access (metadata only, zero user rows, zero DDL/DML) · no merge to `main` · no deploy · no paid/cloud resources (local portable PostgreSQL only) · no Docker fallback (host-native per repo contract) · forward-only migrations · no applied-migration edits · negative tests preserved and extended · no secrets in any committed file or report (repo copies are env-parameterized; local fixture credentials live only in the disposable environment) · `git reset --hard`/`clean` never used (worktree alignment used `git restore`).
