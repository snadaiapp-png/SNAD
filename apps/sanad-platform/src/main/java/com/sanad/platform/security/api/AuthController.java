package com.sanad.platform.security.api;

import com.sanad.platform.access.grant.UserGrantStatus;
import com.sanad.platform.security.authorization.RequireCapability;
import com.sanad.platform.access.grant.UserRoleGrant;
import com.sanad.platform.access.grant.UserRoleGrantRepository;
import com.sanad.platform.access.role.Role;
import com.sanad.platform.access.role.RoleRepository;
import com.sanad.platform.organization.membership.domain.OrganizationMembership;
import com.sanad.platform.organization.membership.repository.OrganizationMembershipRepository;
import com.sanad.platform.security.dto.AdminResetPasswordRequest;
import com.sanad.platform.security.dto.AuthResponse;
import com.sanad.platform.security.dto.ChangeCredentialRequest;
import com.sanad.platform.security.dto.ForgotPasswordRequest;
import com.sanad.platform.security.dto.LoginRequest;
import com.sanad.platform.security.dto.MeResponse;
import com.sanad.platform.security.dto.RefreshRequest;
import com.sanad.platform.security.dto.ResetPasswordRequest;
import com.sanad.platform.security.notification.PasswordRecoveryNotificationCoordinator;
import com.sanad.platform.security.service.AuthService;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Authentication API consumed by the trusted same-origin Next.js BFF. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Login, logout, refresh, password recovery, and session management.")
public class AuthController {

    public static final String REFRESH_TOKEN_HEADER = "X-SANAD-Refresh-Token";
    private static final String LOCAL_REFRESH_COOKIE = "sanad_refresh";
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final UserRepository userRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final UserRoleGrantRepository roleGrantRepository;
    private final RoleRepository roleRepository;
    private final Environment environment;
    private final PasswordRecoveryNotificationCoordinator recoveryNotifications;

