package com.sanad.platform.security.tenant;

import com.sanad.platform.shared.api.RequestIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Stage 04 §7-8 — Establishes the {@link TenantContext} AFTER authentication.
 *
 * <p>This filter is NOT a Spring {@code @Component}; it is instantiated
 * explicitly by {@code SecurityConfig} and added to the Spring Security
 * filter chain AFTER {@code JwtAuthenticationFilter}. This avoids being
 * auto-registered as a servlet filter, which would break {@code @WebMvcTest}
 * slice tests (the filter requires a {@link TenantContextProvider} bean
 * that slice tests don't load).</p>
 *
 * <p>For every authenticated request to {@code /api/**}:</p>
 * <ol>
 *   <li>Reads the verified JWT claims from {@code Authentication.details}.</li>
 *   <li>Builds a {@link TenantContext} from the verified tenant_id claim.</li>
 *   <li>Stores it via {@link TenantContextProvider#setContext}.</li>
 *   <li>Populates MDC keys for structured logging ({@code tenantId}, {@code userId}).</li>
 *   <li>In {@code finally}: clears the context and MDC to prevent leakage.</li>
 * </ol>
 */
@Order(Ordered.LOWEST_PRECEDENCE)
public class TenantContextFilter extends OncePerRequestFilter {

    private final TenantContextProvider contextProvider;

    public TenantContextFilter(TenantContextProvider contextProvider) {
        this.contextProvider = contextProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            // Unauthenticated request — no context to establish.
            filterChain.doFilter(request, response);
            return;
        }

        Object detailsObj = authentication.getDetails();
        if (!(detailsObj instanceof Map<?, ?> detailsMap)) {
            // No JWT details — cannot establish context.
            filterChain.doFilter(request, response);
            return;
        }

        Object tenantIdObj = detailsMap.get("tenant_id");
        Object userIdObj = detailsMap.get("user_id");
        Object emailObj = detailsMap.get("email");
        Object sessionIdObj = detailsMap.get("jti");
        Object sessionVersionObj = detailsMap.get("session_version");

        if (tenantIdObj == null || userIdObj == null) {
            // Incomplete claims — cannot establish context.
            filterChain.doFilter(request, response);
            return;
        }

        UUID tenantId;
        UUID userId;
        try {
            tenantId = UUID.fromString(tenantIdObj.toString());
            userId = UUID.fromString(userIdObj.toString());
        } catch (IllegalArgumentException e) {
            // Malformed UUID in claims — treat as no context (downstream will 401/403).
            filterChain.doFilter(request, response);
            return;
        }

        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        String email = emailObj != null ? emailObj.toString() : null;
        String sessionId = sessionIdObj != null ? sessionIdObj.toString() : null;

        long sessionVersion = 0L;
        if (sessionVersionObj instanceof Number) {
            sessionVersion = ((Number) sessionVersionObj).longValue();
        }

        // Capabilities are not stored in the JWT; they are evaluated by the
        // @RequireCapability aspect against the DB. For the TenantContext,
        // we carry an empty set — the aspect remains the source of truth.
        // capabilitiesVerified() returns false when empty, so callers should
        // not treat this as "zero capabilities".
        Set<String> capabilities = Set.of();

        TenantContext context = new TenantContext(
                tenantId,
                userId,
                sessionId,
                sessionVersion,
                capabilities,
                TenantContext.TenantContextSource.JWT_CLAIM,
                requestId
        );

        contextProvider.setContext(context);

        // Populate MDC for structured logging (Stage 04 §27).
        // MDC is NEVER trusted as a source of authority — only for logging.
        MDC.put("tenantId", tenantId.toString());
        MDC.put("userId", userId.toString());

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Stage 04 §8 — clear in finally to prevent ThreadLocal leakage.
            contextProvider.clear();
            MDC.remove("tenantId");
            MDC.remove("userId");
        }
    }
}
