package com.sanad.platform.management.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Analytics Management Integration Service — bridges Senior Management with the Analytics module.
 *
 * <p>Provides executive-level Analytics metrics by querying Analytics tables directly through
 * the tenant-scoped JdbcTemplate. READ-ONLY — does NOT mutate Analytics data.
 *
 * <p>Follows the same pattern as {@link CrmManagementIntegrationService} and
 * {@link FinanceManagementIntegrationService}.
 */
@Service
public class AnalyticsManagementIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsManagementIntegrationService.class);

    private final JdbcTemplate jdbc;

    public AnalyticsManagementIntegrationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAnalyticsOverview(UUID tenantId) {
        var overview = new HashMap<String, Object>();

        overview.putAll(getDashboardMetrics(tenantId));
        overview.putAll(getReportMetrics(tenantId));
        overview.putAll(getDataSourceMetrics(tenantId));

        log.info("Analytics overview generated for tenant {}", tenantId);
        return overview;
    }

    private Map<String, Object> getDashboardMetrics(UUID tenantId) {
        var metrics = new HashMap<String, Object>();

        try {
            var totalDashboards = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM analytics_dashboards WHERE tenant_id = ?",
                    Integer.class, tenantId);
            metrics.put("totalDashboards", totalDashboards != null ? totalDashboards : 0);

            var activeDashboards = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM analytics_dashboards WHERE tenant_id = ? AND status = 'ACTIVE'",
                    Integer.class, tenantId);
            metrics.put("activeDashboards", activeDashboards != null ? activeDashboards : 0);

            var dashboardTypes = jdbc.queryForList(
                    "SELECT dashboard_type, COUNT(*) as count FROM analytics_dashboards " +
                    "WHERE tenant_id = ? GROUP BY dashboard_type",
                    tenantId);
            metrics.put("dashboardTypeBreakdown", dashboardTypes);
        } catch (Exception e) {
            metrics.put("totalDashboards", 0);
            metrics.put("activeDashboards", 0);
            metrics.put("dashboardTypeBreakdown", List.of());
        }

        return metrics;
    }

    private Map<String, Object> getReportMetrics(UUID tenantId) {
        var metrics = new HashMap<String, Object>();

        try {
            var totalReports = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM analytics_reports WHERE tenant_id = ?",
                    Integer.class, tenantId);
            metrics.put("totalReports", totalReports != null ? totalReports : 0);

            var activeReports = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM analytics_reports WHERE tenant_id = ? AND status = 'ACTIVE'",
                    Integer.class, tenantId);
            metrics.put("activeReports", activeReports != null ? activeReports : 0);

            var scheduledReports = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM analytics_reports WHERE tenant_id = ? AND status = 'SCHEDULED'",
                    Integer.class, tenantId);
            metrics.put("scheduledReports", scheduledReports != null ? scheduledReports : 0);

            var reportTypes = jdbc.queryForList(
                    "SELECT report_type, COUNT(*) as count FROM analytics_reports " +
                    "WHERE tenant_id = ? GROUP BY report_type",
                    tenantId);
            metrics.put("reportTypeBreakdown", reportTypes);
        } catch (Exception e) {
            metrics.put("totalReports", 0);
            metrics.put("activeReports", 0);
            metrics.put("scheduledReports", 0);
            metrics.put("reportTypeBreakdown", List.of());
        }

        return metrics;
    }

    private Map<String, Object> getDataSourceMetrics(UUID tenantId) {
        var metrics = new HashMap<String, Object>();

        try {
            var totalSources = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM analytics_data_sources WHERE tenant_id = ?",
                    Integer.class, tenantId);
            metrics.put("totalDataSources", totalSources != null ? totalSources : 0);

            var activeSources = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM analytics_data_sources WHERE tenant_id = ? AND status = 'ACTIVE'",
                    Integer.class, tenantId);
            metrics.put("activeDataSources", activeSources != null ? activeSources : 0);

            var pendingSources = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM analytics_data_sources WHERE tenant_id = ? AND status = 'PENDING'",
                    Integer.class, tenantId);
            metrics.put("pendingDataSources", pendingSources != null ? pendingSources : 0);

            var errorSources = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM analytics_data_sources WHERE tenant_id = ? AND status = 'ERROR'",
                    Integer.class, tenantId);
            metrics.put("errorDataSources", errorSources != null ? errorSources : 0);

            var sourceTypes = jdbc.queryForList(
                    "SELECT source_type, COUNT(*) as count FROM analytics_data_sources " +
                    "WHERE tenant_id = ? GROUP BY source_type",
                    tenantId);
            metrics.put("dataSourceTypeBreakdown", sourceTypes);
        } catch (Exception e) {
            metrics.put("totalDataSources", 0);
            metrics.put("activeDataSources", 0);
            metrics.put("pendingDataSources", 0);
            metrics.put("errorDataSources", 0);
            metrics.put("dataSourceTypeBreakdown", List.of());
        }

        return metrics;
    }
}
