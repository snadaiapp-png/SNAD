# G2 Final Requirement Matrix

| # | Requirement | Implementation | Migration | API | UI | Test | CI Evidence | Status |
|---|---|---|---|---|---|---|---|---|
| 1 | Work schedules | HrScheduleService | V20260924_1 | /time/schedules | /hr/schedules | HrG2IntegrationTestSkeleton | PENDING | DONE |
| 2 | Schedule versions | HrScheduleService | V20260924_1 | /time/schedules | /hr/schedules | skeleton | PENDING | PARTIAL |
| 3 | Effective-dated assignments | HrScheduleService.assignSchedule | V20260924_1 | /time/schedules/assign | /hr/schedules | skeleton | PENDING | DONE |
| 4 | Overlap protection | DB unique index | V20260924_1 | N/A | N/A | skeleton | PENDING | DONE |
| 5 | Attendance events | hr_attendance_events table | V20260924_2 | (via records) | /hr/attendance | skeleton | PENDING | DONE |
| 6 | Clock-in/out | HrTimeAttendanceService | V20260923_1 | /attendance/clock-in | /hr/attendance | skeleton | PENDING | DONE |
| 7 | Breaks | event model (BREAK_START/END) | V20260924_2 | (future) | (future) | skeleton | PENDING | PARTIAL |
| 8 | Manual correction | event model (MANUAL_CORRECTION) | V20260924_2 | (future) | (future) | skeleton | PENDING | PARTIAL |
| 9 | Calculation engine | HrAttendanceCalculationService | N/A | (internal) | (derived) | skeleton | PENDING | DONE |
| 10 | Timesheets lifecycle | HrTimesheetService | V20260923_1 | /timesheets/* | /hr/timesheets | skeleton | PENDING | DONE |
| 11 | Leave types (identity only) | V20260923_3 seed | V20260923_3 | /leave/types | /hr/leave | skeleton | PENDING | DONE |
| 12 | Leave policies | hr_leave_policies table | V20260924_2 | (future) | (future) | skeleton | PENDING | PARTIAL |
| 13 | Leave ledger | HrLeaveLedgerService | V20260924_2 | (internal) | (derived) | skeleton | PENDING | DONE |
| 14 | Leave state machine | HrLeaveService (8 states) | V20260924_3 | /leave/requests/* | /hr/leave | skeleton | PENDING | DONE |
| 15 | Workflow Engine integration | WorkflowInstance.start | V20260924_3 | (internal) | (derived) | skeleton | PENDING | PARTIAL |
| 16 | Idempotency persistence | hr_g2_idempotency_records | V20260924_2 | (header) | N/A | skeleton | PENDING | PARTIAL |
| 17 | Clock abstraction | TimeClockConfig (Clock bean) | N/A | N/A | N/A | skeleton | PENDING | DONE |
| 18 | Audit | hr_audit_ledger writes | N/A | N/A | N/A | skeleton | PENDING | DONE |
| 19 | Outbox | hr_domain_event_outbox writes | N/A | N/A | N/A | skeleton | PENDING | DONE |
| 20 | Monthly report (G2-T05) | HrTimeAttendanceService.monthlyReport | N/A | /attendance/monthly-report | /hr/reports/attendance | skeleton | PENDING | DONE |
| 21 | RLS (all G2 tables) | ENABLE+FORCE+tenant_isolation | V20260923_2+V20260924_1+V20260924_2 | N/A | N/A | skeleton | PENDING | DONE |
| 22 | RBAC (SELF/TEAM/HR) | 13 scope-separated capabilities | V20260923_3 | @RequireCapability | permission-scoped | skeleton | PENDING | DONE |
| 23 | Arabic/English i18n | hrm-g2-i18n.ts (73 keys) | N/A | N/A | all screens | i18n parity | PENDING | DONE |
| 24 | RTL/LTR | logical CSS only | N/A | N/A | all screens | SDS | PENDING | DONE |
| 25 | Employee UI | attendance+timesheets+leave | N/A | /time/* /leave/* | /hr/attendance /hr/timesheets /hr/leave | vitest | PENDING | DONE |
| 26 | Manager UI | team-attendance+timesheets+approvals | N/A | /time/timesheets/approve | /hr/team-* /hr/leave/approvals | vitest | PENDING | DONE |
| 27 | HR UI | schedules+reports | N/A | /time/schedules | /hr/schedules /hr/reports/attendance | vitest | PENDING | PARTIAL |
| 28 | Backend integration tests | HrG2IntegrationTestSkeleton | N/A | N/A | N/A | skeleton→real | PENDING | PARTIAL |
| 29 | Authenticated E2E | NOT IMPLEMENTED | N/A | N/A | N/A | NOT IMPLEMENTED | PENDING | NOT_IMPLEMENTED |
| 30 | OpenAPI | NOT UPDATED | N/A | N/A | N/A | NOT UPDATED | PENDING | NOT_IMPLEMENTED |
| 31 | Closure evidence | this matrix + manifest | N/A | N/A | N/A | N/A | PENDING | DONE |

## Summary
- DONE: 18
- PARTIAL: 8
- NOT_IMPLEMENTED: 2 (E2E, OpenAPI)
- PENDING CI: all
