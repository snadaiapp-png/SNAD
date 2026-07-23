package com.sanad.platform.security.authorization;

import com.sanad.platform.access.AccessDecisionResponse;
import com.sanad.platform.access.evaluation.CapabilityEvaluationService;
import com.sanad.platform.admin.service.PlatformAuditWriter;
import com.sanad.platform.crm.integration.domain.CorrelationContextPort;
import org.aspectj.lang.JoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CapabilityAuthorizationAspectTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unauthenticatedRequestFailsClosedAndIsAudited() {
        Fixture fixture = fixture();
        RequireCapability annotation = annotation("CRM.TEAM.READ");

        assertThrows(
                AuthenticationCredentialsNotFoundException.class,
                () -> fixture.aspect().checkCapability(mock(JoinPoint.class), annotation));

        verify(fixture.audit()).writeFailure(
                isNull(), isNull(), isNull(), eq("CRM.TEAM.READ"), eq("AUTHORIZATION"),
                any(), eq("Unauthenticated request"), any(), eq("correlation-test"), any());
    }

    @Test
    void authenticatedRequestWithoutTenantDetailsFailsClosed() {
        Fixture fixture = fixture();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("operator", "n/a", List.of()));

        assertThrows(
                AccessDeniedException.class,
                () -> fixture.aspect().checkCapability(
                        mock(JoinPoint.class), annotation("CRM.TEAM.READ")));
    }

    @Test
    void deniedDecisionThrowsAuditsAndPreventsControllerExecution() {
        UUID tenantId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID userId = UUID.fromString("20000000-0000-0000-0000-000000000001");
        Fixture fixture = fixture();
        when(fixture.evaluation().evaluate(tenantId, userId, "USER.READ", null))
                .thenReturn(new AccessDecisionResponse(
                        tenantId, userId, null, "USER.READ", false,
                        "missing capability", null, null));
        authenticate(tenantId, userId);

        assertThrows(
                AccessDeniedException.class,
                () -> fixture.aspect().checkCapability(
                        mock(JoinPoint.class), annotation("USER.READ")));

        verify(fixture.audit()).writeFailure(
                eq(tenantId), eq(userId), eq(tenantId), eq("USER.READ"),
                eq("AUTHORIZATION"), any(), eq("missing capability"),
                any(), eq("correlation-test"), any());
    }

    @Test
    void allowedDecisionContinues() {
        UUID tenantId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID userId = UUID.fromString("20000000-0000-0000-0000-000000000001");
        Fixture fixture = fixture();
        when(fixture.evaluation().evaluate(tenantId, userId, "CRM.TEAM.READ", null))
                .thenReturn(new AccessDecisionResponse(
                        tenantId, userId, null, "CRM.TEAM.READ", true,
                        "allowed", null, "SALES_MANAGER"));
        authenticate(tenantId, userId);

        assertDoesNotThrow(() -> fixture.aspect().checkCapability(
                mock(JoinPoint.class), annotation("CRM.TEAM.READ")));
    }

    private Fixture fixture() {
        CapabilityEvaluationService evaluation = mock(CapabilityEvaluationService.class);
        PlatformAuditWriter audit = mock(PlatformAuditWriter.class);
        CorrelationContextPort correlation = () -> "correlation-test";
        return new Fixture(
                new CapabilityAuthorizationAspect(evaluation, audit, correlation),
                evaluation,
                audit);
    }

    private RequireCapability annotation(String capability) {
        RequireCapability annotation = mock(RequireCapability.class);
        when(annotation.value()).thenReturn(capability);
        return annotation;
    }

    private void authenticate(UUID tenantId, UUID userId) {
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated("operator", "n/a", List.of());
        authentication.setDetails(Map.of(
                "tenant_id", tenantId.toString(),
                "user_id", userId.toString()));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private record Fixture(
            CapabilityAuthorizationAspect aspect,
            CapabilityEvaluationService evaluation,
            PlatformAuditWriter audit) {
    }
}
