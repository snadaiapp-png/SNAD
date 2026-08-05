package com.sanad.platform.crm.reporting.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Domain value object representing generated report data.
 */
public record ReportData(
        UUID reportId,
        ReportType reportType,
        Instant generatedAt,
        Instant dateFrom,
        Instant dateTo,
        List<Map<String, Object>> rows,
        Map<String, Object> summary,
        List<ReportChart> charts
) {
    public static ReportData of(ReportType reportType, Instant dateFrom, Instant dateTo,
                                List<Map<String, Object>> rows, Map<String, Object> summary,
                                List<ReportChart> charts) {
        return new ReportData(UUID.randomUUID(), reportType, Instant.now(),
                dateFrom, dateTo, rows, summary, charts);
    }

    /**
     * Represents a chart configuration for visualization.
     */
    public record ReportChart(
            String title,
            String chartType,
            List<String> labels,
            List<Number> values
    ) {}
}
