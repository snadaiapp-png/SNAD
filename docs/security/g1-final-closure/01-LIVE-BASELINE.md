# 01 — G1 LIVE BASELINE & EVIDENCE RECONCILIATION

**Command:** SNAD-P0-G1-1462-FINAL-CLOSURE (Phase 0) · **Repository:** snadaiapp-png/SNAD
**Verification window:** 2026-11-02T09:09Z → 2026-11-02T10:00Z (all data pulled live from the GitHub REST API and the local clone; historical baseline text from the command was NOT trusted)
**Verified main SHA:** `f95a30b64e75815d571e582e1b555c01bcd53abe`
**Verifier:** Independent engineering & security execution platform (this session), GitHub identity `snadaiapp-png` (PAT-scoped API account of the project owner)

---

## 0.1 Live state (re-verified, not assumed)

| Item | Historical baseline (command §2) | Live verified 2026-11-02 | Verdict |
|---|---|---|---|
| main HEAD | `f95a30b64e75815d571e582e1b555c01bcd53abe` | `f95a30b64e75815d571e582e1b555c01bcd53abe` (commit "docs(g1): G1 final evidence package + governance controls (Issue #1462) (#1467)", committer date 2026-11-02T08:47:36Z; `repo.pushed_at=2026-11-02T08:47:38Z`) | MATCH — no drift since command issuance |
| PR #1465 | MERGED | `state=closed, merged=true`, merged_at 2026-11-02T08:47:09Z, merge_commit `177e27c2648ce5ba02988413981a650d3d7a2a5a`, head `ecf90bb08f75749ec058227e5c4978b5fa23dc6f` | VERIFIED |
| PR #1466 | MERGED | `state=closed, merged=true`, merged_at 2026-11-02T08:47:28Z, merge_commit `90576df061ff7a79ce99b2564bcf6d554368c64d`, head `4e37865be2fc592159f4777fbe33c1e1c28727af` | VERIFIED |
| PR #1467 | MERGED | `state=closed, merged=true`, merged_at 2026-11-02T08:47:36Z, merge_commit `f95a30b64e75815d571e582e1b555c01bcd53abe` (= main HEAD), head `62944e8e22df3e474e5dd210e4571f6c3cc4ef7f` | VERIFIED |
| Issue #1462 | OPEN, 0/6 formally closed | `state=open`, locked=false, 15 comments (all by `snadaiapp-png`), updated 2026-11-02T01:20:34Z; body contains six `- [ ]` unchecked mandatory gates | VERIFIED — 0/6 |
| Post-Merge Main Verification | Run #41415645715 PENDING | `status=pending, conclusion=null` (queued, zero jobs). Cause established: workflow concurrency group — run #41415592079 (SHA `177e27c2`, PR #1465 merge) started first at 08:47:16Z and is still `in_progress`; run #41415628833 (SHA `90576df0`, PR #1466 merge) was `cancelled` by the same group at 08:47:31Z | VERIFIED — queue semantics, not a failure |
| Workflow Y2 #41415840956 | FAILURE at authorization verification | `status=completed, conclusion=failure` at step 2 "Verify exact current main and classify production authorization". Runtime log: `HEAD_SHA: 177e27c2…` (image publication SHA) vs `Observed current main: f95a30b6…` → `##[error]Image publication SHA is no longer exact current main` → exit 1. **Correct fail-closed SHA-binding refusal** (full analysis in 02) | VERIFIED — security barrier worked as designed |
| G1_CERTIFICATION=HOLD / P0_SECURITY_HOLD=ACTIVE / PAYMENTS=OFF / PRODUCTION_RELEASE=NO-GO | fixed | unchanged; no workflow dispatched any production release on this SHA (Y2 success run #41415955410 = "clean no-op": authorization absent → dispatch skipped; SCP/G2 recovery workflows: dispatch steps skipped) | VERIFIED |

### Protection surface (live)

- Branch protection `main` (HTTP 200): required checks = `Build Next.js Web`, `provenance`, `CRM Integration Tests`, `Maven Test Suite`, `CRM Deployment Readiness`, `Verify 8 tables, 26 indexes, and tenant isolation`; enforce_admins=true; required approvals=1; dismiss_stale=false; require_code_owner_reviews=false; allow_force_pushes=false; allow_deletions=false.
- `.github/CODEOWNERS` exists at f95a30b6 (added by PR #1467) covering `db/migration/`, `tests/security/`, `docker-compose*.yml`, `docker/postgres-init/`, `docs/security/` → `@snadaiapp-png`. Note: `require_code_owner_reviews` is currently `false`, so CODEOWNERS is declarative only — recorded as governance gap G-02 in 10/11.
- PR head check suites (required contexts) were green at each merged head: #1465 `ecf90bb0` (incl. Maven Test Suite, CRM Integration Tests, PostgreSQL Acceptance), #1466 `4e37865b`, #1467 `62944e8e`. Reviews: single reviewer `abdulrhmansenan1985-creator` on all three (1–2 approvals; one DISMISSED review on #1465) — independence analysis in 07.

### Flyway ledger (live, from repo tree at f95a30b6)

- `apps/sanad-platform/src/main/resources/db/migration` contains **222 migration files** (plus `db/vendor/postgresql`); full chain applies as **250 migrations** (222 versioned + repeatable set) ending at `v20261102.2`.
- The five mandated migrations are present: `V20261028_2__d6_auth_token_tenant_index_and_rls.sql`, `V20261031_1__d6_t2_batch2_rls_closure.sql`, `V20261101_1__auth_token_index_acl_and_identity.sql`, `V20261102_1__auth_token_runtime_grants.sql` (PR #1465), `V20261102_2__t2_batch2_runtime_grants.sql` (PR #1465).

---

## 0.2 Evidence matrix

Statuses allowed: VERIFIED_PASS / VERIFIED_FAIL / BLOCKED / UNVERIFIED / NOT_APPLICABLE_WITH_EVIDENCE.

| REQUIREMENT_ID | REQUIRED_EVIDENCE | EVIDENCE_SOURCE | COMMIT_SHA | ENVIRONMENT | EXECUTION_TIMESTAMP (UTC) | TEST_COMMAND | EXPECTED_RESULT | ACTUAL_RESULT | STATUS | REVIEWER | APPROVAL_REFERENCE | BLOCKER |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| E00-MAIN-IDENTITY | main HEAD identity proof | `GET /repos/.../commits/main` + local `git rev-parse HEAD` | f95a30b64e75815d571e582e1b555c01bcd53abe | GitHub API + local clone | 2026-11-02T09:10Z | `curl api.github.com/repos/snadaiapp-png/SNAD/commits/main` | SHA equals baseline | f95a30b64e75815d571e582e1b555c01bcd53abe | VERIFIED_PASS | executor | — | — |
| E00-PR-STATE | #1465/#1466/#1467 merged state | `GET /pulls/{n}` ×3 | f95a30b64e75815d571e582e1b555c01bcd53abe | GitHub API | 2026-11-02T09:10Z | per-PR REST reads | all merged with recorded merge SHAs | see table above | VERIFIED_PASS | executor | — | — |
| E00-ISSUE-STATE | #1462 open, 0/6 boxes | `GET /issues/1462` + body parse | — | GitHub API | 2026-11-02T09:10Z | body regex `- \[ \]` ×6 | open, 0 checked | open, 6× `- [ ]` | VERIFIED_PASS | executor | — | — |
| E00-PROTECTION | branch protection + CODEOWNERS | `GET /branches/main/protection`, `GET /contents/.github/CODEOWNERS` | f95a30b64e75815d571e582e1b555c01bcd53abe | GitHub API | 2026-11-02T09:11Z | REST reads | protection active; CODEOWNERS present | 6 required contexts, enforce_admins, 1 review; CODEOWNERS present, require_code_owner_reviews=false | VERIFIED_PASS (gap G-02 noted) | executor | — | — |
| E00-POSTMERGE-QUEUE | post-merge run terminal state on exact SHA | `GET /actions/runs/{41415592079,41415628833,41415645715}` | 177e27c2 / 90576df0 / f95a30b6 | GitHub Actions | 2026-11-02T09:10Z→10:02Z (polling continues) | REST reads | terminal PASS on f95a30b6 | `41415592079`@177e27c2 = **completed/success (all 6 jobs incl. PostgreSQL Direct Maven suite)**; `41415628833`@90576df0 = cancelled by concurrency; `41415645715`@f95a30b6 = **in_progress** (started ~10:02Z) | BLOCKED (pending terminal on exact SHA; predecessor SHA fully green — 02 §1.4) | executor | — | run queue not terminal at evidence time |
| E00-Y2-REFUSAL | Y2 failure root cause | `GET /actions/jobs/125179348971/logs` | 177e27c2 (trigger) / f95a30b6 (main) | GitHub Actions | 2026-11-02T09:12Z | log retrieval + step timeline | refusal is correct SHA-binding guard | `Image publication SHA is no longer exact current main` (177e27c2 vs f95a30b6), exit 1 | VERIFIED_PASS (correct denial) | executor | — | — |
| E01-SIXTABLE-MATRIX | 57-test RLS matrix on chain-only DB | isolated PostgreSQL 17.6.0 + Flyway 11.7.2, full chain migrated fresh | f95a30b64e75815d571e582e1b555c01bcd53abe | local isolated cluster `rehearsal_final_a` (127.0.0.1:5434) | 2026-11-02T09:52Z | `scripts/finalclosure_sixtable_matrix_v2.py` | MATRIX=GREEN | RESULT: PASS=57 FAIL=0, MATRIX=GREEN | VERIFIED_PASS | executor (independent review pending, see 07) | — | — |
| E01-E2E-LIFECYCLE | auth/session/reset/subscription/worker E2E | same isolated chain DB (+ fix-contract clone `e2e_fixed` for F-01) | f95a30b64e75815d571e582e1b555c01bcd53abe | local isolated cluster | 2026-11-02T09:56Z | `scripts/finalclosure_e2e_lifecycle.py` | E2E=GREEN | pure chain 36/38 (F-01 finding), with F-01 fix grants 38/38 | VERIFIED_PASS with finding F-01 | executor | — | F-01 documented in 04 |
| E01-FLYWAY-REHEARSAL | scenarios A/B/C at exact SHA | `scripts/finalclosure_flyway_rehearsal*.sh` logs | f95a30b64e75815d571e582e1b555c01bcd53abe | local isolated cluster | 2026-11-02T09:26Z→09:46Z | Flyway migrate/validate per scenario | A fresh OK; B upgrade OK+data preserved; C refusal fail-closed | A: 250 applied, validate OK; B3: 10 applied, validate OK, data preserved; C3: default migrate refused (exit 1), data intact, isolated outOfOrder probe OK | VERIFIED_PASS | executor | — | — |
| E01-BACKUP-RESTORE | authorized production backup restore rehearsal | requires owner-provided backup via secure channel | n/a | isolated env | — | — | restore + migrate + verify | no authorized backup available to the executor | BLOCKED | executor | — | no backup delivered; see 06 |
| E01-IND-REVIEW | independent security review covering f95a30b6 | review records (PR reviews, docs/security/independent-assurance) | f95a30b64e75815d571e582e1b555c01bcd53abe | GitHub + repo | 2026-11-02T09:40Z | records audit (07) | approval by reviewer independent of remediation executor | only owner self-approvals; assessor slots PENDING | BLOCKED | executor | — | no independent reviewer engaged for G1 scope |
| E01-PROD-READONLY | authorized production read-only verification | production read credentials via approved channel | n/a | production | — | prepared query set (08) | catalog identity proof | not authorized to execute | BLOCKED | executor | — | standing credential-rotation policy; no scoped read grant |

**Immutable security state carried forward unchanged:** `P0_SECURITY_HOLD=ACTIVE`, `G1_CERTIFICATION=HOLD`, `PAYMENTS=OFF`, `PRODUCTION_RELEASE=NO-GO`, `PRODUCTION_DEPLOYMENT=PROHIBITED`, `PRODUCTION_SQL_WRITE=PROHIBITED`, `AUTOMATIC_MERGE=PROHIBITED`, `SECURITY_GATE_BYPASS=PROHIBITED`.
