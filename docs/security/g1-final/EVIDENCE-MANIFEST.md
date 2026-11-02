# EVIDENCE-MANIFEST.md

**Command:** SANAD-G1-P0-UNIFIED-2026-11-02 · **Generated:** 2026-11-02 UTC · **Executor:** Z.ai agent (isolated Linux sandbox; PostgreSQL 17.6; Flyway CLI 11.7.2; GitHub REST API via a scoped PAT)

Every finding below is bound to a SHA, an environment, and a PASS/FAIL/BLOCKED verdict. Raw API responses and logs are archived in the executor workspace under `workspace/g1-20261102/evidence-raw/` (filenames in brackets).

## Primary SHAs

| SHA | Role |
|---|---|
| `12f92d7cfaa3bff45dee214029fa418b84080310` | `main` HEAD at baseline (= PR #1458 merge) |
| `81b5585fdc3254523b69572ca0fd812a6063249e` | PR #1465 head at baseline (superseded) |
| `4a0cf08028cc59045353f82faa3fb9bfbef90593` | PR #1465: + `V20261102_2` + test pins |
| `d0694a00f…` | PR #1465: guard scoping (CI-verified fix of run-observed defect) |
| `8e93beaec…` | PR #1465: pending-sequence fix #1 (still contained a duplicate `20261102.2` entry) |
| `ecf90bb08f75749ec058227e5c4978b5fa23dc6f` | **PR #1465 final head** (test-only correction; migration files identical to `d0694a00f`; all runtime evidence binds to this tree) |
| `4e37865be2fc592159f4777fbe33c1e1c28727af` | PR #1466 head (self-hosted identity split) |

## Findings register

| # | Finding | Classification | SHA | Evidence | Verdict |
|---|---|---|---|---|---|
| F1 | T2 batch 2 tables (`provisioning_jobs`, `provisioning_job_steps`, `subscription_commands`) had **no runtime ACL** for `sanad` — same masked-gap class as the token tables | New P0 defect (fail-closed availability) | `d0694a00f` tree | first matrix run RED (5/47, `permission denied`); `V20261101_1` chain probe 0 grants | **FIXED** by `V20261102_2`; matrix 57/57 GREEN |
| F2 | Token tables runtime ACL (`V20261102_1`) correct and sufficient | — | `f5434274e3`/`8e93beaec` | matrix sections 1-3 | **VERIFIED** |
| F3 | Self-hosted compose maps `POSTGRES_USER := DATABASE_USERNAME` → app pool runs as initdb **superuser** → RLS advisory on app plane | P1 CONFIRMED_VULNERABILITY (deployment path) | `12f92d7cf` | `docker-compose.prod.yml` L8, `docker-compose.self-hosted.yml` | **REMEDIATED** by PR #1466 (identity split + init script, regression-tested) |
| F4 | V20261102_2 append-only guard conflicted with owner-as-`sanad` CI fixture model → 6 gates red on `4a0cf0802` | Defect in this run's change | `4a0cf0802` | runs 41364348714/41364348777 logs | **FIXED** `d0694a00f` |
| F5 | Duplicate `20261102.2` entry in two `containsExactly` pending sequences → 2 gates red on `d0694a00f`; the first fix (`8e93beaec`) still left one duplicate per list (expected +2 vs actual) — CI kept the finding alive until the tail was reduced to exactly `20261101.1, 20261102.1, 20261102.2` | Defect in this run's change | `d0694a00f`/`8e93beaec` | runs 41364747831 + Wave-1 log on `8e93beaec` | **FIXED** `ecf90bb0` |
| F6 | `Maven Test Suite` on `81b5585f` stuck `in_progress` >12 h | Operational anomaly | `81b5585f` | run 41357909105 | superseded; recorded |
| F7 | G1 governance: empty-body approval satisfied merge; `dismiss_stale_reviews=false`, no CODEOWNERS, checkbox state not evidence-bound | Governance findings | `12f92d7cf` | branch-protection API, review bodies | documented + controls proposed (`g1-governance-controls.md`, CODEOWNERS) |
| F8 | Issue #1462 checkbox check/uncheck history not provable via API (no body-revision endpoint) | UNRESOLVED | — | timeline JSON (0 `edited` events) | recorded as UNRESOLVED |
| F9 | `outOfOrder=true` required for DBs at `20261031.1` without `20261028.2` | Bounded, evidenced | `8e93beaec` | Scenario C | PASS with restrictions; not adopted globally |
| F10 | Backup-restore rehearsal | BLOCKED | — | Scenario D | `HISTORICAL_BACKUP_REHEARSAL=BLOCKED` |
| F11 | Independent security review | BLOCKED | — | reviews API (1 dismissed review, 0 active) | `INDEPENDENT_REVIEW=BLOCKED`; formal request posted |

## Verdict summary

```text
SOURCE_REMEDIATION:        PASS
PR1465_MERGE_READINESS:    HOLD
G1_CERTIFICATION:          HOLD (BLOCKED on independent review + backup rehearsal + terminal CI on final SHA)
PRODUCTION_RELEASE:        NO-GO
```

## Secrets posture

No token/secret values are stored in this package. The five tokens pasted in chat remain **exposed and must be rotated/revoked**; four of them (Vercel, Render, Supabase, Resend) were never used; the GitHub PAT was used transiently for API/git operations in the isolated sandbox only.
