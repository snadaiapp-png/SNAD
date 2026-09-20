# HRM-G1 Engineering Closure Certificate (Scaffold — T12)

> **STATUS_AUTHORITY:** DRAFT — IN_PROGRESS  
> **Stage:** G1 (Core Features — Recruitment & Onboarding)  
> **Closure gate:** `G1_FINAL_GATE = NOT_CLOSED`  
> **Reconciliation bound to:** `apps/web/app/hr/hr-execution-data.ts → HR_G1_CLOSURE`  
> **Regression test:** `apps/web/app/hr/hr-g1-closure-state.regression.test.ts` (fails if this file drifts)

---

## 1. Purpose

This certificate is the engineering closure evidence bundle for HRM-G1. It
mirrors the G0 closure pattern (`docs/hrm/g0/evidence/HRM-G0-FINAL-ENGINEERING-CLOSURE.md`).

G1 closure is declared ONLY when:
- T1..T12 all DONE per `HR_G1_CLOSURE.implementationPerCanonicalTask`
- Exact-head CI = SUCCESS on the closure SHA
- Independent human approval = APPROVED
- Protected merge to main = SUCCESS
- Post-merge main verification = SUCCESS
- `G1_FINAL_GATE = PASS`

**Current state:** T1..T10 DONE; T11 IN_PROGRESS (4 of 15 §14 screens scaffolded); T12 PENDING.

## 2. Canonical G1 specification

| Source | Path |
| --- | --- |
| Design spec | `docs/superpowers/specs/2026-09-07-hrm-g1-recruitment-onboarding-design.md` |
| Implementation plan | `docs/superpowers/plans/2026-09-07-hrm-g1-recruitment-onboarding-implementation.md` |
| T1..T9 evidence | PR #998 — `81a86faa HRM-G1: Recruitment & Onboarding through T9` |
| T10 evidence | Commit `b8af9346` — `V20260918_8__hr_t10_candidate_self_service.sql` + V2 controllers |
| T11 (this branch) | `g1/t11-hr-recruitment-onboarding-ui` — 4/15 screens scaffolded |
| T12 (this branch) | `g1/t11-hr-recruitment-onboarding-ui` — closure block + evidence doc + regression test |

## 3. Per-task implementation matrix

| Task | Status | Migration / File | Merge SHA | Evidence |
| --- | --- | --- | --- | --- |
| T1 — Schema (job_openings, applicants, applications, interviews, offers, onboarding_plans, onboarding_tasks) | DONE | `V20260918_2__hr_g1_recruitment_onboarding_schema.sql` | `81a86faa` | PR #998 merged |
| T2 — RLS policies + indexes | DONE | `V20260918_3__hr_g1_rls_policies.sql` | `81a86faa` | PR #998 merged |
| T3 — Domain aggregates (single-active-application, one-acceptance, expiry math, completion derivation) | DONE | (Java domain layer under `hr/recruitment/domain/`) | `81a86faa` | PR #998 merged |
| T4 — Candidate identity (Person link, masked contact) | DONE | `hr/recruitment/application/HrCandidateService.java` | `81a86faa` | PR #998 merged |
| T5 — Applications (state machine, stage history) | DONE | `hr/recruitment/application/HrApplicationService.java` | `81a86faa` | PR #998 merged |
| T6 — Interviews (scheduling, scorecard) | DONE | (HR service layer) | `81a86faa` | PR #998 merged |
| T7 — Offers + approvals (versioned payload, extender/approver separation) | DONE | `V20260918_5__hr_t7_offer_approval_correlation.sql` | `81a86faa` | PR #998 merged |
| T8 — Candidate → Hire conversion (atomic/idempotent ledger) | DONE | `V20260918_6__hr_t8_hire_conversion_governance.sql` | `81a86faa` | PR #998 merged |
| T9 — Onboarding domain (template, plan, task, waive-with-reason) | DONE | `V20260918_7__hr_t9_governed_onboarding.sql` | `81a86faa` | PR #998 merged |
| T10 — V2 API + OpenAPI + idempotency executor + error mapping | DONE | `V20260918_8__hr_t10_candidate_self_service.sql`, `hr/api/v2/recruitment/HrRecruitmentV2Controller.java`, `hr/api/v2/onboarding/HrOnboardingV2Controller.java`, `hr/api/v2/HrmIdempotentCommandExecutor.java`, `HrApiErrorCode.java`, `HrApiErrorResponse.java`, `HrApiExceptionHandler.java` | `b8af9346` | T10 fast-forward into main on this branch's baseline |
| T11 — Web UI (15 §14 screens) | IN_PROGRESS (4/15) | `apps/web/app/hr/recruitment/page.tsx` (Dashboard), `apps/web/app/hr/recruitment/openings/page.tsx` (List), `apps/web/app/hr/recruitment/candidates/page.tsx` (Directory), `apps/web/app/hr/onboarding/page.tsx` (Dashboard), `apps/web/app/hr/onboarding/plans/[planId]/page.tsx` (Plan Detail), `apps/web/app/hr/components/hr-data-table.tsx` + `hr-state-badge.tsx` (shared), `apps/web/lib/api/hr-v2-recruitment-api.ts` (API client), `apps/web/lib/i18n/locales/hrm-g1-i18n.ts` + spread into `ar.ts`/`en.ts`, `apps/web/lib/auth/capabilities.ts` (RECRUITMENT_* + ONBOARDING_* capabilities), `apps/web/app/hr/hr.module.css` (stateBadge, kpiGrid, activityList, filterBar, taskList) | _this branch_ | Partial — 4 of 15 §14 screens scaffolded with i18n/RTL/a11y/tests |
| T12 — Security/integration/closure | IN_PROGRESS | This file (closure scaffold) + `HR_G1_CLOSURE` reconciliation block in `apps/web/app/hr/hr-execution-data.ts` + `apps/web/app/hr/hr-g1-closure-state.regression.test.ts` | _this branch_ | Final gate PENDING T11 done + independent approval + exact-head CI |

