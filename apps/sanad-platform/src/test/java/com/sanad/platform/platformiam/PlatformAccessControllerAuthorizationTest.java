package com.sanad.platform.platformiam;

import com.sanad.platform.access.AccessConflictException;
import com.sanad.platform.access.AccessResourceNotFoundException;
import com.sanad.platform.platformiam.api.PlatformAccessController;
import com.sanad.platform.platformiam.api.PlatformIamApiExceptionHandler;
import com.sanad.platform.platformiam.dto.CreatePlatformRoleRequest;
import com.sanad.platform.platformiam.dto.ReplaceRoleCapabilitiesRequest;
import com.sanad.platform.platformiam.dto.UpdatePlatformRoleRequest;
import com.sanad.platform.platformiam.exception.LastPlatformOwnerException;
import com.sanad.platform.platformiam.service.PlatformRoleService;
import com.sanad.platform.security.authorization.PlatformMembershipGuard;
import com.sanad.platform.security.authorization.RequireCapability;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PlatformAccessControllerAuthorizationTest {

    @Test
    void roleAndCapabilityEndpointsUseExactPlatformCapabilities() throws Exception {
        RequestMapping mapping = PlatformAccessController.class.getAnnotation(RequestMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/api/v1/executive");

        assertCapability("listRoles", "PLATFORM.ROLE.READ", Authentication.class);
        assertCapability("getRole", "PLATFORM.ROLE.READ", Authentication.class, java.util.UUID.class);
        assertCapability("createRole", "PLATFORM.ROLE.CREATE", Authentication.class, CreatePlatformRoleRequest.class);
        assertCapability("updateRole", "PLATFORM.ROLE.UPDATE", Authentication.class, java.util.UUID.class,
                UpdatePlatformRoleRequest.class);
        assertCapability("listCapabilities", "PLATFORM.PERMISSION.READ", Authentication.class);
        assertCapability("listRoleCapabilities", "PLATFORM.PERMISSION.READ", Authentication.class, java.util.UUID.class);
        assertCapability("replaceRoleCapabilities", "PLATFORM.PERMISSION.MANAGE", Authentication.class,
                java.util.UUID.class, ReplaceRoleCapabilitiesRequest.class);
    }

    @Test
    void hardPlatformMembershipBoundaryRunsBeforeRoleService() {
        PlatformRoleService roles = mock(PlatformRoleService.class);
        PlatformMembershipGuard guard = mock(PlatformMembershipGuard.class);
        PlatformAccessController controller = new PlatformAccessController(roles, guard);
        Authentication actor = actor();

        controller.listRoles(actor);

        verify(guard).requireActive(actor);
        verify(roles).list(actor);
    }

    @Test
    void accessControllerAcceptsNoCallerSuppliedControlTenant() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/sanad/platform/platformiam/api/PlatformAccessController.java"));
        assertThat(source).doesNotContain("controlTenantId");
        assertThat(CreatePlatformRoleRequest.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("tenantId", "controlTenantId");
        assertThat(UpdatePlatformRoleRequest.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("tenantId", "controlTenantId");
    }

    @Test
    void platformIamExceptionsMapToStableHttpStatuses() {
        PlatformIamApiExceptionHandler handler = new PlatformIamApiExceptionHandler();

        assertThat(handler.notFound(new AccessResourceNotFoundException("missing")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(handler.conflict(new AccessConflictException("duplicate")).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(handler.lastOwner(new LastPlatformOwnerException()).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(handler.forbidden(new AccessDeniedException("denied")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    private static void assertCapability(String methodName, String expected, Class<?>... parameterTypes)
            throws Exception {
        Method method = PlatformAccessController.class.getDeclaredMethod(methodName, parameterTypes);
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
