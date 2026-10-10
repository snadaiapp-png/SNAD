package com.sanad.platform.user.access;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import com.sanad.platform.security.authorization.RequireCapability;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class UserApplicationAccessControllerTest {

    @Test
    void crossTenantReadIsDeniedBeforeProjection() {
        UUID authenticatedTenant = UUID.fromString("11111111-1111-4111-8111-111111111111");
        UUID requestedTenant = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        UUID userId = UUID.fromString("22222222-2222-4222-8222-222222222222");

        var auth = new UsernamePasswordAuthenticationToken("admin", "n/a", java.util.List.of());
        auth.setDetails(Map.of(
                "tenant_id", authenticatedTenant.toString(),
                "user_id", UUID.randomUUID().toString()));

        var moduleUserCreation = mock(ModuleUserCreationService.class);
        var controller = new UserApplicationAccessController(
                mock(UserApplicationAccessProjectionService.class),
                mock(ModuleUserProvisioningService.class),
                moduleUserCreation);

        assertThatThrownBy(() -> controller.list(auth, requestedTenant, userId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Cross-tenant");

        assertThatThrownBy(() -> controller.provisioningContext(auth, requestedTenant, "hr"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Cross-tenant");

        var request = new ModuleUserProvisionRequest(
                new com.sanad.platform.user.dto.CreateUserRequest(
                        "new@example.com", "new.user", "New User",
                        com.sanad.platform.user.domain.UserStatus.ACTIVE),
                "hr",
                List.of("HRM.EMPLOYEE.VIEW"));

        assertThatThrownBy(() -> controller.provisionModuleUser(auth, requestedTenant, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Cross-tenant");
        assertThatThrownBy(() -> controller.provisionModuleUserWithOverrides(auth, requestedTenant, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Cross-tenant");

        verifyNoInteractions(moduleUserCreation);
    }

    @Test
    void moduleContextReadAndProvisioningUsePurposeSpecificCanonicalCapabilities() throws Exception {
        RequireCapability readContext = UserApplicationAccessController.class
                .getMethod("moduleContext", org.springframework.security.core.Authentication.class, String.class)
                .getAnnotation(RequireCapability.class);
        RequireCapability provisionContext = UserApplicationAccessController.class
                .getMethod("provisioningContext", org.springframework.security.core.Authentication.class, UUID.class, String.class)
                .getAnnotation(RequireCapability.class);
        RequireCapability tenantGrant = UserApplicationAccessController.class
                .getMethod("grantModuleCapabilities", org.springframework.security.core.Authentication.class, UUID.class, ModuleCapabilityGrantRequest.class)
                .getAnnotation(RequireCapability.class);
        RequireCapability overrideGrant = UserApplicationAccessController.class
                .getMethod("grantModuleCapabilityOverrides", org.springframework.security.core.Authentication.class, UUID.class, ModuleCapabilityGrantRequest.class)
                .getAnnotation(RequireCapability.class);
        RequireCapability atomicTenantProvision = UserApplicationAccessController.class
                .getMethod("provisionModuleUser", org.springframework.security.core.Authentication.class, UUID.class, ModuleUserProvisionRequest.class)
                .getAnnotation(RequireCapability.class);
        RequireCapability atomicOverrideProvision = UserApplicationAccessController.class
                .getMethod("provisionModuleUserWithOverrides", org.springframework.security.core.Authentication.class, UUID.class, ModuleUserProvisionRequest.class)
                .getAnnotation(RequireCapability.class);

        assertThat(readContext.value()).isEqualTo("CAPABILITY.READ");
        assertThat(provisionContext.value()).isEqualTo("USER.CREATE");
        assertThat(tenantGrant.value()).isEqualTo("USER.GRANT_ROLE");
        assertThat(overrideGrant.value()).isEqualTo("AUTHORIZATION.OVERRIDE.MANAGE");
        assertThat(atomicTenantProvision.value()).isEqualTo("USER.GRANT_ROLE");
        assertThat(atomicOverrideProvision.value()).isEqualTo("AUTHORIZATION.OVERRIDE.MANAGE");
    }
}
