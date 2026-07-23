package com.sanad.platform.crm.ownership.domain;

import java.time.Instant;
import java.util.UUID;

/** Membership of a user in a queue (claim eligibility). */
public record QueueMembership(
        UUID id,
        UUID tenantId,
        UUID queueId,
        UUID userId,
        QueueMembershipStatus status,
        Instant addedAt,
        Instant removedAt,
        String removedReason,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy
) {
    public QueueMembership {
        if (tenantId == null) throw new IllegalArgumentException("tenantId required");
        if (queueId == null) throw new IllegalArgumentException("queueId required");
        if (userId == null) throw new IllegalArgumentException("userId required");
        if (status == null) status = QueueMembershipStatus.ACTIVE;
    }

    public boolean isActive() {
        return status == QueueMembershipStatus.ACTIVE;
    }
}
