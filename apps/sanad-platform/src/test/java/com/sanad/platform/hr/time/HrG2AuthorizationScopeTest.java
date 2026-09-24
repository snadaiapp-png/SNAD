package com.sanad.platform.hr.time;

import com.sanad.platform.hr.time.application.HrG2ScopedReadController;
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
 * <p>SELF routes remain on the canonical G2 controller. TEAM/HR reads use a
 * dedicated scoped controller so broader capabilities never reuse SELF-only
 * list contracts. Relationship filtering is enforced by the scoped read
 * service and PostgreSQL RLS remains authoritative for tenant isolation.</p>
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
