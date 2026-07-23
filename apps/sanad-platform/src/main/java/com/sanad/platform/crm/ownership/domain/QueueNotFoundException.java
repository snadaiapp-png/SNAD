package com.sanad.platform.crm.ownership.domain;

import java.util.UUID;

public class QueueNotFoundException extends OwnershipDomainException {
    public QueueNotFoundException(UUID tenantId, UUID queueId) {
        super("Queue not found: tenant=" + tenantId + " queue=" + queueId);
    }
}
