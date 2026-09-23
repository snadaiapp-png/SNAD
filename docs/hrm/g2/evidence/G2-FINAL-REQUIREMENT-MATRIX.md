# G2 Final Requirement Matrix

> Status authority: DRAFT — PRE-MERGE
> All CI Evidence values reflect TRUTHFUL terminal status of the historical failed SHA `e81aa13e`
> AND PENDING status for the upcoming NEW head SHA. No pre-written PASS for unrun CI.

## FAILED e81aa13e CI status (historical — do NOT reuse as certification)

| Gate | e81aa13e status | Root cause |
|---|---|---|
| G2 Authenticated Acceptance | FAILURE | Seed schema mismatch — `g2-acceptance-seed.sql` used wrong column names (`entitled/used/pending/carried`) instead of canonical `entitled_days/used_days/pending_days/carried_over_days`; Playwright tests NOT_RUN because seed step failed |
| Generic Playwright | FAILURE | `g2-authenticated.spec.ts` remained inside `playwright.standard.config.ts` matrix (only `playwright.config.ts` had the testIgnore — generic CI uses `playwright.standard.config.ts`); tests failed because E2E_<ROLE>_EMAIL/PASSWORD env vars not provisioned in generic matrix |
| Security Baseline | FAILURE | Gitleaks `generic-api-key` false positive on `docs/hrm/g2/evidence/HRM-G2-ENGINEERING-CLOSURE.md` line 156 — prose "G2 OpenAPI completeness, concurrency/idempotency proof" resembled an API-key assignment |
| Maven | IN_PROGRESS at last verification (e81aa13e already a failed exact-head checkpoint regardless of Maven outcome) | — |

## e81aa13e corrections (this push)

| Field | Status |
|---|---|
| SEED_SCHEMA_FIX | IMPLEMENTED — `g2-acceptance-seed.sql` column list corrected to `entitled_days, used_days, pending_days, carried_over_days` (canonical schema from V20260923_1) |
| SEED_EXECUTION | PENDING — to be verified by fresh CI on new SHA |
| GENERIC_PLAYWRIGHT_CONFIG_FIX | IMPLEMENTED — `playwright.standard.config.ts` testIgnore array now includes `**/g2-authenticated.spec.ts` |
| G2_SPEC_IN_GENERIC_MATRIX | 0 (verified by `npx playwright test --config=playwright.standard.config.ts --list` local pre-push) |
| G2_SPEC_IN_DEDICATED_JOB | > 0 (verified by `npx playwright test --config=playwright-g2.config.ts --list` local pre-push) |
| SECURITY_FALSE_POSITIVE_FIX | IMPLEMENTED — reworded prose in `HRM-G2-ENGINEERING-CLOSURE.md` to "contract-schema coverage and concurrency/idempotency evidence" (no "API" + "completeness" + comma sequence that triggered the gitleaks generic-api-key rule) |
| GITLEAKS_FINDINGS | PENDING — to be re-verified by fresh Security Baseline run on new SHA |
| COMMITTED_PASSWORD_LITERALS | NONE — workflow generates ephemeral per-run DB + G2 E2E passwords via `openssl rand`, masks via `::add-mask::`, exports to GITHUB_ENV; psql uses variable binding (`-v g2_e2e_password=...`) so cleartext never appears in SQL text |
| EPHEMERAL_CREDENTIALS | IMPLEMENTED — DB_PASSWORD + G2_E2E_PASSWORD generated at runtime; the three isolated CI identities (Employee/Manager/HR) share the same ephemeral password (lives only in the destroyed-after-run G2 test tenant) |
| MOBILE_REAL_MUTATION | IMPLEMENTED — mobile test performs real clock-in OR clock-out mutation (whichever transition is available), waits for API response 200, verifies opposite button is now visible (state transition persisted). No `else => PASS` fallback. |
| SWALLOWED_ERRORS | NONE — removed `.catch(() => false)` from logout helper; now uses explicit `logoutBtn.first().click({ timeout: 5_000 })` (deterministic locator, fail-closed on missing button). Removed unused `findLeaveRequestIdByReason` helper that had `.isVisible().catch(() => false)`. |

## Requirement matrix

