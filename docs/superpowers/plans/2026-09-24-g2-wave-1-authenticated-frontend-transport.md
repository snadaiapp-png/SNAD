# G2 Wave 1 — Authenticated Frontend Transport Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure every authenticated G2 browser request uses SANAD's canonical in-memory bearer-token transport through `apiClient`.

**Architecture:** Add one focused HR G2 API facade that delegates to `apiClient`. UI components consume typed functions from that facade instead of raw `fetch()`, so Authorization, refresh-on-401, timeouts, BFF routing, error normalization, and request headers follow the existing platform path.

**Tech Stack:** TypeScript, React, Next.js, shared `apiClient`, Vitest.

**Spec:** `docs/superpowers/plans/2026-09-24-g2-canonical-remediation-master.md`

## Global Constraints
- Do not persist access tokens in local/session storage.
- Do not add a second auth header manager.
- Preserve `apiClient` as the single browser transport owner.
- Keep same-origin BFF route `/api/platform` and existing token refresh behavior.

## Review Focus
- Access token available only in memory after login.
- 401 refresh + exactly-one retry remains functional.
- State-changing requests preserve `Idempotency-Key`.
- 204 responses are handled without JSON parsing.
- G2 code contains no authenticated raw fetch except intentionally unauthenticated probes.

---

### Task 1: Add a typed G2 HR API facade

**Files:**
- Create: `apps/web/lib/api/hr-g2-api.ts`
- Test: `apps/web/lib/api/hr-g2-api.test.ts`

**Interfaces:**
- Consumes: `apiClient.get/post` from `apps/web/lib/api/client.ts`.
- Produces: `hrG2Api` methods for attendance, schedules, timesheets, leave types, leave balances, leave requests, manager/HR approval, policies, and monthly report.

- [ ] **Step 1: Write failing facade tests** that mock `apiClient` and assert paths, body payloads, query parameters, and `Idempotency-Key` context for mutations.
- [ ] **Step 2: Run** `npm test -- hr-g2-api.test.ts` from `apps/web` and confirm failure because the facade does not exist.
- [ ] **Step 3: Implement `hrG2Api`** with typed DTOs and no direct `fetch()`.
- [ ] **Step 4: Re-run** the focused test and require PASS.
- [ ] **Step 5: Commit** `feat(hr): route G2 APIs through canonical client`.

### Task 2: Migrate G2 pages from raw fetch to `hrG2Api`

**Files:**
- Modify: `apps/web/app/hr/attendance/page.tsx`
- Modify: `apps/web/app/hr/attendance/admin/page.tsx`
- Modify: `apps/web/app/hr/team-attendance/page.tsx`
- Modify: `apps/web/app/hr/schedules/page.tsx`
- Modify: `apps/web/app/hr/timesheets/page.tsx`
- Modify: `apps/web/app/hr/team-timesheets/page.tsx`
- Modify: `apps/web/app/hr/leave/page.tsx`
- Modify: `apps/web/app/hr/leave/approvals/page.tsx`
- Modify: `apps/web/app/hr/leave/policies/page.tsx`
- Modify: `apps/web/app/hr/reports/attendance/page.tsx`

- [ ] **Step 1: Add page-level regression tests** for at least Attendance, Team Attendance, Leave, and Leave Approvals proving they invoke `hrG2Api` and do not depend on global `fetch` for authenticated calls.
- [ ] **Step 2: Run focused tests** and confirm current raw-fetch implementation fails the new contract.
- [ ] **Step 3: Replace raw fetch calls** with typed facade calls while preserving existing loading/error UI.
- [ ] **Step 4: Run** `npm run typecheck`, focused Vitest tests, and `npm run lint`.
- [ ] **Step 5: Commit** `fix(hr): use canonical authenticated transport in G2 pages`.

### Task 3: Add a static regression guard against authenticated raw fetch

**Files:**
- Create: `apps/web/app/hr/g2-authenticated-transport.regression.test.ts`

- [ ] **Step 1: Write a source-level regression test** that scans the G2 page set for direct `fetch("/api/platform/api/v2/hr/` and fails if found.
- [ ] **Step 2: Run the test** against the migrated pages and require PASS.
- [ ] **Step 3: Commit** `test(hr): guard canonical G2 auth transport`.

## Wave Exit Gate

- Focused API facade tests PASS.
- G2 page tests PASS.
- `npm run typecheck` PASS.
- No authenticated G2 raw fetch remains in product pages.
- No product behavior or authorization scope is widened in this wave.