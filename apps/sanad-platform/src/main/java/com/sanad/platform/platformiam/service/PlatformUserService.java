package com.sanad.platform.platformiam.service;

import com.sanad.platform.access.AccessConflictException;
import com.sanad.platform.access.AccessResourceNotFoundException;
import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.platformiam.domain.PlatformMembership;
import com.sanad.platform.platformiam.domain.PlatformMembershipStatus;
import com.sanad.platform.platformiam.dto.CreatePlatformUserRequest;
import com.sanad.platform.platformiam.dto.PlatformSessionSummaryResponse;
import com.sanad.platform.platformiam.dto.PlatformUserResponse;
import com.sanad.platform.platformiam.dto.UpdatePlatformUserRequest;
import com.sanad.platform.platformiam.repository.PlatformMembershipRepository;
import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import com.sanad.platform.security.service.AuthService;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class PlatformUserService {

    private final ControlPlaneAccessGuard controlPlaneAccessGuard;
    private final UserRepository users;
    private final PlatformMembershipRepository memberships;
    private final PlatformOwnerSafetyService ownerSafety;
    private final AuthService authService;
    private final PlatformAuditService audit;
    private final TenantRlsTransactionContext rlsContext;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public PlatformUserService(
            ControlPlaneAccessGuard controlPlaneAccessGuard,
            UserRepository users,
            PlatformMembershipRepository memberships,
            PlatformOwnerSafetyService ownerSafety,
            AuthService authService,
            PlatformAuditService audit,
            TenantRlsTransactionContext rlsContext) {
        this(controlPlaneAccessGuard, users, memberships, ownerSafety, authService, audit,
                rlsContext, Clock.systemUTC());
    }

    public PlatformUserService(
            ControlPlaneAccessGuard controlPlaneAccessGuard,
            UserRepository users,
            PlatformMembershipRepository memberships,
            PlatformOwnerSafetyService ownerSafety,
            AuthService authService,
            PlatformAuditService audit,
            TenantRlsTransactionContext rlsContext,
            Clock clock) {
        this.controlPlaneAccessGuard = Objects.requireNonNull(controlPlaneAccessGuard, "controlPlaneAccessGuard");
        this.users = Objects.requireNonNull(users, "users");
        this.memberships = Objects.requireNonNull(memberships, "memberships");
        this.ownerSafety = Objects.requireNonNull(ownerSafety, "ownerSafety");
        this.authService = Objects.requireNonNull(authService, "authService");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.rlsContext = Objects.requireNonNull(rlsContext, "rlsContext");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional(readOnly = true)
    public List<PlatformUserResponse> list(Authentication actor) {
        UUID tenantId = controlTenant(actor);
        return memberships.findByControlTenantId(tenantId).stream()
                .map(membership -> users.findByTenantIdAndId(tenantId, membership.userId())
                        .map(user -> response(user, membership))
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public PlatformUserResponse get(Authentication actor, UUID userId) {
        UUID tenantId = controlTenant(actor);
        return response(requireUser(tenantId, userId), requireMembership(tenantId, userId));
    }

    @Transactional
    public PlatformUserResponse createPlatformUser(Authentication actor, CreatePlatformUserRequest request) {
        Objects.requireNonNull(request, "request");
        UUID tenantId = controlTenant(actor);
        UUID actorId = actorUserId(actor);
        String email = normalizeEmail(request.email());
        if (email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }

        User user = users.findByTenantIdAndEmail(tenantId, email).orElseGet(() ->
                users.saveAndFlush(new User(tenantId, email, blankToNull(request.displayName()), UserStatus.INVITED)));

        if (memberships.findByControlTenantIdAndUserId(tenantId, user.getId()).isPresent()) {
            throw new AccessConflictException("Platform membership already exists");
        }

        Instant now = clock.instant();
        PlatformMembership membership = memberships.save(new PlatformMembership(
                UUID.randomUUID(), tenantId, user.getId(), PlatformMembershipStatus.INVITED,
                now, null, null, null, null,
                actorId, actorId, null, now, now));
        PlatformUserResponse result = response(user, membership);
        audit.success(actor, tenantId, "PLATFORM_USER_CREATED", "PLATFORM_USER",
                user.getId().toString(), null, null, result);
        return result;
    }

    @Transactional
    public PlatformUserResponse update(Authentication actor, UUID userId, UpdatePlatformUserRequest request) {
        Objects.requireNonNull(request, "request");
        UUID tenantId = controlTenant(actor);
        User user = requireUser(tenantId, userId);
        PlatformMembership membership = requireMembership(tenantId, userId);
        PlatformUserResponse before = response(user, membership);

        if (request.email() != null && !request.email().isBlank()) {
            String email = normalizeEmail(request.email());
            users.findByTenantIdAndEmail(tenantId, email)
                    .filter(other -> !other.getId().equals(userId))
                    .ifPresent(other -> { throw new AccessConflictException("Email already exists in control tenant"); });
            user.setEmail(email);
        }
        if (request.displayName() != null) {
            user.setDisplayName(blankToNull(request.displayName()));
        }
        user = users.save(user);
        PlatformUserResponse after = response(user, membership);
        audit.success(actor, tenantId, "PLATFORM_USER_UPDATED", "PLATFORM_USER",
                userId.toString(), null, before, after);
        return after;
    }

    @Transactional
    public PlatformUserResponse activate(Authentication actor, UUID userId, String reason) {
        UUID tenantId = controlTenant(actor);
        User user = requireUser(tenantId, userId);
        PlatformMembership current = requireMembership(tenantId, userId);
        if (current.status() == PlatformMembershipStatus.DISABLED
                || (current.status() != PlatformMembershipStatus.INVITED
                && current.status() != PlatformMembershipStatus.SUSPENDED
                && current.status() != PlatformMembershipStatus.LOCKED)) {
            throw new AccessConflictException("Invalid platform membership activation transition");
        }
        if (user.getStatus() == UserStatus.INVITED) {
            user.setStatus(UserStatus.ACTIVE);
            user = users.save(user);
        }
        PlatformMembership saved = saveTransition(current, PlatformMembershipStatus.ACTIVE,
                blankToNull(reason), actorUserId(actor));
        PlatformUserResponse result = response(user, saved);
        audit.success(actor, tenantId, "PLATFORM_USER_ACTIVATED", "PLATFORM_USER",
                userId.toString(), blankToNull(reason), current, saved);
        return result;
    }

    @Transactional
    public PlatformUserResponse suspend(Authentication actor, UUID userId, String reason) {
        return sensitiveTransition(actor, userId, PlatformMembershipStatus.SUSPENDED,
                requireReason(reason), "PLATFORM_USER_SUSPENDED");
    }

    @Transactional
    public PlatformUserResponse lock(Authentication actor, UUID userId, String reason) {
        return sensitiveTransition(actor, userId, PlatformMembershipStatus.LOCKED,
                requireReason(reason), "PLATFORM_USER_LOCKED");
    }

    @Transactional
    public PlatformUserResponse disable(Authentication actor, UUID userId, String reason) {
        return sensitiveTransition(actor, userId, PlatformMembershipStatus.DISABLED,
                requireReason(reason), "PLATFORM_USER_DISABLED");
    }

    @Transactional(readOnly = true)
    public PlatformSessionSummaryResponse sessions(Authentication actor, UUID userId) {
        UUID tenantId = controlTenant(actor);
        requireMembership(tenantId, userId);
        User user = requireUser(tenantId, userId);
        return new PlatformSessionSummaryResponse(userId, user.getSessionVersion(), user.getLastLoginAt());
    }

    @Transactional
    public PlatformSessionSummaryResponse revokeSessions(Authentication actor, UUID userId, String reason) {
        UUID tenantId = controlTenant(actor);
        requireMembership(tenantId, userId);
        requireUser(tenantId, userId);
        String safeReason = requireReason(reason);
        authService.logout(tenantId, userId);
        PlatformSessionSummaryResponse result = sessions(actor, userId);
        audit.success(actor, tenantId, "PLATFORM_SESSION_REVOKED", "PLATFORM_USER",
                userId.toString(), safeReason, null, result);
        return result;
    }

    private PlatformUserResponse sensitiveTransition(
            Authentication actor,
            UUID userId,
            PlatformMembershipStatus target,
            String reason,
            String action) {
        UUID tenantId = controlTenant(actor);
        User user = requireUser(tenantId, userId);
        PlatformMembership current = requireMembership(tenantId, userId);
        if (target == PlatformMembershipStatus.DISABLED) {
            if (current.status() != PlatformMembershipStatus.ACTIVE
                    && current.status() != PlatformMembershipStatus.SUSPENDED
                    && current.status() != PlatformMembershipStatus.LOCKED) {
                throw new AccessConflictException("Invalid platform membership disable transition");
            }
        } else if (current.status() != PlatformMembershipStatus.ACTIVE) {
            throw new AccessConflictException("Invalid platform membership security transition");
        }

        ownerSafety.assertMayDeactivateMembership(tenantId, userId);
        PlatformMembership saved = saveTransition(current, target, reason, actorUserId(actor));
        authService.logout(tenantId, userId);
        PlatformUserResponse result = response(user, saved);
        audit.success(actor, tenantId, action, "PLATFORM_USER", userId.toString(),
                reason, current, saved);
        return result;
    }

    private PlatformMembership saveTransition(
            PlatformMembership current,
            PlatformMembershipStatus target,
            String reason,
            UUID actorId) {
        Instant now = clock.instant();
        return memberships.save(new PlatformMembership(
                current.id(), current.controlTenantId(), current.userId(), target,
                current.invitedAt(),
                target == PlatformMembershipStatus.ACTIVE ? now : current.activatedAt(),
                target == PlatformMembershipStatus.SUSPENDED ? now : current.suspendedAt(),
                target == PlatformMembershipStatus.LOCKED ? now : current.lockedAt(),
                target == PlatformMembershipStatus.DISABLED ? now : current.disabledAt(),
                current.createdBy(), actorId, reason, current.createdAt(), now));
    }

    private User requireUser(UUID tenantId, UUID userId) {
        return users.findByTenantIdAndId(tenantId, Objects.requireNonNull(userId, "userId"))
                .orElseThrow(() -> new AccessResourceNotFoundException("Platform user not found"));
    }

    private PlatformMembership requireMembership(UUID tenantId, UUID userId) {
        return memberships.findByControlTenantIdAndUserId(tenantId, userId)
                .orElseThrow(() -> new AccessResourceNotFoundException("Platform membership not found"));
    }

    private UUID controlTenant(Authentication actor) {
        controlPlaneAccessGuard.require(actor);
        UUID tenantId = requiredUuid(actor, "tenant_id");
        if (!controlPlaneAccessGuard.isControlPlaneTenant(tenantId)) {
            throw new AccessDeniedException("Control-plane tenant required");
        }
        // platform_memberships is FORCE RLS. Bind only the already-validated
        // control-plane tenant, on the same transaction/connection used below.
        rlsContext.applyForCurrentTransaction(tenantId);
        return tenantId;
    }

    private static UUID actorUserId(Authentication actor) {
        return requiredUuid(actor, "user_id");
    }

    private static UUID requiredUuid(Authentication authentication, String key) {
        if (authentication == null || !(authentication.getDetails() instanceof Map<?, ?> details)) {
            throw new AccessDeniedException("Trusted authentication details required");
        }
        Object raw = details.get(key);
        try {
            return raw instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(raw));
        } catch (RuntimeException exception) {
            throw new AccessDeniedException("Invalid authenticated " + key);
        }
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String requireReason(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException("Reason is required");
        }
        return normalized;
    }

    private static PlatformUserResponse response(User user, PlatformMembership membership) {
        return new PlatformUserResponse(
                user.getId(), user.getEmail(), user.getDisplayName(), user.getStatus(),
                membership.status(), user.getLastLoginAt(), membership.createdAt());
    }
}
