package com.sanad.platform.hr.time;

import com.sanad.platform.hr.time.application.HrTimeAttendanceV2Controller;
import com.sanad.platform.security.authorization.RequireCapability;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract-level proof that G2 read APIs expose explicit SELF/TEAM/HR scopes.
 *
 * <p>This deliberately verifies route + capability metadata without booting the
 * application. Relationship filtering is covered by PostgreSQL integration
 * tests after the scoped service methods are implemented.</p>
 */
class HrG2AuthorizationScopeTest {

    @Test
    void teamAndHrReadRoutesUseDedicatedCapabilities() {
        assertScopedGet("/time/attendance/team", TimeAttendanceCapabilities.ATTENDANCE_TEAM_VIEW);
        assertScopedGet("/time/attendance/admin", TimeAttendanceCapabilities.ATTENDANCE_ADMIN);
        assertScopedGet("/time/timesheets/team", TimeAttendanceCapabilities.TIMESHEET_TEAM_APPROVE);
        assertScopedGet("/leave/requests/team", TimeAttendanceCapabilities.LEAVE_TEAM_APPROVE);
        assertScopedGet("/leave/requests/hr", TimeAttendanceCapabilities.LEAVE_HR_APPROVE);
        assertScopedGet("/leave/types/admin", TimeAttendanceCapabilities.LEAVE_POLICY_ADMIN);
        assertScopedGet("/time/attendance/monthly-report/team", TimeAttendanceCapabilities.ATTENDANCE_TEAM_VIEW);
        assertScopedGet("/time/attendance/monthly-report/admin", TimeAttendanceCapabilities.ATTENDANCE_ADMIN);
    }

    @Test
    void selfReadRoutesRemainNarrowlyProtected() {
        assertScopedGet("/time/attendance", TimeAttendanceCapabilities.ATTENDANCE_SELF_VIEW);
        assertScopedGet("/time/timesheets", TimeAttendanceCapabilities.TIMESHEET_SELF_VIEW);
        assertScopedGet("/leave/requests", TimeAttendanceCapabilities.LEAVE_SELF_VIEW);
        assertScopedGet("/leave/types", TimeAttendanceCapabilities.LEAVE_SELF_VIEW);
        assertScopedGet("/leave/balances", TimeAttendanceCapabilities.LEAVE_SELF_VIEW);
    }

    private static void assertScopedGet(String path, String expectedCapability) {
        Optional<Method> route = Arrays.stream(HrTimeAttendanceV2Controller.class.getDeclaredMethods())
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
