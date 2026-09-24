package com.sanad.platform.analytics.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalyticsDashboardRepository {
    AnalyticsDashboard save(AnalyticsDashboard dashboard);
    Optional<AnalyticsDashboard> findById(UUID tenantId, UUID id);
    Optional<AnalyticsDashboard> findByCode(UUID tenantId, String code);
    List<AnalyticsDashboard> findByTenant(UUID tenantId, int limit);
    List<AnalyticsDashboard> findByTenantAndStatus(UUID tenantId, AnalyticsDashboard.Status status, int limit);
}
