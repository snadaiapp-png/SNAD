# HRM-G2: Time & Attendance — Scheduling — Timesheets — Leave Design

> **Status:** DRAFT — PRE-MERGE  
> **Branch:** `hr/g2-time-attendance-leave`  
> **Base SHA:** `8e4079cc148a297052b171690ea0dec6e3d26904`

## 1. Purpose

G2 implements time & attendance management with work scheduling,
attendance event tracking, timesheet lifecycle, and leave management
with a multi-step approval workflow using the central Workflow Engine.

## 2. Domain Model

### 2.1 Work Schedules
- `hr_work_schedules` — schedule template (working days, start/end time, break rules)
- `hr_schedule_versions` — versioned schedule definitions (immutable after publish)
- `hr_schedule_assignments` — effective-dated employee→schedule binding

### 2.2 Attendance Events
- `hr_attendance_events` — append-only event log (CLOCK_IN, CLOCK_OUT, BREAK_START, BREAK_END, MANUAL_CORRECTION)
- Provenance: actor, source, reason, idempotency identity, timestamp, timezone

### 2.3 Timesheets
- `hr_timesheets` — period aggregation (DRAFT→SUBMITTED→APPROVED, REJECTED, LOCKED)
- `hr_timesheet_entries` — per-day entries within a timesheet period

### 2.4 Leave
- `hr_leave_types` — identity only (no statutory entitlements)
- `hr_leave_policies` — configurable entitlement rules per tenant
- `hr_leave_ledger_entries` — append-only ledger (OPENING, ACCRUAL, ADJUSTMENT, RESERVATION, RELEASE, CONSUMPTION, CARRYOVER, EXPIRY)
- `hr_leave_requests` — state machine: DRAFT→SUBMITTED→PENDING_MANAGER→PENDING_HR→APPROVED (with REJECTED, WITHDRAWN, CANCELLED)

## 3. State Machines

### 3.1 Attendance Record
```
OPEN → COMPLETED (clock-out)
OPEN → MISSED (no clock-out by end of day)
COMPLETED → CORRECTED (manual correction)
```

### 3.2 Timesheet
```
DRAFT → SUBMITTED → APPROVED
DRAFT → SUBMITTED → REJECTED → DRAFT
APPROVED → LOCKED
```

### 3.3 Leave Request
```
DRAFT → SUBMITTED → PENDING_MANAGER → PENDING_HR → APPROVED
PENDING_MANAGER → REJECTED
PENDING_HR → REJECTED
SUBMITTED → WITHDRAWN
APPROVED → CANCELLED
```

## 4. Leave Balance Ledger

Current balance = projection from append-only ledger entries:
- OPENING: beginning-of-year balance
- ACCRUAL: periodic accrual per policy
- RESERVATION: pending leave request reserves days
- RELEASE: rejected/cancelled request releases reservation
- CONSUMPTION: approved leave consumes reserved days
- ADJUSTMENT: manual HR adjustment
- CARRYOVER: year-end carryover
- EXPIRY: expired balance

## 5. Workflow Engine Integration

Leave approval uses the central Workflow Engine:
1. Employee submits → creates workflow instance
2. Manager task → approve/reject
3. HR task → approve/reject
4. On final approval: leave ledger CONSUMPTION entry + audit + outbox

## 6. Idempotency

Persistent idempotency via `hr_idempotency_records` table (same pattern as G1):
- (tenant_id, idempotency_key, request_fingerprint) UNIQUE
- Same key + same fingerprint → replay original response
- Same key + different fingerprint → 409 conflict

## 7. Audit + Outbox

Every core mutation writes:
- `hr_audit_ledger` entry (actor, action, resource, timestamp)
- `hr_domain_event_outbox` entry (event type, payload, tenant)
- In the SAME transaction as the business mutation

Events: AttendanceRecorded, AttendanceCorrected, TimesheetSubmitted, TimesheetApproved, TimesheetRejected, LeaveRequested, LeaveManagerApproved, LeaveHrApproved, LeaveRejected, LeaveCancelled, LeaveBalanceChanged

## 8. Clock Abstraction

All time-dependent business logic uses `java.time.Clock` (injectable):
- `ClockProvider` bean configured per environment
- Tests inject a fixed Clock for deterministic testing
- No `Instant.now()` or `LocalDate.now()` in business logic

## 9. RLS

All tenant-owned G2 tables: ENABLE + FORCE RLS + tenant_isolation policy.
Migration seeding uses `set_config('app.tenant_id', ...)` pattern (V20260918_4 convention).

## 10. API V2

Endpoints under `/api/v2/hr/`:
- `/time/schedules` — CRUD + assignment
- `/time/attendance/events` — clock-in/out/break events
- `/time/attendance/records` — derived attendance records
- `/time/attendance/monthly-report` — G2-T05
- `/time/timesheets` — lifecycle management
- `/leave/types` — leave type catalog
- `/leave/policies` — entitlement rules
- `/leave/balances` — derived balance projection
- `/leave/requests` — state machine + workflow

## 11. Frontend

### Employee
- `/hr/attendance` — clock in/out, history, exceptions
- `/hr/timesheets` — my timesheets, submit
- `/hr/leave` — balances, request, status

### Manager
- `/hr/team-attendance` — team attendance, exceptions
- `/hr/team-timesheets` — approval queue
- `/hr/leave/approvals` — leave approval queue

### HR
- `/hr/attendance/admin` — corrections, administration
- `/hr/schedules` — schedule management
- `/hr/leave/policies` — policy management
- `/hr/reports/attendance` — monthly report

## 12. Legal Boundary

G2 Core is country-neutral. No statutory entitlement values.
Saudi-specific configuration comes from an independent Country Pack.
No `SAUDI_LEGAL_COMPLIANT` claim without independent legal review.

## 13. Payroll Boundary

G2 does NOT implement payroll valuation.
Work-hours and attendance status are computed for payroll input only.
Payroll calculation remains outside G2 scope.
