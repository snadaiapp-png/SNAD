package com.sanad.platform.analytics.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalyticsDataSourceRepository {
    AnalyticsDataSource save(AnalyticsDataSource dataSource);
    Optional<AnalyticsDataSource> findById(UUID tenantId, UUID id);
    Optional<AnalyticsDataSource> findByCode(UUID tenantId, String code);
    List<AnalyticsDataSource> findByTenant(UUID tenantId, int limit);
    List<AnalyticsDataSource> findByTenantAndStatus(UUID tenantId, AnalyticsDataSource.Status status, int limit);
}