| # | Requirement | Implementation | Test | CI Evidence (e81aa13e) | CI Evidence (NEW SHA) | Status |
|---|---|---|---|---|---|---|
| 1 | Work schedules | HrScheduleService + V20260924_1 | HrG2PostgresIntegrationTest | PENDING | PENDING | IMPLEMENTED |
| 2 | Schedule assignments | HrScheduleService.assignSchedule | schema test | PENDING | PENDING | IMPLEMENTED |
| 3 | Overlap protection | DB unique index | schema test | PENDING | PENDING | IMPLEMENTED |
| 4 | Attendance events | hr_attendance_events + V20260924_2 | schema test | PENDING | PENDING | IMPLEMENTED |
| 5 | Clock-in/out | HrTimeAttendanceService | HrG2PostgresIntegrationTest | PENDING | PENDING | IMPLEMENTED |
| 6 | Calculation engine | HrAttendanceCalculationService | (internal) | PENDING | PENDING | IMPLEMENTED |
| 7 | Timesheets lifecycle | HrTimesheetService | HrG2PostgresIntegrationTest | PENDING | PENDING | IMPLEMENTED |
| 8 | Leave types (identity) | V20260923_3 seed | schema test | PENDING | PENDING | IMPLEMENTED |
| 9 | Leave policies | hr_leave_policies table | schema test | PENDING | PENDING | PARTIAL — policy-driven entitlement rule evaluation not wired |
| 10 | Leave ledger | HrLeaveLedgerService | schema test | PENDING | PENDING | IMPLEMENTED |
| 11 | Leave state machine (8 states) | HrLeaveService | schema test | PENDING | PENDING | IMPLEMENTED |
| 12 | Workflow Engine integration | HrLeaveWorkflowAdapter (step-instance-bound) | HrLeaveWorkflowAdapterStepBindingTest + HrG2LeavePostgresIntegrationTest | PENDING | PENDING | IMPLEMENTED |
| 13 | Workflow definition resolution | findOrPublishDefinition (deterministic familyId) | HrG2LeavePostgresIntegrationTest | PENDING | PENDING | IMPLEMENTED |
| 14 | Workflow approval requests | via adapter + WorkflowApprovalService | HrG2LeavePostgresIntegrationTest | PENDING | PENDING | IMPLEMENTED |
| 15 | Idempotency persistence | SELECT FOR UPDATE + DB unique constraint | HrG2LeavePostgresIntegrationTest | PENDING | PENDING | IMPLEMENTED |
| 16 | Clock abstraction | TimeClockConfig (Clock bean) | (internal) | PENDING | PENDING | IMPLEMENTED |
| 17 | Audit + outbox | hr_audit_ledger + hr_domain_event_outbox | schema test | PENDING | PENDING | IMPLEMENTED |
| 18 | Monthly report (G2-T05) | HrTimeAttendanceService.monthlyReport | (API) | PENDING | PENDING | IMPLEMENTED |
| 19 | RLS (all G2 tables) | ENABLE+FORCE+tenant_isolation | HrG2PostgresIntegrationTest | PENDING | PENDING | IMPLEMENTED |
| 20 | RBAC (SELF/TEAM/HR) | 13 capabilities + @RequireCapability | (internal) | PENDING | PENDING | IMPLEMENTED |
| 21 | Arabic/English i18n | hrm-g2-i18n.ts (73 keys) | i18n parity check | PENDING | PENDING (local PASS 1125=1125) | IMPLEMENTED |
| 22 | RTL/LTR | logical CSS only | SDS check | PENDING | PENDING (local PASS) | IMPLEMENTED |
| 23 | Employee UI | 3 screens (attendance+timesheets+leave) | vitest | PENDING | PENDING (local PASS 1034/1034) | IMPLEMENTED |
| 24 | Manager UI | 3 screens (team-attendance+timesheets+approvals) | vitest | PENDING | PENDING (local PASS) | IMPLEMENTED |
| 25 | HR UI | 4 screens (schedules+policies+admin+reports) | vitest | PENDING | PENDING (local PASS) | IMPLEMENTED |
| 26 | OpenAPI path/method coverage | springdoc @Operation annotations | HrG2OpenApiContractTest | PENDING | PENDING | IMPLEMENTED |
| 27 | Full OpenAPI contract (request/response schemas, 400/401/403/404/409, Idempotency-Key, expectedVersion/If-Match) | NOT YET | NOT YET | NOT_PROVEN | NOT_PROVEN | NOT_IMPLEMENTED |
| 28 | G2 authenticated Employee E2E (desktop) | g2-authenticated.spec.ts + g2-acceptance-seed.sql | Playwright (g2-authenticated-acceptance.yml) | FAILURE (seed schema mismatch) | PENDING | IMPLEMENTED |
| 29 | G2 authenticated Manager E2E (desktop) | g2-authenticated.spec.ts + g2-acceptance-seed.sql | Playwright (g2-authenticated-acceptance.yml) | NOT_RUN (seed failure prevented Playwright) | PENDING | IMPLEMENTED |
| 30 | G2 authenticated HR E2E (desktop) | g2-authenticated.spec.ts + g2-acceptance-seed.sql | Playwright (g2-authenticated-acceptance.yml) | NOT_RUN (seed failure prevented Playwright) | PENDING | IMPLEMENTED |
| 31 | G2 Employee mobile E2E (real mutation) | g2-authenticated.spec.ts (mobile viewport, clock-in/out mutation + API verification) | Playwright (g2-authenticated-acceptance.yml) | NOT_RUN (seed failure prevented Playwright) | PENDING | IMPLEMENTED |
| 32 | G2 step-instance approval binding regression | HrLeaveWorkflowAdapter strict resolver | HrLeaveWorkflowAdapterStepBindingTest (10 tests) | NOT_RUN (local backend NOT_RUN_ENVIRONMENT_LIMITATION) | PENDING | IMPLEMENTED |
| 33 | PostgreSQL step-binding regression (NOT full lifecycle) | HrG2LeavePostgresIntegrationTest (uses real JDBC repos for step-binding data, mocks WorkflowApprovalService/WorkflowExecutionService/WorkflowGraphExecutionService/WorkflowEntitlementGuard; manually simulates Manager→HR progression) | HrG2LeavePostgresIntegrationTest (4 tests) | NOT_RUN (local backend NOT_RUN_ENVIRONMENT_LIMITATION) | PENDING | IMPLEMENTED — STEP_BINDING_REGRESSION only, NOT FULL_LIFECYCLE |
| 34 | Full backend leave lifecycle (real service integration) | NOT YET — current HrG2LeavePostgresIntegrationTest mocks workflow services | NOT YET | NOT_PROVEN | NOT_PROVEN | NOT_IMPLEMENTED |
| 35 | G2 tenant isolation | tenantId + RLS + FK constraints | HrG2PostgresIntegrationTest + HrG2LeavePostgresIntegrationTest | PENDING | PENDING | IMPLEMENTED |