## 4. T11 partial coverage detail

The 15 §14 screens (per `docs/superpowers/specs/2026-09-07-hrm-g1-recruitment-onboarding-design.md` §14):

| # | Screen | Status |
| --- | --- | --- |
| 1 | Recruitment Dashboard | DONE (this branch) |
| 2 | Job Openings List | DONE (this branch) |
| 3 | Job Opening Detail | NOT IMPLEMENTED |
| 4 | Candidate Directory | DONE (this branch) |
| 5 | Candidate Profile | NOT IMPLEMENTED |
| 6 | Applications Board (Pipeline) | NOT IMPLEMENTED |
| 7 | Application Detail | NOT IMPLEMENTED |
| 8 | Interview Scheduling | NOT IMPLEMENTED |
| 9 | Interview Feedback | NOT IMPLEMENTED |
| 10 | Offer Editor | NOT IMPLEMENTED |
| 11 | Offer Approval | NOT IMPLEMENTED |
| 12 | Hire Conversion | NOT IMPLEMENTED |
| 13 | Onboarding Dashboard | DONE (this branch) |
| 14 | Onboarding Plan Detail | DONE (this branch) |
| 15 | Onboarding Tasks (my tasks) | NOT IMPLEMENTED |

**Coverage:** 4 / 15 = **26.7%**. T11 acceptance criterion "§14 table coverage complete" is NOT satisfied. T11 = NOT CLOSED.

## 5. T12 closure deliverables (current branch)

| Deliverable | Path | Status |
| --- | --- | --- |
| HR_G1_CLOSURE reconciliation block | `apps/web/app/hr/hr-execution-data.ts` | DONE (this branch) |
| Engineering closure certificate | `docs/hrm/g1/evidence/HRM-G1-ENGINEERING-CLOSURE.md` | DONE (this file, scaffold) |
| Closure-state regression test | `apps/web/app/hr/hr-g1-closure-state.regression.test.ts` | DONE (this branch) |
| Extended security suites (RLS/authorization matrices final) | _N/A — backend_ | NOT IMPLEMENTED (backend suite requires PostgreSQL Direct env) |
| Consolidated gate suite run | _N/A_ | BLOCKED_BY_ENVIRONMENT (no host-native PostgreSQL in this run) |
| CI role contract + PMV green on closure merge SHA | _N/A_ | PENDING (no merge authorized yet) |
| Final cross-tenant sweep green | _N/A_ | PENDING (requires full PostgreSQL Direct test run) |

## 6. Blockers (fail-closed)

1. **T11 coverage incomplete (4/15 screens).** Cannot close T11 until all 15 §14 screens are implemented with i18n/RTL/a11y/tests per spec.
2. **Independent human approval.** Project Constitution §3.5 requires independent approval on the final SHA. Cannot self-approve.
3. **Branch protection on main.** Not yet configured per the Constitution; merge-protection gate cannot be satisfied.
4. **PostgreSQL Direct test environment.** This session has no host-native PostgreSQL (`psql` not available). The mandatory suite (3,507 tests, ~155min) cannot be executed locally; CI run on GitHub is required for evidence.
5. **Exact-head CI on closure SHA.** Requires a push + GitHub Actions run on the exact closure SHA.

## 7. Claim discipline

This certificate intentionally does NOT claim:
- `G1_CLOSED`
- `PRODUCTION_READY`
- `PRODUCTION_CERTIFIED`
- `SAUDI_LEGAL_COMPLIANT`

Engineering closure is `PENDING`. Legal certification is `BLOCKED` (independent human gate). Production authorization is `NO`.

## 8. G1 Final Gate

```
G1_FINAL_GATE = NOT_CLOSED
```

Reason: T11 acceptance criterion (§14 coverage complete) is not satisfied; T12 closure deliverables are scaffolded but not validated against exact-head CI + independent approval.

## 9. Path to closure (downstream)

To flip `G1_FINAL_GATE = NOT_CLOSED` → `PASS`:

1. Complete remaining 11 of 15 §14 screens with i18n/RTL/a11y/tests.
2. Push branch, run exact-head CI on GitHub Actions; verify all gates green.
3. Obtain independent human approval on the final SHA.
4. Verify branch protection on main is configured (Constitution §3.5).
5. Protected merge to main; record `G1_FINAL_MAIN_SHA`.
6. Post-merge: run frontend lint+typecheck+test+build; run backend Maven test suite against host-native PostgreSQL; verify `FAILURES=0, ERRORS=0, UNEXPLAINED_SKIPS=0`.
7. Update `HR_G1_CLOSURE.implementation` → `DONE` and `engineeringCertification` → `APPROVED` (only after steps 1-6).
8. Update this certificate to `STATUS_AUTHORITY: CURRENT` and declare `G1_FINAL_GATE = PASS`.

Until ALL steps complete: `G1_FINAL_GATE = NOT_CLOSED`.
