# HRM G2 Product Surfaces Human Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current route/table-oriented G2 presentation with coherent Employee, Manager, and HR product surfaces that visibly satisfy the approved G2 visual-closure specification and can pass both automated and human visual acceptance.

**Architecture:** Keep all existing G2 APIs, RBAC/RLS, Workflow Y2 authority, tenant isolation, and PostgreSQL Direct governance unchanged. Concentrate changes in the HR frontend presentation layer and visual acceptance harness: reusable product-surface primitives, role-specific operational summaries/actions, responsive layouts, and fail-closed human-visual assertions bound to exact SHA.

**Tech Stack:** Next.js 16, React 19, TypeScript 5.9, Vitest 4, Testing Library, Playwright 1.61, existing SDS tokens, Spring Boot/JDK 21, PostgreSQL Direct.

**Spec:** `docs/superpowers/specs/2026-09-25-g2-visual-closure-design.md`

## Global Constraints

- PostgreSQL Direct only; no Docker/Testcontainers governing path.
- Preserve RLS, tenant isolation, RBAC, scope separation, audit/outbox, optimistic locking, and Workflow Y2 ownership.
- No new backend contract unless a failing test proves a real contract defect.
- No production-data mocks or fake screenshots; preview fixtures remain deterministic and non-production only.
- SDS tokens only; no independent design system and no hard-coded hex palette.
- Logical CSS only; RTL/LTR parity and 375x667 usability are mandatory.
- Every changed head invalidates historical CI, visual evidence, and approval.
- Release-Control, provisioning, and production release remain blocked until human visual acceptance explicitly passes.

## Review Focus

1. A real G2 screen must be visually distinct from `HrWorkspace + heading + table`; tests must require product-summary/action regions, not merely route/data presence.
2. Empty but valid queues must still render a coherent operational surface, while non-empty preview fixtures prove representative states.
3. Employee, Manager, and HR identities must never see controls outside their backend-authorized capability set.
4. Mobile 375x667 must not degrade into horizontal page scrolling; dense records use cards or bounded table scrollers.
5. Arabic/English screenshots must contain no raw i18n keys and must preserve correct direction, localized dates, lifecycle labels, and action copy.

---

### Task 1: Product-surface primitives and visual contract

**Files:**
- Create: `apps/web/app/hr/components/hr-product-surface.tsx`
- Create: `apps/web/app/hr/components/hr-product-surface.test.tsx`
- Modify: `apps/web/app/hr/components/hr-g2-visual.module.css`
- Modify: `apps/web/app/hr/g2-visual-closure.regression.test.ts`

**Interfaces:**
- Produces: `HrProductHeader`, `HrKpiGrid`, `HrKpiCard`, `HrActionBar`, `HrOperationalPanel`, `HrMobileRecordList`.
- Consumes: existing SDS/HR styles, route-scoped i18n text, React children.

- [ ] **Step 1: Write RED component tests** asserting semantic page title/subtitle, at least one operational summary region, action-region semantics, keyboard-visible interactive controls, and responsive mobile-record container hooks.
- [ ] **Step 2: Run** `cd apps/web && npx vitest run app/hr/components/hr-product-surface.test.tsx app/hr/g2-visual-closure.regression.test.ts` and confirm expected failures for missing primitives/visual contract.
- [ ] **Step 3: Implement the primitives** with SDS tokens and logical CSS only; no API/business behavior.
- [ ] **Step 4: Re-run the focused tests** and require 0 failures.
- [ ] **Step 5: Commit** `feat(hr): add G2 product surface primitives`.

### Task 2: Employee Self Service product surfaces

**Files:**
- Modify: `apps/web/app/hr/attendance/page.tsx`
- Modify: `apps/web/app/hr/timesheets/page.tsx`
- Modify: `apps/web/app/hr/leave/page.tsx`
- Modify: `apps/web/lib/i18n/locales/hrm-g2-i18n.ts`
- Test: `apps/web/app/hr/g2-product-surfaces.employee.test.tsx`

**Interfaces:**
- Attendance: current/open attendance state, clock action, today/month summary, recent history.
- Timesheets: current period/state summary, submit eligibility/action, recent periods.
- Leave: balance summary, request lifecycle summary, primary new-request action, recent requests.

