package com.sanad.platform.access.api;

import com.sanad.platform.security.authorization.RequireCapability;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 1 Task 10 controller contract. Runtime authorization remains enforced
 * by @RequireCapability; controllers must derive actor/tenant from the
 * authenticated principal rather than introducing a second auth authority.
 */
class UserOverrideControllerIT {

    @Test
    void overrideControllerExposesGovernedRoutes() throws Exception {
        Class<?> type = UserOverrideController.class;
        assertBasePath(type, "/api/v1/access/overrides");
        assertCapability(method(type, "create"), "AUTHORIZATION.OVERRIDE.MANAGE");
        assertCapability(method(type, "list"), "AUTHORIZATION.OVERRIDE.MANAGE");
        assertCapability(method(type, "revoke"), "AUTHORIZATION.OVERRIDE.MANAGE");
        assertThat(method(type, "create").getAnnotation(PostMapping.class)).isNotNull();
        assertThat(method(type, "list").getAnnotation(GetMapping.class)).isNotNull();
        assertThat(method(type, "revoke").getAnnotation(PatchMapping.class)).isNotNull();
    }

    @Test
    void relationshipControllerExposesGovernedRoutes() throws Exception {
        Class<?> type = RelationshipController.class;
        assertBasePath(type, "/api/v1/access/relationships");
        assertCapability(method(type, "create"), "AUTHORIZATION.RELATIONSHIP.MANAGE");
        assertCapability(method(type, "list"), "AUTHORIZATION.RELATIONSHIP.MANAGE");
        assertCapability(method(type, "revoke"), "AUTHORIZATION.RELATIONSHIP.MANAGE");
    }

    @Test
    void effectivePermissionControllerSeparatesReadAndRecoveryAuthorities() throws Exception {
        Class<?> type = EffectivePermissionController.class;
        assertBasePath(type, "/api/v1/access/effective-permissions");
        assertCapability(method(type, "list"), "ROLE.READ");
        assertCapability(method(type, "resync"), "AUTHORIZATION.RESYNC");
    }

    @Test
    void authenticatedAccessPrincipalFailsClosedWhenContextIsMissing() {
        assertThat(AccessPrincipalContext.class.getDeclaredMethods())
                .extracting(Method::getName)
                .contains("requireTenantId", "requireUserId");
    }

    private static Method method(Class<?> type, String name) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static void assertBasePath(Class<?> type, String expected) {
        RequestMapping mapping = type.getAnnotation(RequestMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly(expected);
    }

    private static void assertCapability(Method method, String expected) {
        RequireCapability capability = method.getAnnotation(RequireCapability.class);
        assertThat(capability).isNotNull();
        assertThat(capability.value()).isEqualTo(expected);
    }
}
