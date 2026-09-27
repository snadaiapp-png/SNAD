package com.sanad.platform.platformiam;

import com.sanad.platform.platformiam.api.PlatformUserController;
import com.sanad.platform.platformiam.dto.CreatePlatformUserRequest;
import com.sanad.platform.platformiam.service.PlatformRoleService;
import com.sanad.platform.platformiam.service.PlatformUserService;
import com.sanad.platform.security.authorization.PlatformMembershipGuard;
import com.sanad.platform.security.authorization.RequireCapability;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PlatformUserControllerAuthorizationTest {

    @Test
    void routeAndCapabilityContractIsExact() throws Exception {
        RequestMapping mapping = PlatformUserController.class.getAnnotation(RequestMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/api/v1/executive/users");

        assertCapability("list", "PLATFORM.USER.READ", Authentication.class);
        assertCapability("get", "PLATFORM.USER.READ", Authentication.class, UUID.class);
        assertCapability("create", "PLATFORM.USER.CREATE", Authentication.class, CreatePlatformUserRequest.class);
        assertCapability("update", "PLATFORM.USER.UPDATE", Authentication.class, UUID.class,
                com.sanad.platform.platformiam.dto.UpdatePlatformUserRequest.class);
        assertCapability("activate", "PLATFORM.USER.UPDATE", Authentication.class, UUID.class,
                com.sanad.platform.platformiam.dto.PlatformLifecycleRequest.class);
        assertCapability("suspend", "PLATFORM.USER.SUSPEND", Authentication.class, UUID.class,
                com.sanad.platform.platformiam.dto.PlatformLifecycleRequest.class);
        assertCapability("lock", "PLATFORM.SECURITY.MANAGE", Authentication.class, UUID.class,
                com.sanad.platform.platformiam.dto.PlatformLifecycleRequest.class);
        assertCapability("disable", "PLATFORM.USER.DISABLE", Authentication.class, UUID.class,
                com.sanad.platform.platformiam.dto.PlatformLifecycleRequest.class);
        assertCapability("roles", "PLATFORM.ROLE.READ", Authentication.class, UUID.class);
        assertCapability("replaceRoles", "PLATFORM.ROLE.ASSIGN", Authentication.class, UUID.class,
                com.sanad.platform.platformiam.dto.ReplacePlatformRolesRequest.class);
        assertCapability("permissions", "PLATFORM.PERMISSION.READ", Authentication.class, UUID.class);
        assertCapability("sessions", "PLATFORM.SESSION.READ", Authentication.class, UUID.class);
        assertCapability("revokeSessions", "PLATFORM.SESSION.REVOKE", Authentication.class, UUID.class,
                com.sanad.platform.platformiam.dto.PlatformLifecycleRequest.class);
    }

    @Test
    void hardPlatformMembershipBoundaryRunsBeforeUserService() {
        PlatformUserService users = mock(PlatformUserService.class);
        PlatformRoleService roles = mock(PlatformRoleService.class);
        PlatformMembershipGuard guard = mock(PlatformMembershipGuard.class);
        PlatformUserController controller = new PlatformUserController(users, roles, guard);
        Authentication actor = actor();

        controller.list(actor);

        verify(guard).requireActive(actor);
        verify(users).list(actor);
    }

    @Test
    void controllerAndRequestDtosExposeNoCallerSuppliedControlTenantAuthority() throws Exception {
        String controllerSource = Files.readString(Path.of(
                "src/main/java/com/sanad/platform/platformiam/api/PlatformUserController.java"));
        assertThat(controllerSource).doesNotContain("controlTenantId");
        assertThat(CreatePlatformUserRequest.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("controlTenantId", "tenantId");
        assertThat(com.sanad.platform.platformiam.dto.UpdatePlatformUserRequest.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("controlTenantId", "tenantId");
    }

    private static void assertCapability(String methodName, String expected, Class<?>... parameterTypes)
            throws Exception {
        Method method = PlatformUserController.class.getDeclaredMethod(methodName, parameterTypes);
        RequireCapability capability = method.getAnnotation(RequireCapability.class);
        assertThat(capability).as(methodName + " capability").isNotNull();
        assertThat(capability.value()).isEqualTo(expected);
    }

    private static Authentication actor() {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken("owner", "n/a", List.of());
        authentication.setDetails(Map.of(
                "tenant_id", "10000000-0000-0000-0000-000000000001",
                "user_id", "20000000-0000-0000-0000-000000000001"));
        return authentication;
    }
}