- [ ] **Step 1: Write RED tests** requiring route-specific KPI/status cards plus the primary action region for all three Employee pages; tests must reject a page containing only heading/subtitle/table.
- [ ] **Step 2: Run focused Employee tests** and confirm RED against current `main` presentation.
- [ ] **Step 3: Implement Attendance product surface** reusing existing `hrG2Api` data and clock commands.
- [ ] **Step 4: Implement Timesheets product surface** reusing existing period/state/submit behavior.
- [ ] **Step 5: Implement Leave product surface** reusing existing balance/request APIs and lifecycle actions.
- [ ] **Step 6: Add exact AR/EN copy** for KPI labels, panels, actions, and empty/loaded states.
- [ ] **Step 7: Run Employee Vitest suite + typecheck** and require 0 failures.
- [ ] **Step 8: Commit** `feat(hr): deliver G2 employee product surfaces`.

### Task 3: Manager / Team product surfaces

**Files:**
- Modify: `apps/web/app/hr/team-attendance/page.tsx`
- Modify: `apps/web/app/hr/team-timesheets/page.tsx`
- Modify: `apps/web/app/hr/leave/approvals/page.tsx`
- Modify: `apps/web/app/hr/reports/attendance/page.tsx`
- Modify: `apps/web/lib/i18n/locales/hrm-g2-i18n.ts`
- Test: `apps/web/app/hr/g2-product-surfaces.manager.test.tsx`

**Interfaces:**
- Team Attendance: present/absent/open-record summary + team record view.
- Team Timesheets: submitted/pending summary + approve/reject actions.
- Leave Approvals: queue summary by lifecycle + role-appropriate approval actions.
- Attendance Report: period summary and team metrics when capability permits.

- [ ] **Step 1: Write RED Manager tests** requiring team KPIs, queue/action panels, role-scoped controls, and absence of HR-only administration controls.
- [ ] **Step 2: Prove RED** with the focused Manager suite.
- [ ] **Step 3: Implement Team Attendance and Team Timesheets product summaries/actions** using existing API contracts.
- [ ] **Step 4: Implement Leave Approval queue summary/action surface** without changing Workflow Y2 authority.
- [ ] **Step 5: Implement Attendance Report operational summary/filter surface** using existing report data.
- [ ] **Step 6: Run Manager Vitest suite + typecheck** and require 0 failures.
- [ ] **Step 7: Commit** `feat(hr): deliver G2 manager product surfaces`.

### Task 4: HR Administration product surfaces

**Files:**
- Modify: `apps/web/app/hr/schedules/page.tsx`
- Modify: `apps/web/app/hr/attendance/admin/page.tsx`
- Modify: `apps/web/app/hr/leave/policies/page.tsx`
- Modify: `apps/web/app/hr/leave/approvals/page.tsx`
- Modify: `apps/web/app/hr/reports/attendance/page.tsx`
- Modify: `apps/web/lib/i18n/locales/hrm-g2-i18n.ts`
- Test: `apps/web/app/hr/g2-product-surfaces.hr.test.tsx`

**Interfaces:**
- Schedules: schedule-count/status summary + create/manage action region.
- Attendance Admin: exception/open/correction summary + correction availability state.
- Leave Policies: policy-count/paid/attachment summary + policy administration region.
- HR approvals/report: HR-specific lifecycle queue and monthly operational metrics.

- [ ] **Step 1: Write RED HR tests** requiring HR Operations KPI cards, administration action regions, correction/policy states, and absence of Control Plane/admin-platform controls.
- [ ] **Step 2: Prove RED** with focused HR tests.
- [ ] **Step 3: Implement Schedules and Attendance Administration product surfaces** using existing commands/read models.
- [ ] **Step 4: Implement Leave Policies, HR approval, and monthly report product surfaces** with no backend authority changes.
- [ ] **Step 5: Run HR Vitest suite + typecheck** and require 0 failures.
- [ ] **Step 6: Commit** `feat(hr): deliver G2 HR administration surfaces`.

### Task 5: HR landing and discoverability closure

