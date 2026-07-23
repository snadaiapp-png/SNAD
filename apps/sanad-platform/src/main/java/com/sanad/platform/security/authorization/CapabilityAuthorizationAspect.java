package com.sanad.platform.security.authorization;

import com.sanad.platform.access.AccessDecisionResponse;
import com.sanad.platform.access.evaluation.CapabilityEvaluationService;
import com.sanad.platform.admin.service.PlatformAuditWriter;
import com.sanad.platform.crm.integration.domain.CorrelationContextPort;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Enforces tenant-scoped, deny-by-default capability authorization. */
@Aspect
@Component
public class CapabilityAuthorizationAspect {

    private static final Logger log = LoggerFactory.getLogger(CapabilityAuthorizationAspect.class);

    private final CapabilityEvaluationService evaluationService;
    private final PlatformAuditWriter auditWriter;
    private final CorrelationContextPort correlationContext;

    public CapabilityAuthorizationAspect(
            CapabilityEvaluationService evaluationService,
            PlatformAuditWriter auditWriter,
            CorrelationContextPort correlationContext) {
        this.evaluationService = evaluationService;
        this.auditWriter = auditWriter;
        this.correlationContext = correlationContext;
    }

    @Before("@annotation(requireCapability)")
    public void checkCapability(JoinPoint joinPoint, RequireCapability requireCapability) {
        String capabilityCode = requireCapability == null ? null : requireCapability.value();
        if (capabilityCode == null || capabilityCode.isBlank()) {
            auditFailure(null, null, "INVALID_CAPABILITY", "Capability annotation is empty");
            throw new AccessDeniedException("Invalid capability policy");
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            auditFailure(null, null, capabilityCode, "Unauthenticated request");
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }

        if (!(authentication.getDetails() instanceof Map<?, ?> details)) {
            auditFailure(null, null, capabilityCode, "Missing authenticated tenant context");
            throw new AccessDeniedException("Invalid authenticated authorization context");
        }

        UUID tenantId = contextUuid(details, "tenant_id", capabilityCode);
        UUID userId = contextUuid(details, "user_id", capabilityCode);
        UUID organizationId = resolveOrganizationId();

        AccessDecisionResponse decision = evaluationService.evaluate(
                tenantId, userId, capabilityCode, organizationId);
        if (decision == null || !decision.allowed()) {
            String reason = decision == null ? "No authorization decision" : decision.reason();
            auditFailure(tenantId, userId, capabilityCode, reason);
            log.warn("RBAC DENY: userId={} tenantId={} capability={} reason={} path={}",
                    userId, tenantId, capabilityCode, reason, requestPath());
            throw new AccessDeniedException("غير مصرح بهذه العملية");
        }

        if (isWriteRequest()) {
            auditSuccess(tenantId, userId, capabilityCode, decision.matchedRoleCode());
        }
        log.debug("RBAC ALLOW: userId={} tenantId={} capability={} role={}",
                userId, tenantId, capabilityCode, decision.matchedRoleCode());
    }

    private UUID contextUuid(Map<?, ?> details, String key, String capabilityCode) {
        Object raw = details.get(key);
        if (raw == null) {
            auditFailure(null, null, capabilityCode, "Missing authenticated " + key);
            throw new AccessDeniedException("Invalid authenticated authorization context");
        }
        try {
            return UUID.fromString(raw.toString());
        } catch (IllegalArgumentException exception) {
            auditFailure(null, null, capabilityCode, "Invalid authenticated " + key);
            throw new AccessDeniedException("Invalid authenticated authorization context", exception);
        }
    }

    private void auditFailure(UUID tenantId, UUID userId, String capabilityCode, String reason) {
        try {
            auditWriter.writeFailure(
                    tenantId, userId, tenantId,
                    capabilityCode, "AUTHORIZATION", requestPath(), reason,
                    requestDetails(), correlationContext.currentCorrelationId(), Instant.now());
        } catch (RuntimeException auditError) {
            log.error("Unable to persist failed authorization audit: capability={} path={}",
                    capabilityCode, requestPath(), auditError);
        }
    }

    private void auditSuccess(UUID tenantId, UUID userId, String capabilityCode, String roleCode) {
        auditWriter.writeSuccess(
                tenantId, userId, tenantId,
                capabilityCode, "AUTHORIZATION", requestPath(), "ALLOW",
                null, Map.of("role", roleCode == null ? "" : roleCode),
                correlationContext.currentCorrelationId(), Instant.now());
    }

    private Map<String, Object> requestDetails() {
        HttpServletRequest request = request();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("method", request == null ? "UNKNOWN" : request.getMethod());
        details.put("path", requestPath());
        details.put("ipAddress", request == null ? "UNKNOWN" : String.valueOf(request.getRemoteAddr()));
        details.put("userAgent", request == null || request.getHeader("User-Agent") == null
                ? "UNKNOWN" : request.getHeader("User-Agent"));
        return details;
    }

    private boolean isWriteRequest() {
        HttpServletRequest request = request();
        if (request == null || request.getMethod() == null) return false;
        return switch (request.getMethod()) {
            case "POST", "PUT", "PATCH", "DELETE" -> true;
            default -> false;
        };
    }

    private UUID resolveOrganizationId() {
        HttpServletRequest request = request();
        if (request == null) return null;
        String orgIdParam = request.getParameter("organizationId");
        if (orgIdParam != null && !orgIdParam.isBlank()) {
            try {
                return UUID.fromString(orgIdParam);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "/organizations/([0-9a-fA-F-]{36})").matcher(request.getRequestURI());
        if (matcher.find()) {
            try {
                return UUID.fromString(matcher.group(1));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private String requestPath() {
        HttpServletRequest request = request();
        return request == null || request.getRequestURI() == null
                ? "unknown" : request.getRequestURI();
    }

    private HttpServletRequest request() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }
}
