package com.sanad.platform.management.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository for {@link KeyResult} persistence. */
public interface KeyResultRepository {

    KeyResult save(KeyResult keyResult);

    Optional<KeyResult> findById(UUID tenantId, UUID id);

    List<KeyResult> findByObjective(UUID tenantId, UUID objectiveId);

    List<KeyResult> findByTenant(UUID tenantId, int limit);

    List<KeyResult> findByTenantAndStatus(UUID tenantId, KeyResult.Status status, int limit);

    void deleteById(UUID tenantId, UUID id);
}
