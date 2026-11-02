# 02 — POST-MERGE CI & MAVEN FINAL VERIFICATION (Phase 1)

**Exact SHA under certification:** `f95a30b64e75815d571e582e1b555c01bcd53abe`
**Evidence window:** 2026-11-02T09:09Z → 2026-11-02T10:00Z (live API evidence; polling of the run queue continues until terminal)

## 1.1 What actually happened after the three merges (reconstructed from API evidence, not assumption)

Three PRs merged to `main` within 27 seconds (08:47:09Z #1465, 08:47:28Z #1466, 08:47:36Z #1467). Each push started a post-merge verification run:

| Run | Head SHA (= merge commit) | Trigger | Status at evidence time | Explanation |
|---|---|---|---|---|
| `41415592079` | `177e27c2` (#1465 merge) | push | **completed / success** (10:02Z) — jobs A (Frontend), B (Backend compile), C (PostgreSQL Direct integration, full Maven suite), D (HRM focused security/RLS), E (Security & governance scans), F (Final evidence aggregation) ALL `success` | first run in the workflow's concurrency group |
| `41415628833` | `90576df0` (#1466 merge) | push | **cancelled** (08:47:31Z) | cancelled by the concurrency group when a newer run queued — GitHub queue semantics, NOT a test failure |
| `41415645715` | `f95a30b6` (#1467 merge = current main) | push | **in_progress** (started ~10:02Z after the leading run released the concurrency slot) | the authoritative run for the exact SHA; polling until terminal |

The workflow (`.github/workflows/post-merge-verification.yml` at f95a30b6) runs six jobs: `JOB A — Frontend verification`, `JOB B — Backend compile`, `JOB C — PostgreSQL Direct integration` (full Maven suite against host-native PostgreSQL with the `sanad` least-privilege role contract asserted), `JOB D — HRM focused security/RLS`, `JOB E — Security & governance scans`, `JOB F — Final evidence aggregation`. In the leading run (`177e27c2`) **all six concluded `success`**.

**Rule applied:** the command forbids treating PR-head CI success as post-merge success, and forbids assembling certification from mixed SHAs. Therefore this phase remains **BLOCKED (pending terminal)** until run `41415645715` completes on `f95a30b6`. No local re-run was substituted for the authoritative CI run; local evidence (03/04/05) is complementary and SHA-bound to f95a30b6 by content.

## 1.2 Workflow Y2 Production Release Orchestrator — failure forensics

**Run:** `41415840956` (run_number 219, event `workflow_run`, started 08:49:18Z, failed 08:49:35Z).
**Failed step:** 2 — `Verify exact current main and classify production authorization` (job `125179348971`).

**Runtime log evidence (verbatim):**
```
HEAD_SHA: 177e27c2648ce5ba02988413981a650d3d7a2a5a
MANUAL_AUTHORIZATION: false
##[error]Image publication SHA is no longer exact current main
Expected current main: 177e27c2648ce5ba02988413981a650d3d7a2a5a
Observed current main: f95a30b64e75815d571e582e1b555c01bcd53abe
##[error]Process completed with exit code 1.
```

**Root-cause classification (per the command's demanded taxonomy):**

| Candidate cause | Verdict | Evidence |
|---|---|---|
| Correct security refusal of missing authorization | **YES — primary** | Y2 triggered by "Publish Render Backend Image" completed for `177e27c2`; the workflow requires the image SHA to equal current `refs/heads/main` before anything else; main had already advanced to `f95a30b6` (PRs #1466/#1467 merged seconds later) → guard fired, exit 1, release NOT dispatched |
| SHA divergence | Ruled out as defect | main never moved *backwards*; the guard correctly rejected a *stale* publication SHA |
| Protection-condition malfunction | Ruled out | guard logic in `.github/workflows/workflow-y2-production-release-orchestrator.yml` matches the log exactly |
| Workflow bug | Ruled out | the follow-up Y2 run `41415955410` (08:50:16Z, publication SHA = `f95a30b6`) passed step 2, took the "No production release authorization present; leaving main unchanged" branch (`exit 0`), skipped dispatch steps 3–6, recorded "clean non-release evidence" (step 7, success) |
| Other | — | "Workflow Production 3-User Final Gate" and all dispatch steps in SCP One-Time Production Recovery `41415955344` and G2 Recovery Release Dispatch `41415955383` were **skipped** — no production action executed |

**Conclusion:** `Y2 #41415840956` is a **correct fail-closed denial** — the documented classification "failure of a workflow run = success of the prevention policy". No remediation is required and the guard MUST NOT be modified. Production release authorization remains absent; main is unchanged.

## 1.3 Required-check coverage on the exact SHA

- Branch-protection required contexts (Maven Test Suite, CRM Integration Tests, Verify 8 tables, Build Next.js Web, provenance, CRM Deployment Readiness) were all green **on each merged PR head** (verified via check-runs at `ecf90bb0`, `4e37865b`, `62944e8e`) — this is what permitted the protected merges.
- Post-merge terminal status on `f95a30b6` itself: **pending** (see §1.1). This is the only outstanding input for this phase.

## 1.4 Acceptance criterion status

> "نتائج نهائية ناجحة لجميع الفحوص المطلوبة على SHA موحد، مع تفسير موثق لكل تخطٍ"
> (Terminal successful results for all required checks on a single SHA, with documented explanation for every skip)

- PHASE1_STATUS: **BLOCKED — awaiting terminal state of run `41415645715` on `f95a30b6`** (single-SHA discipline enforced; no results from other SHAs were counted toward certification).
- Supporting evidence (not counted toward the exact-SHA gate): the predecessor run `41415592079` at `177e27c2` — one merge-commit behind `f95a30b6`, differing **only** by PR #1467 (docs + `.github/CODEOWNERS` + governance records; zero application code or migration changes, verified from `pr1467-files.json`) — is **fully green**, including the full Maven suite on PostgreSQL Direct (job C) and HRM security (job D). This makes a failure on `f95a30b6` unlikely and binds the code content of the certified SHA to a passing run, while the exact-SHA run completes.
- Skips observed and explained: PR #1466/#1467 "Full-stack ERP human preview" = `skipped` (environment-gated preview job, not a required check); Y2/SCP/G2 dispatch steps skipped = fail-closed design; "Workflow Production 3-User Final Gate" skipped on push = dispatch-only workflow.
- No `continue-on-error` was used to mask anything; the only `continue-on-error: true` in the post-merge workflow is the role-contract *assertion helper* step (`Assert least-privilege role contract`), which is followed by an explicit hard assertion inside the same step — recorded here for transparency; its outcome was `success` in the leading run.
