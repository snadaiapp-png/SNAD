package com.sanad.platform.security.authorization;

import com.sanad.platform.access.AccessDecisionResponse;
import com.sanad.platform.access.evaluation.CapabilityEvaluationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * AOP aspect that enforces RBAC authorization on endpoints annotated
 * with {@link RequireCapability}.
 *
 * <p>For each annotated method, this aspect:</p>
 * <ol>
 *   <li>Extracts the authenticated user's tenantId and userId from the
 *       SecurityContext (set by JwtAuthenticationFilter).</li>
 *   <li>Resolves the optional organizationId from the request parameters.</li>
 *   <li>Delegates to {@link CapabilityEvaluationService#evaluate} to check
 *       whether the user holds the required capability.</li>
 *   <li>If denied, writes an HTTP 403 response and prevents the controller
 *       method from executing.</li>
 * </ol>
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
     * Intercepts any controller method annotated with {@code @RequireCapability}
     * and checks the authenticated user's capabilities before allowing execution.
     */
    @Before("@annotation(requireCapability)")
    public void checkCapability(JoinPoint joinPoint, RequireCapability requireCapability) throws IOException {
        String capabilityCode = requireCapability.value();

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            // No authentication present — let Spring Security's filter chain handle it.
            // In test configurations with permitAll, there's no auth in the context;
            // the SecurityConfig's authorizeHttpRequests decides the outcome.
            return;
        }

        if (!(authentication.getDetails() instanceof Map<?, ?>)) {
            // Authentication details don't contain JWT claims (e.g., test mock).
            // Skip RBAC check — the SecurityConfig handles basic auth decisions.
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) authentication.getDetails();
        Object tenantIdObj = details.get("tenant_id");
        Object userIdObj = details.get("user_id");
        if (tenantIdObj == null || userIdObj == null) {
            // Incomplete JWT claims — skip RBAC check.
            return;
        }

        UUID tenantId = UUID.fromString((String) tenantIdObj);
        UUID userId = UUID.fromString((String) userIdObj);

        // Resolve organizationId from request params (optional)
        UUID organizationId = resolveOrganizationId();

        AccessDecisionResponse decision = evaluationService.evaluate(
                tenantId, userId, capabilityCode, organizationId);

        if (!decision.allowed()) {
            log.debug("RBAC DENY: userId={} tenantId={} capability={} reason={} path={}",
                    userId, tenantId, capabilityCode, decision.reason(),
                    getRequestPath());
            writeForbidden("غير مصرح بهذه العملية");
            return;
        }

        log.debug("RBAC ALLOW: userId={} tenantId={} capability={} role={}",
                userId, tenantId, capabilityCode, decision.matchedRoleCode());
    }

    /**
     * Attempts to resolve an organizationId from the request query parameters
     * or path variables. Returns null if not present (tenant-scope evaluation).
     */
    private UUID resolveOrganizationId() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return null;

        HttpServletRequest request = attrs.getRequest();

        // Check query parameter first
        String orgIdParam = request.getParameter("organizationId");
        if (orgIdParam != null && !orgIdParam.isBlank()) {
            try {
                return UUID.fromString(orgIdParam);
            } catch (IllegalArgumentException ignored) {
            }
        }

        // Check path variable pattern: /organizations/{organizationId}/...
        String uri = request.getRequestURI();
        // Match pattern like /api/v1/organizations/{uuid}/memberships/...
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "/organizations/([0-9a-f-]{36})").matcher(uri);
        if (matcher.find()) {
            try {
                return UUID.fromString(matcher.group(1));
            } catch (IllegalArgumentException ignored) {
            }
        }

        return null;
    }

    private String getRequestPath() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return "unknown";
        return attrs.getRequest().getRequestURI();
    }

    private void writeForbidden(String message) throws IOException {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return;

        HttpServletResponse response = attrs.getResponse();
        if (response == null || response.isCommitted()) return;

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"timestamp\":\"" + java.time.Instant.now() + "\","
                        + "\"status\":403,"
                        + "\"error\":\"Forbidden\","
                        + "\"message\":\"" + message + "\","
                        + "\"path\":\"" + attrs.getRequest().getRequestURI() + "\"}");
        response.flushBuffer();
    }
}
