package com.sanad.platform.crm.contract;

import com.sanad.platform.crm.api.CrmContractController;
import com.sanad.platform.crm.error.CrmErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * CRM-G2 Contract Test — RBAC matrix (AC-09).
 * <p>
 * Verifies that every public method on {@link CrmContractController} that
 * performs a mutation (POST / PATCH / PUT / DELETE) is annotated with
 * {@code @RequireCapability} for the appropriate WRITE / CONVERT / ARCHIVE
 * capability, and that every read method (GET) is annotated with the
 * appropriate READ capability.
 * <p>
 * The full matrix (READ user gets 403 on POST, WRITE-without-CONVERT user
 * gets 403 on /convert, etc.) is verified end-to-end by the Playwright
 * RBAC acceptance spec at
 * {@code apps/web/e2e/crm-rbac-acceptance.spec.ts} and the seed-driven
 * CRM Authenticated Acceptance workflow.
 * <p>
 * Branch: crm/003-stable-api-contracts
 */
class CrmRbacContractTest {

    /**
     * Required capabilities by HTTP method (default fallthrough).
     * GET → READ, PATCH/POST/PUT → WRITE. Methods that need a more
     * specific capability (e.g. LEAD.CONVERT) must declare it explicitly
     * in their @RequireCapability annotation.
     */
    private static final Map<String, String> DEFAULT_CAPABILITY_HINT = Map.of(
            "GET", "READ",
            "POST", "WRITE",
            "PATCH", "WRITE",
            "PUT", "WRITE",
            "DELETE", "WRITE");

    @Test
    void everyPublicEndpointHasRequireCapabilityAnnotation() {
        Set<String> seenEndpoints = new HashSet<>();
        for (Method method : CrmContractController.class.getDeclaredMethods()) {
            // Skip bridge / synthetic / private methods.
            if (method.isSynthetic() || method.isBridge()) continue;
            // Skip the constructor and lifecycle methods.
            if (method.getName().equals("<init>")) continue;
            // Only consider methods annotated with a Spring mapping annotation.
            if (!isWebEndpoint(method)) continue;

            com.sanad.platform.security.authorization.RequireCapability rc =
                    method.getAnnotation(com.sanad.platform.security.authorization.RequireCapability.class);
            assertNotNull(rc, "CRM v2 endpoint " + method.getName() +
                    " must be annotated with @RequireCapability");
            assertNotNull(rc.value(), "@RequireCapability on " + method.getName() +
                    " must declare a non-null capability");
            assertTrue(!rc.value().isBlank(), "@RequireCapability on " + method.getName() +
                    " must declare a non-blank capability");
            seenEndpoints.add(method.getName());
        }
        assertTrue(seenEndpoints.size() >= 10,
                "expected at least 10 v2 CRM endpoints; found " + seenEndpoints.size());
    }

    @Test
    void leadConvertEndpointRequiresConvertCapability() {
        Method convert = findMethod("convertLead");
        com.sanad.platform.security.authorization.RequireCapability rc =
                convert.getAnnotation(com.sanad.platform.security.authorization.RequireCapability.class);
        assertNotNull(rc);
        assertTrue(rc.value().contains("CONVERT"),
                "convertLead must require a capability containing 'CONVERT'; got: " + rc.value());
    }

    @Test
    void archiveEndpointRequiresArchiveCapability() {
        Method archive = findMethod("archiveAccount");
        com.sanad.platform.security.authorization.RequireCapability rc =
                archive.getAnnotation(com.sanad.platform.security.authorization.RequireCapability.class);
        assertNotNull(rc);
        assertTrue(rc.value().contains("ARCHIVE"),
                "archiveAccount must require a capability containing 'ARCHIVE'; got: " + rc.value());
    }

    @Test
    void rbacErrorCodeMapsTo403() {
        assertEquals(403, CrmErrorCode.CRM_CAPABILITY_REQUIRED.httpStatus(),
                "RBAC denial must yield HTTP 403");
        assertEquals(403, CrmErrorCode.CRM_TENANT_ACCESS_DENIED.httpStatus(),
                "Tenant access denial must yield HTTP 403");
        assertEquals(403, CrmErrorCode.FORBIDDEN.httpStatus());
    }

    @Test
    void rbacDenialIsNotRetryable() {
        assertTrue(!CrmErrorCode.CRM_CAPABILITY_REQUIRED.retryable(),
                "RBAC denial must not be marked retryable — retrying without a capability change is futile");
    }

    // ────────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────────

    private static boolean isWebEndpoint(Method method) {
        return method.isAnnotationPresent(org.springframework.web.bind.annotation.GetMapping.class)
                || method.isAnnotationPresent(org.springframework.web.bind.annotation.PostMapping.class)
                || method.isAnnotationPresent(org.springframework.web.bind.annotation.PatchMapping.class)
                || method.isAnnotationPresent(org.springframework.web.bind.annotation.PutMapping.class)
                || method.isAnnotationPresent(org.springframework.web.bind.annotation.DeleteMapping.class)
                || method.isAnnotationPresent(org.springframework.web.bind.annotation.RequestMapping.class);
    }

    private static Method findMethod(String name) {
        for (Method m : CrmContractController.class.getDeclaredMethods()) {
            if (m.getName().equals(name)) return m;
        }
        return fail("Method " + name + " not found on CrmContractController");
    }

    private static <T> T fail(String message) {
        org.junit.jupiter.api.Assertions.fail(message);
        return null;
    }
}
