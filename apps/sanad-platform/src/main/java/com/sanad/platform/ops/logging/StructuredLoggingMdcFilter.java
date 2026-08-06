package com.sanad.platform.ops.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Servlet filter that populates MDC fields for structured JSON logging.
 * <p>
 * Extracts tenant_id, user_id, correlation_id, and request_id from the
 * HTTP request and authentication context, making them available in all
 * log entries via SLF4J MDC.
 * <p>
 * CRM-008 remediation: structured JSON logging with contextual fields.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StructuredLoggingMdcFilter extends OncePerRequestFilter {

    private static final String TENANT_ID = "tenant_id";
    private static final String USER_ID = "user_id";
    private static final String CORRELATION_ID = "correlation_id";
    private static final String REQUEST_ID = "request_id";
    private static final String ORGANIZATION_ID = "organization_id";
    private static final String ENVIRONMENT = "environment";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        try {
            // Extract or generate correlation ID
            String correlationId = request.getHeader("X-Correlation-ID");
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = request.getHeader("X-Request-ID");
            }
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }

            // Extract request ID
            String requestId = request.getHeader("X-Request-ID");
            if (requestId == null || requestId.isBlank()) {
                requestId = UUID.randomUUID().toString();
            }

            // Set correlation and request IDs
            MDC.put(CORRELATION_ID, correlationId);
            MDC.put(REQUEST_ID, requestId);
            MDC.put(ENVIRONMENT, System.getProperty("ENVIRONMENT", "production"));

            // Extract tenant and user from authentication context
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getDetails() instanceof Map<?, ?> details) {
                Object tenantId = details.get("tenant_id");
                if (tenantId != null) {
                    MDC.put(TENANT_ID, tenantId.toString());
                }
                Object userId = details.get("user_id");
                if (userId != null) {
                    MDC.put(USER_ID, userId.toString());
                }
                Object orgId = details.get("organization_id");
                if (orgId != null) {
                    MDC.put(ORGANIZATION_ID, orgId.toString());
                }
            }

            // Set response header for traceability
            response.setHeader("X-Correlation-ID", correlationId);
            response.setHeader("X-Request-ID", requestId);

            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
