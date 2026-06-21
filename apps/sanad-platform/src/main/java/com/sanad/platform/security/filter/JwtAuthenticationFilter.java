package com.sanad.platform.security.filter;

import com.sanad.platform.security.service.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JWT authentication filter with central tenant binding enforcement.
 *
 * <p>Extracts the Bearer token from the {@code Authorization} header,
 * validates it, and populates the {@code SecurityContext} with the
 * authenticated user's identity.</p>
 *
 * <p><strong>Tenant Binding:</strong> For any request to {@code /api/**}
 * that includes a {@code tenantId} query parameter, the filter validates
 * that the parameter matches the {@code tenant_id} claim in the JWT.
 * If they don't match, the request is rejected with 403 Forbidden.
 * This prevents cross-tenant data access even if a frontend bug sends
 * the wrong tenantId.</p>
 *
 * <p>Endpoints that don't take a {@code tenantId} parameter (like
 * {@code /api/v1/auth/me}) are not subject to this check.</p>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        Claims claims = jwtTokenProvider.parseAndValidate(token);

        if (claims == null) {
            log.debug("Invalid JWT token presented to {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        // Extract tenant_id from JWT
        String jwtTenantIdStr = claims.get("tenant_id", String.class);
        UUID jwtTenantId = null;
        try {
            jwtTenantId = UUID.fromString(jwtTenantIdStr);
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warn("JWT token has invalid tenant_id claim: {}", jwtTenantIdStr);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"timestamp\":\"" + java.time.Instant.now() + "\","
                            + "\"status\":401,"
                            + "\"error\":\"Unauthorized\","
                            + "\"message\":\"رمز المصادقة غير صالح\","
                            + "\"path\":\"" + request.getRequestURI() + "\"}"
            );
            return;
        }

        // CENTRAL TENANT BINDING: validate that the request's tenantId param
        // matches the JWT's tenant_id. This prevents cross-tenant access.
        String requestTenantIdParam = request.getParameter("tenantId");
        if (requestTenantIdParam != null && !requestTenantIdParam.isBlank()) {
            try {
                UUID requestTenantId = UUID.fromString(requestTenantIdParam);
                if (!requestTenantId.equals(jwtTenantId)) {
                    log.warn("Tenant binding violation: JWT tenantId={} but request tenantId={} for path={}",
                            jwtTenantId, requestTenantId, request.getRequestURI());
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write(
                            "{\"timestamp\":\"" + java.time.Instant.now() + "\","
                                    + "\"status\":403,"
                                    + "\"error\":\"Forbidden\","
                                    + "\"message\":\"تم رفض الوصول: تعارض في هوية المستأجر\","
                                    + "\"path\":\"" + request.getRequestURI() + "\"}"
                    );
                    return;
                }
            } catch (IllegalArgumentException e) {
                // Malformed tenantId param — let the controller handle it as 400
            }
        }

        // Build the authentication object with claims as details
        Map<String, Object> details = new HashMap<>();
        details.put("user_id", claims.getSubject());
        details.put("tenant_id", jwtTenantIdStr);
        details.put("email", claims.get("email", String.class));

        List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_USER")
        );

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
        authentication.setDetails(details);

        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }
}
