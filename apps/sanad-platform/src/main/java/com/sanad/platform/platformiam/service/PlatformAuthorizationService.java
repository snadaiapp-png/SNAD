package com.sanad.platform.platformiam.service;

import com.sanad.platform.access.AccessDecisionResponse;
import com.sanad.platform.access.evaluation.CapabilityEvaluationService;
import com.sanad.platform.platformiam.domain.PlatformMembership;
import com.sanad.platform.platformiam.domain.PlatformMembershipStatus;
import com.sanad.platform.security.authorization.ControlPlaneAccessGuard;
import com.sanad.platform.security.scope.AccessScopeGrant;
import com.sanad.platform.security.scope.AccessScopeType;
import com.sanad.platform.security.scope.JdbcAccessScopeRepository;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Platform IAM authorization orchestration layered over the existing capability engine.
 * The legacy platform_admin flag is intentionally not consulted for authorization.
 */
@Service
public class PlatformAuthorizationService {

    private final ControlPlaneAccessGuard controlPlaneAccessGuard;
    private final PlatformMembershipService membershipService;
    private final UserRepository users;
    private final JdbcAccessScopeRepository scopeRepository;
    private final CapabilityEvaluationService capabilityEvaluation;
    private final Clock clock;

    @Autowired
    public PlatformAuthorizationService(
            ControlPlaneAccessGuard controlPlaneAccessGuard,
            PlatformMembershipService membershipService,
            UserRepository users,
            JdbcAccessScopeRepository scopeRepository,
            CapabilityEvaluationService capabilityEvaluation) {
        this(controlPlaneAccessGuard, membershipService, users, scopeRepository,
                capabilityEvaluation, Clock.systemUTC());
    }

    public PlatformAuthorizationService(
            ControlPlaneAccessGuard controlPlaneAccessGuard,
            PlatformMembershipService membershipService,
            UserRepository users,
            JdbcAccessScopeRepository scopeRepository,
            CapabilityEvaluationService capabilityEvaluation,
            Clock clock) {
        this.controlPlaneAccessGuard = Objects.requireNonNull(controlPlaneAccessGuard, "controlPlaneAccessGuard");
        this.membershipService = Objects.requireNonNull(membershipService, "membershipService");
        this.users = Objects.requireNonNull(users, "users");
        this.scopeRepository = Objects.requireNonNull(scopeRepository, "scopeRepository");
        this.capabilityEvaluation = Objects.requireNonNull(capabilityEvaluation, "capabilityEvaluation");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public AccessDecisionResponse evaluate(Authentication authentication, String capabilityCode) {
        String capability = normalizeCapability(capabilityCode);
        Identity identity;
        try {
            controlPlaneAccessGuard.require(authentication);
            identity = trustedIdentity(authentication);
        } catch (AccessDeniedException ex) {
            return deny(null, null, capability, "CONTROL_PLANE_ACCESS_DENIED");
        }

        Optional<PlatformMembership> membership = membershipService.find(identity.tenantId(), identity.userId());
        if (membership.isEmpty()) {
            return deny(identity.tenantId(), identity.userId(), capability, "PLATFORM_MEMBERSHIP_REQUIRED");
        }
        if (membership.get().status() != PlatformMembershipStatus.ACTIVE) {
            return deny(identity.tenantId(), identity.userId(), capability, "PLATFORM_MEMBERSHIP_NOT_ACTIVE");
        }

        Optional<User> user = users.findByTenantIdAndId(identity.tenantId(), identity.userId());
        if (user.isEmpty() || user.get().getStatus() != UserStatus.ACTIVE) {
            return deny(identity.tenantId(), identity.userId(), capability, "PLATFORM_USER_NOT_ACTIVE");
        }

        Instant now = clock.instant();
        List<AccessScopeGrant> directGrants = scopeRepository.findEffectiveGrants(
                identity.tenantId(), identity.userId(), null, capability, now);
        if (directGrants.stream().anyMatch(grant -> validDirectGrant(grant, identity.userId(), now))) {
            return new AccessDecisionResponse(
                    identity.tenantId(), identity.userId(), null, capability,
                    true, "TEMPORARY_DIRECT_GRANT", null, null);
        }

        AccessDecisionResponse roleDecision = capabilityEvaluation.evaluate(
                identity.tenantId(), identity.userId(), capability, null);
        if (roleDecision != null) {
            return roleDecision;
        }
        return deny(identity.tenantId(), identity.userId(), capability, "NO_AUTHORIZATION_DECISION");
    }

    private static boolean validDirectGrant(AccessScopeGrant grant, UUID userId, Instant now) {
        return grant != null
                && grant.directException()
                && grant.roleId() == null
                && userId.equals(grant.userId())
                && grant.scopeType() == AccessScopeType.TENANT
                && grant.reason() != null
                && !grant.reason().isBlank()
                && grant.grantedBy() != null
                && grant.effectiveFrom() != null
                && !now.isBefore(grant.effectiveFrom())
                && grant.effectiveTo() != null
                && now.isBefore(grant.effectiveTo());
    }

    private static String normalizeCapability(String capabilityCode) {
        if (capabilityCode == null || capabilityCode.isBlank()) {
            throw new IllegalArgumentException("capabilityCode must not be blank");
        }
        return capabilityCode.trim().toUpperCase(Locale.ROOT);
    }

    private static Identity trustedIdentity(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }
        Object details = authentication.getDetails();
        if (!(details instanceof Map<?, ?> map)) {
            throw new AccessDeniedException("Trusted authentication details required");
        }
        return new Identity(requiredUuid(map.get("tenant_id"), "tenant_id"),
                requiredUuid(map.get("user_id"), "user_id"));
    }

    private static UUID requiredUuid(Object raw, String key) {
        if (raw instanceof UUID uuid) {
            return uuid;
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return UUID.fromString(text.trim());
            } catch (IllegalArgumentException ignored) {
                throw new AccessDeniedException("Invalid authenticated " + key);
            }
        }
        throw new AccessDeniedException("Missing authenticated " + key);
    }

    private static AccessDecisionResponse deny(UUID tenantId, UUID userId, String capability, String reason) {
        return new AccessDecisionResponse(tenantId, userId, null, capability, false, reason, null, null);
    }

    private record Identity(UUID tenantId, UUID userId) {
    }
}
