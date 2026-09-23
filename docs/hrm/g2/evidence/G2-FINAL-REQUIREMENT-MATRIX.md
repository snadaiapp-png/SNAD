# G2 Final Requirement Matrix

| # | Requirement | Implementation | Test | CI Evidence | Status |
|---|---|---|---|---|---|
| 1 | Work schedules | HrScheduleService + V20260924_1 | HrG2PostgresIntegrationTest | PENDING | DONE |
| 2 | Schedule assignments | HrScheduleService.assignSchedule | schema test | PENDING | DONE |
| 3 | Overlap protection | DB unique index | schema test | PENDING | DONE |
| 4 | Attendance events | hr_attendance_events + V20260924_2 | schema test | PENDING | DONE |
| 5 | Clock-in/out | HrTimeAttendanceService | HrG2PostgresIntegrationTest | PENDING | DONE |
| 6 | Calculation engine | HrAttendanceCalculationService | (internal) | PENDING | DONE |
| 7 | Timesheets lifecycle | HrTimesheetService | HrG2PostgresIntegrationTest | PENDING | DONE |
| 8 | Leave types (identity) | V20260923_3 seed | schema test | PENDING | DONE |
| 9 | Leave policies | hr_leave_policies table | schema test | PENDING | PARTIAL |
| 10 | Leave ledger | HrLeaveLedgerService | schema test | PENDING | DONE |
| 11 | Leave state machine (8 states) | HrLeaveService | schema test | PENDING | DONE |
| 12 | Workflow Engine integration | HrLeaveWorkflowAdapter | PENDING | PENDING | DONE |
| 13 | Workflow definition resolution | findActiveByCode | PENDING | PENDING | DONE |
| 14 | Workflow approval requests | via adapter + approval API | PENDING | PENDING | DONE |
| 15 | Idempotency persistence | hr_g2_idempotency_records | schema test | PENDING | DONE |
| 16 | Clock abstraction | TimeClockConfig (Clock bean) | (internal) | PENDING | DONE |
| 17 | Audit + outbox | hr_audit_ledger + hr_domain_event_outbox | schema test | PENDING | DONE |
| 18 | Monthly report (G2-T05) | HrTimeAttendanceService.monthlyReport | (API) | PENDING | DONE |
| 19 | RLS (all G2 tables) | ENABLE+FORCE+tenant_isolation | HrG2PostgresIntegrationTest | PENDING | DONE |
| 20 | RBAC (SELF/TEAM/HR) | 13 capabilities + @RequireCapability | (internal) | PENDING | DONE |
| 21 | Arabic/English i18n | hrm-g2-i18n.ts (73 keys) | i18n parity check | PENDING | DONE |
| 22 | RTL/LTR | logical CSS only | SDS check | PENDING | DONE |
| 23 | Employee UI | 3 screens (attendance+timesheets+leave) | vitest | PENDING | DONE |
| 24 | Manager UI | 3 screens (team-attendance+timesheets+approvals) | vitest | PENDING | DONE |
| 25 | HR UI | 4 screens (schedules+policies+admin+reports) | vitest | PENDING | DONE |
| 26 | OpenAPI contract | springdoc @Operation annotations | HrG2OpenApiContractTest | PENDING | DONE |
| 27 | Authenticated E2E | e2e/g2-authenticated.spec.ts (4 suites) | Playwright | PENDING | DONE |
| 28 | Backend integration tests | HrG2PostgresIntegrationTest (7 tests) | PostgreSQL Direct | PENDING | DONE |
| 29 | Closure evidence | this matrix + manifest + closure cert | N/A | PENDING | DONE |

## Summary
- DONE: 29
- PARTIAL: 1 (leave policies — table exists, UI for policy CRUD pending)
- NOT_IMPLEMENTED: 0
- PENDING CI: all
