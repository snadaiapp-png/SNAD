package com.sanad.platform.security.authorization;

import com.sanad.platform.access.AccessDecisionResponse;
import com.sanad.platform.access.evaluation.CapabilityEvaluationService;
import org.aspectj.lang.JoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CapabilityAuthorizationAspectTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deniedDecisionThrowsAndPreventsControllerExecution() {
        UUID tenantId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID userId = UUID.fromString("20000000-0000-0000-0000-000000000001");
        CapabilityEvaluationService evaluationService = mock(CapabilityEvaluationService.class);
        when(evaluationService.evaluate(tenantId, userId, "USER.READ", null))
                .thenReturn(new AccessDecisionResponse(
                        tenantId, userId, null, "USER.READ", false,
                        "missing capability", null, null));

        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated("operator", "n/a", List.of());
        authentication.setDetails(Map.of(
                "tenant_id", tenantId.toString(),
                "user_id", userId.toString()
        ));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        RequireCapability annotation = mock(RequireCapability.class);
        when(annotation.value()).thenReturn("USER.READ");

        CapabilityAuthorizationAspect aspect = new CapabilityAuthorizationAspect(evaluationService);
        assertThrows(
                AccessDeniedException.class,
                () -> aspect.checkCapability(mock(JoinPoint.class), annotation)
        );
    }
}
