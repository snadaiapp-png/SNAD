# G2 Final Requirement Matrix

> Status authority: DRAFT — PRE-MERGE
> All CI Evidence values are PENDING until the new head SHA's fresh exact-head CI completes.
> Historical rows from prior pushes (495c7d65 / 560df3be / 50111a35) are NOT reused as certification.

| # | Requirement | Implementation | Test | CI Evidence | Status |
|---|---|---|---|---|---|
| 1 | Work schedules | HrScheduleService + V20260924_1 | HrG2PostgresIntegrationTest | PENDING | IMPLEMENTED |
| 2 | Schedule assignments | HrScheduleService.assignSchedule | schema test | PENDING | IMPLEMENTED |
| 3 | Overlap protection | DB unique index | schema test | PENDING | IMPLEMENTED |
| 4 | Attendance events | hr_attendance_events + V20260924_2 | schema test | PENDING | IMPLEMENTED |
| 5 | Clock-in/out | HrTimeAttendanceService | HrG2PostgresIntegrationTest | PENDING | IMPLEMENTED |
| 6 | Calculation engine | HrAttendanceCalculationService | (internal) | PENDING | IMPLEMENTED |
| 7 | Timesheets lifecycle | HrTimesheetService | HrG2PostgresIntegrationTest | PENDING | IMPLEMENTED |
| 8 | Leave types (identity) | V20260923_3 seed | schema test | PENDING | IMPLEMENTED |
| 9 | Leave policies | hr_leave_policies table | schema test | PENDING | PARTIAL |
| 10 | Leave ledger | HrLeaveLedgerService | schema test | PENDING | IMPLEMENTED |
| 11 | Leave state machine (8 states) | HrLeaveService | schema test | PENDING | IMPLEMENTED |
| 12 | Workflow Engine integration | HrLeaveWorkflowAdapter (step-instance-bound) | HrLeaveWorkflowAdapterStepBindingTest + HrG2LeavePostgresIntegrationTest | PENDING | IMPLEMENTED |
| 13 | Workflow definition resolution | findOrPublishDefinition (deterministic familyId) | HrG2LeavePostgresIntegrationTest | PENDING | IMPLEMENTED |
| 14 | Workflow approval requests | via adapter + WorkflowApprovalService | HrG2LeavePostgresIntegrationTest | PENDING | IMPLEMENTED |
| 15 | Idempotency persistence | SELECT FOR UPDATE + DB unique constraint | HrG2LeavePostgresIntegrationTest | PENDING | IMPLEMENTED |
| 16 | Clock abstraction | TimeClockConfig (Clock bean) | (internal) | PENDING | IMPLEMENTED |
| 17 | Audit + outbox | hr_audit_ledger + hr_domain_event_outbox | schema test | PENDING | IMPLEMENTED |
| 18 | Monthly report (G2-T05) | HrTimeAttendanceService.monthlyReport | (API) | PENDING | IMPLEMENTED |
| 19 | RLS (all G2 tables) | ENABLE+FORCE+tenant_isolation | HrG2PostgresIntegrationTest | PENDING | IMPLEMENTED |
| 20 | RBAC (SELF/TEAM/HR) | 13 capabilities + @RequireCapability | (internal) | PENDING | IMPLEMENTED |
| 21 | Arabic/English i18n | hrm-g2-i18n.ts (73 keys) | i18n parity check | PENDING (local PASS 1125=1125) | IMPLEMENTED |
| 22 | RTL/LTR | logical CSS only | SDS check | PENDING (local PASS) | IMPLEMENTED |
| 23 | Employee UI | 3 screens (attendance+timesheets+leave) | vitest | PENDING (local PASS 1034/1034) | IMPLEMENTED |
| 24 | Manager UI | 3 screens (team-attendance+timesheets+approvals) | vitest | PENDING (local PASS) | IMPLEMENTED |
| 25 | HR UI | 4 screens (schedules+policies+admin+reports) | vitest | PENDING (local PASS) | IMPLEMENTED |
| 26 | OpenAPI contract | springdoc @Operation annotations | HrG2OpenApiContractTest | PENDING | IMPLEMENTED |
| 27 | G2 authenticated Employee E2E (desktop) | g2-authenticated.spec.ts + g2-acceptance-seed.sql | Playwright (g2-authenticated-acceptance.yml) | PENDING | IMPLEMENTED |
| 28 | G2 authenticated Manager E2E (desktop) | g2-authenticated.spec.ts + g2-acceptance-seed.sql | Playwright (g2-authenticated-acceptance.yml) | PENDING | IMPLEMENTED |
| 29 | G2 authenticated HR E2E (desktop) | g2-authenticated.spec.ts + g2-acceptance-seed.sql | Playwright (g2-authenticated-acceptance.yml) | PENDING | IMPLEMENTED |
| 30 | G2 Employee mobile E2E | g2-authenticated.spec.ts (mobile viewport) | Playwright (g2-authenticated-acceptance.yml) | PENDING | IMPLEMENTED |
| 31 | G2 step-instance approval binding | HrLeaveWorkflowAdapter strict resolver | HrLeaveWorkflowAdapterStepBindingTest (10 tests) | PENDING (local backend NOT_RUN_ENVIRONMENT_LIMITATION) | IMPLEMENTED |
| 32 | G2 PostgreSQL Direct lifecycle | HrG2LeavePostgresIntegrationTest (4 tests) | Playwright (g2-authenticated-acceptance.yml) — host-native PG | PENDING | IMPLEMENTED |
| 33 | G2 tenant isolation | tenantId + RLS + FK constraints | HrG2PostgresIntegrationTest + HrG2LeavePostgresIntegrationTest | PENDING | IMPLEMENTED |

## Notes
- "IMPLEMENTED" means the source code is present in the repository; it does NOT mean CI has certified the implementation.
- "PENDING" CI Evidence means the new head SHA's fresh exact-head CI has not yet been observed terminal SUCCESS.
- "local PASS" annotations are local-only verification (frontend-only or NOT_RUN_ENVIRONMENT_LIMITATION for backend); they do NOT certify CI.
- Row 9 (Leave policies) is PARTIAL because policy administration UI exists but policy-driven entitlement rule evaluation is not yet wired.
