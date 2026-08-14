package com.sanad.platform.management.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExecutiveHealthSnapshotRepository {
    ExecutiveHealthSnapshot save(ExecutiveHealthSnapshot snapshot);
    Optional<ExecutiveHealthSnapshot> findLatest(UUID tenantId);
    List<ExecutiveHealthSnapshot> findByTenant(UUID tenantId, int limit);
}
