package com.sanad.platform.internal.bootstrap.api;

import com.sanad.platform.internal.bootstrap.service.ControlPlaneBootstrapResult;
import com.sanad.platform.internal.bootstrap.service.ControlPlaneBootstrapService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ControlPlaneBootstrapController}.
 *
 * <p>Verifies that the security gates (enabled flag, token presence, token equality)
 * are enforced independently of the underlying service, and that the success response
 * never contains the raw email or password.</p>
 */
class ControlPlaneBootstrapControllerTest {

    private ControlPlaneBootstrapService service;
    private ControlPlaneBootstrapController controller;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        service = mock(ControlPlaneBootstrapService.class);
        request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getRequestURI()).thenReturn("/api/v1/internal/control-plane/bootstrap-admin");
    }

    @Test
    void rejectsRequestWhenBootstrapDisabled() {
        controller = new ControlPlaneBootstrapController(service, false, "valid-token");

        ResponseEntity<Map<String, Object>> response = controller.bootstrapAdmin("valid-token", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).extractingByKey("code").isEqualTo("bootstrap_disabled");
        verify(service, never()).bootstrapControlPlaneAdmin();
    }

    @Test
    void rejectsRequestWhenTokenIsNotConfigured() {
        controller = new ControlPlaneBootstrapController(service, true, "");

        ResponseEntity<Map<String, Object>> response = controller.bootstrapAdmin("anything", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).extractingByKey("code").isEqualTo("bootstrap_misconfigured");
        verify(service, never()).bootstrapControlPlaneAdmin();
    }

    @Test
    void rejectsRequestWhenTokenHeaderMissing() {
        controller = new ControlPlaneBootstrapController(service, true, "valid-token");

        ResponseEntity<Map<String, Object>> response = controller.bootstrapAdmin(null, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).extractingByKey("code").isEqualTo("unauthorized");
        verify(service, never()).bootstrapControlPlaneAdmin();
    }

    @Test
    void rejectsRequestWhenTokenDoesNotMatch() {
        controller = new ControlPlaneBootstrapController(service, true, "valid-token");

        ResponseEntity<Map<String, Object>> response = controller.bootstrapAdmin("wrong-token", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).extractingByKey("code").isEqualTo("unauthorized");
        verify(service, never()).bootstrapControlPlaneAdmin();
    }

    @Test
    void bootstrapsWhenEnabledAndTokenMatches() {
        controller = new ControlPlaneBootstrapController(service, true, "valid-token");
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ControlPlaneBootstrapResult result = new ControlPlaneBootstrapResult(
                tenantId, userId, "cp-admin@control-plane.internal",
                true, true, List.of("ROLE.READ", "ROLE.WRITE"));
        when(service.bootstrapControlPlaneAdmin()).thenReturn(result);

        ResponseEntity<Map<String, Object>> response = controller.bootstrapAdmin("valid-token", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).extractingByKey("status").isEqualTo("ok");
        assertThat(body).extractingByKey("bootstrap").isEqualTo("complete");
        assertThat(body).extractingByKey("tenantId").isEqualTo(tenantId);
        assertThat(body).extractingByKey("userId").isEqualTo(userId);
        // Masked email must never contain the local part in clear.
        String masked = (String) body.get("adminEmailMasked");
        assertThat(masked).isNotNull();
        assertThat(masked).doesNotContain("cp-admin");
        // No password must ever appear in the response.
        assertThat(body.toString()).doesNotContain("password");
    }

    @Test
    void returnsServerErrorWhenServiceThrows() {
        controller = new ControlPlaneBootstrapController(service, true, "valid-token");
        when(service.bootstrapControlPlaneAdmin())
                .thenThrow(new IllegalStateException("tenant not found"));

        ResponseEntity<Map<String, Object>> response = controller.bootstrapAdmin("valid-token", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).extractingByKey("code").isEqualTo("bootstrap_failed");
        // The exception message is propagated as a safe summary; assert it doesn't include secrets.
        assertThat(response.getBody().toString()).doesNotContain("password");
    }

    @Test
    void returnsServerErrorOnUnexpectedException() {
        controller = new ControlPlaneBootstrapController(service, true, "valid-token");
        when(service.bootstrapControlPlaneAdmin()).thenThrow(new RuntimeException("unexpected"));

        ResponseEntity<Map<String, Object>> response = controller.bootstrapAdmin("valid-token", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).extractingByKey("code").isEqualTo("bootstrap_failed");
        assertThat(response.getBody().toString()).doesNotContain("unexpected");
    }
}
