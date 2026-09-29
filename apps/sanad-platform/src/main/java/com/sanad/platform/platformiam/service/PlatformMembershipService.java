package com.sanad.platform.platformiam.service;

import com.sanad.platform.platformiam.domain.PlatformMembership;
import com.sanad.platform.platformiam.domain.PlatformMembershipStatus;
import com.sanad.platform.platformiam.repository.PlatformMembershipRepository;
import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Focused lifecycle lookup service for the explicit Platform IAM membership boundary.
 *
 * <p>{@code platform_memberships} is protected by FORCE RLS. Every public read therefore
 * starts a Spring-managed read-only transaction and binds the already-trusted control
 * tenant to that transaction before querying the repository. Authorization remains the
 * responsibility of the upstream control-plane guard; this service only applies the
 * validated tenant scope required by the persistence boundary.</p>
 */
@Service
public class PlatformMembershipService {

    private final PlatformMembershipRepository memberships;
    private final TenantRlsTransactionContext rlsContext;

    public PlatformMembershipService(
            PlatformMembershipRepository memberships,
            TenantRlsTransactionContext rlsContext) {
        this.memberships = Objects.requireNonNull(memberships, "memberships");
        this.rlsContext = Objects.requireNonNull(rlsContext, "rlsContext");
    }

    @Transactional(readOnly = true)
    public Optional<PlatformMembership> find(UUID controlTenantId, UUID userId) {
        Objects.requireNonNull(controlTenantId, "controlTenantId");
        Objects.requireNonNull(userId, "userId");
        rlsContext.applyForCurrentTransaction(controlTenantId);
        return memberships.findByControlTenantIdAndUserId(controlTenantId, userId);
    }

    @Transactional(readOnly = true)
    public PlatformMembership requireActive(UUID controlTenantId, UUID userId) {
        PlatformMembership membership = find(controlTenantId, userId)
                .orElseThrow(() -> new AccessDeniedException("Active platform membership required"));
        if (membership.status() != PlatformMembershipStatus.ACTIVE) {
            throw new AccessDeniedException("ACTIVE platform membership required");
        }
        return membership;
    }
}
