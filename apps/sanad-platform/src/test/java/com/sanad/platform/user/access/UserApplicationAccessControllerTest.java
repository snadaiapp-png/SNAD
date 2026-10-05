package com.sanad.platform.user.access;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

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

        var controller = new UserApplicationAccessController(
                mock(UserApplicationAccessProjectionService.class));

        assertThatThrownBy(() -> controller.list(auth, requestedTenant, userId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Cross-tenant");
    }
}
