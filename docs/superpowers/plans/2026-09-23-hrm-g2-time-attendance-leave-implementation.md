# HRM-G2 Implementation Plan

> **Status:** DRAFT — PRE-MERGE  
> **Branch:** `hr/g2-time-attendance-leave`  
> **Base SHA:** `8e4079cc148a297052b171690ea0dec6e3d26904`

## Task Cards

### G2-T01: Work Schedules Schema
- **Status:** DONE (V20260923_1 + V20260924_1)
- **Migration:** `V20260924_1__hr_g2_scheduling_schema.sql`
- **Tables:** hr_work_schedules, hr_schedule_versions, hr_schedule_assignments

### G2-T02: Leave Requests Schema
- **Status:** DONE (V20260923_1)
- **Migration:** `V20260923_1__hr_g2_time_attendance_leave_schema.sql`
- **Tables:** hr_leave_types, hr_leave_balances, hr_attendance_records, hr_leave_requests, hr_timesheets

### G2-T03: Attendance Tracking UI
- **Status:** DONE
- **Route:** `/hr/attendance`
- **Features:** clock-in/out, history, permission-scoped

### G2-T04: Leave Management UI
- **Status:** DONE
- **Route:** `/hr/leave`
- **Features:** request form, balances, approval workflow

### G2-T05: Monthly Attendance Report
- **Status:** IMPLEMENTED (backend + API endpoint)
- **Route:** `/hr/reports/attendance` (pending frontend)
- **Endpoint:** GET `/api/v2/hr/time/attendance/monthly-report`

### G2-T06: Attendance Event Model
- **Status:** DONE (V20260924_2)
- **Migration:** `V20260924_2__hr_g2_attendance_events_and_ledger.sql`
- **Tables:** hr_attendance_events, hr_leave_ledger_entries, hr_leave_policies, hr_idempotency_records

### G2-T07: Calculation Engine
- **Status:** DONE
- **Service:** HrAttendanceCalculationService
- **Features:** expected/worked/break minutes, lateness, early departure, absence, missing punch

### G2-T08: Timesheet Lifecycle
- **Status:** DONE
- **Service:** HrTimesheetService
- **State machine:** DRAFT→SUBMITTED→APPROVED (REJECTED, LOCKED)

### G2-T09: Leave Ledger
- **Status:** DONE
- **Service:** HrLeaveLedgerService
- **Ledger types:** OPENING, ACCRUAL, ADJUSTMENT, RESERVATION, RELEASE, CONSUMPTION, CARRYOVER, EXPIRY

### G2-T10: Leave State Machine
- **Status:** DONE
- **States:** DRAFT→SUBMITTED→PENDING_MANAGER→PENDING_HR→APPROVED (REJECTED, WITHDRAWN, CANCELLED)

### G2-T11: Audit + Outbox
- **Status:** DONE
- **Integration:** Uses existing hr_audit_ledger + hr_domain_event_outbox tables
- **Events:** AttendanceRecorded, AttendanceCorrected, TimesheetSubmitted, TimesheetApproved, LeaveRequested, LeaveBalanceChanged

### G2-T12: Idempotency
- **Status:** DONE
- **Table:** hr_idempotency_records
- **Pattern:** (tenant_id, idempotency_key, request_fingerprint) UNIQUE

### G2-T13: Clock Abstraction
- **Status:** DONE
- **Pattern:** java.time.Clock injected via Spring @Bean

### G2-T14: Full Frontend
- **Status:** PARTIAL (attendance + leave done; team/HR views pending)

### G2-T15: Backend Integration Tests
- **Status:** NOT IMPLEMENTED (requires PostgreSQL Direct on CI)

## Execution Order

1. ✅ V20260923_1 schema (base tables)
2. ✅ V20260923_2 RLS policies
3. ✅ V20260923_3 seed (migration-safe RLS pattern)
4. ✅ Backend services + controller
5. ✅ Frontend attendance + leave
6. ✅ V20260924_1 scheduling schema
7. ✅ V20260924_2 attendance events + ledger + policies + idempotency
8. ✅ Calculation engine + timesheet lifecycle
9. ✅ Leave state machine + ledger
10. ✅ Audit/outbox integration
11. ✅ Clock abstraction
12. ✅ G2-T05 monthly report endpoint
13. ⬜ Frontend: team/HR views
14. ⬜ Backend integration tests
15. ⬜ Design/plan docs completion
16. ⬜ Commit + push + CI verification
