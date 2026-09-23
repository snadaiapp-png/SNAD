# HRM-G2 Engineering Closure Certificate (Pre-Merge DRAFT)

> STATUS_AUTHORITY: DRAFT — PRE-MERGE
> Stage: G2 (Time & Attendance — Scheduling — Timesheets — Leave)
> G2_FINAL_GATE = NOT_CLOSED
> PR: #1133

## Latest reconciliation (2026-09-23 comprehensive remediation)

| Field | Value |
|---|---|
| Branch | `hr/g2-time-attendance-leave` |
| Base SHA (origin/main at rebase) | `7a8399e25fe97758ee1e0ff6df512d18f8654da1` |
| Local HEAD (post-rebase, pre-commit) | `d0835ae19af4998724ccaf07ecdfd3973d1771d9` |
| Previous checkpoint (HISTORICAL — DO NOT REUSE) | `495c7d6561e0ce6dd8717868799081cda0193afc` |
| Step-binding fix commit (HISTORICAL — preserved as evidence) | `560df3bef1382ec57c83013a8e4687e4ac16d4b9` |

## Status
- G2_FINAL_GATE = NOT_CLOSED
- IMPLEMENTATION_COMPLETE = NO
- ENGINEERING_CERTIFICATION = NOT_YET_APPROVED
- MERGE_AUTHORIZATION = NO

## What this push addresses (SANAD HRM G2 comprehensive remediation)

### 1. PLAYWRIGHT_ROOT_CAUSE (proven from run 35885345067)

24 G2 authenticated E2E tests failed in the generic Playwright matrix
with:
```
Error: G2 E2E FAIL-CLOSED: E2E_EMPLOYEE_EMAIL and E2E_EMPLOYEE_PASSWORD must be provisioned
```
(Equivalent for MANAGER and HR roles across all 6 locale/theme projects.)

ROOT CAUSE: The CI environment did not provision E2E_<ROLE>_EMAIL /
E2E_<ROLE>_PASSWORD env vars. The test code itself uses the correct
fail-closed pattern (no test.skip, no Assumptions, no @Disabled,
no continue-on-error). The defect is at the CI workflow level, not the
test code.

CORRECTION:
- Created `.github/workflows/g2-authenticated-acceptance.yml` — dedicated
  required G2 authenticated CI job with host-native PostgreSQL, Spring
  Boot backend, Next.js frontend, and deterministic G2 test tenant
  provisioning (Employee + Manager + HR users with WORKFLOW module
  entitlement + RBAC capabilities).
- Created `apps/sanad-platform/src/test/resources/sql/g2-acceptance-seed.sql`
  — idempotent G2 acceptance seed (tenant + plan + subscription +
  plan_module_entitlements[WORKFLOW] + 3 users + 3 hr_employees +
  Employee→Manager reporting line + 3 RBAC roles + role_capabilities
  + user_role_assignments + leave balance).
- Created `apps/web/playwright-g2.config.ts` — single-project Playwright
  config (no locale/theme matrix) for the G2 stateful journey.
- Updated `apps/web/playwright.config.ts` — added `**/g2-authenticated.spec.ts`
  to testIgnore so the generic visual regression matrix no longer runs
  the G2 spec (which requires isolated data + secrets).
- Updated `apps/web/e2e/g2-authenticated.spec.ts` — real stateful
  cross-role journey (Employee submits → Manager approves → HR approves
  → APPROVED using the SAME leave request). Uses correct login
  selectors (`#login-email`, `#login-password`, `form button[type="submit"]`).
- Created `apps/web/e2e/g2-auth-session.ts` — login helper mirroring
  the canonical CRM `crm-auth-session.ts` pattern.
- Updated `apps/web/app/hr/leave/approvals/page.tsx` — fix broken
  approve/reject endpoints. Now calls `/manager-approve`, `/hr-approve`,
  `/manager-reject`, `/hr-reject` per the controller. Manager sees
  PENDING_MANAGER requests + "Manager Approve" button. HR sees
  PENDING_HR requests + "HR Approve" button. Manager does NOT see
  HR-only approval button (directive §6).
