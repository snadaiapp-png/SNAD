# G2 Wave 5 — UI State & Navigation Contracts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make G2 HR pages deterministic, capability-correct, accessible, and stable for both users and automated acceptance.

**Architecture:** Keep `HrWorkspace` as the shared shell, but give page content explicit semantic anchors and stable test IDs. Render role-specific controls from canonical capabilities while backend authorization remains authoritative.

**Tech Stack:** React, Next.js, TypeScript, Vitest/Testing Library, Playwright.

**Spec:** `docs/superpowers/plans/2026-09-24-g2-canonical-remediation-master.md`

## Global Constraints
- UI capability checks are UX-only and must not replace backend authorization.
- Preserve RTL/Arabic support and existing SDS styling.
- Avoid duplicate ambiguous page-heading selectors in acceptance tests.
- Loading readiness must be represented by product state, not network heuristics.

## Review Focus
- Two `<h1>` elements must not make page identity ambiguous.
- Unauthorized controls are hidden, but backend still denies forged calls.
- Empty state differs from permission error and transport error.
- Mobile view preserves all mandatory actions.
- Locale changes do not break stable selectors.

---

### Task 1: Add stable page identity anchors

**Files:**
- Modify: G2 page files under `apps/web/app/hr/**/page.tsx`
- Test: page component tests.

- [ ] **Step 1: Add failing tests** requiring each G2 page to expose a unique `data-testid="g2-page-title"` or equivalent role/name contract on the content heading.
- [ ] **Step 2: Implement the stable page title anchor** without changing `HrWorkspace` shell semantics.
- [ ] **Step 3: Run focused component tests** and require PASS.
- [ ] **Step 4: Commit** `test(hr): stabilize G2 page identity selectors`.

### Task 2: Make readiness and mutation state explicit

**Files:**
- Modify: `attendance/page.tsx`, `leave/page.tsx`, `leave/approvals/page.tsx`, `team-attendance/page.tsx`, `team-timesheets/page.tsx`, `schedules/page.tsx`.

- [ ] **Step 1: Add tests** for `loading → ready`, `error → retry`, mutation busy/disabled, and success state.
- [ ] **Step 2: Add stable readiness markers** such as `data-testid="attendance-ready"` after successful data load.
- [ ] **Step 3: Ensure mutation controls expose one canonical current action** (for attendance, clock-in vs clock-out) rather than rendering contradictory controls.
- [ ] **Step 4: Run page tests and typecheck.**
- [ ] **Step 5: Commit** `fix(hr): make G2 UI readiness deterministic`.

### Task 3: Correct workspace navigation visibility

**Files:**
- Modify: `apps/web/app/hr/components/hr-workspace.tsx`
- Modify: shared capability constants if required.
- Test: workspace navigation tests.

- [ ] **Step 1: Add failing role-matrix tests** for Employee, Manager, and HR links covering attendance, team attendance, timesheets, approvals, schedules, policies, and reports.
- [ ] **Step 2: Extend `HR_WORKSPACE_LINKS`** with G2 routes and canonical single/any-of capability gates.
- [ ] **Step 3: Verify active-route behavior** for nested G2 paths.
- [ ] **Step 4: Run navigation tests.**
- [ ] **Step 5: Commit** `feat(hr): expose capability-scoped G2 workspace navigation`.

### Task 4: Add deterministic logout affordance

**Files:**
- Modify the canonical workspace/logout component that owns session logout.
- Test: auth/workspace component test.

- [ ] **Step 1: Add a failing test** requiring `data-testid="logout"` on the canonical logout action.
- [ ] **Step 2: Add the test ID without changing logout behavior.**
- [ ] **Step 3: Run auth/workspace tests.**
- [ ] **Step 4: Commit** `test(auth): stabilize logout acceptance selector`.

## Wave Exit Gate

- Every G2 page has a deterministic content identity.
- Readiness is explicit and testable.
- Workspace navigation reflects canonical role capabilities.
- Mobile and locale-independent selectors are stable.