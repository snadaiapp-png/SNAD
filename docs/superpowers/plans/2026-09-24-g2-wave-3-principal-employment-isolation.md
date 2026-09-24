# G2 Wave 3 — Principal-to-Employment Binding & Isolation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent SELF operations from trusting caller-supplied employee identifiers and enforce tenant/relationship scope before touching HR data.

**Architecture:** Resolve the authenticated user to the active HR employee inside the current tenant on the backend. SELF endpoints operate on that resolved employment identity; TEAM operations validate reporting relationships before query/mutation; PostgreSQL FORCE RLS remains the final isolation layer.

**Tech Stack:** Java, Spring Security, JdbcTemplate, PostgreSQL Direct, JUnit.

**Spec:** `docs/superpowers/plans/2026-09-24-g2-canonical-remediation-master.md`

## Global Constraints
- Never infer employment ownership from a request body.
- Never disable FORCE RLS or use BYPASSRLS for tests.
- Tenant context must be set before RLS-protected queries and fail closed when absent.
- Cross-tenant IDs must return denial/not-found behavior without data disclosure.

## Review Focus
- User ID differs from HR employee ID.
- Same-tenant attacker supplies another employee's ID.
- Cross-tenant attacker supplies a valid foreign employee ID.
- Manager attempts access to a non-direct-report employee.
- User has no active HR employee mapping.

---

### Task 1: Add principal-to-employee resolver

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/hr/time/application/HrEmploymentScopeResolver.java`
- Test: `apps/sanad-platform/src/test/java/com/sanad/platform/hr/time/HrEmploymentScopeResolverPostgresTest.java`

**Interfaces:**
- `UUID requireSelfEmployment(UUID tenantId, UUID userId)`
- `void requireManagedEmployment(UUID tenantId, UUID managerUserId, UUID targetEmploymentId)`

- [ ] **Step 1: Write PostgreSQL Direct tests** for valid mapping, missing mapping, same-tenant foreign employee, cross-tenant employee, and direct-report relationship.
- [ ] **Step 2: Run the focused class** and confirm failure because the resolver does not exist.
- [ ] **Step 3: Implement resolver queries** constrained by `tenant_id`, active employee/user state, and `manager_id` relationship.
- [ ] **Step 4: Re-run focused tests** and require PASS.
- [ ] **Step 5: Commit** `feat(hr): add authenticated employment scope resolver`.

### Task 2: Bind SELF attendance/timesheet operations to principal

**Files:**
- Modify: `HrTimeAttendanceV2Controller.java`
- Modify: `HrTimeAttendanceService.java`
- Modify: `HrTimesheetService.java`
- Modify: corresponding DTOs and tests.

- [ ] **Step 1: Add failing tests** proving a SELF caller cannot choose a different `employmentId` for clock-in, attendance reads, timesheet creation, or submission.
- [ ] **Step 2: Change SELF request contracts** to omit caller-owned employment IDs where possible; otherwise validate equality with the resolver result.
- [ ] **Step 3: Make service methods receive the resolved employee ID** from trusted backend code rather than raw client identity.
- [ ] **Step 4: Run focused authorization and PostgreSQL tests** and require PASS.
- [ ] **Step 5: Commit** `fix(hr): bind G2 self time operations to authenticated employee`.

### Task 3: Bind SELF leave operations to principal

**Files:**
- Modify: `HrTimeAttendanceV2Controller.java`
- Modify: `HrLeaveService.java`
- Modify: `apps/web/lib/api/hr-g2-api.ts`
- Modify: `apps/web/app/hr/leave/page.tsx`
- Test: backend + frontend contract tests.

- [ ] **Step 1: Add failing tests** proving leave create/list/balance self operations cannot target another employee.
- [ ] **Step 2: Remove `employmentId` from the SELF leave creation payload** and resolve it from `(tenantId,userId)` server-side.
- [ ] **Step 3: Update frontend DTOs/forms** so `me.id` is never treated as an HR employee ID.
- [ ] **Step 4: Run focused backend/frontend tests** and require PASS.
- [ ] **Step 5: Commit** `fix(hr): derive self leave employment from principal`.

### Task 4: Prove fail-closed RLS behavior

**Files:**
- Modify: `apps/sanad-platform/src/test/java/com/sanad/platform/hr/time/HrG2PostgresIntegrationTest.java`
- Modify if needed: `apps/sanad-platform/src/test/java/com/sanad/platform/hr/time/HrG2LeavePostgresIntegrationTest.java`

- [ ] **Step 1: Add negative tests** for no tenant context, foreign tenant context, and foreign row IDs across G2 tables.
- [ ] **Step 2: Run PostgreSQL Direct tests** as the non-superuser application role.
- [ ] **Step 3: Fix only real policy/service defects; never weaken the assertions.**
- [ ] **Step 4: Re-run until PASS.**
- [ ] **Step 5: Commit** `test(hr): certify G2 principal and RLS isolation`.

## Wave Exit Gate

- SELF requests no longer trust arbitrary employment IDs.
- Team relationship enforcement is proven.
- Cross-tenant and no-context access fail closed under PostgreSQL Direct.
- No RLS bypass or policy weakening introduced.