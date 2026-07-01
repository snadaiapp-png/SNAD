package com.sanad.platform.security.filter;

import com.sanad.platform.security.service.JwtTokenProvider;
import com.sanad.platform.user.repository.UserRepository;
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

/** JWT authentication filter with tenant binding, session versioning, and bootstrap-session restriction. */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserRepository userRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
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

        String jwtTenantIdStr = claims.get("tenant_id", String.class);
        UUID jwtTenantId;
        try {
            jwtTenantId = UUID.fromString(jwtTenantIdStr);
        } catch (IllegalArgumentException | NullPointerException e) {
            writeError(response, request, 401, "Unauthorized", "رمز المصادقة غير صالح");
            return;
        }

        String requestTenantIdParam = request.getParameter("tenantId");
        if (requestTenantIdParam != null && !requestTenantIdParam.isBlank()) {
            try {
                UUID requestTenantId = UUID.fromString(requestTenantIdParam);
                if (!requestTenantId.equals(jwtTenantId)) {
                    log.warn("Tenant binding violation: JWT tenantId={} request tenantId={} path={}",
                            jwtTenantId, requestTenantId, request.getRequestURI());
                    writeError(response, request, 403, "Forbidden",
                            "تم رفض الوصول: تعارض في هوية المستأجر");
                    return;
                }
            } catch (IllegalArgumentException ignored) {
                // The controller validates malformed tenant identifiers as a bad request.
            }
        }

        // --- Session version validation (DEFECT-001) ---
        // Compare the session_version in the JWT against the current DB value.
        // If they differ, the token was issued before a logout/password-change/reset
        // and must be rejected immediately.
        UUID jwtUserId;
        try {
            jwtUserId = UUID.fromString(claims.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            writeError(response, request, 401, "Unauthorized", "رمز المصادقة غير صالح");
            return;
        }

        Long currentSessionVersion = userRepository.findSessionVersionByTenantIdAndId(jwtTenantId, jwtUserId);
        if (currentSessionVersion == null) {
            // User no longer exists — treat as unauthenticated
            log.debug("Session version check failed: user not found for userId={} tenantId={}", jwtUserId, jwtTenantId);
            writeError(response, request, 401, "Unauthorized", "رمز المصادقة غير صالح");
            return;
        }

        Object jwtVersionObj = claims.get(JwtTokenProvider.SESSION_VERSION_CLAIM);
        long jwtSessionVersion = 0L;
        if (jwtVersionObj instanceof Number) {
            jwtSessionVersion = ((Number) jwtVersionObj).longValue();
        }

        if (jwtSessionVersion != currentSessionVersion) {
            log.debug("Session version mismatch: JWT={} DB={} userId={} path={}",
                    jwtSessionVersion, currentSessionVersion, jwtUserId, request.getRequestURI());
            writeError(response, request, 401, "Unauthorized",
                    "تم إبطال الجلسة؛ يرجى تسجيل الدخول مرة أخرى");
            return;
        }

        boolean rotationRequired = Boolean.TRUE.equals(
                claims.get(JwtTokenProvider.ROTATION_REQUIRED_CLAIM, Boolean.class));
        if (rotationRequired && request.getRequestURI().startsWith("/api/")
                && !isRotationSafeEndpoint(request.getRequestURI())) {
            writeError(response, request, 403, "Forbidden",
                    "يجب تغيير بيانات الاعتماد قبل استخدام واجهات المنصة");
            return;
        }

        Map<String, Object> details = new HashMap<>();
        details.put("user_id", claims.getSubject());
        details.put("tenant_id", jwtTenantIdStr);
        details.put("email", claims.get("email", String.class));
        details.put("jti", claims.getId());
        details.put(JwtTokenProvider.ROTATION_REQUIRED_CLAIM, rotationRequired);
        details.put(JwtTokenProvider.SESSION_VERSION_CLAIM, jwtSessionVersion);

        List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_USER"));
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
        authentication.setDetails(details);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private boolean isRotationSafeEndpoint(String uri) {
        return "/api/v1/auth/me".equals(uri)
                || "/api/v1/auth/change-credential".equals(uri)
                || "/api/v1/auth/logout".equals(uri);
    }

    private void writeError(
            HttpServletResponse response,
            HttpServletRequest request,
            int status,
            String error,
            String message
    ) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"timestamp\":\"" + java.time.Instant.now() + "\","
                        + "\"status\":" + status + ","
                        + "\"error\":\"" + error + "\","
                        + "\"message\":\"" + message + "\","
                        + "\"path\":\"" + request.getRequestURI() + "\"}");
    }
}
