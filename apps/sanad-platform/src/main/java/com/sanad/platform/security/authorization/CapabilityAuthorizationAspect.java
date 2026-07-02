package com.sanad.platform.security.authorization;

import com.sanad.platform.access.AccessDecisionResponse;
import com.sanad.platform.access.evaluation.CapabilityEvaluationService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.UUID;

/**
 * AOP aspect that enforces RBAC authorization on endpoints annotated
 * with {@link RequireCapability}.
 */
@Aspect
@Component
public class CapabilityAuthorizationAspect {

    private static final Logger log = LoggerFactory.getLogger(CapabilityAuthorizationAspect.class);

    private final CapabilityEvaluationService evaluationService;

    public CapabilityAuthorizationAspect(CapabilityEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    /**
     * Intercepts an annotated controller method and stops execution when the
     * requested capability is denied. Throwing AccessDeniedException is
     * intentional: writing a 403 response without throwing would allow the
     * target method to continue and could execute a protected mutation.
     */
    @Before("@annotation(requireCapability)")
    public void checkCapability(JoinPoint joinPoint, RequireCapability requireCapability) {
        String capabilityCode = requireCapability.value();

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return;
        }

        if (!(authentication.getDetails() instanceof Map<?, ?>)) {
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) authentication.getDetails();
        Object tenantIdObj = details.get("tenant_id");
        Object userIdObj = details.get("user_id");
        if (tenantIdObj == null || userIdObj == null) {
            return;
        }

        UUID tenantId;
        UUID userId;
        try {
            tenantId = UUID.fromString(tenantIdObj.toString());
            userId = UUID.fromString(userIdObj.toString());
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Invalid authenticated authorization context", exception);
        }

        UUID organizationId = resolveOrganizationId();
        AccessDecisionResponse decision = evaluationService.evaluate(
                tenantId, userId, capabilityCode, organizationId);

        if (!decision.allowed()) {
            log.warn("RBAC DENY: userId={} tenantId={} capability={} reason={} path={}",
                    userId, tenantId, capabilityCode, decision.reason(), getRequestPath());
            throw new AccessDeniedException("غير مصرح بهذه العملية");
        }

        log.debug("RBAC ALLOW: userId={} tenantId={} capability={} role={}",
                userId, tenantId, capabilityCode, decision.matchedRoleCode());
    }

    private UUID resolveOrganizationId() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }

        HttpServletRequest request = attrs.getRequest();
        String orgIdParam = request.getParameter("organizationId");
        if (orgIdParam != null && !orgIdParam.isBlank()) {
            try {
                return UUID.fromString(orgIdParam);
            } catch (IllegalArgumentException ignored) {
                // Invalid organization identifiers are validated by the endpoint.
            }
        }

        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "/organizations/([0-9a-f-]{36})").matcher(request.getRequestURI());
        if (matcher.find()) {
            try {
                return UUID.fromString(matcher.group(1));
            } catch (IllegalArgumentException ignored) {
                // Invalid path identifiers are validated by the endpoint.
            }
        }
        return null;
    }

    private String getRequestPath() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? "unknown" : attrs.getRequest().getRequestURI();
    }
}
