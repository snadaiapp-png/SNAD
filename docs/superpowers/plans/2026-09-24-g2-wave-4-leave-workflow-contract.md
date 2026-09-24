# G2 Wave 4 — Leave Lifecycle & Workflow Contract Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the leave lifecycle explicit and deterministic from create through submit, manager approval, HR approval/rejection, withdrawal, and cancellation while preserving Workflow Engine ownership of approval progression.

**Architecture:** HRM owns leave-domain state and ledger effects; Workflow owns approval graph progression. The UI must execute explicit product actions rather than test-only direct API fallbacks. Queue reads must use canonical states instead of synthetic `PENDING` semantics unless a dedicated backend queue endpoint implements them explicitly.

**Tech Stack:** Spring Boot, Workflow Engine adapter/port, PostgreSQL, TypeScript, Playwright.

**Spec:** `docs/superpowers/plans/2026-09-24-g2-canonical-remediation-master.md`

## Global Constraints
- Preserve canonical states: `DRAFT`, `PENDING_MANAGER`, `PENDING_HR`, `APPROVED`, `REJECTED`, `WITHDRAWN`, `CANCELLED` and any explicitly retained transition state documented by the domain.
- Workflow Engine remains the approval progression owner.
- HRM must not bypass Workflow or fake approval state locally.
- Ledger reservation/consumption/release/adjustment must stay transactionally consistent with domain transitions.

## Review Focus
- Create returns a DRAFT that is not visible to manager approval until submit succeeds.
- Duplicate/concurrent submit is idempotent and creates one workflow binding.
- Manager cannot perform HR final approval.
- Reject/withdraw releases reserved leave exactly once.
- Final HR approval consumes reserved balance exactly once.

---

### Task 1: Make Create → Submit a first-class product flow

**Files:**
- Modify: `apps/web/app/hr/leave/page.tsx`
- Modify: `apps/web/lib/api/hr-g2-api.ts`
- Test: leave page tests.

- [ ] **Step 1: Write a failing UI test** asserting that the primary leave-request action creates the request and then explicitly submits the returned request ID using the typed API facade.
- [ ] **Step 2: Run the test** and confirm the current create-only behavior fails.
- [ ] **Step 3: Implement deterministic create→submit sequencing** with separate error handling; do not use `Promise.race` or test-only fallback fetch.
- [ ] **Step 4: Verify the UI reports success only after submit succeeds and reloads canonical state.**
- [ ] **Step 5: Commit** `fix(hr): make leave create and submit explicit`.

### Task 2: Correct pending approval queue semantics

**Files:**
- Modify: `HrTimeAttendanceV2Controller.java`
- Modify: `HrLeaveService.java`
- Modify: `apps/web/app/hr/leave/approvals/page.tsx`
- Modify: `apps/web/lib/api/hr-g2-api.ts`
- Test: backend and frontend queue tests.

- [ ] **Step 1: Add failing tests** for Manager queue=`PENDING_MANAGER`, HR queue=`PENDING_HR`, and combined HR visibility only when explicitly authorized.
- [ ] **Step 2: Remove the unsupported assumption** that `state=PENDING` magically expands to both states, unless a dedicated queue endpoint is implemented and tested.
- [ ] **Step 3: Implement exact queue contract** with capability-appropriate scope.
- [ ] **Step 4: Run focused tests** and require PASS.
- [ ] **Step 5: Commit** `fix(hr): align leave approval queues with canonical states`.

### Task 3: Preserve the Workflow Engine boundary

**Files:**
- Modify if needed: `apps/sanad-platform/src/main/java/com/sanad/platform/hr/time/application/HrLeaveWorkflowAdapter.java`
- Modify if needed: workflow port/interface used by HRM.
- Test: `HrLeaveWorkflowAdapterStepBindingTest.java`
- Test: `HrG2LeavePostgresIntegrationTest.java`

- [ ] **Step 1: Write/retain tests** that assert manager step → HR step → terminal graph progression and optimistic-lock-safe persistence.
- [ ] **Step 2: Verify HRM depends on an explicit port/adapter boundary rather than a prohibited direct application-service coupling.**
- [ ] **Step 3: Implement only the minimum boundary correction required by the architecture test.**
- [ ] **Step 4: Run architecture + leave workflow integration tests** and require PASS.
- [ ] **Step 5: Commit** `refactor(hr): enforce HRM workflow approval boundary`.

### Task 4: Verify leave ledger invariants

**Files:**
- Modify: `HrLeaveLedgerService.java` only if a defect is proven.
- Test: `HrG2LeavePostgresIntegrationTest.java`

- [ ] **Step 1: Add assertions** for reservation on submit, consumption on final approval, release on rejection/withdraw, adjustment on post-approval cancellation, and idempotent repeated calls.
- [ ] **Step 2: Run PostgreSQL Direct integration tests.**
- [ ] **Step 3: Fix only proven ledger defects.**
- [ ] **Step 4: Re-run until PASS.**
- [ ] **Step 5: Commit** `test(hr): certify G2 leave ledger lifecycle`.

## Wave Exit Gate

- UI executes real create→submit.
- Manager and HR queues use canonical state semantics.
- Workflow graph and HR state remain consistent.
- Ledger invariants pass under PostgreSQL Direct.