package com.sanad.platform.subscription.api;

import com.sanad.platform.executive.api.PlatformOperationsQueryController;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Regression coverage for the executive audit route collision that prevented
 * the Spring MVC application context from starting in CI.
 */
class ExecutiveAuditRouteCompatibilityTest {

    @Test
    void preservesLegacyAuditAndKeepsScpAuditAdditive() {
        String legacyRoute = getRoute(PlatformOperationsQueryController.class, "audit");
        String scpRoute = getRoute(GovernanceController.class, "audit");

        assertEquals("/api/v1/executive/audit", legacyRoute);
        assertEquals("/api/v1/executive/audit/v2", scpRoute);
        assertNotEquals(legacyRoute, scpRoute);
    }

    private static String getRoute(Class<?> controllerClass, String methodName) {
        RequestMapping classMapping = controllerClass.getAnnotation(RequestMapping.class);
        assertNotNull(classMapping, () -> controllerClass.getSimpleName() + " must declare @RequestMapping");

        Method method = Arrays.stream(controllerClass.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        controllerClass.getSimpleName() + " must declare method " + methodName));

        GetMapping methodMapping = method.getAnnotation(GetMapping.class);
        assertNotNull(methodMapping, () -> controllerClass.getSimpleName() + "#" + methodName
                + " must declare @GetMapping");

        return firstPath(classMapping.value(), classMapping.path())
                + firstPath(methodMapping.value(), methodMapping.path());
    }

    private static String firstPath(String[] value, String[] path) {
        if (value.length > 0) {
            return value[0];
        }
        if (path.length > 0) {
            return path[0];
        }
        return "";
    }
}
