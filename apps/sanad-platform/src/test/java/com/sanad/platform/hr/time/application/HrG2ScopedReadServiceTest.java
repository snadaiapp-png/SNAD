package com.sanad.platform.hr.time.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HrG2ScopedReadServiceTest {

    private JdbcTemplate jdbc;
    private HrG2ScopedReadService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new HrG2ScopedReadService(
                jdbc,
                mock(HrTimeAttendanceService.class),
                mock(HrLeaveService.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void teamLeaveReadUsesCanonicalPersonAndAssignmentReportingGraph() {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        UUID tenantId = UUID.randomUUID();
        UUID managerUserId = UUID.randomUUID();
        service.listTeamLeaveRequests(tenantId, managerUserId, null);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), any(Object[].class));

        assertCanonicalTeamScope(sql.getValue());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void teamAttendanceReadUsesCanonicalPersonAndAssignmentReportingGraph() {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        UUID tenantId = UUID.randomUUID();
        UUID managerUserId = UUID.randomUUID();
        service.listTeamAttendance(tenantId, managerUserId, null, null);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), any(Object[].class));

        assertCanonicalTeamScope(sql.getValue());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void teamTimesheetReadUsesCanonicalPersonAndAssignmentReportingGraph() {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        UUID tenantId = UUID.randomUUID();
        UUID managerUserId = UUID.randomUUID();
        service.listTeamTimesheets(tenantId, managerUserId, null);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), any(Object[].class));

        assertCanonicalTeamScope(sql.getValue());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void teamMonthlyReportResolvesDirectReportsThroughCanonicalAssignments() {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        UUID tenantId = UUID.randomUUID();
        UUID managerUserId = UUID.randomUUID();
        service.teamMonthlyAttendanceReport(tenantId, managerUserId, 2026, 9);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), any(Object[].class));

        assertCanonicalTeamScope(sql.getValue());
    }

    private void assertCanonicalTeamScope(String sql) {
        assertThat(sql)
                .contains("JOIN hr_people manager_person")
                .contains("JOIN hr_employee_assignments manager_assignment")
                .contains("JOIN hr_employee_assignments employee_assignment")
                .contains("employee_assignment.reports_to_assignment_id = manager_assignment.id")
                .contains("manager_person.user_id = ?")
                .doesNotContain("manager.user_id = ?")
                .doesNotContain("manager.id = employee.manager_id");
    }
}
