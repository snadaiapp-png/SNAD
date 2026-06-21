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
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p>All endpoints are under {@code /api/v1/auth} and are publicly
 * accessible (no Bearer token required for login and refresh).
 * The {@code /me} and {@code /logout} endpoints require authentication.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Login, logout, refresh, and session management.")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final UserRepository userRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final UserRoleGrantRepository roleGrantRepository;
    private final RoleRepository roleRepository;

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
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new access + refresh token pair")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        AuthResponse response = authService.refresh(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke all active refresh tokens for the authenticated user")
    public ResponseEntity<Void> logout(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.noContent().build();
        }

        Map<String, Object> claims = (Map<String, Object>) authentication.getDetails();
        UUID tenantId = UUID.fromString((String) claims.get("tenant_id"));
        UUID userId = UUID.fromString((String) claims.get("user_id"));

        authService.logout(tenantId, userId);
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
}
