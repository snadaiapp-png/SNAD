package com.sanad.platform.management.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExecutiveDecisionRepository {
    ExecutiveDecision save(ExecutiveDecision decision);
    Optional<ExecutiveDecision> findById(UUID tenantId, UUID id);
    Optional<ExecutiveDecision> findByNumber(UUID tenantId, String decisionNumber);
    List<ExecutiveDecision> findByTenant(UUID tenantId, int limit);
    List<ExecutiveDecision> findByTenantAndStatus(UUID tenantId, ExecutiveDecision.Status status, int limit);
    void deleteById(UUID tenantId, UUID id);
}
