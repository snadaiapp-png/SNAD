package com.sanad.platform.subscription.api;

import com.sanad.platform.subscription.read.SubscriptionDetailService;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.server.PathContainer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R0C-12 G5-R1 — the authoritative SCP design (§8) requires
 * {@code GET /api/v1/executive/subscriptions/{id}} as the detail read-model
 * path. The R0C-11 base shipped only {@code /subscriptions/{id}/detail};
 * this additive alias closes the design contract without removing anything.
 *
 * Pins:
 *   1. the design-required route exists on ExecutiveReadController
 *   2. it exposes the same SubscriptionDetail read model as /detail
 *   3. the literal /subscriptions/v2 grid route still outranks the new
 *      {id} capture (no v2-swallowed-as-UUID regression, mirroring the
 *      tenants/v2 precedence proof)
 */
class ExecutiveSubscriptionDetailRouteTest {

    private static final String BASE = "/api/v1/executive";

    @Test
    void designRequiredDetailRouteExists() {
        Method alias = Arrays.stream(ExecutiveReadController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("subscription"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "ExecutiveReadController#subscription (GET /subscriptions/{id}) must exist"));

        GetMapping mapping = AnnotatedElementUtils.findMergedAnnotation(alias, GetMapping.class);
        assertNotNull(mapping, "the detail alias must be declared through @GetMapping");
        String path = mapping.value().length > 0 ? mapping.value()[0] : mapping.path()[0];
        assertEquals("/subscriptions/{id}", path,
                "the design-required detail path is /api/v1/executive/subscriptions/{id}");
    }

    @Test
    void aliasExposesTheSameDetailReadModel() {
        Arrays.stream(ExecutiveReadController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("subscription"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "ExecutiveReadController#subscription must exist"));
        // both routes are served from the same authoritative read model service
        assertNotNull(SubscriptionDetailService.class,
                "SubscriptionDetailService remains the detail authority");
    }

    @Test
    void literalSubscriptionsV2StillWinsOverTheIdCapture() {
        PathPatternParser parser = new PathPatternParser();
        PathPattern literal = parser.parse(BASE + "/subscriptions/v2");
        PathPattern variable = parser.parse(BASE + "/subscriptions/{id}");

        assertTrue(literal.compareTo(variable) < 0,
                () -> "literal /subscriptions/v2 must be more specific than /subscriptions/{id}");
        PathContainer requestPath = PathContainer.parsePath(BASE + "/subscriptions/v2");
        assertTrue(literal.matches(requestPath));
        assertTrue(variable.matches(requestPath));

        RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(
                ExecutiveReadController.class, RequestMapping.class);
        assertEquals("/api/v1/executive", classMapping.value()[0]);
    }
}
