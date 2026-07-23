package com.sanad.platform.crm.ownership.domain;

import java.time.Instant;
import java.util.UUID;

/** CRM queue for round-robin or claim-based record distribution. */
public record Queue(
        UUID id,
        UUID tenantId,
        String code,
        String displayName,
        QueueRecordType recordType,
        String description,
        QueueStatus status,
        int maxItemsPerUser,
        Integer slaMinutes,
        UUID escalationTargetQueueId,
        UUID defaultOwnerId,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy
) {
    public Queue {
        if (tenantId == null) throw new IllegalArgumentException("tenantId required");
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code required");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("displayName required");
        if (recordType == null) throw new IllegalArgumentException("recordType required");
        if (status == null) status = QueueStatus.ACTIVE;
        if (maxItemsPerUser < 1 || maxItemsPerUser > 1000) {
            throw new OwnershipDomainException("maxItemsPerUser must be between 1 and 1000");
        }
        if (slaMinutes != null && slaMinutes < 1) {
            throw new OwnershipDomainException("slaMinutes must be positive when provided");
        }
        if (escalationTargetQueueId != null && escalationTargetQueueId.equals(id)) {
            throw new OwnershipDomainException("Queue cannot escalate to itself: " + id);
        }
    }

    /** Only ACTIVE queues accept newly queued records. */
    public boolean acceptsNewItems() {
        return status == QueueStatus.ACTIVE;
    }

    /** ACTIVE and DRAINING queues allow claims/releases for existing items. */
    public boolean allowsClaims() {
        return status == QueueStatus.ACTIVE || status == QueueStatus.DRAINING;
    }

    /** Compatibility alias retained for existing callers. */
    public boolean isClaimable() {
        return allowsClaims();
    }
}
