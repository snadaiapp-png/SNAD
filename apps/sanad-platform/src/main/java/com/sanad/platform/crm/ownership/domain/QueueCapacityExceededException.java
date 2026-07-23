package com.sanad.platform.crm.ownership.domain;

import java.util.UUID;

public class QueueCapacityExceededException extends OwnershipDomainException {
    public QueueCapacityExceededException(UUID tenantId, UUID queueId, UUID userId, int max) {
        super("Queue capacity exceeded: tenant=" + tenantId + " queue=" + queueId + " user=" + userId + " max=" + max);
    }
}
