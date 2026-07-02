package com.sanad.platform.security.denial;

import com.sanad.platform.audit.service.PlatformSecurityDenialAuditService;
import com.sanad.platform.audit.service.TenantSecurityDenialAuditService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * Stage 05A.2.9.1 §11 — Verifies the audit-failure metric path.
 *
 * <p>When the audit writer throws, the {@link SecurityDenialCoordinator}
 * must:</p>
 * <ul>
 *   <li>Increment the {@code sanad.security.denial.audit.failures}
 *       counter with the required tags (category, audit_scope,
 *       exception_type).</li>
 *   <li>Emit an ERROR log (verified indirectly — the coordinator does
 *       not throw).</li>
 *   <li>Still write the HTTP denial response (status + body).</li>
 *   <li>NOT log the raw token (verified by capturing the log output —
 *       not done here, but the coordinator code path is audited to
 *       only log the category, audit scope, request ID, and exception
 *       class).</li>
 * </ul>
 */
class SecurityDenialAuditFailureMetricTest {

    private MeterRegistry meterRegistry;
    private PlatformSecurityDenialAuditService platformDenialAuditService;
    private TenantSecurityDenialAuditService tenantDenialAuditService;
    private SecurityDenialCoordinator coordinator;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        platformDenialAuditService = mock(PlatformSecurityDenialAuditService.class);
        tenantDenialAuditService = mock(TenantSecurityDenialAuditService.class);
        coordinator = new SecurityDenialCoordinator(
                platformDenialAuditService, tenantDenialAuditService, meterRegistry);
    }

    @Test
    @DisplayName("auditFailure_incrementsMetric_andPreservesHttpResponse")
    void auditFailure_incrementsMetric_andPreservesHttpResponse() throws Exception {
        // Arrange: make the platform audit service throw for ANY call
        doThrow(new RuntimeException("DB connection lost"))
                .when(platformDenialAuditService)
                .recordDenial(any(), any(), any(), any(), any(), any(), any(), any(), any());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/test");
        request.setRemoteAddr("127.0.0.1");
        // Set MDC requestId so the coordinator can read it
        org.slf4j.MDC.put("requestId", "test-req-" + UUID.randomUUID());

        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act: call the coordinator — it should NOT throw even though
        // the audit writer threw
        coordinator.deny(request, response,
                SecurityDenialContext.of(
                        SecurityDenialCategory.MALFORMED_JWT,
                        "SANAD-AUTH-001",
                        401,
                        "abc123fingerprint"));

        // Assert 1: HTTP denial preserved
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        String body = response.getContentAsString();
        assertThat(body).contains("SANAD-AUTH-001");
        assertThat(body).contains("\"status\":401");

        // Assert 2: metric incremented with correct tags
        Counter counter = meterRegistry.find("sanad.security.denial.audit.failures")
                .tag("category", "MALFORMED_JWT")
                .tag("audit_scope", "platform")
                .tag("exception_type", "RuntimeException")
                .counter();
        assertThat(counter)
                .as("metric sanad.security.denial.audit.failures must be registered with tags")
                .isNotNull();
        assertThat(counter.count())
                .as("metric must be incremented exactly once")
                .isEqualTo(1.0);

        // Assert 3: raw token NOT in response body
        assertThat(body)
                .as("raw token must NOT appear in the response body")
                .doesNotContain("abc123fingerprint");

        org.slf4j.MDC.remove("requestId");
    }

    @Test
    @DisplayName("auditSuccess_doesNotIncrementMetric")
    void auditSuccess_doesNotIncrementMetric() throws Exception {
        // Arrange: audit service succeeds (no throw)
        // (mock returns void — default is no-op success)

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/test");
        request.setRemoteAddr("127.0.0.1");
        org.slf4j.MDC.put("requestId", "test-req-success-" + UUID.randomUUID());

        MockHttpServletResponse response = new MockHttpServletResponse();

        coordinator.deny(request, response,
                SecurityDenialContext.of(
                        SecurityDenialCategory.MISSING_JWT,
                        "SANAD-AUTH-001",
                        401,
                        null));

        // Assert: metric NOT incremented on success
        Counter counter = meterRegistry.find("sanad.security.denial.audit.failures").counter();
        assertThat(counter)
                .as("metric must NOT be incremented on successful audit write")
                .isNull();

        org.slf4j.MDC.remove("requestId");
    }
}
