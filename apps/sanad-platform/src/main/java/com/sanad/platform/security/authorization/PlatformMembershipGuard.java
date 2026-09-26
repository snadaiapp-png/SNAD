package com.sanad.platform.security.authorization;

import com.sanad.platform.platformiam.service.PlatformMembershipService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Hard Platform IAM boundary for Executive Platform IAM endpoints.
 * Identity is resolved only from the trusted authenticated context.
 */
@Component
public class PlatformMembershipGuard {

    private final ControlPlaneAccessGuard controlPlaneAccessGuard;
    private final PlatformMembershipService membershipService;

    public PlatformMembershipGuard(
            ControlPlaneAccessGuard controlPlaneAccessGuard,
            PlatformMembershipService membershipService) {
        this.controlPlaneAccessGuard = Objects.requireNonNull(controlPlaneAccessGuard, "controlPlaneAccessGuard");
        this.membershipService = Objects.requireNonNull(membershipService, "membershipService");
    }

    public void requireActive(Authentication authentication) {
        controlPlaneAccessGuard.require(authentication);
        UUID tenantId = requiredUuid(authentication, "tenant_id");
        UUID userId = requiredUuid(authentication, "user_id");
        if (!controlPlaneAccessGuard.isControlPlaneTenant(tenantId)) {
            throw new AccessDeniedException("Control-plane tenant required");
        }
        membershipService.requireActive(tenantId, userId);
    }

    private static UUID requiredUuid(Authentication authentication, String key) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }
        Object details = authentication.getDetails();
        if (!(details instanceof Map<?, ?> map)) {
            throw new AccessDeniedException("Trusted authentication details required");
        }
        Object raw = map.get(key);
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
}
