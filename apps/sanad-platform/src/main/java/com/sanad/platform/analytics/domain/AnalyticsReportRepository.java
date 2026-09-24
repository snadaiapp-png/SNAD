package com.sanad.platform.analytics.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalyticsReportRepository {
    AnalyticsReport save(AnalyticsReport report);
    Optional<AnalyticsReport> findById(UUID tenantId, UUID id);
    Optional<AnalyticsReport> findByCode(UUID tenantId, String code);
    List<AnalyticsReport> findByTenant(UUID tenantId, int limit);
    List<AnalyticsReport> findByTenantAndStatus(UUID tenantId, AnalyticsReport.Status status, int limit);
}
