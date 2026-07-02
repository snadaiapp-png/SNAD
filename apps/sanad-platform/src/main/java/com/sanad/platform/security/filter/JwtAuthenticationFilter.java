package com.sanad.platform.security.filter;

import com.sanad.platform.audit.service.SecurityTokenFingerprintService;
import com.sanad.platform.security.denial.JwtValidationResult;
import com.sanad.platform.security.denial.SecurityDenialAttributes;
import com.sanad.platform.security.denial.SecurityDenialCategory;
import com.sanad.platform.security.denial.SecurityDenialContext;
import com.sanad.platform.security.denial.SecurityDenialCoordinator;
import com.sanad.platform.security.denial.SessionValidationResult;
import com.sanad.platform.security.service.JwtTokenProvider;
import com.sanad.platform.security.tenant.JwtSessionValidationService;
import com.sanad.platform.security.tenant.JwtSessionValidationService.ValidatedSession;
import com.sanad.platform.security.tenant.JwtSessionValidationService.VerifiedJwtClaims;
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
 * JWT authentication filter with tenant binding, session versioning, and
 * bootstrap-session restriction.
 *
 * <p>Stage 05A.2.9.1 §9 — Every denial path now flows through the
 * {@link SecurityDenialCoordinator}. Direct {@code writeError()} calls
 * are forbidden. Each classified failure produces exactly one audit row
 * (either in {@code platform_security_audit_events} for pre-auth denials
 * or in {@code audit_events} for tenant-verified denials).</p>
 *
 * <p>Failure categories wired (§9):</p>
 * <ul>
 *   <li>No Authorization header → {@link SecurityDenialCategory#MISSING_JWT}</li>
 *   <li>Unreadable / wrong issuer → {@link SecurityDenialCategory#MALFORMED_JWT}</li>
 *   <li>Bad signature → {@link SecurityDenialCategory#INVALID_SIGNATURE}</li>
 *   <li>Expired token → {@link SecurityDenialCategory#EXPIRED_JWT}</li>
 *   <li>Invalid subject UUID → {@link SecurityDenialCategory#INVALID_SUBJECT}</li>
 *   <li>Invalid tenant UUID claim → {@link SecurityDenialCategory#UNVERIFIED_TENANT}</li>
 *   <li>User not found / no active membership → {@link SecurityDenialCategory#UNKNOWN_SESSION}</li>
 *   <li>Session version mismatch / suspended user → {@link SecurityDenialCategory#REVOKED_SESSION}</li>
 *   <li>Archived tenant → {@link SecurityDenialCategory#UNVERIFIED_TENANT}</li>
 *   <li>Tenant selector mismatch → {@link SecurityDenialCategory#TENANT_SELECTOR_MISMATCH}</li>
 *   <li>Rotation required → {@link SecurityDenialCategory#ROTATION_REQUIRED}</li>
 * </ul>
 *
 * <p>The coordinator handles Spring Security's AuthenticationEntryPoint
 * re-entry by checking {@link SecurityDenialAttributes#DENIAL_RECORDED}
 * — exactly one audit row per denial, never zero, never two.</p>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtSessionValidationService sessionValidationService;
    private final SecurityDenialCoordinator denialCoordinator;
    private final SecurityTokenFingerprintService tokenFingerprintService;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                    JwtSessionValidationService sessionValidationService,
                                    SecurityDenialCoordinator denialCoordinator,
                                    SecurityTokenFingerprintService tokenFingerprintService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.sessionValidationService = sessionValidationService;
        this.denialCoordinator = denialCoordinator;
        this.tokenFingerprintService = tokenFingerprintService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            // No Authorization header → MISSING_JWT. Store the denial
            // context as a request attribute and let the chain continue.
            // Spring Security's AuthenticationEntryPoint will fire ONLY
            // for protected endpoints (permitAll endpoints like /login
            // will proceed normally). The entry point reads the stored
            // context and delegates to the coordinator — this way the
            // audit row is recorded exactly once and only for endpoints
            // that actually require authentication.
            request.setAttribute(SecurityDenialAttributes.DENIAL_CONTEXT,
                    SecurityDenialContext.of(
                            SecurityDenialCategory.MISSING_JWT,
                            "SANAD-AUTH-001",
                            401,
                            null));
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        // Compute the fingerprint once — used by every downstream denial
        // path so the audit row can correlate repeated attempts by the
        // same token without storing the raw token.
        String tokenFingerprint = tokenFingerprintService.fingerprint(token);

        // Stage 05A.2.9.1 §6 — Typed JWT validation result. Replaces the
        // old parseAndValidate() -> null pattern that collapsed every
        // Bearer failure to MALFORMED_JWT.
        JwtValidationResult jwtResult = jwtTokenProvider.validateAndClassify(token);
        if (jwtResult instanceof JwtValidationResult.Invalid invalid) {
            // Invalid JWT → store denial context and continue the chain.
            // The AuthenticationEntryPoint will fire for protected
            // endpoints and delegate to the coordinator.
            request.setAttribute(SecurityDenialAttributes.DENIAL_CONTEXT,
                    SecurityDenialContext.of(
                            invalid.reason(),
                            "SANAD-AUTH-001",
                            401,
                            tokenFingerprint));
            filterChain.doFilter(request, response);
            return;
        }

        Claims claims = ((JwtValidationResult.Valid) jwtResult).claims();

        String jwtTenantIdStr = claims.get("tenant_id", String.class);
        UUID jwtTenantId;
        try {
            jwtTenantId = UUID.fromString(jwtTenantIdStr);
        } catch (IllegalArgumentException | NullPointerException e) {
            // tenant_id claim missing or not a UUID → UNVERIFIED_TENANT.
            // Store context and continue chain for AuthenticationEntryPoint.
            request.setAttribute(SecurityDenialAttributes.DENIAL_CONTEXT,
                    SecurityDenialContext.of(
                            SecurityDenialCategory.UNVERIFIED_TENANT,
                            "SANAD-AUTH-001",
                            401,
                            tokenFingerprint));
            filterChain.doFilter(request, response);
            return;
        }

        String requestTenantIdParam = request.getParameter("tenantId");
        if (requestTenantIdParam != null && !requestTenantIdParam.isBlank()) {
            try {
                UUID requestTenantId = UUID.fromString(requestTenantIdParam);
                if (!requestTenantId.equals(jwtTenantId)) {
                    // JWT verified but request tenantId selector differs.
                    // The tenant in the JWT is cryptographically verified,
                    // so this is a tenant-verified denial (post-auth).
                    log.warn("Tenant binding violation: JWT tenantId={} request tenantId={} path={}",
                            jwtTenantId, requestTenantId, request.getRequestURI());
                    UUID jwtUserIdForMismatch = safeParseUuid(claims.getSubject());
                    denialCoordinator.deny(request, response,
                            SecurityDenialContext.of(
                                    SecurityDenialCategory.TENANT_SELECTOR_MISMATCH,
                                    "SANAD-TEN-002",
                                    403,
                                    tokenFingerprint),
                            jwtTenantId,
                            jwtUserIdForMismatch);
                    return;
                }
            } catch (IllegalArgumentException ignored) {
                // The controller validates malformed tenant identifiers
                // as a bad request — fall through to controller path.
            }
        }

        UUID jwtUserId;
        try {
            jwtUserId = UUID.fromString(claims.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            // Subject claim missing or not a UUID → INVALID_SUBJECT.
            // Store context and continue chain for AuthenticationEntryPoint.
            request.setAttribute(SecurityDenialAttributes.DENIAL_CONTEXT,
                    SecurityDenialContext.of(
                            SecurityDenialCategory.INVALID_SUBJECT,
                            "SANAD-AUTH-001",
                            401,
                            tokenFingerprint));
            filterChain.doFilter(request, response);
            return;
        }

        Object jwtVersionObj = claims.get(JwtTokenProvider.SESSION_VERSION_CLAIM);
        long jwtSessionVersion = 0L;
        if (jwtVersionObj instanceof Number) {
            jwtSessionVersion = ((Number) jwtVersionObj).longValue();
        }

        String tokenId = claims.getId();

        VerifiedJwtClaims verifiedClaims = new VerifiedJwtClaims(
                jwtTenantId,
                jwtUserId,
                tokenId,
                claims.get("email", String.class),
                jwtSessionVersion,
                false
        );

        // Stage 05A.2.9.1 §7 — Typed session validation result.
        SessionValidationResult sessionResult =
                sessionValidationService.validateAndClassify(verifiedClaims);
        if (sessionResult instanceof SessionValidationResult.Invalid invalid) {
            log.debug("Session validation failed: userId={} tenantId={} category={}",
                    jwtUserId, jwtTenantId, invalid.reason());
            // Store context and continue chain for AuthenticationEntryPoint.
            request.setAttribute(SecurityDenialAttributes.DENIAL_CONTEXT,
                    SecurityDenialContext.of(
                            invalid.reason(),
                            "SANAD-AUTH-001",
                            401,
                            tokenFingerprint));
            filterChain.doFilter(request, response);
            return;
        }

        ValidatedSession session = ((SessionValidationResult.Valid) sessionResult).session();

        boolean rotationRequired = Boolean.TRUE.equals(
                claims.get(JwtTokenProvider.ROTATION_REQUIRED_CLAIM, Boolean.class));
        if (rotationRequired && request.getRequestURI().startsWith("/api/")
                && !isRotationSafeEndpoint(request.getRequestURI())) {
            // Valid session but credential rotation required before API
            // use → tenant-verified denial.
            denialCoordinator.deny(request, response,
                    SecurityDenialContext.of(
                            SecurityDenialCategory.ROTATION_REQUIRED,
                            "SANAD-ROT-001",
                            403,
                            tokenFingerprint),
                    jwtTenantId,
                    jwtUserId);
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

    private static UUID safeParseUuid(String s) {
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
