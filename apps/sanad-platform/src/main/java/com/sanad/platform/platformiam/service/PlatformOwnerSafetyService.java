package com.sanad.platform.platformiam.service;

import com.sanad.platform.platformiam.domain.PlatformMembership;
import com.sanad.platform.platformiam.domain.PlatformRoleMetadata;
import com.sanad.platform.platformiam.exception.LastPlatformOwnerException;
import com.sanad.platform.platformiam.repository.PlatformMembershipRepository;
import com.sanad.platform.platformiam.repository.PlatformRoleMetadataRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Enforces the protected Platform Owner invariant.
 *
 * <p>Owner-removal checks must execute in the same transaction as the
 * corresponding mutation. The repository serializes owner-sensitive checks
 * by locking the protected PLATFORM_OWNER metadata row before locking and
 * reading the effective owner membership set.</p>
 */
@Service
public class PlatformOwnerSafetyService {

    private static final String PLATFORM_OWNER = "PLATFORM_OWNER";

    private final PlatformMembershipRepository memberships;
    private final PlatformRoleMetadataRepository roleMetadata;

    public PlatformOwnerSafetyService(
            PlatformMembershipRepository memberships,
            PlatformRoleMetadataRepository roleMetadata) {
        this.memberships = Objects.requireNonNull(memberships, "memberships");
        this.roleMetadata = Objects.requireNonNull(roleMetadata, "roleMetadata");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void assertMayDeactivateMembership(UUID controlTenantId, UUID userId) {
        Objects.requireNonNull(controlTenantId, "controlTenantId");
        Objects.requireNonNull(userId, "userId");

        List<PlatformMembership> effectiveOwners = lockEffectiveOwners(controlTenantId);
        rejectIfFinalEffectiveOwner(effectiveOwners, userId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void assertMayRemoveOwnerRole(UUID controlTenantId, UUID userId, UUID roleId) {
        Objects.requireNonNull(controlTenantId, "controlTenantId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(roleId, "roleId");

        PlatformRoleMetadata metadata = roleMetadata
                .findByControlTenantIdAndRoleId(controlTenantId, roleId)
                .orElse(null);
        if (metadata == null || !metadata.ownerRole()) {
            return;
        }

        List<PlatformMembership> effectiveOwners = lockEffectiveOwners(controlTenantId);
        rejectIfFinalEffectiveOwner(effectiveOwners, userId);
    }

    private List<PlatformMembership> lockEffectiveOwners(UUID controlTenantId) {
        return memberships.lockActiveMembershipsByRoleCode(controlTenantId, PLATFORM_OWNER);
    }

    private static void rejectIfFinalEffectiveOwner(
            List<PlatformMembership> effectiveOwners,
            UUID targetUserId) {
        boolean targetIsEffectiveOwner = effectiveOwners.stream()
                .map(PlatformMembership::userId)
                .anyMatch(targetUserId::equals);

        if (targetIsEffectiveOwner && effectiveOwners.size() <= 1) {
            throw new LastPlatformOwnerException();
        }
    }
}
