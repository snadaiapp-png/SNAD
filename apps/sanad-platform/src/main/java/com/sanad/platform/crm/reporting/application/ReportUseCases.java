package com.sanad.platform.crm.reporting.application;

import com.sanad.platform.crm.reporting.domain.ReportData;
import com.sanad.platform.crm.reporting.domain.ReportRepository;
import com.sanad.platform.crm.reporting.domain.ReportRequest;
import com.sanad.platform.crm.reporting.domain.ReportType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Application service orchestrating report generation.
 * Follows the thin facade pattern with no Spring annotations.
 */
public class ReportUseCases {

    private final ReportRepository reportRepository;

    public ReportUseCases(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    /**
     * Generate a report based on the request parameters.
     */
    public ReportData generateReport(ReportRequest request) {
        return switch (request.reportType()) {
            case LEAD_PIPELINE -> generateLeadPipelineReport(request);
            case OPPORTUNITY_PIPELINE -> generateOpportunityPipelineReport(request);
            case ACTIVITY_SUMMARY -> generateActivitySummaryReport(request);
            case EMAIL_ENGAGEMENT -> generateEmailEngagementReport(request);
            case CONVERSION_FUNNEL -> generateConversionFunnelReport(request);
            case SALES_FORECAST -> generateSalesForecastReport(request);
        };
    }

    private ReportData generateLeadPipelineReport(ReportRequest request) {
        List<Map<String, Object>> rows = reportRepository.getLeadCountsByStatus(
                request.tenantId(), request.dateFrom(), request.dateTo());
        Map<String, Object> summary = reportRepository.getSummaryStats(
                request.tenantId(), request.dateFrom(), request.dateTo());

        List<ReportData.ReportChart> charts = new ArrayList<>();
        List<String> labels = rows.stream().map(r -> (String) r.get("status")).toList();
        List<Number> values = rows.stream().map(r -> (Number) r.get("count")).toList();
        charts.add(new ReportData.ReportChart("Leads by Status", "pie", labels, values));

        return ReportData.of(ReportType.LEAD_PIPELINE, request.dateFrom(), request.dateTo(),
                rows, summary, charts);
    }

    private ReportData generateOpportunityPipelineReport(ReportRequest request) {
        List<Map<String, Object>> rows = reportRepository.getOpportunityCountsByStage(
                request.tenantId(), request.dateFrom(), request.dateTo());
        Map<String, Object> summary = reportRepository.getSummaryStats(
                request.tenantId(), request.dateFrom(), request.dateTo());

        List<ReportData.ReportChart> charts = new ArrayList<>();
        List<String> labels = rows.stream().map(r -> (String) r.get("stage_name")).toList();
        List<Number> values = rows.stream().map(r -> (Number) r.get("total_amount")).toList();
        charts.add(new ReportData.ReportChart("Pipeline by Stage", "bar", labels, values));

        return ReportData.of(ReportType.OPPORTUNITY_PIPELINE, request.dateFrom(), request.dateTo(),
                rows, summary, charts);
    }

    private ReportData generateActivitySummaryReport(ReportRequest request) {
        List<Map<String, Object>> rows = reportRepository.getActivityCountsByType(
                request.tenantId(), request.dateFrom(), request.dateTo());
        Map<String, Object> summary = reportRepository.getSummaryStats(
                request.tenantId(), request.dateFrom(), request.dateTo());

        List<ReportData.ReportChart> charts = new ArrayList<>();
        List<String> labels = rows.stream().map(r -> (String) r.get("activity_type")).toList();
        List<Number> values = rows.stream().map(r -> (Number) r.get("count")).toList();
        charts.add(new ReportData.ReportChart("Activities by Type", "bar", labels, values));

        return ReportData.of(ReportType.ACTIVITY_SUMMARY, request.dateFrom(), request.dateTo(),
                rows, summary, charts);
    }

    private ReportData generateEmailEngagementReport(ReportRequest request) {
        List<Map<String, Object>> rows = reportRepository.getEmailEngagementMetrics(
                request.tenantId(), request.dateFrom(), request.dateTo());
        Map<String, Object> summary = reportRepository.getSummaryStats(
                request.tenantId(), request.dateFrom(), request.dateTo());

        List<ReportData.ReportChart> charts = new ArrayList<>();
        List<String> labels = rows.stream().map(r -> (String) r.get("status")).toList();
        List<Number> values = rows.stream().map(r -> (Number) r.get("count")).toList();
        charts.add(new ReportData.ReportChart("Email Status Distribution", "pie", labels, values));

        return ReportData.of(ReportType.EMAIL_ENGAGEMENT, request.dateFrom(), request.dateTo(),
                rows, summary, charts);
    }

    private ReportData generateConversionFunnelReport(ReportRequest request) {
        List<Map<String, Object>> rows = reportRepository.getConversionFunnel(
                request.tenantId(), request.dateFrom(), request.dateTo());
        Map<String, Object> summary = reportRepository.getSummaryStats(
                request.tenantId(), request.dateFrom(), request.dateTo());

        List<ReportData.ReportChart> charts = new ArrayList<>();
        if (!rows.isEmpty()) {
            Map<String, Object> funnel = rows.get(0);
            List<String> labels = List.of("Total Leads", "Contacted", "Qualified", "Converted");
            List<Number> values = List.of(
                    (Number) funnel.get("total_leads"),
                    (Number) funnel.get("contacted"),
                    (Number) funnel.get("qualified"),
                    (Number) funnel.get("converted"));
            charts.add(new ReportData.ReportChart("Conversion Funnel", "funnel", labels, values));
        }

        return ReportData.of(ReportType.CONVERSION_FUNNEL, request.dateFrom(), request.dateTo(),
                rows, summary, charts);
    }

    private ReportData generateSalesForecastReport(ReportRequest request) {
        List<Map<String, Object>> rows = reportRepository.getSalesForecast(
                request.tenantId(), request.dateFrom(), request.dateTo());
        Map<String, Object> summary = reportRepository.getSummaryStats(
                request.tenantId(), request.dateFrom(), request.dateTo());

        List<ReportData.ReportChart> charts = new ArrayList<>();
        List<String> labels = rows.stream().map(r -> (String) r.get("stage_name")).toList();
        List<Number> values = rows.stream().map(r -> (Number) r.get("weighted_amount")).toList();
        charts.add(new ReportData.ReportChart("Weighted Forecast by Stage", "bar", labels, values));

        return ReportData.of(ReportType.SALES_FORECAST, request.dateFrom(), request.dateTo(),
                rows, summary, charts);
    }
}
