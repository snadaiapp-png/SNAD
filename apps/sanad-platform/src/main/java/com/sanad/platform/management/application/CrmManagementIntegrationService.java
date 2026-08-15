package com.sanad.platform.management.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
 *
 * <p>v20260815.9 FIX: Previously queried stale columns {@code estimated_value}
 * and {@code actual_value} which do NOT exist in the {@code crm_opportunities}
 * table. The actual column is {@code amount NUMERIC(24,6)} (V20260702_1).
 * The broad {@code catch (Exception)} blocks that silently returned zero
 * have been REMOVED — structural SQL errors now surface in tests/CI instead
 * of being masked as "empty tenant" results.
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

        // Total accounts
        var accountCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_accounts WHERE tenant_id = ?",
                Integer.class, tenantId);
        overview.put("totalAccounts", accountCount != null ? accountCount : 0);

        // Total contacts
        var contactCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_contacts WHERE tenant_id = ?",
                Integer.class, tenantId);
        overview.put("totalContacts", contactCount != null ? contactCount : 0);

        // Opportunity metrics
        var oppMetrics = getOpportunityMetrics(tenantId);
        overview.putAll(oppMetrics);

        // Pipeline metrics
        var pipelineMetrics = getPipelineMetrics(tenantId);
        overview.putAll(pipelineMetrics);

        // Activity metrics
        var activityMetrics = getActivityMetrics(tenantId);
        overview.putAll(activityMetrics);

        log.info("CRM overview generated for tenant {}: {} accounts, {} opportunities, wonRevenue={}",
                tenantId, overview.get("totalAccounts"), overview.get("totalOpportunities"),
                overview.get("wonRevenue"));

        return overview;
    }

    private Map<String, Object> getAccountMetrics(UUID tenantId) {
        var metrics = new HashMap<String, Object>();

        // Active accounts
        var activeAccounts = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_accounts WHERE tenant_id = ? AND lifecycle_status = 'ACTIVE'",
                Integer.class, tenantId);
        metrics.put("activeAccounts", activeAccounts != null ? activeAccounts : 0);

        // Account types breakdown
        var accountTypes = jdbc.queryForList(
                "SELECT COALESCE(account_type, 'UNKNOWN') as type, COUNT(*) as count " +
                "FROM crm_accounts WHERE tenant_id = ? GROUP BY account_type",
                tenantId);
        metrics.put("accountTypeBreakdown", accountTypes);

        return metrics;
    }

    private Map<String, Object> getOpportunityMetrics(UUID tenantId) {
        var metrics = new HashMap<String, Object>();

        // Total opportunities
        var totalOpps = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_opportunities WHERE tenant_id = ?",
                Integer.class, tenantId);
        metrics.put("totalOpportunities", totalOpps != null ? totalOpps : 0);

        // Open opportunities
        var openOpps = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_opportunities WHERE tenant_id = ? AND status NOT IN ('WON','LOST','CLOSED')",
                Integer.class, tenantId);
        metrics.put("openOpportunities", openOpps != null ? openOpps : 0);

        // Won opportunities
        var wonOpps = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_opportunities WHERE tenant_id = ? AND status = 'WON'",
                Integer.class, tenantId);
        metrics.put("wonOpportunities", wonOpps != null ? wonOpps : 0);

        // Lost opportunities
        var lostOpps = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_opportunities WHERE tenant_id = ? AND status = 'LOST'",
                Integer.class, tenantId);
        metrics.put("lostOpportunities", lostOpps != null ? lostOpps : 0);

        // Estimated pipeline value — sum of opportunity AMOUNT for open opps.
        // v20260815.9 FIX: was "estimated_value" (stale, non-existent column).
        // Actual column is "amount NUMERIC(24,6)" per V20260702_1.
        var estRevenue = jdbc.queryForObject(
                "SELECT COALESCE(SUM(COALESCE(amount, 0)), 0) FROM crm_opportunities " +
                "WHERE tenant_id = ? AND status NOT IN ('WON','LOST','CLOSED')",
                BigDecimal.class, tenantId);
        metrics.put("estimatedPipelineValue", estRevenue != null ? estRevenue : BigDecimal.ZERO);

        // Won revenue — sum of opportunity AMOUNT for WON opps.
        // v20260815.9 FIX: was "actual_value" (stale, non-existent column).
        // Actual column is "amount NUMERIC(24,6)" per V20260702_1.
        var wonRevenue = jdbc.queryForObject(
                "SELECT COALESCE(SUM(COALESCE(amount, 0)), 0) FROM crm_opportunities " +
                "WHERE tenant_id = ? AND status = 'WON'",
                BigDecimal.class, tenantId);
        metrics.put("wonRevenue", wonRevenue != null ? wonRevenue : BigDecimal.ZERO);

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
        var activePipelines = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_pipelines WHERE tenant_id = ? AND active = TRUE",
                Integer.class, tenantId);
        metrics.put("activePipelines", activePipelines != null ? activePipelines : 0);

        // Pipeline stages — v20260815.9 FIX: was "o.pipeline_stage_id" (stale)
        // and "ps.sequence_order" (stale). Actual columns per V20260702_1:
        // crm_opportunities.stage_id, crm_pipeline_stages.sequence.
        // Also: ps.sequence must appear in GROUP BY when used in ORDER BY.
        var stageCounts = jdbc.queryForList(
                "SELECT ps.name as stage, COUNT(o.id) as count " +
                "FROM crm_pipeline_stages ps " +
                "LEFT JOIN crm_opportunities o ON o.stage_id = ps.id AND o.tenant_id = ps.tenant_id " +
                "WHERE ps.tenant_id = ? " +
                "GROUP BY ps.name, ps.sequence ORDER BY ps.sequence",
                tenantId);
        metrics.put("pipelineStages", stageCounts);

        return metrics;
    }

    private Map<String, Object> getActivityMetrics(UUID tenantId) {
        var metrics = new HashMap<String, Object>();

        // Total activities
        var totalActivities = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_activities WHERE tenant_id = ?",
                Integer.class, tenantId);
        metrics.put("totalActivities", totalActivities != null ? totalActivities : 0);

        // Activities this month
        var monthActivities = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_activities WHERE tenant_id = ? " +
                "AND created_at >= date_trunc('month', NOW())",
                Integer.class, tenantId);
        metrics.put("activitiesThisMonth", monthActivities != null ? monthActivities : 0);

        // Activity types breakdown
        var activityTypes = jdbc.queryForList(
                "SELECT COALESCE(activity_type, 'UNKNOWN') as type, COUNT(*) as count " +
                "FROM crm_activities WHERE tenant_id = ? " +
                "GROUP BY activity_type ORDER BY count DESC LIMIT 10",
                tenantId);
        metrics.put("activityTypeBreakdown", activityTypes);

        return metrics;
    }
}
