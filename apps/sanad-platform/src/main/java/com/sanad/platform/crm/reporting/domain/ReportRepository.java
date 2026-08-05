package com.sanad.platform.crm.reporting.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Outbound port for report data persistence and retrieval.
 */
public interface ReportRepository {

    /**
     * Get lead counts by status for a tenant within a date range.
     */
    List<Map<String, Object>> getLeadCountsByStatus(UUID tenantId, Instant dateFrom, Instant dateTo);

    /**
     * Get opportunity counts by pipeline stage for a tenant within a date range.
     */
    List<Map<String, Object>> getOpportunityCountsByStage(UUID tenantId, Instant dateFrom, Instant dateTo);

    /**
     * Get activity counts by type for a tenant within a date range.
     */
    List<Map<String, Object>> getActivityCountsByType(UUID tenantId, Instant dateFrom, Instant dateTo);

    /**
     * Get email engagement metrics for a tenant within a date range.
     */
    List<Map<String, Object>> getEmailEngagementMetrics(UUID tenantId, Instant dateFrom, Instant dateTo);

    /**
     * Get conversion funnel data for a tenant within a date range.
     */
    List<Map<String, Object>> getConversionFunnel(UUID tenantId, Instant dateFrom, Instant dateTo);

    /**
     * Get sales forecast data for a tenant within a date range.
     */
    List<Map<String, Object>> getSalesForecast(UUID tenantId, Instant dateFrom, Instant dateTo);

    /**
     * Get summary statistics for a tenant.
     */
    Map<String, Object> getSummaryStats(UUID tenantId, Instant dateFrom, Instant dateTo);
}
