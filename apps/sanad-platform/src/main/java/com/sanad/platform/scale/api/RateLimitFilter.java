package com.sanad.platform.scale.api;

import com.sanad.platform.scale.domain.TenantQuota.Dimension;
import com.sanad.platform.scale.service.TenantQuotaService;
import com.sanad.platform.scale.service.TenantQuotaService.QuotaCheckResult;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * Stage 08 Sprint 1 — ST8-S1-003 API Rate Limiting Filter.
 *
 * Enforces per-tenant {@link Dimension#API_RPM} quota at the gateway.
 *
 * On breach: returns HTTP 429 with {@code Retry-After},
 * {@code X-RateLimit-Limit}, {@code X-RateLimit-Remaining},
 * {@code X-RateLimit-Reset}, {@code X-SANAD-Quota-Exceeded: API_RPM}.
 *
 * Security: tenant context resolved from authenticated principal. If no
 * tenant context is available, the request is rejected with 401 (the
 * filter does not consume anonymous traffic).
 *
 * Tenant isolation: per-tenant bucket — tenant A exceeding limit has no
 * impact on tenant B.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final TenantQuotaService quotaService;

    public RateLimitFilter(TenantQuotaService quotaService) {
        this.quotaService = quotaService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String tenantId = resolveTenantId();
        if (tenantId == null) {
            // No tenant context — let auth filters handle the 401.
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        if (isExcluded(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        QuotaCheckResult result = quotaService.checkAndConsume(tenantId, Dimension.API_RPM, 1L);

        long nowEpoch = Instant.now().getEpochSecond();
        response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(nowEpoch + Math.max(0L, result.retryAfter())));

        if (result.exceeded()) {
            log.warn("Rate limit exceeded tenant={} path={} retryAfter={}s",
                    tenantId, path, result.retryAfter());
            response.setHeader("Retry-After", String.valueOf(result.retryAfter()));
            response.setHeader("X-SANAD-Quota-Exceeded", Dimension.API_RPM.name());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"error\":\"rate_limit_exceeded\",\"dimension\":\"API_RPM\",\"retryAfterSeconds\":"
                            + result.retryAfter() + "}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            return null;
        }
        Object principal = auth.getPrincipal();
        // Resolve tenant id from principal — supports common SANAD principal types.
        try {
            java.lang.reflect.Method m = principal.getClass().getMethod("getTenantId");
            Object tid = m.invoke(principal);
            return tid == null ? null : tid.toString();
        } catch (Exception e) {
            // Principal has no getTenantId() — treat as no tenant context.
            return null;
        }
    }

    private boolean isExcluded(String path) {
        return path.startsWith("/actuator/")
                || path.startsWith("/api/v1/auth/")
                || path.startsWith("/api/v1/health")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/webhooks/");
    }
}
