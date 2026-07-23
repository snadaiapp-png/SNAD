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
        if (maxItemsPerUser < 1) maxItemsPerUser = 10;
        if (escalationTargetQueueId != null && escalationTargetQueueId.equals(id)) {
            throw new OwnershipDomainException("Queue cannot escalate to itself: " + id);
        }
    }

    public boolean isClaimable() {
        return status == QueueStatus.ACTIVE;
    }
}
