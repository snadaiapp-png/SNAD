package com.sanad.platform.hr.time;

import com.sanad.platform.hr.time.application.HrTimeAttendanceV2Controller;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class HrG2SelfIdentityContractTest {

    @Test
    void selfMutationPayloadsDoNotExposeEmploymentId() {
        assertNoEmploymentComponent(HrTimeAttendanceV2Controller.ClockInRequest.class);
        assertNoEmploymentComponent(HrTimeAttendanceV2Controller.CreateTimesheetRequest.class);
        assertNoEmploymentComponent(HrTimeAttendanceV2Controller.CreateLeaveRequest.class);
    }

    @Test
    void selfReadAndSubmitRoutesDoNotAcceptEmploymentIdRequestParameters() {
        assertNoEmploymentRequestParam("listAttendance");
        assertNoEmploymentRequestParam("listTimesheets");
        assertNoEmploymentRequestParam("listLeaveRequests");
        assertNoEmploymentRequestParam("listLeaveBalances");
        assertNoEmploymentRequestParam("submitTimesheet");
    }

    private static void assertNoEmploymentComponent(Class<?> recordType) {
        assertThat(Arrays.stream(recordType.getRecordComponents())
                .map(RecordComponent::getName))
                .as("%s must derive employment from authenticated principal", recordType.getSimpleName())
                .doesNotContain("employmentId");
    }

    private static void assertNoEmploymentRequestParam(String methodName) {
        Method method = Arrays.stream(HrTimeAttendanceV2Controller.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();

        boolean exposesEmploymentId = Arrays.stream(method.getParameters())
                .map(parameter -> parameter.getAnnotation(RequestParam.class))
                .filter(annotation -> annotation != null)
                .anyMatch(annotation -> "employmentId".equals(annotation.value())
                        || "employmentId".equals(annotation.name()));

        assertThat(exposesEmploymentId)
                .as("%s must not trust caller-selected employmentId", methodName)
                .isFalse();
    }
}
