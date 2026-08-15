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
 * CRM Management Integration Service — bridges Senior Management with the CRM module.
 *
 * <p>Provides executive-level CRM metrics by querying CRM tables directly through
 * the tenant-scoped JdbcTemplate. This follows the existing SNAD pattern where
 * cross-module queries use JdbcTemplate (as BusinessProcessService does).
 *
 * <p>CRITICAL: All queries are tenant-scoped via WHERE tenant_id = ?
 * No duplicate CRM business logic. No parallel CRM tables.
 * This is a READ-ONLY aggregation service — it does NOT mutate CRM data.
 */
@Service
public class CrmManagementIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(CrmManagementIntegrationService.class);

    private final JdbcTemplate jdbc;

    public CrmManagementIntegrationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Get CRM overview metrics for the management dashboard.
     *
     * @return map with account/contact/opportunity/pipeline/activity counts and revenue
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCrmOverview(UUID tenantId) {
        var overview = new HashMap<String, Object>();

        // Account metrics
        var accountMetrics = getAccountMetrics(tenantId);
        overview.putAll(accountMetrics);

        // Contact metrics
        var contactCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_accounts WHERE tenant_id = ? AND deleted_at IS NULL",
                Integer.class, tenantId);
        overview.put("totalAccounts", contactCount != null ? contactCount : 0);

        // Contact count
        var contactCountVal = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_contacts WHERE tenant_id = ? AND deleted_at IS NULL",
                Integer.class, tenantId);
        overview.put("totalContacts", contactCountVal != null ? contactCountVal : 0);

        // Opportunity metrics
        var oppMetrics = getOpportunityMetrics(tenantId);
        overview.putAll(oppMetrics);

        // Pipeline metrics
        var pipelineMetrics = getPipelineMetrics(tenantId);
        overview.putAll(pipelineMetrics);

        // Activity metrics
        var activityMetrics = getActivityMetrics(tenantId);
        overview.putAll(activityMetrics);

        log.info("CRM overview generated for tenant {}: {} accounts, {} opportunities",
                tenantId, overview.get("totalAccounts"), overview.get("totalOpportunities"));

        return overview;
    }

    private Map<String, Object> getAccountMetrics(UUID tenantId) {
        var metrics = new HashMap<String, Object>();

        // Active accounts
        var activeAccounts = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_accounts WHERE tenant_id = ? AND status = 'ACTIVE' AND deleted_at IS NULL",
                Integer.class, tenantId);
        metrics.put("activeAccounts", activeAccounts != null ? activeAccounts : 0);

        // Account types breakdown
        try {
            var accountTypes = jdbc.queryForList(
                    "SELECT COALESCE(account_type, 'UNKNOWN') as type, COUNT(*) as count " +
                    "FROM crm_accounts WHERE tenant_id = ? AND deleted_at IS NULL GROUP BY account_type",
                    tenantId);
            metrics.put("accountTypeBreakdown", accountTypes);
        } catch (Exception e) {
            metrics.put("accountTypeBreakdown", List.of());
        }

        return metrics;
    }

    private Map<String, Object> getOpportunityMetrics(UUID tenantId) {
        var metrics = new HashMap<String, Object>();

        // Total opportunities
        var totalOpps = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_opportunities WHERE tenant_id = ? AND deleted_at IS NULL",
                Integer.class, tenantId);
        metrics.put("totalOpportunities", totalOpps != null ? totalOpps : 0);

        // Open opportunities
        var openOpps = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_opportunities WHERE tenant_id = ? AND status NOT IN ('WON','LOST','CLOSED') AND deleted_at IS NULL",
                Integer.class, tenantId);
        metrics.put("openOpportunities", openOpps != null ? openOpps : 0);

        // Won opportunities
        var wonOpps = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_opportunities WHERE tenant_id = ? AND status = 'WON' AND deleted_at IS NULL",
                Integer.class, tenantId);
        metrics.put("wonOpportunities", wonOpps != null ? wonOpps : 0);

        // Lost opportunities
        var lostOpps = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_opportunities WHERE tenant_id = ? AND status = 'LOST' AND deleted_at IS NULL",
                Integer.class, tenantId);
        metrics.put("lostOpportunities", lostOpps != null ? lostOpps : 0);

        // Total estimated revenue (sum of opportunity value for open opps)
        try {
            var estRevenue = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(COALESCE(estimated_value, 0)), 0) FROM crm_opportunities " +
                    "WHERE tenant_id = ? AND status NOT IN ('WON','LOST','CLOSED') AND deleted_at IS NULL",
                    java.math.BigDecimal.class, tenantId);
            metrics.put("estimatedPipelineValue", estRevenue != null ? estRevenue : java.math.BigDecimal.ZERO);
        } catch (Exception e) {
            metrics.put("estimatedPipelineValue", java.math.BigDecimal.ZERO);
        }

        // Won revenue
        try {
            var wonRevenue = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(COALESCE(actual_value, 0)), 0) FROM crm_opportunities " +
                    "WHERE tenant_id = ? AND status = 'WON' AND deleted_at IS NULL",
                    java.math.BigDecimal.class, tenantId);
            metrics.put("wonRevenue", wonRevenue != null ? wonRevenue : java.math.BigDecimal.ZERO);
        } catch (Exception e) {
            metrics.put("wonRevenue", java.math.BigDecimal.ZERO);
        }

        // Win rate
        int total = metrics.get("totalOpportunities") != null ? (int) metrics.get("totalOpportunities") : 0;
        int won = metrics.get("wonOpportunities") != null ? (int) metrics.get("wonOpportunities") : 0;
        double winRate = total > 0 ? (double) won / total * 100 : 0;
        metrics.put("winRate", Math.round(winRate * 100.0) / 100.0);

        return metrics;
    }

    private Map<String, Object> getPipelineMetrics(UUID tenantId) {
        var metrics = new HashMap<String, Object>();

        // Active pipelines
        try {
            var activePipelines = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM crm_pipelines WHERE tenant_id = ? AND status = 'ACTIVE'",
                    Integer.class, tenantId);
            metrics.put("activePipelines", activePipelines != null ? activePipelines : 0);
        } catch (Exception e) {
            metrics.put("activePipelines", 0);
        }

        // Pipeline stages
        try {
            var stageCounts = jdbc.queryForList(
                    "SELECT ps.name as stage, COUNT(o.id) as count " +
                    "FROM crm_pipeline_stages ps " +
                    "LEFT JOIN crm_opportunities o ON o.pipeline_stage_id = ps.id AND o.deleted_at IS NULL " +
                    "WHERE ps.tenant_id = ? " +
                    "GROUP BY ps.name ORDER BY ps.sequence_order",
                    tenantId);
            metrics.put("pipelineStages", stageCounts);
        } catch (Exception e) {
            metrics.put("pipelineStages", List.of());
        }

        return metrics;
    }

    private Map<String, Object> getActivityMetrics(UUID tenantId) {
        var metrics = new HashMap<String, Object>();

        // Total activities
        try {
            var totalActivities = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM crm_activities WHERE tenant_id = ? AND deleted_at IS NULL",
                    Integer.class, tenantId);
            metrics.put("totalActivities", totalActivities != null ? totalActivities : 0);
        } catch (Exception e) {
            metrics.put("totalActivities", 0);
        }

        // Activities this month
        try {
            var monthActivities = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM crm_activities WHERE tenant_id = ? AND deleted_at IS NULL " +
                    "AND created_at >= date_trunc('month', NOW())",
                    Integer.class, tenantId);
            metrics.put("activitiesThisMonth", monthActivities != null ? monthActivities : 0);
        } catch (Exception e) {
            metrics.put("activitiesThisMonth", 0);
        }

        // Activity types breakdown
        try {
            var activityTypes = jdbc.queryForList(
                    "SELECT COALESCE(activity_type, 'UNKNOWN') as type, COUNT(*) as count " +
                    "FROM crm_activities WHERE tenant_id = ? AND deleted_at IS NULL " +
                    "GROUP BY activity_type ORDER BY count DESC LIMIT 10",
                    tenantId);
            metrics.put("activityTypeBreakdown", activityTypes);
        } catch (Exception e) {
            metrics.put("activityTypeBreakdown", List.of());
        }

        return metrics;
    }
}
