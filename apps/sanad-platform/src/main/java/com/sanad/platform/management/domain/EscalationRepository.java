package com.sanad.platform.management.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EscalationRepository {
    Escalation save(Escalation escalation);
    Optional<Escalation> findById(UUID tenantId, UUID id);
    List<Escalation> findByTenant(UUID tenantId, int limit);
    List<Escalation> findByTenantAndStatus(UUID tenantId, Escalation.Status status, int limit);
    List<Escalation> findBySourceEntity(UUID tenantId, Escalation.SourceEntityType sourceType, UUID sourceId);
    void deleteById(UUID tenantId, UUID id);
}
