package com.sanad.platform.crm.reports.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Reports repository port — read-only aggregations across CRM entities.
 * <p>
 * Provides sales pipeline summary, lead conversion rates, activity
 * breakdown, and account growth metrics. All methods are tenant-scoped
 * and read-only (no mutations).
 * <p>
 * Branch: feature/crm-reports
 */
public interface ReportsRepository {

    SalesPipelineReport getSalesPipelineReport(UUID tenantId);
    LeadConversionReport getLeadConversionReport(UUID tenantId);
    ActivitySummaryReport getActivitySummaryReport(UUID tenantId);
    AccountGrowthReport getAccountGrowthReport(UUID tenantId);

    record SalesPipelineReport(
            List<StageSummary> stages,
            BigDecimal totalPipelineValue,
            int totalOpportunities,
            BigDecimal weightedPipelineValue) {}

    record StageSummary(
            String stageName,
            UUID stageId,
            int opportunityCount,
            BigDecimal totalAmount,
            BigDecimal avgProbability) {}

    record LeadConversionReport(
            int totalLeads,
            int convertedLeads,
            int qualifiedLeads,
            int disqualifiedLeads,
            int newLeads,
            double conversionRate,
            List<LeadSourceSummary> bySource) {}

    record LeadSourceSummary(
            String source,
            int count,
            int converted) {}

    record ActivitySummaryReport(
            int totalActivities,
            int openActivities,
            int completedActivities,
            int totalTasks,
            int openTasks,
            int completedTasks,
            List<ActivityTypeBreakdown> activitiesByType) {}

    record ActivityTypeBreakdown(
            String activityType,
            int count,
            int openCount) {}

    record AccountGrowthReport(
            int totalAccounts,
            int activeAccounts,
            int newThisMonth,
            int newThisQuarter,
            List<MonthlyGrowth> monthlyGrowth) {}

    record MonthlyGrowth(
            String month,
            int newAccounts,
            int cumulative) {}
}
