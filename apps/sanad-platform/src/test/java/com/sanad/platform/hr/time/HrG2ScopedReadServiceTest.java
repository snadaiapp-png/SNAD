package com.sanad.platform.hr.time;

import com.sanad.platform.hr.time.application.HrG2ScopedReadService;
import com.sanad.platform.hr.time.application.HrLeaveService;
import com.sanad.platform.hr.time.application.HrTimeAttendanceService;
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

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void teamLeaveReadUsesCanonicalPersonAndPrimaryAssignmentReportingRelationship() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        HrTimeAttendanceService timeService = mock(HrTimeAttendanceService.class);
        HrLeaveService leaveService = mock(HrLeaveService.class);
        HrG2ScopedReadService service = new HrG2ScopedReadService(jdbc, timeService, leaveService);

        UUID tenantId = UUID.randomUUID();
        UUID managerUserId = UUID.randomUUID();
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        service.listTeamLeaveRequests(tenantId, managerUserId, null);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), any(Object[].class));

        assertThat(sql.getValue())
                .contains("JOIN hr_people manager_person")
                .contains("JOIN hr_employee_assignments manager_assignment")
                .contains("JOIN hr_employee_assignments employee_assignment")
                .contains("employee_assignment.reports_to_assignment_id = manager_assignment.id")
                .contains("manager_person.user_id = ?")
                .doesNotContain("employee.manager_id")
                .doesNotContain("manager.user_id");
    }
}