**Files:**
- Modify: `apps/web/app/hr/page.tsx`
- Modify: `apps/web/app/hr/components/hr-g2-launcher.tsx`
- Modify: `apps/web/app/hr/components/hr-workspace.tsx`
- Modify: `apps/web/app/hr/components/hr-g2-visual.module.css`
- Test: `apps/web/app/hr/components/hr-g2-launcher.test.tsx`
- Test: `apps/web/app/hr/components/hr-workspace.test.tsx`

**Interfaces:**
- Produces capability-aware `My Workday`, `My Team`, `HR Operations` operational launcher sections that visibly read as product areas rather than nav tabs.

- [ ] **Step 1: Write RED discoverability tests** requiring grouped launcher sections, product cards with summary/intent copy, current-role relevance, and most-specific active navigation.
- [ ] **Step 2: Prove RED** against the current shell-heavy landing/navigation.
- [ ] **Step 3: Implement the richer launcher and workspace hierarchy** while keeping `HR_WORKSPACE_LINKS` canonical and capability fail-closed.
- [ ] **Step 4: Verify Arabic/English and mobile layout tests**.
- [ ] **Step 5: Commit** `feat(hr): make G2 product areas discoverable`.

### Task 6: Fail-closed visual and human-acceptance gate

**Files:**
- Modify: `apps/web/e2e/g2-visual-helpers.ts`
- Modify: `apps/web/e2e/g2-visual.spec.ts`
- Modify: `.github/workflows/hrm-human-preview.yml`
- Modify: `tests/ci/test_workflow_final_closure_unified.py`

**Interfaces:**
- Every route declares required product-surface markers, role-specific action/summary markers, and forbidden controls.
- Manifest remains exact-SHA bound and 60-row complete for Employee/Manager/HR × ar/en × desktop/mobile.

- [ ] **Step 1: Write RED visual assertions** that reject `HrWorkspace + heading + table` even when real rows exist; require route-specific KPI/action/panel markers.
- [ ] **Step 2: Run the focused Playwright visual suite** against local preview and confirm current shell-only presentation would fail the new contract.
- [ ] **Step 3: Implement route-specific visual assertions** including raw-key guard, role identity, API health, responsive overflow, and forbidden-control absence.
- [ ] **Step 4: Run Vitest, Next build, G2 authenticated acceptance, and HRM Human Preview**; require exact-head 60/60 technical PASS.
- [ ] **Step 5: Download and inspect representative screenshots manually**: Employee Attendance/Leave, Manager Team Attendance/Approvals, HR Attendance Admin/Schedules, in Arabic and English, desktop and mobile.
- [ ] **Step 6: Record human verdict**; any shell-like or visually incomplete route sets `G2_HUMAN_VISUAL_ACCEPTANCE=FAIL` regardless of automated PASS.
- [ ] **Step 7: Commit** `test(hr): fail closed on incomplete G2 product surfaces`.

### Task 7: Full exact-head certification and PR readiness

**Files:**
- No new product behavior; verification only.

- [ ] **Step 1: Run/observe all protected exact-head checks**: Build Next.js Web, provenance, CRM Integration Tests, Maven Test Suite, CRM Deployment Readiness, PostgreSQL tenant-isolation check.
- [ ] **Step 2: Require additional gates**: Identity Governance, G2 Authenticated Acceptance, Security Baseline, HRM Human Preview, Playwright visual regression.
- [ ] **Step 3: Obtain independent human approval on the final exact head** after visual evidence review.
- [ ] **Step 4: Re-check `main` for drift immediately before merge**; any drift requires rebase/update + fresh exact-head gates.
- [ ] **Step 5: Squash merge only when every gate is terminal PASS and human visual acceptance is explicitly PASS.**
- [ ] **Step 6: After merge, run exact-main/post-merge verification before creating any Release-Control.**

## Human Acceptance Definition

A screenshot is acceptable only when a reviewer can identify the operational purpose of the page without relying on the URL or navigation label. Each required route must visibly contain a coherent summary/status area, a role-appropriate primary action or operational state, and its detailed records/queue/report region. A plain heading/subtitle/table composition is an automatic human FAIL even when data and automation are green.
