package com.sanad.platform.crm.ownership.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository for queue memberships (tenant-scoped). */
public interface QueueMembershipRepository {

    QueueMembership save(QueueMembership membership);

    Optional<QueueMembership> findActive(UUID tenantId, UUID queueId, UUID userId);

    List<QueueMembership> findActiveByQueue(UUID tenantId, UUID queueId);

    List<QueueMembership> findActiveByUser(UUID tenantId, UUID userId);

    void remove(UUID tenantId, UUID membershipId, String reason, UUID updatedBy);

    long countActiveByQueue(UUID tenantId, UUID queueId);
}
