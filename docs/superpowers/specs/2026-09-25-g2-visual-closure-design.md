# HRM G2 Visual Closure — Design Specification

Date: 2026-09-25
Status: APPROVED DESIGN / IMPLEMENTATION NOT STARTED
Target repository: `snadaiapp-png/SNAD`
Baseline main SHA: `eb19a2dd9b476bc3a5af0c9b0755489fc1865c0f`
Working branch: `hr/g2-visual-closure`

## 1. Purpose

Close the remaining HRM G2 product-quality and visual-certification gap before production final closure.

G2 backend and route implementations already exist, but the current product surface is not yet acceptable as a fully closed stage because:

- the HR landing page does not expose G2 as a coherent product area;
- several G2 pages remain visually primitive and contain hard-coded English UI strings;
- capability-scoped navigation exists, but discoverability is weak;
- the historical HRM Human Preview workflow is pinned to PR #914 and therefore does not certify G2;
- the dedicated G2 Playwright config captures screenshots only on failure and does not produce a role-by-device visual evidence matrix;
- Employee/Manager/HR are not all proven on Desktop + Mobile as a visual release gate.

The outcome must be a coherent, capability-aware HRM G2 experience with reproducible visual evidence and a fail-closed release gate.

## 2. Non-negotiable invariants

This work must not weaken or bypass any existing security or architecture control.

- PostgreSQL Direct only; no Docker/Testcontainers path may be introduced.
- RLS, tenant isolation, RBAC, scope separation, optimistic locking, audit/outbox, Workflow Y2 ownership, and existing API authorization remain authoritative.
- No manual production deployment.
- No Flyway repair, history mutation, out-of-order production workaround, or force push.
- No fake screenshots, mocked production evidence, skipped mandatory tests, or `continue-on-error` for closure gates.
- Frontend capability checks are UX visibility only; backend authorization remains authoritative.
- Runtime behavior must remain country-neutral where G2 Core is country-neutral.
- Existing G2 API contracts are reused unless a real contract defect is demonstrated by tests.

## 3. Product information architecture

G2 is presented inside HRM as three capability-driven work areas.

### 3.1 Employee Self Service

Required surfaces:

- `/hr/attendance` — attendance status, clock action, current/open state, recent attendance history.
- `/hr/timesheets` — own timesheets, period/state, submit action when allowed.
- `/hr/leave` — balances, requests, new leave request, lifecycle state.

### 3.2 Manager / Team

Required surfaces:

- `/hr/team-attendance` — team attendance visibility.
- `/hr/team-timesheets` — submitted team timesheets and approval actions.
- `/hr/leave/approvals` — manager and HR approval queue according to capability and workflow state.
- `/hr/reports/attendance` — team report when `ATTENDANCE_TEAM_VIEW` is present.

### 3.3 HR Administration

Required surfaces:

- `/hr/schedules` — work schedule management.
- `/hr/attendance/admin` — attendance administration/correction surface.
- `/hr/leave/policies` — leave policy administration.
- `/hr/leave/approvals` — HR approval step.
- `/hr/reports/attendance` — HR monthly attendance report.

## 4. HR landing-page redesign

`/hr` becomes a capability-aware operational launcher rather than a G0/G1-only dashboard.

The page will retain existing HR operational summary data and add clearly separated G2 sections:

1. **My Workday** — Attendance, My Timesheets, My Leave.
2. **My Team** — Team Attendance, Team Timesheets, Leave Approvals.
3. **HR Operations** — Work Schedules, Attendance Administration, Leave Policies, Attendance Reports.

Each card is rendered only when the current identity has at least one relevant capability. No inaccessible route is advertised.

The existing quick links for employees, org structure and compliance remain available and are not displaced.

## 5. Shared visual system for G2

G2 must use the existing SNAD design tokens and HR workspace shell. No new independent design system is introduced.

Add reusable HR presentation primitives only where they reduce duplication, for example:

- section/card launcher layout;
- page heading + subtitle + primary action region;
- compact KPI/status cards;
- filter/action toolbar;
- responsive table container;
- mobile record cards for dense tables where horizontal scrolling would otherwise become the primary interaction;
- consistent success/error/loading/empty states.

Rules:

- SDS tokens only; no hard-coded hex colors.
- logical CSS only for RTL safety; no physical `left`/`right` layout assumptions.
- keyboard-visible focus states on interactive controls.
- semantic headings and landmarks.
- reduced-motion preference respected.
- no viewport-level horizontal overflow at the supported mobile width.

## 6. Localization closure

All user-visible G2 strings must be route-scoped through the existing HRM i18n augmentation path.

The current G2 localization file is expanded beyond Attendance and Leave to cover at minimum:

- Timesheets (self/team);
- Work Schedules;
- Attendance Administration;
- Leave Policies;
- Leave Approval Queue;
- Attendance Reports;
- landing-page G2 cards and section headings;
- actions, table headings, empty states, success messages, filter labels and status labels used by those pages.

Arabic and English parity is mandatory. Hard-coded English strings in G2 pages are removed.

## 7. Navigation behavior

`HR_WORKSPACE_LINKS` remains the canonical HR workspace navigation source.

Required behavior:

- capability-scoped visibility remains fail-closed;
- active-link resolution uses the most-specific matching route;
- mobile navigation remains usable when the number of visible HR routes is large;
- G2 routes are discoverable both from `/hr` landing cards and workspace navigation;
- no new route bypasses capability checks.

## 8. Functional G2 acceptance remains separate from visual acceptance

The existing stateful G2 authenticated acceptance journey remains responsible for business-function proof such as:

- employee leave submission;
- manager approval;
- HR approval;
- final employee state verification;
- real attendance clock mutation.

It must not be multiplied across a visual browser matrix because mutable state can collide.

A separate visual suite is introduced for deterministic, primarily read-oriented evidence.

## 9. Generalized HRM Human Preview architecture

The historical `.github/workflows/hrm-human-preview.yml` is replaced or refactored into a general HRM preview gate that is not pinned to PR #914.

The generalized preview must use the current PostgreSQL Direct governance model:

- host-native PostgreSQL on the runner;
- least-privilege application database role with `NOBYPASSRLS`;
- exact candidate SHA checkout;
- all migrations applied through the normal build path;
- deterministic HR/G2 acceptance seed;
- Spring Boot backend;
- Next.js frontend;
- ephemeral runtime credentials generated per run and masked;
- no committed credential literals.

The workflow triggers on relevant HR/backend/web changes and supports `workflow_dispatch`.

It must fail closed when provisioning, authentication, role/capability seeding, backend startup, frontend startup, visual assertions, or artifact generation fails.

## 10. Visual evidence matrix

Required roles:

- Employee
- Manager
- HR

Required viewports:

- Desktop Chrome
- Mobile Chrome-compatible viewport representative of 375x667 or equivalent supported project

Required evidence is role-appropriate. Each role does not need access to routes outside its capabilities.

### Employee evidence

Desktop + Mobile:

- HR landing with Self Service section visible;
- Attendance;
- My Timesheets;
- My Leave.

### Manager evidence

Desktop + Mobile:

- HR landing with Team section visible;
- Team Attendance;
- Team Timesheets;
- Leave Approval Queue;
- team Attendance Report if manager capability includes it.

### HR evidence

Desktop + Mobile:

- HR landing with HR Operations section visible;
- Work Schedules;
- Attendance Administration;
- Leave Policies;
- Leave Approval Queue;
- Monthly Attendance Report.

## 11. Visual test assertions

Visual closure is not screenshot-only. Every visual route must assert:

- authenticated role identity is correct;
- expected route loads without redirect loops;
- expected page heading is visible;
- primary data region or explicit empty state is visible;
- role-appropriate navigation item/card is visible;
- forbidden role-only controls are absent where applicable;
- no page-level uncaught error state;
- no failed mandatory API response for the page's required data load;
- no viewport horizontal overflow beyond an explicitly documented table scroller;
- no duplicate active navigation state;
- responsive layout is usable at mobile width.