- Updated `apps/web/app/hr/leave/page.tsx` — fix broken capability
  checks (`HRM.LEAVE.VIEW` / `HRM.LEAVE.REQUEST` / `HRM.LEAVE.APPROVE`
  don't exist in the capability catalog). Now checks `HRM.LEAVE.SELF_VIEW`,
  `HRM.LEAVE.SELF_REQUEST`, `HRM.LEAVE.TEAM_APPROVE`, `HRM.LEAVE.HR_APPROVE`.
  Added `data-testid` attributes to the request form for deterministic
  Playwright selectors.

### 2. Step-instance approval binding (preserved from prior push 560df3be)

`HrLeaveWorkflowAdapter.findPendingApprovalForCurrentStep()` strictly
binds the returned approval to the CURRENT `WorkflowStepInstance` id
(directive §4 algorithm: load instance → require RUNNING → require
currentStepKey match → resolve current step instance PENDING/IN_PROGRESS
→ filter approvals by workflowStepInstanceId + PENDING). Prevents the
historical wrong-step selection defect.

Regression tests:
- `HrLeaveWorkflowAdapterStepBindingTest` — 10 focused unit tests
  covering all 7 directive §8 cases + 3 defensive tests.
- `HrG2LeavePostgresIntegrationTest` — 4 PostgreSQL Direct lifecycle
  tests (manager stage, HR stage, stale-PENDING edge, cross-tenant
  isolation). NO Assumptions skip. NO Docker/Testcontainers/H2/SQLite.

## Verification Status

### Local frontend verification (all PASS)
- `npm run typecheck` → PASS
- `npm run lint` → 0 errors (65 pre-existing warnings, none new)
- `npm test` (vitest) → 1034/1034 PASS (115 files)
- `npm run brand:check` (SDS) → PASS — "SNAD identity validation passed"
- `scripts/ci/check_i18n_keys.py` → 1125 = 1125 PASS
- `npm run build` → ✓ Compiled successfully in 3.3s
- `scripts/ci/check-performance-budget.py` → PASS — all routes within budget

### Local backend verification (NOT_RUN_ENVIRONMENT_LIMITATION)
- LOCAL_BACKEND_COMPILE = NOT_RUN_ENVIRONMENT_LIMITATION (no local JDK)
- LOCAL_BACKEND_TESTS = NOT_RUN_ENVIRONMENT_LIMITATION (no local JDK/PostgreSQL)
- LOCAL_G2_AUTH_E2E = NOT_RUN_ENVIRONMENT_LIMITATION (requires CI environment
  with host-native PostgreSQL + Spring Boot + Next.js + Playwright browsers
  + G2 acceptance seed — only the dedicated G2 workflow can provision this
  end-to-end)

### Backend verification relies on fresh exact-head CI per directive §15

## Required Fresh Exact-Head CI (directive §15)

To be confirmed green on the new head SHA:

| Gate | Required | Status |
|---|---|---|
| Compile Diagnostics | SUCCESS | PENDING |
| Web CI | SUCCESS | PENDING |
| SNAD Identity Governance | SUCCESS | PENDING |
| Maven Test Suite | SUCCESS | PENDING |
| PostgreSQL Acceptance | SUCCESS | PENDING |
| CRM Integration | SUCCESS | PENDING |
| Security Baseline | SUCCESS | PENDING |
| Schema Isolation | SUCCESS | PENDING |
| Performance Baseline | SUCCESS | PENDING |
| Backup Restore Validation | SUCCESS | PENDING |
| Pre-Merge Operational Smoke | SUCCESS | PENDING |
| Workflow Y2 G4 Release Gate | SUCCESS | PENDING |
| Stage 07 Artifact Provenance | SUCCESS | PENDING |
| API Contract Validation | SUCCESS | PENDING |
| Service Decomposition Validation | SUCCESS | PENDING |
| Generic Playwright E2E & Visual Regression | terminal result | PENDING |
| G2 Authenticated Acceptance | SUCCESS | PENDING |

G2-specific evidence (directive §7):

| Gate | Required | Status |
|---|---|---|
| G2_EMPLOYEE_E2E | PASS | PENDING |
| G2_MANAGER_E2E  | PASS | PENDING |
| G2_HR_E2E       | PASS | PENDING |
| G2_MOBILE_E2E   | PASS | PENDING |
| G2_FAILED       | 0    | PENDING |
| G2_SKIPPED_REQUIRED | 0 | PENDING |

## Authorization
- IMPLEMENTATION_COMPLETE = NO
- ENGINEERING_CERTIFICATION = NOT_YET_APPROVED
- FINAL_EXACT_HEAD_CI = PENDING (must run on new head SHA)
- MERGE_AUTHORIZATION = NO

Do NOT merge until all required CI on the new SHA is green AND the
broader G2 closure (lifecycle evidence, authenticated Employee/Manager/HR
E2E desktop/mobile, G2 OpenAPI completeness, concurrency/idempotency
proof, tenant isolation/RLS evidence, closure manifest, independent
approval) is genuinely complete per directive §17.
