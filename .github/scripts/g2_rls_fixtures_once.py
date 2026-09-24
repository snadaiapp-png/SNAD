from pathlib import Path

TARGET = Path("apps/sanad-platform/src/test/java/com/sanad/platform/hr/rls/HrRlsFailClosedIntegrationTest.java")
text = TARGET.read_text()

marker = "    // --- Generic seed dispatch ---\n"
if text.count(marker) != 1:
    raise SystemExit(f"fixture insertion marker count={text.count(marker)}")

helpers = r'''    // --- G2 time/attendance/leave/scheduling seed helpers ---

    private UUID insertG2LeaveType(UUID tenantId) throws Exception {
        UUID leaveTypeId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_leave_types (id, tenant_id, code, name_ar, name_en) " +
                "VALUES (?, ?, ?, 'RLS', 'RLS')")) {
            ps.setObject(1, leaveTypeId);
            ps.setObject(2, tenantId);
            ps.setString(3, "LT-" + leaveTypeId.toString().substring(0, 8));
            ps.executeUpdate();
        }
        return leaveTypeId;
    }

    private UUID insertG2Timesheet(UUID tenantId) throws Exception {
        UUID timesheetId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_timesheets (id, tenant_id, employment_id, period_start, period_end) " +
                "VALUES (?, ?, ?, DATE '2026-01-01', DATE '2026-01-07')")) {
            ps.setObject(1, timesheetId);
            ps.setObject(2, tenantId);
            ps.setObject(3, UUID.randomUUID());
            ps.executeUpdate();
        }
        return timesheetId;
    }

    private UUID insertG2WorkSchedule(UUID tenantId) throws Exception {
        UUID scheduleId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_work_schedules " +
                "(id, tenant_id, code, name_ar, name_en, shift_start, shift_end, expected_minutes) " +
                "VALUES (?, ?, ?, 'RLS', 'RLS', TIME '09:00', TIME '17:00', 480)")) {
            ps.setObject(1, scheduleId);
            ps.setObject(2, tenantId);
            ps.setString(3, "SCH-" + scheduleId.toString().substring(0, 8));
            ps.executeUpdate();
        }
        return scheduleId;
    }

    private void insertG2AttendanceEvent(UUID tenantId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_attendance_events (tenant_id, employment_id, event_type, event_timestamp) " +
                "VALUES (?, ?, 'CLOCK_IN', NOW())")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, UUID.randomUUID());
            ps.executeUpdate();
        }
    }

    private void insertG2AttendanceRecord(UUID tenantId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_attendance_records (tenant_id, employment_id, record_date) " +
                "VALUES (?, ?, DATE '2026-01-01')")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, UUID.randomUUID());
            ps.executeUpdate();
        }
    }

    private void insertG2IdempotencyRecord(UUID tenantId) throws Exception {
        UUID key = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_g2_idempotency_records " +
                "(tenant_id, idempotency_key, request_fingerprint, operation, response_status) " +
                "VALUES (?, ?, ?, 'RLS.TEST', 200)")) {
            ps.setObject(1, tenantId);
            ps.setString(2, key.toString());
            ps.setString(3, "fp-" + key);
            ps.executeUpdate();
        }
    }

    private void insertG2LeaveBalance(UUID tenantId) throws Exception {
        UUID leaveTypeId = insertG2LeaveType(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_leave_balances (tenant_id, employment_id, leave_type_id, year) " +
                "VALUES (?, ?, ?, 2026)")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, UUID.randomUUID());
            ps.setObject(3, leaveTypeId);
            ps.executeUpdate();
        }
    }

    private void insertG2LeaveLedgerEntry(UUID tenantId) throws Exception {
        UUID leaveTypeId = insertG2LeaveType(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_leave_ledger_entries " +
                "(tenant_id, employment_id, leave_type_id, entry_type, year, days) " +
                "VALUES (?, ?, ?, 'OPENING', 2026, 1)")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, UUID.randomUUID());
            ps.setObject(3, leaveTypeId);
            ps.executeUpdate();
        }
    }

    private void insertG2LeavePolicy(UUID tenantId) throws Exception {
        UUID leaveTypeId = insertG2LeaveType(tenantId);
        UUID policyId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_leave_policies " +
                "(id, tenant_id, leave_type_id, code, name_ar, name_en, effective_from) " +
                "VALUES (?, ?, ?, ?, 'RLS', 'RLS', DATE '2026-01-01')")) {
            ps.setObject(1, policyId);
            ps.setObject(2, tenantId);
            ps.setObject(3, leaveTypeId);
            ps.setString(4, "POL-" + policyId.toString().substring(0, 8));
            ps.executeUpdate();
        }
    }

    private void insertG2LeaveRequest(UUID tenantId) throws Exception {
        UUID leaveTypeId = insertG2LeaveType(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_leave_requests " +
                "(tenant_id, employment_id, leave_type_id, start_date, end_date, days_count, state) " +
                "VALUES (?, ?, ?, DATE '2026-01-01', DATE '2026-01-01', 1, 'DRAFT')")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, UUID.randomUUID());
            ps.setObject(3, leaveTypeId);
            ps.executeUpdate();
        }
    }

    private void insertG2ScheduleVersion(UUID tenantId) throws Exception {
        UUID scheduleId = insertG2WorkSchedule(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_schedule_versions " +
                "(tenant_id, schedule_id, version_number, definition) VALUES (?, ?, 1, '{}'::jsonb)")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, scheduleId);
            ps.executeUpdate();
        }
    }

    private void insertG2ScheduleAssignment(UUID tenantId) throws Exception {
        UUID scheduleId = insertG2WorkSchedule(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_schedule_assignments " +
                "(tenant_id, employment_id, schedule_id, effective_from) " +
                "VALUES (?, ?, ?, DATE '2026-01-01')")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, UUID.randomUUID());
            ps.setObject(3, scheduleId);
            ps.executeUpdate();
        }
    }

    private void insertG2TimesheetEntry(UUID tenantId) throws Exception {
        UUID timesheetId = insertG2Timesheet(tenantId);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hr_timesheet_entries (tenant_id, timesheet_id, entry_date) " +
                "VALUES (?, ?, DATE '2026-01-01')")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, timesheetId);
            ps.executeUpdate();
        }
    }

'''

