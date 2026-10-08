package com.sanad.platform.hr.api.v2;

import com.sanad.platform.security.authorization.RequireCapability;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G4-T8 RED-first contract for the governed payroll API surface.
 *
 * <p>The test is reflection-based so it compiles before the production
 * controller exists. Tenant identity must come from Authentication rather than
 * a tenant path/header parameter. Mutations must require Idempotency-Key.</p>
 */
class PayrollApiContractTest {

    private static final String CONTROLLER =
            "com.sanad.platform.hr.api.v2.PayrollController";

    @Test
    void payrollControllerMustOwnCanonicalV2BasePath() {
        Class<?> type = loadRequired();
        RequestMapping mapping = type.getAnnotation(RequestMapping.class);

        assertThat(mapping).isNotNull();
        assertThat(Set.of(mapping.value())).containsExactly("/api/v2/hr/payroll");
        assertThat(type.getAnnotation(RestController.class)).isNotNull();
    }

    @Test
    void requiredOperationsMustExistWithExplicitCapabilities() {
        Class<?> type = loadRequired();

        Map<String, String> expectedCapabilities = Map.of(
                "createRun", "HRM.PAYROLL.CALCULATE",
                "listRuns", "HRM.PAYROLL.VIEW",
                "getRun", "HRM.PAYROLL.VIEW",
                "calculate", "HRM.PAYROLL.CALCULATE",
                "recalculate", "HRM.PAYROLL.CALCULATE",
                "listItems", "HRM.PAYROLL.VIEW",
                "getItem", "HRM.PAYROLL.VIEW",
                "review", "HRM.PAYROLL.REVIEW",
                "approve", "HRM.PAYROLL.APPROVE",
                "export", "HRM.PAYROLL.EXPORT");

        Set<String> methods = Arrays.stream(type.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
        assertThat(methods).containsAll(expectedCapabilities.keySet());

        expectedCapabilities.forEach((methodName, capability) -> {
            Method method = Arrays.stream(type.getDeclaredMethods())
                    .filter(m -> m.getName().equals(methodName))
                    .findFirst()
                    .orElseThrow();
            RequireCapability guard = method.getAnnotation(RequireCapability.class);
            assertThat(guard)
                    .as(methodName + " must have an explicit capability guard")
                    .isNotNull();
            assertThat(guard.value()).isEqualTo(capability);
        });
    }

    @Test
    void mutationsMustRequireIdempotencyKeyHeader() {
        Class<?> type = loadRequired();
        for (String methodName : Set.of(
                "createRun", "calculate", "recalculate", "review", "approve", "export")) {
            Method method = Arrays.stream(type.getDeclaredMethods())
                    .filter(m -> m.getName().equals(methodName))
                    .findFirst()
                    .orElseThrow();

            boolean hasIdempotencyHeader = Arrays.stream(method.getParameters())
                    .map(parameter -> parameter.getAnnotation(RequestHeader.class))
                    .filter(annotation -> annotation != null)
                    .anyMatch(annotation -> "Idempotency-Key".equals(annotation.value()));

            assertThat(hasIdempotencyHeader)
                    .as(methodName + " must require Idempotency-Key")
                    .isTrue();
        }
    }

    @Test
    void noStatutoryBankOrPaymentEndpointIsExposed() {
        Class<?> type = loadRequired();
        String surface = Arrays.stream(type.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.joining(" "))
                .toLowerCase();

        assertThat(surface)
                .doesNotContain("wps")
                .doesNotContain("gosi")
                .doesNotContain("tax")
                .doesNotContain("statutory")
                .doesNotContain("bank")
                .doesNotContain("payment");
    }

    private static Class<?> loadRequired() {
        try {
            return Class.forName(CONTROLLER);
        } catch (ClassNotFoundException missing) {
            throw new AssertionError(
                    "G4-T8 requires PayrollController under /api/v2/hr/payroll", missing);
        }
    }
}
