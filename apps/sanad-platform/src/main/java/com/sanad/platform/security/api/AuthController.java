package com.sanad.platform.security.api;

import com.sanad.platform.access.grant.UserGrantStatus;
import com.sanad.platform.access.grant.UserRoleGrant;
import com.sanad.platform.access.grant.UserRoleGrantRepository;
import com.sanad.platform.access.role.Role;
import com.sanad.platform.access.role.RoleRepository;
import com.sanad.platform.organization.membership.domain.OrganizationMembership;
import com.sanad.platform.organization.membership.repository.OrganizationMembershipRepository;
import com.sanad.platform.security.dto.AuthResponse;
import com.sanad.platform.security.dto.LoginRequest;
import com.sanad.platform.security.dto.MeResponse;
import com.sanad.platform.security.dto.RefreshRequest;
import com.sanad.platform.security.service.AuthService;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for authentication endpoints.
 *
 * <p>Implements a BFF (Backend-For-Frontend) pattern for refresh tokens:
 * the refresh token is set as an HttpOnly cookie (not exposed to JavaScript)
 * and the access token is returned in the JSON response body (for the
 * frontend to use in Authorization headers).</p>
 *
 * <p>For backward compatibility, the refresh token is ALSO returned in the
 * JSON response body. The frontend can choose to use either the cookie
 * (recommended) or the body value.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Login, logout, refresh, and session management.")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final String REFRESH_COOKIE_NAME = "sanad_refresh";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    private final AuthService authService;
    private final UserRepository userRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final UserRoleGrantRepository roleGrantRepository;
    private final RoleRepository roleRepository;

    @Value("${sanad.security.cookie.secure:true}")
    private boolean cookieSecure;

    @Value("${sanad.security.cookie.same-site:lax}")
    private String cookieSameSite;

    @Value("${sanad.security.cookie.domain:}")
    private String cookieDomain;

    public AuthController(
            AuthService authService,
            UserRepository userRepository,
            OrganizationMembershipRepository membershipRepository,
            UserRoleGrantRepository roleGrantRepository,
            RoleRepository roleRepository
    ) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.roleGrantRepository = roleGrantRepository;
        this.roleRepository = roleRepository;
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate and obtain access + refresh tokens")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        AuthResponse authResponse = authService.login(request);
        setRefreshCookie(response, authResponse.getRefreshToken());
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new access + refresh token pair")
    public ResponseEntity<AuthResponse> refresh(
            @RequestBody(required = false) RefreshRequest body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        // Try cookie first (BFF pattern), fall back to body (backward compatibility)
        String refreshToken = extractRefreshToken(request, body);
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(401).build();
        }

        AuthResponse authResponse = authService.refresh(new RefreshRequest(refreshToken));
        setRefreshCookie(response, authResponse.getRefreshToken());
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke all active refresh tokens for the authenticated user")
    public ResponseEntity<Void> logout(
            Authentication authentication,
            HttpServletResponse response
    ) {
        if (authentication != null) {
            Map<String, Object> claims = (Map<String, Object>) authentication.getDetails();
            UUID tenantId = UUID.fromString((String) claims.get("tenant_id"));
            UUID userId = UUID.fromString((String) claims.get("user_id"));
            authService.logout(tenantId, userId);
        }

        // Clear the refresh cookie
        clearRefreshCookie(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Get the authenticated user's identity, memberships, and role grants")
    public ResponseEntity<MeResponse> me(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }

        Map<String, Object> claims = (Map<String, Object>) authentication.getDetails();
        UUID tenantId = UUID.fromString((String) claims.get("tenant_id"));
        UUID userId = UUID.fromString((String) claims.get("user_id"));

        User user = userRepository.findByTenantIdAndId(tenantId, userId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        MeResponse response = new MeResponse();
        response.setId(user.getId());
        response.setTenantId(user.getTenantId());
        response.setEmail(user.getEmail());
        response.setDisplayName(user.getDisplayName());
        response.setStatus(user.getStatus().name());
        response.setLastLoginAt(user.getLastLoginAt());

        // Load memberships
        List<OrganizationMembership> memberships = membershipRepository.findByTenantIdAndUserId(tenantId, userId);
        response.setMemberships(memberships.stream()
                .map(m -> new MeResponse.MembershipSummary(m.getId(), m.getOrganizationId(), m.getStatus().name()))
                .collect(Collectors.toList()));

        // Load role grants with role codes
        List<UserRoleGrant> grants = roleGrantRepository.findByTenantIdAndUserIdAndStatus(tenantId, userId, UserGrantStatus.ACTIVE);
        List<MeResponse.RoleGrantSummary> grantSummaries = grants.stream().map(grant -> {
            String roleCode = roleRepository.findByTenantIdAndId(tenantId, grant.getRoleId())
                    .map(Role::getCode)
                    .orElse("UNKNOWN");
            return new MeResponse.RoleGrantSummary(
                    grant.getId(),
                    grant.getRoleId(),
                    roleCode,
                    grant.getOrganizationId(),
                    grant.getStatus().name()
            );
        }).collect(Collectors.toList());
        response.setRoleGrants(grantSummaries);

        return ResponseEntity.ok(response);
    }

    // ------------------------------------------------------------
    // Cookie helpers (BFF pattern for refresh tokens)
    // ------------------------------------------------------------

    private void setRefreshCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie(REFRESH_COOKIE_NAME, refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath(REFRESH_COOKIE_PATH);
        cookie.setMaxAge(60 * 60 * 24 * 7); // 7 days
        if (cookieDomain != null && !cookieDomain.isBlank()) {
            cookie.setDomain(cookieDomain);
        }
        // SameSite is set via header because Cookie API doesn't support it directly
        response.addCookie(cookie);
        // Add SameSite attribute via header
        String sameSiteHeader = String.format("%s=%s; Path=%s; HttpOnly; %s; SameSite=%s%s",
                REFRESH_COOKIE_NAME,
                refreshToken,
                REFRESH_COOKIE_PATH,
                cookieSecure ? "Secure" : "",
                cookieSameSite,
                (cookieDomain != null && !cookieDomain.isBlank()) ? "; Domain=" + cookieDomain : ""
        );
        response.setHeader("Set-Cookie", sameSiteHeader);
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(REFRESH_COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath(REFRESH_COOKIE_PATH);
        cookie.setMaxAge(0); // Delete immediately
        response.addCookie(cookie);
        response.setHeader("Set-Cookie", String.format("%s=; Path=%s; HttpOnly; %s; SameSite=%s; Max-Age=0",
                REFRESH_COOKIE_NAME, REFRESH_COOKIE_PATH, cookieSecure ? "Secure" : "", cookieSameSite));
    }

    private String extractRefreshToken(HttpServletRequest request, RefreshRequest body) {
        // Try cookie first
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (REFRESH_COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        // Fall back to body
        if (body != null && body.getRefreshToken() != null && !body.getRefreshToken().isBlank()) {
            return body.getRefreshToken();
        }
        return null;
    }
}
