package com.sanad.platform.hr.onboarding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G1-T9 RED acceptance contract for the onboarding application service.
 *
 * <p>This test intentionally uses reflection so the RED phase remains a test
 * failure rather than a compilation failure while the T9 application service
 * does not yet exist. No production implementation is added in this commit.
 */
class HrOnboardingServiceIntegrationTest {

    private static final String SERVICE_CLASS =
            "com.sanad.platform.hr.onboarding.application.HrOnboardingService";

    @Test
    @DisplayName("T9 RED: onboarding application service exists as the boundary for derived completion")
    void completionIsOwnedByOnboardingServiceBoundary() {
        Class<?> service = loadServiceClass();

        assertThat(service)
                .as("T9 requires HrOnboardingService; completion must be derived through the service, not manually persisted")
                .isNotNull();
        assertThat(publicMethodNames(service))
                .as("service must expose task completion behavior used to derive plan completion")
                .anyMatch(name -> name.contains("complete"));
    }

    @Test
    @DisplayName("T9 RED: waiver behavior is explicit so capability and reason can be enforced")
    void waiverHasExplicitServiceBehavior() {
        Class<?> service = loadServiceClass();

        assertThat(service)
                .as("T9 requires HrOnboardingService before waiver authorization can be enforced")
                .isNotNull();
        assertThat(publicMethodNames(service))
                .as("service must expose an explicit waive operation; waiver cannot be represented as generic completion")
                .anyMatch(name -> name.contains("waive"));
    }

    @Test
    @DisplayName("T9 RED: template materialization is explicit to preserve snapshot semantics")
    void templateMaterializationHasExplicitServiceBehavior() {
        Class<?> service = loadServiceClass();

        assertThat(service)
                .as("T9 requires HrOnboardingService before template snapshot materialization can be implemented")
                .isNotNull();
        Set<String> methods = publicMethodNames(service);
        assertThat(methods)
                .as("service must expose template materialization/snapshot behavior")
                .anyMatch(name -> name.contains("materializ") || name.contains("snapshot") || name.contains("template"));
    }

    @Test
    @DisplayName("T9 RED: plan cancellation is explicit so task and Y2 cancellation can cascade")
    void planCancellationHasExplicitServiceBehavior() {
        Class<?> service = loadServiceClass();

        assertThat(service)
                .as("T9 requires HrOnboardingService before terminal cancel cascade can be implemented")
                .isNotNull();
        assertThat(publicMethodNames(service))
                .as("service must expose explicit cancellation behavior for onboarding plans/tasks")
                .anyMatch(name -> name.contains("cancel"));
    }

    private static Class<?> loadServiceClass() {
        try {
            return Class.forName(SERVICE_CLASS);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private static Set<String> publicMethodNames(Class<?> type) {
        return Arrays.stream(type.getMethods())
                .map(Method::getName)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }
}
