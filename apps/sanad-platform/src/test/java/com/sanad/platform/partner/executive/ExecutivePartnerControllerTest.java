package com.sanad.platform.partner.executive;

import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.security.authorization.RequireCapability;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ExecutivePartnerControllerTest {

    private final ControlPlaneAccessGuard guard = mock(ControlPlaneAccessGuard.class);
    private final ExecutivePartnerService service = mock(ExecutivePartnerService.class);
    private final ExecutivePartnerController controller =
            new ExecutivePartnerController(guard, service);

    @Test
    void tenantPlaneCallerIsDeniedBeforePartnerEnumeration() {
        var auth = new UsernamePasswordAuthenticationToken("tenant-user", null, List.of());
        doThrow(new AccessDeniedException("Control-plane tenant required"))
                .when(guard).requireRead(auth);

        assertThatThrownBy(() -> controller.list(auth))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Control-plane tenant required");

        verifyNoInteractions(service);
    }

    @Test
    void partnerPrincipalWithoutControlPlaneContextIsDeniedBeforeGlobalRead() {
        var auth = new UsernamePasswordAuthenticationToken("partner-user", null, List.of());
        doThrow(new AccessDeniedException("Control-plane tenant required"))
                .when(guard).requireRead(auth);

        assertThatThrownBy(() -> controller.detail(auth, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(service);
    }

    @Test
    void writeIsDeniedBeforeMutationWhenControlPlaneGuardRejects() {
        var auth = new UsernamePasswordAuthenticationToken("partner-admin", null, List.of());
        UUID partnerId = UUID.randomUUID();
        doThrow(new AccessDeniedException("Control-plane tenant required"))
                .when(guard).requireWrite(auth);

        assertThatThrownBy(() -> controller.changeStatus(
                auth, partnerId,
                new ExecutivePartnerController.ChangePartnerStatusRequest("SUSPENDED", "test")))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(service);
    }

    @Test
    void authorizedControlPlaneReadUsesExplicitPartnerTarget() {
        var auth = new UsernamePasswordAuthenticationToken("platform-user", null, List.of());
        UUID partnerId = UUID.randomUUID();
        when(service.memberships(partnerId)).thenReturn(List.of());

        var response = controller.users(auth, partnerId);

        assertThat(response.getBody()).isEmpty();
        verify(guard).requireRead(auth);
        verify(service).memberships(partnerId);
    }

    @Test
    void endpointCapabilitiesFollowCanonicalExecutivePattern() throws Exception {
        assertCapability("list", "EXECUTIVE_VIEW", org.springframework.security.core.Authentication.class);
        assertCapability("create", "EXECUTIVE_MANAGE",
                org.springframework.security.core.Authentication.class,
                ExecutivePartnerController.CreatePartnerRequest.class);
        assertCapability("detail", "EXECUTIVE_VIEW",
                org.springframework.security.core.Authentication.class, UUID.class);
        assertCapability("changeStatus", "EXECUTIVE_MANAGE",
                org.springframework.security.core.Authentication.class, UUID.class,
                ExecutivePartnerController.ChangePartnerStatusRequest.class);
        assertCapability("users", "EXECUTIVE_VIEW",
                org.springframework.security.core.Authentication.class, UUID.class);
        assertCapability("tenants", "EXECUTIVE_VIEW",
                org.springframework.security.core.Authentication.class, UUID.class);
        assertCapability("delegations", "EXECUTIVE_VIEW",
                org.springframework.security.core.Authentication.class, UUID.class);
    }

    private void assertCapability(String methodName, String expected, Class<?>... parameterTypes)
            throws Exception {
        Method method = ExecutivePartnerController.class.getMethod(methodName, parameterTypes);
        RequireCapability annotation = method.getAnnotation(RequireCapability.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo(expected);
    }
}
