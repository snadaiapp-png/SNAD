# G2 Wave 2 — Capability & Scoped API Contracts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make G2 capability checks and API surfaces explicitly distinguish SELF, TEAM, and HR administration scopes without widening privileges.

**Architecture:** Keep `@RequireCapability` as the entry gate, but stop routing manager/HR screens through SELF-protected list endpoints. Introduce scope-specific read methods/routes where required and keep the relationship/data-scope checks in the service layer.

**Tech Stack:** Spring Boot, Java, TypeScript, OpenAPI tests.

**Spec:** `docs/superpowers/plans/2026-09-24-g2-canonical-remediation-master.md`

## Global Constraints
- Preserve `TimeAttendanceCapabilities` canonical G2 codes.
- No capability alias that silently maps broader access to a narrower code.
- TEAM does not mean tenant-wide.
- HR remains tenant-scoped.
- OpenAPI artifact must match runtime endpoints exactly.

## Review Focus
- Employee with SELF cannot call TEAM/HR routes.
- Manager with TEAM does not need SELF to access team queues.
- HR admin route does not imply platform/control-plane rights.
- Unknown or inactive capability remains denied.
- Existing G2 operation IDs remain unique after route changes.

---

### Task 1: Correct frontend capability predicates

**Files:**
- Modify: `apps/web/app/hr/attendance/page.tsx`
- Modify: `apps/web/app/hr/team-attendance/page.tsx`
- Modify: `apps/web/app/hr/schedules/page.tsx`
- Modify: `apps/web/app/hr/team-timesheets/page.tsx`
- Modify: `apps/web/app/hr/leave/page.tsx`
- Modify: `apps/web/app/hr/leave/approvals/page.tsx`
- Test: page/regression tests under `apps/web/app/hr/`

- [ ] **Step 1: Add failing tests** for canonical predicates: Employee uses `HRM.ATTENDANCE.SELF_VIEW/SELF_RECORD`; Manager uses `TEAM_VIEW`/`TIMESHEET.TEAM_APPROVE`; HR uses `ATTENDANCE.ADMIN`, `LEAVE.HR_APPROVE`, and `LEAVE.POLICY_ADMIN` only where appropriate.
- [ ] **Step 2: Run focused tests** and confirm legacy `HRM.ATTENDANCE.VIEW/MANAGE` assumptions fail.
- [ ] **Step 3: Replace legacy predicates** with `TimeAttendanceCapabilities`-equivalent frontend constants or canonical string constants from the shared capability registry.
- [ ] **Step 4: Run typecheck and focused tests** and require PASS.
- [ ] **Step 5: Commit** `fix(hr): align G2 UI with canonical capabilities`.

### Task 2: Introduce scope-specific backend read contracts

**Files:**
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/hr/api/v2/time/HrTimeAttendanceV2Controller.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/hr/time/application/HrTimeAttendanceService.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/hr/time/application/HrTimesheetService.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/hr/time/application/HrLeaveService.java`
- Test: `apps/sanad-platform/src/test/java/com/sanad/platform/hr/time/HrG2AuthorizationScopeTest.java`

**Interfaces:**
- SELF endpoints derive current employee identity.
- TEAM endpoints accept filters only inside the caller's relationship scope.
- HR endpoints are tenant-wide only when protected by HR capability.

- [ ] **Step 1: Write failing controller/service tests** proving manager access to team attendance/timesheets/leave queue without SELF capability and proving employee denial on TEAM routes.
- [ ] **Step 2: Run the focused Maven test** and confirm failures against the current SELF-protected endpoints.
- [ ] **Step 3: Implement scope-specific routes/methods** with explicit capability annotations and service-level scope arguments.
- [ ] **Step 4: Re-run focused tests** and require PASS.
- [ ] **Step 5: Commit** `feat(hr): separate G2 self team and HR API scopes`.

### Task 3: Synchronize OpenAPI and authorization contract tests

**Files:**
- Modify: `docs/hrm/contracts/openapi/hrm-openapi.json`
- Modify: `apps/sanad-platform/src/test/java/com/sanad/platform/hr/time/HrG2OpenApiContractTest.java`
- Modify if required: `apps/sanad-platform/src/test/java/com/sanad/platform/hr/api/HrApiV2AuthorizationTest.java`

- [ ] **Step 1: Extend contract tests** to assert the exact new scoped paths, operation IDs, and required capabilities.
- [ ] **Step 2: Generate/update the committed OpenAPI artifact** from the runtime contract rather than editing counts only.
- [ ] **Step 3: Run the two contract test classes** and require PASS.
- [ ] **Step 4: Commit** `docs(hr): synchronize G2 scoped API contract`.

## Wave Exit Gate

- No manager/HR page depends on a SELF-only endpoint.
- Canonical capability predicates are used end-to-end.
- Authorization scope tests PASS.
- OpenAPI contract is synchronized with runtime.