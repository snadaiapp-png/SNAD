package com.sanad.platform.crm.ownership.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository for queues (tenant-scoped). */
public interface QueueRepository {

    Queue save(Queue queue);

    Optional<Queue> findById(UUID tenantId, UUID queueId);

    Optional<Queue> findByCode(UUID tenantId, String code);

    List<Queue> findByTenant(UUID tenantId, QueueStatus status);

    List<Queue> findByRecordType(UUID tenantId, QueueRecordType recordType);

    void updateStatus(UUID tenantId, UUID queueId, QueueStatus status, UUID updatedBy);
}