## 12. Screenshot and artifact contract

On every successful visual run, not only on failure, the workflow produces deterministic evidence artifacts.

Required artifacts:

- PNG screenshots for each required role/viewport/route surface;
- Playwright HTML report;
- machine-readable manifest listing candidate SHA, role, viewport, route, screenshot path and result;
- traces/videos retained on failure;
- optional accessibility/console diagnostic output where already supported.

Artifact names must include the exact candidate SHA or GitHub run identity to prevent evidence ambiguity.

## 13. Testing and CI gates

The corrective PR cannot merge until all required exact-head gates are terminal and passing.

At minimum:

- TypeScript typecheck;
- ESLint;
- frontend unit/Vitest suite;
- Next.js production build;
- i18n Arabic/English parity;
- existing SDS/RTL constraints;
- existing G2 authenticated acceptance;
- generalized HRM Human Preview;
- Desktop visual matrix PASS;
- Mobile visual matrix PASS;
- Employee/Manager/HR evidence manifest complete;
- existing backend/security/PostgreSQL Direct gates remain green;
- independent human approval on the exact corrective PR head.

No historical approval or historical CI result may certify a changed head.

## 14. PR and release sequence

The existing release-control PR #1145 must not be merged against its current baseline after this corrective work begins.

Execution sequence:

1. Create the G2 Visual Closure corrective branch from exact current `main`.
2. Implement this specification with TDD/regression coverage.
3. Open a dedicated corrective PR to `main`.
4. Run exact-head CI and generalized visual preview.
5. Obtain independent human approval on the exact head.
6. Squash-merge the corrective PR only after all gates pass.
7. Verify the new exact `main` SHA.
8. Close/supersede the old release-control PR #1145 because its bound baseline is stale.
9. Create a new inert Release-Control PR bound to the new exact main.
10. Require the protected squash-merge commit message to contain exactly `PRODUCTION-RELEASE-AUTHORIZED`.
11. Run the canonical production release with `rollback_on_failure=true`.
12. Verify immutable image parity, Workflow Y2 production orchestration, readiness, Flyway/runtime invariants, security/tenant isolation, G2 production smoke, and Vercel/BFF evidence.
13. Declare `G2 FINAL CLOSURE = PASS` only after all production and visual evidence is terminal PASS.

## 15. Out of scope

Unless testing exposes a real defect, this visual closure does not redesign:

- G2 database schema;
- leave/workflow business state machine;
- tenant isolation model;
- RBAC capability semantics;
- Workflow Y2 approval authority;
- billing/subscription model;
- unrelated CRM/ERP/Finance UI.

## 16. Final closure criteria

The stage is closed only when all of the following are true on the final production-bound baseline:

```text
G2_PRODUCT_UI                = PASS
G2_I18N_PARITY               = PASS
G2_NAV_DISCOVERABILITY       = PASS
G2_AUTHENTICATED_E2E         = PASS
G2_VISUAL_EMPLOYEE_DESKTOP   = PASS
G2_VISUAL_EMPLOYEE_MOBILE    = PASS
G2_VISUAL_MANAGER_DESKTOP    = PASS
G2_VISUAL_MANAGER_MOBILE     = PASS
G2_VISUAL_HR_DESKTOP         = PASS
G2_VISUAL_HR_MOBILE          = PASS
HRM_HUMAN_PREVIEW            = PASS
EXACT_HEAD_CI                = PASS
INDEPENDENT_APPROVAL         = PASS
CORRECTIVE_PR_MERGED         = YES
RELEASE_CONTROL_REBOUND      = YES
PRODUCTION_RELEASE           = PASS
POST_MERGE_VERIFICATION      = PASS
PRODUCTION_VISUAL_SMOKE      = PASS
G2_FINAL_CLOSURE             = PASS
```

Any failed, skipped mandatory, queued, or stale-head required gate keeps final closure OPEN.
