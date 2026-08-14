package com.sanad.platform.management.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExecutiveAlertRepository {
    ExecutiveAlert save(ExecutiveAlert alert);
    Optional<ExecutiveAlert> findById(UUID tenantId, UUID id);
    Optional<ExecutiveAlert> findBySource(UUID tenantId, ExecutiveAlert.SourceEntityType sourceType, UUID sourceId, ExecutiveAlert.AlertType type);
    List<ExecutiveAlert> findByTenant(UUID tenantId, int limit);
    List<ExecutiveAlert> findByTenantAndStatus(UUID tenantId, ExecutiveAlert.Status status, int limit);
}
