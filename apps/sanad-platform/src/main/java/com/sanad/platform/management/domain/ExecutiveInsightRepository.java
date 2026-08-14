package com.sanad.platform.management.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExecutiveInsightRepository {
    ExecutiveInsight save(ExecutiveInsight insight);
    Optional<ExecutiveInsight> findById(UUID tenantId, UUID id);
    List<ExecutiveInsight> findByTenant(UUID tenantId, int limit);
    List<ExecutiveInsight> findByTenantAndStatus(UUID tenantId, ExecutiveInsight.InsightStatus status, int limit);
    List<ExecutiveInsight> findByTenantAndType(UUID tenantId, ExecutiveInsight.InsightType type, int limit);
}