text = text.replace(marker, helpers + marker)

default = '            default -> throw new IllegalArgumentException("No seed for table: " + table);\n'
if text.count(default) != 1:
    raise SystemExit(f"seed default count={text.count(default)}")

cases = r'''            case "hr_attendance_events" -> insertG2AttendanceEvent(tenantId);
            case "hr_attendance_records" -> insertG2AttendanceRecord(tenantId);
            case "hr_g2_idempotency_records" -> insertG2IdempotencyRecord(tenantId);
            case "hr_leave_balances" -> insertG2LeaveBalance(tenantId);
            case "hr_leave_ledger_entries" -> insertG2LeaveLedgerEntry(tenantId);
            case "hr_leave_policies" -> insertG2LeavePolicy(tenantId);
            case "hr_leave_requests" -> insertG2LeaveRequest(tenantId);
            case "hr_leave_types" -> insertG2LeaveType(tenantId);
            case "hr_schedule_assignments" -> insertG2ScheduleAssignment(tenantId);
            case "hr_schedule_versions" -> insertG2ScheduleVersion(tenantId);
            case "hr_timesheet_entries" -> insertG2TimesheetEntry(tenantId);
            case "hr_timesheets" -> insertG2Timesheet(tenantId);
            case "hr_work_schedules" -> insertG2WorkSchedule(tenantId);
'''
text = text.replace(default, cases + default)

for table in (
    "hr_attendance_events", "hr_attendance_records", "hr_g2_idempotency_records",
    "hr_leave_balances", "hr_leave_ledger_entries", "hr_leave_policies",
    "hr_leave_requests", "hr_leave_types", "hr_schedule_assignments",
    "hr_schedule_versions", "hr_timesheet_entries", "hr_timesheets", "hr_work_schedules",
):
    case = f'case "{table}"'
    if text.count(case) != 1:
        raise SystemExit(f"{case}: count={text.count(case)}")

if "default -> throw new IllegalArgumentException" not in text:
    raise SystemExit("fail-closed default dispatch removed")

TARGET.write_text(text)
print("G2 RLS fixtures patched for all 13 tenant-scoped tables")
