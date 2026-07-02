package com.sanad.platform.controlplane;

import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ControlPlaneAccessGuardTest {

    @Test
    void acceptsOnlyConfiguredTenant() {
        UUID controlTenant = UUID.fromString("10000000-0000-0000-0000-000000000001");
        ControlPlaneAccessGuard guard = new ControlPlaneAccessGuard(controlTenant.toString());

        UsernamePasswordAuthenticationToken accepted = principal(controlTenant);
        UsernamePasswordAuthenticationToken denied = principal(
                UUID.fromString("20000000-0000-0000-0000-000000000001"));

        assertDoesNotThrow(() -> guard.require(accepted));
        assertThrows(AccessDeniedException.class, () -> guard.require(denied));
    }

    @Test
    void failsClosedWithoutConfiguration() {
        ControlPlaneAccessGuard guard = new ControlPlaneAccessGuard("");
        assertThrows(AccessDeniedException.class, () -> guard.require(principal(
                UUID.fromString("10000000-0000-0000-0000-000000000001"))));
    }

    private static UsernamePasswordAuthenticationToken principal(UUID tenantId) {
        UsernamePasswordAuthenticationToken principal =
                UsernamePasswordAuthenticationToken.authenticated("operator", "n/a", List.of());
        principal.setDetails(Map.of(
                "tenant_id", tenantId.toString(),
                "user_id", UUID.fromString("30000000-0000-0000-0000-000000000001").toString()
        ));
        return principal;
    }
}
