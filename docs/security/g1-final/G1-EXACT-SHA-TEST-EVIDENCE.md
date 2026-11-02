# G1-EXACT-SHA-TEST-EVIDENCE.md

**Rule applied:** no result is attributed to a SHA that did not run it; `SKIPPED` and non-terminal are not success; results from different SHAs are never combined into one PASS.

## 1. SHA lineage of this command

| SHA | What changed | Test binding |
|---|---|---|
| `12f92d7cf…` | `main` HEAD (PR #1458 squash-merge) | required checks all terminal success (`Maven Test Suite=success`, `PostgreSQL Acceptance Tests=success`, `Verify 8 tables…=success`; 25 success / 2 skipped / 1 none) |
| `81b5585fd…` | PR #1465 head at baseline | 47 success / 2 skipped / **`Maven Test Suite` stuck `in_progress` >12 h (run 41357909105) — never terminal → treated as NOT PASSED** |
| `4a0cf0802…` | + `V20261102_2`, test pins | 6 required/mandated gates FAILED — root cause: `T2_BATCH2_RUNTIME_GRANTS_VERIFY` guard vs owner-as-`sanad` fixture model (runs 41364348714, 41364348777; logs archived) |
| `d0694a00f…` | guard scoped to separate-owner model | 36 success; **2 FAILED** — `Wave 1 Task 1 PostgreSQL Direct`, `Verify 8 tables, 26 indexes, and tenant isolation`: my own test-pin defect — duplicate `20261102.2` entry in two `containsExactly` pending sequences (true failure of my change, not flake) |
| `8e93beaec…` | pending-sequence fix #1 (one duplicate per list remained) | `Wave 1 Task 1 PostgreSQL Direct` still FAILED (same two `containsExactly` tests; run log archived) |
| `ecf90bb08f75749ec058227e5c4978b5fa23dc6f` | duplicate removed — tail exactly `20261101.1, 20261102.1, 20261102.2` | **final head** — results in §2 |

## 2. Final head `8e93beaec968dc8bcfee75a7733bff29952930f1`

**TERMINAL (final re-read 2026-11-02):** all **50 check runs on `ecf90bb08…` reached a terminal state — 48 SUCCESS / 2 skipped (by design) / 0 failures**, including every branch-protection required check: `Maven Test Suite` (run id 125025877360, in flight ~50 min, terminal **success**), `PostgreSQL Acceptance Tests`, `CRM Integration Tests`, `Build Next.js Web`, `CRM Deployment Readiness`, `Verify 8 tables, 26 indexes, and tenant isolation` — all success. Skips: `Full-stack ERP human preview` (human-preview lane) and `R0C-12 Canonical Gate G` (dispatch-gated; must be terminal before any certification claim). The authoritative page is the PR #1465 check-runs view of this SHA.

**Locally executed on the exact final tree** (migration content identical to `d0694a00f`; only a test file changed in `8e93beaec`):

| Test | Command | Result |
|---|---|---|
| Fresh chain + validate | Flyway CLI 11.7.2 / PostgreSQL 17.6 | 250/250 applied, validate exit 0 |
| Six-table RLS runtime matrix | `scripts/g1-sixtable-matrix.py` (57 probes as restricted `sanad`) | **57/57 PASS** (`evidence-raw/sixtable-matrix-GREEN-v2.log`) |
| Scenario B historical upgrade | `-target=20261021.1` → full | exit 0, data preserved, validate 0 |
| Scenario C out-of-order catch-up | default exit 1 → `-outOfOrder=true` exit 0 | data preserved, validate 0 |
| Compose init-script regression | fresh PG 17.6, fresh + existing-volume paths | restricted role attributes verified both runs |

## 3. Skipped checks classification

| Check | Disposition |
|---|---|
| `Full-stack ERP human preview` | accepted-by-design (human preview lane, no security gate semantics) |
| `R0C-12 Canonical Gate G` | **blocking-if-required**: skipped on PR heads by design; must be terminal before any production certification claim |

## 4. Prior-run anomalies retained as evidence

- Run 41357909105 (`Maven Test Suite` @ `81b5585f`): `in_progress` since 2026-11-01T22:37:13Z with no completion — recorded as an operational anomaly (runner hang), superseded by fresh runs on later SHAs.
- The six failures on `4a0cf0802` and two on `d0694a00f` were **red evidence for real defects in this run's own changes**, investigated and fixed in-branch (`d0694a00f`, `8e93beaec`) with logs archived under `evidence-raw/logs/`.
