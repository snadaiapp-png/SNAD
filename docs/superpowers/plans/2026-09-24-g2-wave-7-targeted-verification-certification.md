# G2 Wave 7 — Targeted Verification & Certification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove the remediation on one fresh exact branch head before any merge decision.

**Architecture:** Verification is staged from narrow deterministic tests to PostgreSQL Direct integration, then dedicated G2 Playwright, then full exact-head CI. Evidence documents are generated from executed results only.

**Tech Stack:** Maven, PostgreSQL Direct, npm/Vitest, Playwright, GitHub Actions.

**Spec:** `docs/superpowers/plans/2026-09-24-g2-canonical-remediation-master.md`

## Global Constraints
- Never reuse `992f987c` as the final certification SHA after implementation changes.
- Never rerun an old failed SHA and call it final evidence.
- No merge while required jobs are red, missing, cancelled, or still running.
- Preserve PostgreSQL Direct and non-superuser/NOBYPASSRLS execution.
- Evidence must name the exact tested SHA and workflow run IDs.

## Review Focus
- A test passes locally but fails in exact-head CI due to auth/bootstrap differences.
- OpenAPI artifact is stale after route changes.
- Capability counts are changed numerically without verifying the registry contents.
- Playwright passes while backend RLS/authorization integration is red.
- Evidence docs claim PASS based on historical runs.

---

### Task 1: Run frontend targeted verification

**Files:** no product changes unless a test proves a defect.

- [ ] **Step 1:** `cd apps/web && npm run typecheck` — expect PASS.
- [ ] **Step 2:** run focused G2 API/page/navigation tests — expect PASS.
- [ ] **Step 3:** `npm run lint` — expect zero new errors.
- [ ] **Step 4:** `npm test` — expect full Vitest suite PASS.
- [ ] **Step 5:** `npm run build` — expect successful production build.

### Task 2: Run backend targeted verification

**Files:** no product changes unless a test proves a defect.

- [ ] **Step 1:** run `HrEmploymentScopeResolverPostgresTest` and G2 authorization-scope tests.
- [ ] **Step 2:** run `HrG2PostgresIntegrationTest`.
- [ ] **Step 3:** run `HrG2LeavePostgresIntegrationTest` and `HrLeaveWorkflowAdapterStepBindingTest`.
- [ ] **Step 4:** run `HrApiV2AuthorizationTest`, `HrG2OpenApiContractTest`, and the canonical HR OpenAPI contract test.
- [ ] **Step 5:** require zero failures/errors; fix root causes only, then repeat the owning task's test cycle.

### Task 3: Run dedicated authenticated acceptance

- [ ] **Step 1:** provision the same fail-closed ephemeral DB/E2E credential path used by `.github/workflows/g2-authenticated-acceptance.yml`.
- [ ] **Step 2:** start backend with RLS enabled and the non-superuser application role.
- [ ] **Step 3:** start production-built Next.js frontend.
- [ ] **Step 4:** run `npx playwright test --config=playwright-g2.config.ts` with retries=0/workers=1.
- [ ] **Step 5:** require Employee desktop, Manager, HR, and Employee mobile journeys PASS with real mutations.

### Task 4: Create a fresh certification commit and exact-head CI

- [ ] **Step 1:** verify working tree contains only intended remediation/evidence changes.
- [ ] **Step 2:** commit final corrections with a new SHA; record it as `NEW_CERT_SHA`.
- [ ] **Step 3:** push the branch without force if fast-forward; never overwrite newer remote work.
- [ ] **Step 4:** verify PR #1133 head equals `NEW_CERT_SHA`.
- [ ] **Step 5:** wait for fresh exact-head required workflows and record run IDs/statuses.

### Task 5: Reconcile evidence and governance

**Files:**
- Modify: `docs/hrm/g2/evidence/G2-FINAL-EVIDENCE-MANIFEST.md`
- Modify: `docs/hrm/g2/evidence/G2-FINAL-REQUIREMENT-MATRIX.md`
- Modify: `docs/hrm/g2/evidence/HRM-G2-ENGINEERING-CLOSURE.md`
- Update PR #1133 body only after evidence is executed.

- [ ] **Step 1:** mark `992f987c` as historical pre-remediation checkpoint, not final evidence.
- [ ] **Step 2:** record exact `NEW_CERT_SHA`, targeted command results, PostgreSQL Direct results, Playwright run/artifact IDs, and full CI run IDs.
- [ ] **Step 3:** verify all required CI jobs are SUCCESS/SKIPPED only where intentionally non-applicable.
- [ ] **Step 4:** verify current review/approval state is valid for the final SHA; do not carry stale approvals forward.
- [ ] **Step 5:** set `IMPLEMENTATION_COMPLETE=YES` and `ENGINEERING_CERTIFICATION=APPROVED` only if every required gate has executed evidence; leave `MERGE_AUTHORIZATION=NO` until the owner explicitly authorizes merge.

## Wave Exit Gate

`TARGETED_VERIFICATION=PASS`, `POSTGRESQL_DIRECT=PASS`, `G2_AUTHENTICATED_ACCEPTANCE=PASS`, and all required exact-head CI checks green on the same fresh `NEW_CERT_SHA`. No merge is performed by this wave.