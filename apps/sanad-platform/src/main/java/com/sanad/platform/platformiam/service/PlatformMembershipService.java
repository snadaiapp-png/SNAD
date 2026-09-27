package com.sanad.platform.platformiam.service;

import com.sanad.platform.platformiam.domain.PlatformMembership;
import com.sanad.platform.platformiam.domain.PlatformMembershipStatus;
import com.sanad.platform.platformiam.repository.PlatformMembershipRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Focused lifecycle lookup service for the explicit Platform IAM membership boundary.
 */
@Service
public class PlatformMembershipService {

    private final PlatformMembershipRepository memberships;

    public PlatformMembershipService(PlatformMembershipRepository memberships) {
        this.memberships = Objects.requireNonNull(memberships, "memberships");
    }

    public Optional<PlatformMembership> find(UUID controlTenantId, UUID userId) {
        Objects.requireNonNull(controlTenantId, "controlTenantId");
        Objects.requireNonNull(userId, "userId");
        return memberships.findByControlTenantIdAndUserId(controlTenantId, userId);
    }

    public PlatformMembership requireActive(UUID controlTenantId, UUID userId) {
        PlatformMembership membership = find(controlTenantId, userId)
                .orElseThrow(() -> new AccessDeniedException("Active platform membership required"));
        if (membership.status() != PlatformMembershipStatus.ACTIVE) {
            throw new AccessDeniedException("ACTIVE platform membership required");
        }
        return membership;
    }
}
