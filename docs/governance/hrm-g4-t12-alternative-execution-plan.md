# HRM G4-T12 Alternative Path — Review Plan

**Status:** DRAFT / REVIEW REQUIRED — no workflow dispatched, no deployment, no merge.
**Base SHA:** `ef86ffd484ed307fada7fb194e93659298391486`
**Issue:** [#1439](https://github.com/snadaiapp-png/SNAD/issues/1439)
**Constraint:** PR #1331 is merged and closed; do not reopen it or depend on its being open.

## 1. Objective

Define a reviewable, fail-closed path to collect exact-SHA HRM/Payroll G4 evidence and the T12 preview evidence without weakening branch protections, tenant isolation, RLS, production gates, or release guardrails.

## 2. Non-goals and hard stops

- Do not merge or deploy; do not dispatch any workflow in this change.
- Do not run production or protected-environment workflows without explicit authorization and approved test identities/credentials.
- Do not enable payments or statutory payroll.
- Do not weaken RLS, tenant checks, RBAC, or use SUPERUSER/BYPASSRLS to claim tenant-isolation proof.
- Do not mark Issue #1439 or G4 closed based on historical artifacts or missing status records.
- Do not make a production deployment a prerequisite for a preview-only T12 evidence run.

## 3. Proposed T12 alternative

1. Add a new manual workflow on `main` dedicated to T12 preview evidence. It must not require PR #1331 to be open.
2. Require explicit inputs: full 40-character candidate SHA, evidence/PR reference, and a reviewer-approved run purpose. Reject SHA values that are not reachable from the approved repository history; check out the exact SHA and assert `git rev-parse HEAD` equals the input.
3. Restrict permissions to read-only repository/PR metadata plus only the narrowly required preview deployment permission. Use a protected `preview` environment; never use production secrets, production target flags, or `--prod`.
4. Before any preview deployment step, require a separate explicit authorization gate and confirm the run is approved. Until that gate and required credentials are authorized, collect non-deploying evidence only and stop before deployment.
5. Upload a provenance manifest and test reports as Actions artifacts. Include repository, workflow path/ref, run ID/attempt, exact commit SHA, UTC timestamps, tool/runtime versions, test commands, exit codes, artifact names/checksums, preview target (if authorized), and reviewer identity. Never include secrets or credentials.
6. Add a fail-closed summary: any missing report, SHA mismatch, failed/skipped required test, absent tenant-isolation evidence, or missing reviewer approval results in `NOT_READY`, not PASS.

## 4. Evidence matrix required for G4

| Evidence | Acceptance condition |
|---|---|
| JOB A–F | Each required job has a real run URL, run ID, conclusion, and exact SHA match; JOB F manifest references every predecessor artifact by immutable identity/checksum. |
| PostgreSQL Direct / RLS | Missing/empty tenant context fails closed; valid tenant succeeds only within its tenant; cross-tenant read/write is denied; no RLS bypass used for proof. |
| T10 authenticated acceptance | Real workflow run on the candidate SHA; reports and logs show required tests passed with zero failures, errors, or skips. |
| Payroll lifecycle | Draft → calculated → reviewed → approved; idempotency/replay, concurrency/version conflict, and expected 403/409 behavior are evidenced. Do not mutate real production payroll data. |
| RBAC/UI | Authorized least-privilege test identity has effective `HRM.PAYROLL.VIEW`; unauthorized identity receives 403 and does not see payroll navigation; evidence is tenant-scoped and redacted. |
| Artifacts | Actual artifacts are downloadable from the cited run, retained, checksummed, and referenced by the final manifest. A workflow definition or summary text alone is insufficient. |
| Independent review | Reviewer identity, scope, findings, unresolved exceptions, timestamp, and explicit acceptance decision are recorded. |

## 5. Current known blockers

- The existing `hrm-g4-t12-vercel-cli-preview.yml` requires PR number 1331 to be open and checks the old certification branch; PR #1331 is closed/merged, so this workflow cannot serve as the alternative unchanged.
- The current connector query for commit `ef86ffd484ed307fada7fb194e93659298391486` returned no PR-triggered workflow runs and no combined statuses. This is **absence of evidence from a limited query**, not proof that no other runs exist.
- No exact-SHA run IDs or artifacts for the current candidate have been established in this review.
- Production UI/authorization tests remain gated on explicit authorization and approved test identities.

## 6. Review and execution sequence

1. Review this plan and the proposed workflow design; approve or request changes.
2. Implement the alternative workflow in a separate reviewable change. Keep this plan-only branch non-deploying.
3. Review workflow permissions, environment use, SHA validation, artifact provenance, and fail-closed behavior.
4. Merge only through normal protected review after explicit approval; this plan itself does not authorize merge.
5. Run non-production, non-deploying checks only after authorization; record actual run IDs and artifacts.
6. Do not run preview deployment until explicitly authorized; do not run production tests without explicit authorization.
7. Update Issue #1439 with evidence links and a gap-by-gap disposition. Keep it open until independent review and authorized final acceptance are complete.

## 7. Required controls

`G1 = HOLD` · `P0 SECURITY HOLD = ACTIVE` · `NO MERGE` · `NO DEPLOY` · `PAYMENTS = OFF`

RLS and tenant isolation must not be weakened. Saudi statutory payroll remains disabled. This document is a plan, not a certificate, execution record, or acceptance decision.
