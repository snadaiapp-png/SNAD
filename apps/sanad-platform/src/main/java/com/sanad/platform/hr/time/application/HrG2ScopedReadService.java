package com.sanad.platform.hr.time.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Relationship-aware read model for G2 TEAM and HR administration surfaces.
 *
 * <p>Capability checks remain at the controller boundary. This service owns
 * data-scope narrowing: TEAM means direct reports of the authenticated
 * manager, while HR administration remains tenant-scoped. Tenant isolation is
 * additionally enforced by PostgreSQL RLS.</p>
 */
@Service
public class HrG2ScopedReadService {

    private final JdbcTemplate jdbc;
    private final HrTimeAttendanceService timeService;
    private final HrLeaveService leaveService;

    public HrG2ScopedReadService(
            JdbcTemplate jdbc,
            HrTimeAttendanceService timeService,
            HrLeaveService leaveService) {
        this.jdbc = jdbc;
        this.timeService = timeService;
        this.leaveService = leaveService;
    }

    @Transactional(readOnly = true)
    public List<HrTimeAttendanceV2Controller.HrAttendanceRecordResponse> listTeamAttendance(
            UUID tenantId,
            UUID managerUserId,
            LocalDate startDate,
            LocalDate endDate) {
        StringBuilder sql = new StringBuilder("""
                SELECT ar.id, ar.employment_id, ar.record_date, ar.clock_in, ar.clock_out,
                       ar.break_minutes, ar.worked_minutes, ar.source, ar.state
                  FROM hr_attendance_records ar
                  JOIN hr_employees employee
                    ON employee.id = ar.employment_id
                   AND employee.tenant_id = ar.tenant_id
                  JOIN hr_employee_assignments employee_assignment
                    ON employee_assignment.employment_id = employee.id
                   AND employee_assignment.tenant_id = employee.tenant_id
                  JOIN hr_employee_assignments manager_assignment
                    ON manager_assignment.id = employee_assignment.reports_to_assignment_id
                   AND manager_assignment.tenant_id = employee_assignment.tenant_id
                  JOIN hr_employees manager
                    ON manager.id = manager_assignment.employment_id
                   AND manager.tenant_id = manager_assignment.tenant_id
                  JOIN hr_people manager_person
                    ON manager_person.id = manager.person_id
                   AND manager_person.tenant_id = manager.tenant_id
                 WHERE ar.tenant_id = ?
                   AND manager_person.user_id = ?
                   AND employee.status = 'ACTIVE'
                   AND manager.status = 'ACTIVE'
                   AND employee_assignment.assignment_type = 'PRIMARY'
                   AND employee_assignment.status = 'ACTIVE'
                   AND employee_assignment.effective_from <= CURRENT_DATE
                   AND (employee_assignment.effective_to IS NULL OR employee_assignment.effective_to >= CURRENT_DATE)
                   AND manager_assignment.assignment_type = 'PRIMARY'
                   AND manager_assignment.status = 'ACTIVE'
                   AND manager_assignment.effective_from <= CURRENT_DATE
                   AND (manager_assignment.effective_to IS NULL OR manager_assignment.effective_to >= CURRENT_DATE)
                """);
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        params.add(managerUserId);
        if (startDate != null) {
            sql.append(" AND ar.record_date >= ?");
            params.add(startDate);
        }
        if (endDate != null) {
            sql.append(" AND ar.record_date <= ?");
            params.add(endDate);
        }
        sql.append(" ORDER BY ar.record_date DESC, ar.employment_id");

        return jdbc.query(sql.toString(), (rs, rowNum) ->
                new HrTimeAttendanceV2Controller.HrAttendanceRecordResponse(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("employment_id")),
                        rs.getDate("record_date").toLocalDate(),
                        rs.getTimestamp("clock_in") != null ? rs.getTimestamp("clock_in").toInstant() : null,
                        rs.getTimestamp("clock_out") != null ? rs.getTimestamp("clock_out").toInstant() : null,
                        rs.getInt("break_minutes"),
                        rs.getObject("worked_minutes") != null ? rs.getInt("worked_minutes") : null,
                        rs.getString("source"),
                        rs.getString("state")),
                params.toArray());
    }

    @Transactional(readOnly = true)
    public List<HrTimeAttendanceV2Controller.HrAttendanceRecordResponse> listAdminAttendance(
            UUID tenantId,
            UUID employmentId,
            LocalDate startDate,
            LocalDate endDate) {
        return timeService.listAttendance(tenantId, employmentId, startDate, endDate);
    }

    @Transactional(readOnly = true)
    public List<HrTimesheetService.TimesheetResponse> listTeamTimesheets(
            UUID tenantId,
            UUID managerUserId,
            String state) {
        StringBuilder sql = new StringBuilder("""
                SELECT ts.id, ts.employment_id, ts.period_start, ts.period_end, ts.state,
                       ts.submitted_at, ts.approved_at, ts.approver_comment
                  FROM hr_timesheets ts
                  JOIN hr_employees employee
                    ON employee.id = ts.employment_id
                   AND employee.tenant_id = ts.tenant_id
                  JOIN hr_employee_assignments employee_assignment
                    ON employee_assignment.employment_id = employee.id
                   AND employee_assignment.tenant_id = employee.tenant_id
                  JOIN hr_employee_assignments manager_assignment
                    ON manager_assignment.id = employee_assignment.reports_to_assignment_id
                   AND manager_assignment.tenant_id = employee_assignment.tenant_id
                  JOIN hr_employees manager
                    ON manager.id = manager_assignment.employment_id
                   AND manager.tenant_id = manager_assignment.tenant_id
                  JOIN hr_people manager_person
                    ON manager_person.id = manager.person_id
                   AND manager_person.tenant_id = manager.tenant_id
                 WHERE ts.tenant_id = ?
                   AND manager_person.user_id = ?
                   AND employee.status = 'ACTIVE'
                   AND manager.status = 'ACTIVE'
                   AND employee_assignment.assignment_type = 'PRIMARY'
                   AND employee_assignment.status = 'ACTIVE'
                   AND employee_assignment.effective_from <= CURRENT_DATE
                   AND (employee_assignment.effective_to IS NULL OR employee_assignment.effective_to >= CURRENT_DATE)
                   AND manager_assignment.assignment_type = 'PRIMARY'
                   AND manager_assignment.status = 'ACTIVE'
                   AND manager_assignment.effective_from <= CURRENT_DATE
                   AND (manager_assignment.effective_to IS NULL OR manager_assignment.effective_to >= CURRENT_DATE)
                """);
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        params.add(managerUserId);
        if (state != null && !state.isBlank()) {
            sql.append(" AND ts.state = ?");
            params.add(state);
        }
        sql.append(" ORDER BY ts.period_start DESC");

        return jdbc.query(sql.toString(), (rs, rowNum) -> new HrTimesheetService.TimesheetResponse(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("employment_id")),
                rs.getDate("period_start").toLocalDate(),
                rs.getDate("period_end").toLocalDate(),
                rs.getString("state"),
                rs.getTimestamp("submitted_at") != null ? rs.getTimestamp("submitted_at").toInstant() : null,
                rs.getTimestamp("approved_at") != null ? rs.getTimestamp("approved_at").toInstant() : null,
                rs.getString("approver_comment")),
                params.toArray());
    }

    @Transactional(readOnly = true)
    public List<HrTimeAttendanceV2Controller.HrLeaveRequestResponse> listTeamLeaveRequests(
            UUID tenantId,
            UUID managerUserId,
            String state) {
        StringBuilder sql = new StringBuilder("""
                SELECT lr.id, lr.employment_id, lr.leave_type_id, lr.start_date, lr.end_date,
                       lr.days_count, lr.reason, lr.state, lr.submitted_at, lr.approved_at,
                       lr.approver_comment
                  FROM hr_leave_requests lr
                  JOIN hr_employees employee
                    ON employee.id = lr.employment_id
                   AND employee.tenant_id = lr.tenant_id
                  JOIN hr_employee_assignments employee_assignment
                    ON employee_assignment.employment_id = employee.id
                   AND employee_assignment.tenant_id = employee.tenant_id
                  JOIN hr_employee_assignments manager_assignment
                    ON manager_assignment.id = employee_assignment.reports_to_assignment_id
                   AND manager_assignment.tenant_id = employee_assignment.tenant_id
                  JOIN hr_employees manager
                    ON manager.id = manager_assignment.employment_id
                   AND manager.tenant_id = manager_assignment.tenant_id
                  JOIN hr_people manager_person
                    ON manager_person.id = manager.person_id
                   AND manager_person.tenant_id = manager.tenant_id
                 WHERE lr.tenant_id = ?
                   AND manager_person.user_id = ?
                   AND employee.status = 'ACTIVE'
                   AND manager.status = 'ACTIVE'
                   AND employee_assignment.assignment_type = 'PRIMARY'
                   AND employee_assignment.status = 'ACTIVE'
                   AND employee_assignment.effective_from <= CURRENT_DATE
                   AND (employee_assignment.effective_to IS NULL OR employee_assignment.effective_to >= CURRENT_DATE)
                   AND manager_assignment.assignment_type = 'PRIMARY'
                   AND manager_assignment.status = 'ACTIVE'
                   AND manager_assignment.effective_from <= CURRENT_DATE
                   AND (manager_assignment.effective_to IS NULL OR manager_assignment.effective_to >= CURRENT_DATE)
                """);
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        params.add(managerUserId);
        if (state != null && !state.isBlank()) {
            sql.append(" AND lr.state = ?");
            params.add(state);
        }
        sql.append(" ORDER BY lr.submitted_at DESC NULLS LAST, lr.id");

        return jdbc.query(sql.toString(), (rs, rowNum) ->
                new HrTimeAttendanceV2Controller.HrLeaveRequestResponse(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("employment_id")),
                        UUID.fromString(rs.getString("leave_type_id")),
                        rs.getDate("start_date").toLocalDate(),
                        rs.getDate("end_date").toLocalDate(),
                        rs.getBigDecimal("days_count"),
                        rs.getString("reason"),
                        rs.getString("state"),
                        rs.getTimestamp("submitted_at") != null ? rs.getTimestamp("submitted_at").toInstant() : null,
                        rs.getTimestamp("approved_at") != null ? rs.getTimestamp("approved_at").toInstant() : null,
                        rs.getString("approver_comment")),
                params.toArray());
    }

    @Transactional(readOnly = true)
    public List<HrTimeAttendanceV2Controller.HrLeaveRequestResponse> listHrLeaveRequests(
            UUID tenantId,
            String state) {
        return leaveService.listLeaveRequests(tenantId, null, state);
    }

    @Transactional(readOnly = true)
    public List<HrTimeAttendanceV2Controller.HrLeaveTypeResponse> listAdminLeaveTypes(UUID tenantId) {
        return leaveService.listLeaveTypes(tenantId);
    }

    @Transactional(readOnly = true)
    public List<HrTimeAttendanceV2Controller.MonthlyAttendanceReportRow> teamMonthlyAttendanceReport(
            UUID tenantId,
            UUID managerUserId,
            int year,
            int month) {
        List<UUID> directReports = jdbc.query(
                """
                SELECT employee.id
                  FROM hr_people manager_person
                  JOIN hr_employees manager
                    ON manager.person_id = manager_person.id
                   AND manager.tenant_id = manager_person.tenant_id
                  JOIN hr_employee_assignments manager_assignment
                    ON manager_assignment.employment_id = manager.id
                   AND manager_assignment.tenant_id = manager.tenant_id
                  JOIN hr_employee_assignments employee_assignment
                    ON employee_assignment.tenant_id = manager_assignment.tenant_id
                   AND employee_assignment.reports_to_assignment_id = manager_assignment.id
                  JOIN hr_employees employee
                    ON employee.id = employee_assignment.employment_id
                   AND employee.tenant_id = employee_assignment.tenant_id
                 WHERE manager_person.tenant_id = ?
                   AND manager_person.user_id = ?
                   AND employee.status = 'ACTIVE'
                   AND manager.status = 'ACTIVE'
                   AND employee_assignment.assignment_type = 'PRIMARY'
                   AND employee_assignment.status = 'ACTIVE'
                   AND employee_assignment.effective_from <= CURRENT_DATE
                   AND (employee_assignment.effective_to IS NULL OR employee_assignment.effective_to >= CURRENT_DATE)
                   AND manager_assignment.assignment_type = 'PRIMARY'
                   AND manager_assignment.status = 'ACTIVE'
                   AND manager_assignment.effective_from <= CURRENT_DATE
                   AND (manager_assignment.effective_to IS NULL OR manager_assignment.effective_to >= CURRENT_DATE)
                 ORDER BY employee.id
                """,
                (rs, rowNum) -> UUID.fromString(rs.getString("id")),
                tenantId,
                managerUserId);

        return directReports.stream()
                .flatMap(employmentId -> timeService.monthlyAttendanceReport(
                        tenantId, year, month, employmentId).stream())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HrTimeAttendanceV2Controller.MonthlyAttendanceReportRow> adminMonthlyAttendanceReport(
            UUID tenantId,
            int year,
            int month,
            UUID employmentId) {
        return timeService.monthlyAttendanceReport(tenantId, year, month, employmentId);
    }
}
