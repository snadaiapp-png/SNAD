package com.sanad.platform.crm.ownership.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Membership of a user in a sales team.
 * Enforces single-active-per-(tenant,team,user) and single-primary-per-(tenant,user).
 */
public record TeamMembership(
        UUID id,
        UUID tenantId,
        UUID teamId,
        UUID userId,
        MembershipRole role,
        boolean isPrimary,
        MembershipStatus status,
        Instant joinedAt,
        Instant leftAt,
        String leftReason,
        int capacityMax,
        String metadata,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy
) {
    public TeamMembership {
        if (tenantId == null) throw new IllegalArgumentException("tenantId required");
        if (teamId == null) throw new IllegalArgumentException("teamId required");
        if (userId == null) throw new IllegalArgumentException("userId required");
        if (role == null) throw new IllegalArgumentException("role required");
        if (status == null) status = MembershipStatus.ACTIVE;
        if (capacityMax < 0) capacityMax = 50;
    }

    public boolean isActive() { return status == MembershipStatus.ACTIVE; }
}
