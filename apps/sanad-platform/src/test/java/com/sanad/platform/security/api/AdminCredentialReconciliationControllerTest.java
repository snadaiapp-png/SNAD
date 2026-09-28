package com.sanad.platform.security.api;

import com.sanad.platform.security.dto.AdminReconcileCredentialRequest;
import com.sanad.platform.security.service.AdminCredentialReconciliationService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.Authentication;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminCredentialReconciliationControllerTest {

    @Test
    void featureIsFailClosedByDefault() {
        AdminCredentialReconciliationService service = mock(AdminCredentialReconciliationService.class);
        AdminCredentialReconciliationController controller =
                new AdminCredentialReconciliationController(service, new MockEnvironment());
        Authentication authentication = authentication(UUID.randomUUID(), UUID.randomUUID());

        var response = controller.reconcileCredential(
                authentication,
                UUID.randomUUID(),
                new AdminReconcileCredentialRequest("replacement-value"));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        verify(service, never()).reconcileCredential(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void enabledFeatureUsesAuthenticatedGovernedTenantAndActor() {
        UUID tenantId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        AdminCredentialReconciliationService service = mock(AdminCredentialReconciliationService.class);
        MockEnvironment environment = enabledEnvironment(tenantId);
        AdminCredentialReconciliationController controller =
                new AdminCredentialReconciliationController(service, environment);

        var response = controller.reconcileCredential(
                authentication(tenantId, actorUserId),
                targetUserId,
                new AdminReconcileCredentialRequest("replacement-value"));

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        verify(service).reconcileCredential(
                tenantId,
                targetUserId,
                "replacement-value",
                actorUserId);
    }

    @Test
    void enabledFeatureRejectsAnotherTenant() {
        UUID governedTenantId = UUID.randomUUID();
        UUID anotherTenantId = UUID.randomUUID();
        AdminCredentialReconciliationService service = mock(AdminCredentialReconciliationService.class);
        AdminCredentialReconciliationController controller =
                new AdminCredentialReconciliationController(service, enabledEnvironment(governedTenantId));

        var response = controller.reconcileCredential(
                authentication(anotherTenantId, UUID.randomUUID()),
                UUID.randomUUID(),
                new AdminReconcileCredentialRequest("replacement-value"));

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(service, never()).reconcileCredential(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private MockEnvironment enabledEnvironment(UUID tenantId) {
        return new MockEnvironment()
                .withProperty("snad.security.g2-reconciliation-enabled", "true")
                .withProperty("snad.security.g2-reconciliation-tenant-id", tenantId.toString());
    }

    private Authentication authentication(UUID tenantId, UUID userId) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getDetails()).thenReturn(Map.of(
                "tenant_id", tenantId.toString(),
                "user_id", userId.toString()));
        return authentication;
    }
}
