package com.sanad.platform.hr.time;

import com.sanad.platform.hr.time.application.HrEmploymentScopeResolver;
import com.sanad.platform.hr.time.application.HrG2ScopedReadController;
import com.sanad.platform.hr.time.application.HrLeaveService;
import com.sanad.platform.hr.time.application.HrScheduleService;
import com.sanad.platform.hr.time.application.HrTimeAttendanceService;
import com.sanad.platform.hr.time.application.HrTimeAttendanceV2Controller;
import com.sanad.platform.hr.time.application.HrTimesheetService;
import com.sanad.platform.security.authorization.RequireCapability;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Contract-level proof that G2 APIs expose explicit SELF/TEAM/HR scopes and
 * TEAM mutations enforce the authenticated manager's reporting relationship.
 */
class HrG2AuthorizationScopeTest {

    @Test
    void teamAndHrReadRoutesUseDedicatedCapabilities() {
        assertScopedGet(HrG2ScopedReadController.class,
                "/time/attendance/team", TimeAttendanceCapabilities.ATTENDANCE_TEAM_VIEW);
        assertScopedGet(HrG2ScopedReadController.class,
                "/time/attendance/admin", TimeAttendanceCapabilities.ATTENDANCE_ADMIN);
        assertScopedGet(HrG2ScopedReadController.class,
                "/time/timesheets/team", TimeAttendanceCapabilities.TIMESHEET_TEAM_APPROVE);
        assertScopedGet(HrG2ScopedReadController.class,
                "/leave/requests/team", TimeAttendanceCapabilities.LEAVE_TEAM_APPROVE);
        assertScopedGet(HrG2ScopedReadController.class,
                "/leave/requests/hr", TimeAttendanceCapabilities.LEAVE_HR_APPROVE);
        assertScopedGet(HrG2ScopedReadController.class,
                "/leave/types/admin", TimeAttendanceCapabilities.LEAVE_POLICY_ADMIN);
        assertScopedGet(HrG2ScopedReadController.class,
                "/time/attendance/monthly-report/team", TimeAttendanceCapabilities.ATTENDANCE_TEAM_VIEW);
        assertScopedGet(HrG2ScopedReadController.class,
                "/time/attendance/monthly-report/admin", TimeAttendanceCapabilities.ATTENDANCE_ADMIN);
        assertScopedGet(HrTimeAttendanceV2Controller.class,
                "/time/schedules", TimeAttendanceCapabilities.ATTENDANCE_ADMIN);
    }

    @Test
    void selfReadRoutesRemainNarrowlyProtected() {
        assertScopedGet(HrTimeAttendanceV2Controller.class,
                "/time/attendance", TimeAttendanceCapabilities.ATTENDANCE_SELF_VIEW);
        assertScopedGet(HrTimeAttendanceV2Controller.class,
                "/time/timesheets", TimeAttendanceCapabilities.TIMESHEET_SELF_VIEW);
        assertScopedGet(HrTimeAttendanceV2Controller.class,
                "/leave/requests", TimeAttendanceCapabilities.LEAVE_SELF_VIEW);
        assertScopedGet(HrTimeAttendanceV2Controller.class,
                "/leave/types", TimeAttendanceCapabilities.LEAVE_SELF_VIEW);
        assertScopedGet(HrTimeAttendanceV2Controller.class,
                "/leave/balances", TimeAttendanceCapabilities.LEAVE_SELF_VIEW);
    }

    @Test
    void teamTimesheetApprovalResolvesTargetAndEnforcesManagerRelationship() {
        HrTimeAttendanceService timeService = mock(HrTimeAttendanceService.class);
        HrLeaveService leaveService = mock(HrLeaveService.class);
        HrScheduleService scheduleService = mock(HrScheduleService.class);
        HrTimesheetService timesheetService = mock(HrTimesheetService.class);
        HrEmploymentScopeResolver employmentScopeResolver = mock(HrEmploymentScopeResolver.class);
        HrTimeAttendanceV2Controller controller = new HrTimeAttendanceV2Controller(
                timeService, leaveService, scheduleService, timesheetService, employmentScopeResolver);

        UUID tenantId = UUID.randomUUID();
        UUID managerUserId = UUID.randomUUID();
        UUID timesheetId = UUID.randomUUID();
        UUID employmentId = UUID.randomUUID();
        Authentication authentication = authentication(tenantId, managerUserId);

        when(timesheetService.requireTimesheetEmployment(tenantId, timesheetId)).thenReturn(employmentId);

        controller.approveTimesheet(
                authentication,
                timesheetId,
                new HrTimeAttendanceV2Controller.ApproveRejectRequest("approved", null));

        var order = inOrder(timesheetService, employmentScopeResolver);
        order.verify(timesheetService).requireTimesheetEmployment(tenantId, timesheetId);
        order.verify(employmentScopeResolver).requireManagedEmployment(tenantId, managerUserId, employmentId);
        order.verify(timesheetService).approve(tenantId, timesheetId, managerUserId, "approved");
    }

    @Test
    void teamTimesheetRejectionResolvesTargetAndEnforcesManagerRelationship() {
        HrTimeAttendanceService timeService = mock(HrTimeAttendanceService.class);
        HrLeaveService leaveService = mock(HrLeaveService.class);
        HrScheduleService scheduleService = mock(HrScheduleService.class);
        HrTimesheetService timesheetService = mock(HrTimesheetService.class);
        HrEmploymentScopeResolver employmentScopeResolver = mock(HrEmploymentScopeResolver.class);
        HrTimeAttendanceV2Controller controller = new HrTimeAttendanceV2Controller(
                timeService, leaveService, scheduleService, timesheetService, employmentScopeResolver);

        UUID tenantId = UUID.randomUUID();
        UUID managerUserId = UUID.randomUUID();
        UUID timesheetId = UUID.randomUUID();
        UUID employmentId = UUID.randomUUID();
        Authentication authentication = authentication(tenantId, managerUserId);

        when(timesheetService.requireTimesheetEmployment(tenantId, timesheetId)).thenReturn(employmentId);

        controller.rejectTimesheet(
                authentication,
                timesheetId,
                new HrTimeAttendanceV2Controller.ApproveRejectRequest(null, "rejected"));

        var order = inOrder(timesheetService, employmentScopeResolver);
        order.verify(timesheetService).requireTimesheetEmployment(tenantId, timesheetId);
        order.verify(employmentScopeResolver).requireManagedEmployment(tenantId, managerUserId, employmentId);
        order.verify(timesheetService).reject(tenantId, timesheetId, managerUserId, "rejected");
    }

    private static Authentication authentication(UUID tenantId, UUID userId) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getDetails()).thenReturn(Map.of(
                "tenant_id", tenantId,
                "user_id", userId));
        return authentication;
    }

    private static void assertScopedGet(
            Class<?> controllerType,
            String path,
            String expectedCapability) {
        Optional<Method> route = Arrays.stream(controllerType.getDeclaredMethods())
                .filter(method -> {
                    GetMapping mapping = method.getAnnotation(GetMapping.class);
                    return mapping != null && Arrays.asList(mapping.value()).contains(path);
                })
                .findFirst();

        assertThat(route)
                .as("GET %s must exist as an explicit scoped G2 route", path)
                .isPresent();

        RequireCapability requirement = route.orElseThrow().getAnnotation(RequireCapability.class);
        assertThat(requirement)
                .as("GET %s must fail closed behind @RequireCapability", path)
                .isNotNull();
        assertThat(requirement.value())
                .as("GET %s must require the exact canonical capability", path)
                .isEqualTo(expectedCapability);
    }
}