    public AuthController(
            AuthService authService,
            UserRepository userRepository,
            OrganizationMembershipRepository membershipRepository,
            UserRoleGrantRepository roleGrantRepository,
            RoleRepository roleRepository,
            Environment environment,
            PasswordRecoveryNotificationCoordinator recoveryNotifications
    ) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.roleGrantRepository = roleGrantRepository;
        this.roleRepository = roleRepository;
        this.environment = environment;
        this.recoveryNotifications = recoveryNotifications;
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate and return the complete post-login bootstrap")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return authResponse(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate a BFF-held refresh token and return a complete session bootstrap")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request, @RequestBody(required = false) RefreshRequest body) {
        String refreshToken = request.getHeader(REFRESH_TOKEN_HEADER);
        if ((refreshToken == null || refreshToken.isBlank()) && isLocalOrDev() && body != null) {
            refreshToken = body.getRefreshToken();
        }
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(401).header(HttpHeaders.CACHE_CONTROL, "no-store").build();
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
        return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    @PostMapping("/change-credential")
    @Operation(summary = "Rotate the authenticated account credential and terminate refresh sessions")
    public ResponseEntity<Void> changeCredential(Authentication authentication, @Valid @RequestBody ChangeCredentialRequest request) {
        PrincipalIds principal = principal(authentication);
        if (principal == null) {
            return ResponseEntity.status(401).header(HttpHeaders.CACHE_CONTROL, "no-store").build();
        }
        authService.changeCredential(principal.tenantId(), principal.userId(), request);
        try {
            recoveryNotifications.deliverCredentialChangeConfirmation(principal.tenantId(), principal.userId(), "ar");
        } catch (RuntimeException exception) {
            log.error("Credential-change confirmation delivery failed", exception);
        }
        return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    @GetMapping("/me")
    @Operation(summary = "Get the authenticated user's identity, memberships, and role grants")
    public ResponseEntity<MeResponse> me(Authentication authentication) {
        PrincipalIds principal = principal(authentication);
        if (principal == null) {
            return ResponseEntity.status(401).header(HttpHeaders.CACHE_CONTROL, "no-store").build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(buildMeResponse(principal.tenantId(), principal.userId()));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a single-use reset link. Always returns 200 to prevent account enumeration.")
    public ResponseEntity<Map<String, Object>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        String rawToken = authService.initiatePasswordReset(request, httpRequest.getRemoteAddr());
        try {
            recoveryNotifications.deliverRequestedReset(
                    request.getEmail(), rawToken, httpRequest.getLocale().getLanguage());
        } catch (RuntimeException exception) {
            log.error("Password recovery delivery failed; token revoked", exception);
        }

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("message", "إذا كان البريد الإلكتروني مسجلاً لدينا، فستتلقى رابط إعادة تعيين كلمة المرور.");
        if (isLocalOrDev() && rawToken != null) {
            response.put("token", rawToken);
        }
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Set a new password using a valid single-use reset token")
    public ResponseEntity<Map<String, Object>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        authService.completePasswordReset(request);
        try {
            recoveryNotifications.deliverResetConfirmation(request.getToken(), httpRequest.getLocale().getLanguage());
        } catch (RuntimeException exception) {
            log.error("Password reset confirmation delivery failed", exception);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Map.of("message", "تم إعادة تعيين كلمة المرور بنجاح. يمكنك الآن تسجيل الدخول بكلمة المرور الجديدة."));
    }

    @RequireCapability("USER.WRITE")
    @PostMapping("/admin-reset-password/{userId}")
    @Operation(summary = "Send an administrator-issued, single-use set-password link")
    public ResponseEntity<Map<String, Object>> adminResetPassword(
            Authentication authentication,
            @PathVariable UUID userId,
            @Valid @RequestBody AdminResetPasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        PrincipalIds principal = principal(authentication);
        if (principal == null) {
            return ResponseEntity.status(401).header(HttpHeaders.CACHE_CONTROL, "no-store").build();
        }
        String rawToken = recoveryNotifications.createAdministrativeResetLink(
                principal.tenantId(), userId, request.getLocale(), httpRequest.getRemoteAddr());

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("message", "تم إرسال رابط أحادي الاستخدام لإعداد كلمة مرور جديدة.");
        if (isLocalOrDev()) {
            response.put("token", rawToken);
        }
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
    }

    private ResponseEntity<AuthResponse> authResponse(AuthResponse response) {
        enrichBootstrap(response);
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

    private void enrichBootstrap(AuthResponse response) {
        AuthResponse.AuthUser authUser = response.getUser();
        if (authUser == null || authUser.getTenantId() == null || authUser.getId() == null) {
            throw new IllegalStateException("Authentication response is missing the user context");
        }

        MeResponse profile = buildMeResponse(authUser.getTenantId(), authUser.getId());
        UUID defaultOrganizationId = profile.getMemberships().stream()
                .filter(membership -> "ACTIVE".equals(membership.getStatus()))
                .map(MeResponse.MembershipSummary::getOrganizationId)
                .sorted()
                .findFirst()
                .orElse(null);
        boolean administrator = profile.getRoleGrants().stream()
                .anyMatch(grant -> "ACTIVE".equals(grant.getStatus()) && "ADMIN".equals(grant.getRoleCode()));

        List<String> destinations = new ArrayList<>();
        destinations.add("/workspace");
        destinations.add("/crm");
        destinations.add("/crm/command-center");
        if (administrator) {
            destinations.add("/control-plane");
        }

        response.setLastLoginAt(profile.getLastLoginAt());
        response.setCredentialRotationRequired(profile.isCredentialRotationRequired());
        response.setMemberships(profile.getMemberships());
        response.setEffectiveRoleGrants(profile.getRoleGrants());
        response.setDefaultOrganizationId(defaultOrganizationId);
        response.setAvailableDestinations(destinations);
        response.setDefaultDestination(administrator ? "/control-plane" : "/crm");
        response.setTenantContext(new AuthResponse.TenantContext(authUser.getTenantId(), defaultOrganizationId));
    }

    private MeResponse buildMeResponse(UUID tenantId, UUID userId) {
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

        List<OrganizationMembership> memberships = membershipRepository.findByTenantIdAndUserId(tenantId, userId);
        response.setMemberships(memberships.stream()
                .map(membership -> new MeResponse.MembershipSummary(
                        membership.getId(), membership.getOrganizationId(), membership.getStatus().name()))
                .collect(Collectors.toList()));

        List<UserRoleGrant> grants = roleGrantRepository.findByTenantIdAndUserIdAndStatus(
                tenantId, userId, UserGrantStatus.ACTIVE);
        response.setRoleGrants(grants.stream().map(grant -> {
            String roleCode = roleRepository.findByTenantIdAndId(tenantId, grant.getRoleId())
                    .map(Role::getCode).orElse("UNKNOWN");
            return new MeResponse.RoleGrantSummary(
                    grant.getId(), grant.getRoleId(), roleCode, grant.getOrganizationId(), grant.getStatus().name());
        }).collect(Collectors.toList()));
        return response;
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