## Notes
- "IMPLEMENTED" means the source code is present in the repository; it does NOT mean CI has certified the implementation.
- "PENDING" CI Evidence means the new head SHA's fresh exact-head CI has not yet been observed terminal SUCCESS.
- "FAILURE" CI Evidence (e81aa13e column) is the historical failed checkpoint — preserved as evidence, DO NOT reuse.
- "NOT_RUN" means the CI job started but the test was not executed (typically because an earlier setup step failed).
- "NOT_PROVEN" means the test/evidence does not exist or does not cover the full required scope.
- "local PASS" annotations are local-only verification (frontend-only); they do NOT certify CI.
- Row 9 (Leave policies) is PARTIAL because policy administration UI exists but policy-driven entitlement rule evaluation is not yet wired.
- Row 27 (Full OpenAPI contract) is NOT YET because the contract test verifies only paths/methods, not full request/response schemas + 4xx error codes + Idempotency-Key + expectedVersion/If-Match.
- Row 33 is truthfully classified as STEP_BINDING_REGRESSION only (not FULL_LIFECYCLE) because the test mocks WorkflowApprovalService/WorkflowExecutionService/WorkflowGraphExecutionService/WorkflowEntitlementGuard and manually simulates Manager→HR progression. It is useful regression evidence for the step-binding defect but does NOT prove full service lifecycle.
- Row 34 (Full backend leave lifecycle) is NOT YET — a true service integration suite that exercises real WorkflowApprovalService + WorkflowGraphExecutionService (no mocks) does not yet exist.
