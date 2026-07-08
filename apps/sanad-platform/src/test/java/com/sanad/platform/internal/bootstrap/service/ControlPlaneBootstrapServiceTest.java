package com.sanad.platform.internal.bootstrap.service;

import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.security.config.CredentialBootstrapService;
import com.sanad.platform.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ControlPlaneBootstrapService}.
 *
 * <p>Verifies that the service refuses to bootstrap when required configuration
 * is missing or weak, and that it delegates to {@link CredentialBootstrapService}
 * with {@code forceReset=true} when all inputs are valid.</p>
 */
class ControlPlaneBootstrapServiceTest {

    private CredentialBootstrapService credentialBootstrapService;
    private ControlPlaneAccessGuard accessGuard;
    private ControlPlaneBootstrapService service;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        credentialBootstrapService = mock(CredentialBootstrapService.class);
        accessGuard = mock(ControlPlaneAccessGuard.class);
        when(accessGuard.isControlPlaneConfigured()).thenReturn(true);
    }

    @Test
    void throwsWhenControlPlaneTenantNotConfigured() {
        when(accessGuard.isControlPlaneConfigured()).thenReturn(false);
        service = new ControlPlaneBootstrapService(
                credentialBootstrapService, accessGuard,
                "", "admin@example.com", "very-strong-password-123", "Admin");

        assertThatThrownBy(() -> service.bootstrapControlPlaneAdmin())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SANAD_CONTROL_PLANE_TENANT_ID");
    }

    @Test
    void throwsWhenAdminEmailMissing() {
        service = new ControlPlaneBootstrapService(
                credentialBootstrapService, accessGuard,
                tenantId.toString(), "", "very-strong-password-123", "Admin");

        assertThatThrownBy(() -> service.bootstrapControlPlaneAdmin())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONTROL_PLANE_ADMIN_EMAIL");
    }

    @Test
    void throwsWhenPasswordTooShort() {
        service = new ControlPlaneBootstrapService(
                credentialBootstrapService, accessGuard,
                tenantId.toString(), "admin@example.com", "short", "Admin");

        assertThatThrownBy(() -> service.bootstrapControlPlaneAdmin())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONTROL_PLANE_ADMIN_PASSWORD");
    }

    @Test
    void throwsWhenTenantIdIsNotUuid() {
        service = new ControlPlaneBootstrapService(
                credentialBootstrapService, accessGuard,
                "not-a-uuid", "admin@example.com", "very-strong-password-123", "Admin");

        assertThatThrownBy(() -> service.bootstrapControlPlaneAdmin())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a valid UUID");
    }

    @Test
    void delegatesWithForceResetWhenAllInputsValid() {
        service = new ControlPlaneBootstrapService(
                credentialBootstrapService, accessGuard,
                tenantId.toString(),
                "cp-admin@control-plane.internal",
                "very-strong-password-123",
                "SANAD Control Plane Admin");

        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(user.getPasswordSetAt()).thenReturn(Instant.now());
        when(credentialBootstrapService.bootstrap(
                anyBoolean(), anyBoolean(), eq(tenantId), isNull(), isNull(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn(user);

        ControlPlaneBootstrapResult result = service.bootstrapControlPlaneAdmin();

        assertThat(result).isNotNull();
        assertThat(result.tenantId()).isEqualTo(tenantId);
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.adminEmail()).isEqualTo("cp-admin@control-plane.internal");
        assertThat(result.created()).isTrue();
        assertThat(result.membershipActivated()).isTrue();
        assertThat(result.roleGrantsActivated()).containsExactly("ROLE.READ", "ROLE.WRITE");

        // Verify forceReset=true is passed so the password is rotated and grant re-activated.
        verify(credentialBootstrapService).bootstrap(
                eq(true), eq(true), eq(tenantId),
                isNull(),
                isNull(),
                eq("cp-admin@control-plane.internal"),
                eq("very-strong-password-123"),
                eq("SANAD Control Plane Admin"),
                eq("control-plane-bootstrap-endpoint"));
    }

    @Test
    void throwsWhenBootstrapReturnsNullUser() {
        service = new ControlPlaneBootstrapService(
                credentialBootstrapService, accessGuard,
                tenantId.toString(),
                "cp-admin@control-plane.internal",
                "very-strong-password-123",
                "SANAD Control Plane Admin");

        when(credentialBootstrapService.bootstrap(
                anyBoolean(), anyBoolean(), eq(tenantId), any(), any(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn(null);

        assertThatThrownBy(() -> service.bootstrapControlPlaneAdmin())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("returned no user");
    }

    @Test
    void usesDefaultDisplayNameWhenBlank() {
        service = new ControlPlaneBootstrapService(
                credentialBootstrapService, accessGuard,
                tenantId.toString(),
                "cp-admin@control-plane.internal",
                "very-strong-password-123",
                "   ");

        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(user.getPasswordSetAt()).thenReturn(Instant.now());
        when(credentialBootstrapService.bootstrap(
                anyBoolean(), anyBoolean(), eq(tenantId), isNull(), isNull(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn(user);

        service.bootstrapControlPlaneAdmin();

        // Verify the default display name was applied.
        verify(credentialBootstrapService).bootstrap(
                eq(true), eq(true), eq(tenantId),
                isNull(),
                isNull(),
                eq("cp-admin@control-plane.internal"),
                eq("very-strong-password-123"),
                eq("SANAD Control Plane Admin"),
                eq("control-plane-bootstrap-endpoint"));
    }
}
