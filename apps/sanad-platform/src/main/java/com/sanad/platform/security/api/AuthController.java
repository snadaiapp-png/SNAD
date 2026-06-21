package com.sanad.platform.security.api;

import com.sanad.platform.access.grant.UserGrantStatus;
import com.sanad.platform.access.grant.UserRoleGrant;
import com.sanad.platform.access.grant.UserRoleGrantRepository;
import com.sanad.platform.access.role.Role;
import com.sanad.platform.access.role.RoleRepository;
import com.sanad.platform.organization.membership.domain.OrganizationMembership;
import com.sanad.platform.organization.membership.repository.OrganizationMembershipRepository;
import com.sanad.platform.security.dto.AuthResponse;
import com.sanad.platform.security.dto.ChangeCredentialRequest;
import com.sanad.platform.security.dto.LoginRequest;
import com.sanad.platform.security.dto.MeResponse;
import com.sanad.platform.security.dto.RefreshRequest;
import com.sanad.platform.security.service.AuthService;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
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

/** Authentication API consumed by the trusted same-origin Next.js BFF. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Login, logout, refresh, and session management.")
public class AuthController {

    public static final String REFRESH_TOKEN_HEADER = "X-SANAD-Refresh-Token";
    private static final String LOCAL_REFRESH_COOKIE = "sanad_refresh";

    private final AuthService authService;
    private final UserRepository userRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final UserRoleGrantRepository roleGrantRepository;
    private final RoleRepository roleRepository;
    private final Environment environment;

    public AuthController(
            AuthService authService,
            UserRepository userRepository,
            OrganizationMembershipRepository membershipRepository,
            UserRoleGrantRepository roleGrantRepository,
            RoleRepository roleRepository,
            Environment environment
    ) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.roleGrantRepository = roleGrantRepository;
        this.roleRepository = roleRepository;
        this.environment = environment;
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate and return access data to the trusted BFF")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return authResponse(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate a BFF-held refresh token")
    public ResponseEntity<AuthResponse> refresh(
            HttpServletRequest request,
            @RequestBody(required = false) RefreshRequest body
    ) {
        String refreshToken = request.getHeader(REFRESH_TOKEN_HEADER);
        if ((refreshToken == null || refreshToken.isBlank()) && isLocalOrDev() && body != null) {
            refreshToken = body.getRefreshToken();
        }
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(401)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .build();
        }
        return authResponse(authService.refresh(new RefreshRequest(refreshToken)));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke all active refresh tokens for the authenticated user")
    public ResponseEntity<Void> logout(Authentication authentication) {
        PrincipalIds principal = principal(authentication);
        if (principal != null) {
            authService.logout(principal.tenantId(), principal.userId());
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    @PostMapping("/change-credential")
    @Operation(summary = "Rotate the authenticated account credential and terminate refresh sessions")
    public ResponseEntity<Void> changeCredential(
            Authentication authentication,
            @Valid @RequestBody ChangeCredentialRequest request
    ) {
        PrincipalIds principal = principal(authentication);
        if (principal == null) {
            return ResponseEntity.status(401)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .build();
        }
        authService.changeCredential(principal.tenantId(), principal.userId(), request);
        return ResponseEntity.noContent()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    @GetMapping("/me")
    @Operation(summary = "Get the authenticated user's identity, memberships, and role grants")
    public ResponseEntity<MeResponse> me(Authentication authentication) {
        PrincipalIds principal = principal(authentication);
        if (principal == null) {
            return ResponseEntity.status(401)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .build();
        }

        UUID tenantId = principal.tenantId();
        UUID userId = principal.userId();
        User user = userRepository.findByTenantIdAndId(tenantId, userId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        MeResponse response = new MeResponse();
        response.setId(user.getId());
        response.setTenantId(user.getTenantId());
        response.setEmail(user.getEmail());
        response.setDisplayName(user.getDisplayName());
        response.setStatus(user.getStatus().name());
        response.setLastLoginAt(user.getLastLoginAt());
        response.setCredentialRotationRequired(user.isMustChangePassword());

        List<OrganizationMembership> memberships =
                membershipRepository.findByTenantIdAndUserId(tenantId, userId);
        response.setMemberships(memberships.stream()
                .map(membership -> new MeResponse.MembershipSummary(
                        membership.getId(),
                        membership.getOrganizationId(),
                        membership.getStatus().name()))
                .collect(Collectors.toList()));

        List<UserRoleGrant> grants = roleGrantRepository.findByTenantIdAndUserIdAndStatus(
                tenantId, userId, UserGrantStatus.ACTIVE);
        response.setRoleGrants(grants.stream().map(grant -> {
            String roleCode = roleRepository.findByTenantIdAndId(tenantId, grant.getRoleId())
                    .map(Role::getCode)
                    .orElse("UNKNOWN");
            return new MeResponse.RoleGrantSummary(
                    grant.getId(),
                    grant.getRoleId(),
                    roleCode,
                    grant.getOrganizationId(),
                    grant.getStatus().name());
        }).collect(Collectors.toList()));

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(response);
    }

    private ResponseEntity<AuthResponse> authResponse(AuthResponse response) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header(REFRESH_TOKEN_HEADER, response.getRefreshToken())
                .header(HttpHeaders.CACHE_CONTROL, "no-store");
        if (isLocalOrDev()) {
            builder.header(HttpHeaders.SET_COOKIE,
                    LOCAL_REFRESH_COOKIE + "=" + response.getRefreshToken()
                            + "; Path=/api/v1/auth; HttpOnly; SameSite=Strict");
        }
        return builder.body(response);
    }

    @SuppressWarnings("unchecked")
    private PrincipalIds principal(Authentication authentication) {
        if (authentication == null || !(authentication.getDetails() instanceof Map<?, ?>)) {
            return null;
        }
        Map<String, Object> claims = (Map<String, Object>) authentication.getDetails();
        return new PrincipalIds(
                UUID.fromString((String) claims.get("tenant_id")),
                UUID.fromString((String) claims.get("user_id")));
    }

    private boolean isLocalOrDev() {
        return environment.acceptsProfiles(Profiles.of("local", "dev"));
    }

    private record PrincipalIds(UUID tenantId, UUID userId) {
    }
}
