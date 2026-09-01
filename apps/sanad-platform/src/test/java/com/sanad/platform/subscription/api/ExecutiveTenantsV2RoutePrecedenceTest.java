package com.sanad.platform.subscription.api;

import com.sanad.platform.executive.api.PlatformOperationsQueryController;
import com.sanad.platform.subscription.read.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for the production HTTP 400 on
 * {@code GET /api/v1/executive/tenants/v2?status=PENDING&...}.
 *
 * Root cause on the pre-SCP backend image: no literal {@code /tenants/v2}
 * route existed, so the request was captured by
 * {@code PlatformOperationsQueryController#tenant} as
 * {@code tenantId = "v2"} and {@code UUID.fromString("v2")} failed → HTTP 400.
 *
 * This test pins the corrected routing contract:
 *   1. the literal static route {@code /tenants/v2} exists (ExecutiveReadController)
 *   2. the parameterised {@code /tenants/{tenantId}} route still exists (additive)
 *   3. Spring pattern specificity provably prefers the literal route, so the
 *      directory listing can never be swallowed as a tenant UUID lookup
 *   4. the static route returns the PageResponse contract
 */
class ExecutiveTenantsV2RoutePrecedenceTest {

    private static final String BASE = "/api/v1/executive";

    @Test
    void staticTenantsV2RouteExistsOnTheReadController() throws Exception {
        assertEquals("/api/v1/executive/tenants/v2",
                methodMapping(ExecutiveReadController.class, "tenants"));
    }

    @Test
    void parameterisedTenantLookupRouteRemainsAdditive() throws Exception {
        assertEquals("/api/v1/executive/tenants/{tenantId}",
                methodMapping(PlatformOperationsQueryController.class, "tenant"));
        assertNotEquals(methodMapping(ExecutiveReadController.class, "tenants"),
                methodMapping(PlatformOperationsQueryController.class, "tenant"));
    }

    @Test
    void springSpecificityPrefersTheLiteralRouteOverTheUuidCapture() {
        PathPatternParser parser = new PathPatternParser();
        PathPattern literal = parser.parse(BASE + "/tenants/v2");
        PathPattern variable = parser.parse(BASE + "/tenants/{tenantId}");

        // PathPattern's natural ordering is Spring's specificity ordering —
        // the literal route must sort BEFORE the capture-all-variable route
        // when both match GET /api/v1/executive/tenants/v2.
        assertTrue(literal.compareTo(variable) < 0,
                () -> "literal /tenants/v2 must be more specific than /tenants/{tenantId}");
        PathContainer requestPath = PathContainer.parsePath(BASE + "/tenants/v2");
        assertTrue(literal.matches(requestPath));
        assertTrue(variable.matches(requestPath));
    }

    @Test
    void staticRouteReturnsThePageResponseContract() throws Exception {
        Method tenants = Arrays.stream(ExecutiveReadController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("tenants"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("ExecutiveReadController#tenants must exist"));

        assertTrue(tenants.getAnnotation(GetMapping.class) != null,
                "the static route must be declared through @GetMapping");
        RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(
                ExecutiveReadController.class, RequestMapping.class);
        assertEquals("/api/v1/executive", classMapping.value()[0]);

        java.lang.reflect.ParameterizedType returnType =
                (java.lang.reflect.ParameterizedType) tenants.getGenericReturnType();
        String bodyType = returnType.getActualTypeArguments()[0].getTypeName();
        assertTrue(bodyType.startsWith(PageResponse.class.getName()),
                () -> "the /tenants/v2 route must return PageResponse<TenantRow>, found: " + bodyType);
    }

    private static String methodMapping(Class<?> controller, String methodName) {
        RequestMapping classMapping =
                AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
        assertNotNull(classMapping, () -> controller.getSimpleName() + " must declare @RequestMapping");

        Method method = Arrays.stream(controller.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        controller.getSimpleName() + " must declare method " + methodName));

        GetMapping methodMapping = AnnotatedElementUtils.findMergedAnnotation(method, GetMapping.class);
        assertNotNull(methodMapping, () -> controller.getSimpleName() + "#" + methodName
                + " must declare @GetMapping");

        String[] paths = methodMapping.value().length > 0 ? methodMapping.value() : methodMapping.path();
        return classMapping.value()[0] + (paths.length > 0 ? paths[0] : "");
    }
}
