package com.sanad.platform.crm.reports.web;

import com.sanad.platform.crm.reports.application.ReportsUseCases;
import com.sanad.platform.crm.reports.domain.ReportsRepository.*;
import com.sanad.platform.security.authorization.RequireCapability;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * V1 REST controller for CRM Reports.
 * <p>
 * Mounted under {@code /api/v1/crm/reports}. Requires {@code CRM.ACCOUNT.READ}
 * (reports aggregate across all entities — reuses the broadest READ capability).
 * <p>
 * Branch: feature/crm-reports
 */
@RestController
@RequestMapping("/api/v1/crm/reports")
public class ReportsController {

    private final ReportsUseCases reports;

    public ReportsController(ReportsUseCases reports) {
        this.reports = reports;
    }

    @RequireCapability("CRM.ACCOUNT.READ")
    @GetMapping("/sales-pipeline")
    public Map<String, Object> salesPipeline(Authentication auth) {
        return toSalesPipeline(reports.getSalesPipeline(tenantId(auth)));
    }

    @RequireCapability("CRM.ACCOUNT.READ")
    @GetMapping("/lead-conversion")
    public Map<String, Object> leadConversion(Authentication auth) {
        return toLeadConversion(reports.getLeadConversion(tenantId(auth)));
    }

    @RequireCapability("CRM.ACCOUNT.READ")
    @GetMapping("/activity-summary")
    public Map<String, Object> activitySummary(Authentication auth) {
        return toActivitySummary(reports.getActivitySummary(tenantId(auth)));
    }

    @RequireCapability("CRM.ACCOUNT.READ")
    @GetMapping("/account-growth")
    public Map<String, Object> accountGrowth(Authentication auth) {
        return toAccountGrowth(reports.getAccountGrowth(tenantId(auth)));
    }

    @RequireCapability("CRM.ACCOUNT.READ")
    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(Authentication auth) {
        UUID t = tenantId(auth);
        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("salesPipeline", toSalesPipeline(reports.getSalesPipeline(t)));
        dashboard.put("leadConversion", toLeadConversion(reports.getLeadConversion(t)));
        dashboard.put("activitySummary", toActivitySummary(reports.getActivitySummary(t)));
        dashboard.put("accountGrowth", toAccountGrowth(reports.getAccountGrowth(t)));
        return dashboard;
    }

    private Map<String, Object> toSalesPipeline(SalesPipelineReport r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("stages", r.stages().stream().map(s -> {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("stage_name", s.stageName());
            sm.put("stage_id", s.stageId());
            sm.put("opportunity_count", s.opportunityCount());
            sm.put("total_amount", s.totalAmount().toPlainString());
            sm.put("avg_probability", s.avgProbability().toPlainString());
            return sm;
        }).toList());
        m.put("total_pipeline_value", r.totalPipelineValue().toPlainString());
        m.put("total_opportunities", r.totalOpportunities());
        m.put("weighted_pipeline_value", r.weightedPipelineValue().toPlainString());
        return m;
    }

    private Map<String, Object> toLeadConversion(LeadConversionReport r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total_leads", r.totalLeads());
        m.put("converted_leads", r.convertedLeads());
        m.put("qualified_leads", r.qualifiedLeads());
        m.put("disqualified_leads", r.disqualifiedLeads());
        m.put("new_leads", r.newLeads());
        m.put("conversion_rate", r.conversionRate());
        m.put("by_source", r.bySource().stream().map(s -> {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("source", s.source());
            sm.put("count", s.count());
            sm.put("converted", s.converted());
            return sm;
        }).toList());
        return m;
    }

    private Map<String, Object> toActivitySummary(ActivitySummaryReport r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total_activities", r.totalActivities());
        m.put("open_activities", r.openActivities());
        m.put("completed_activities", r.completedActivities());
        m.put("total_tasks", r.totalTasks());
        m.put("open_tasks", r.openTasks());
        m.put("completed_tasks", r.completedTasks());
        m.put("activities_by_type", r.activitiesByType().stream().map(b -> {
            Map<String, Object> bm = new LinkedHashMap<>();
            bm.put("activity_type", b.activityType());
            bm.put("count", b.count());
            bm.put("open_count", b.openCount());
            return bm;
        }).toList());
        return m;
    }

    private Map<String, Object> toAccountGrowth(AccountGrowthReport r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total_accounts", r.totalAccounts());
        m.put("active_accounts", r.activeAccounts());
        m.put("new_this_month", r.newThisMonth());
        m.put("new_this_quarter", r.newThisQuarter());
        m.put("monthly_growth", r.monthlyGrowth().stream().map(g -> {
            Map<String, Object> gm = new LinkedHashMap<>();
            gm.put("month", g.month());
            gm.put("new_accounts", g.newAccounts());
            gm.put("cumulative", g.cumulative());
            return gm;
        }).toList());
        return m;
    }

    private static UUID tenantId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof Map<?, ?> details)
                || details.get("tenant_id") == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated CRM context is required");
        }
        try {
            return UUID.fromString(details.get("tenant_id").toString());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated CRM context", exception);
        }
    }
}
