package com.sanad.platform.crm.reporting.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Domain value object representing a report generation request.
 */
public record ReportRequest(
        UUID tenantId,
        UUID userId,
        ReportType reportType,
        Instant dateFrom,
        Instant dateTo,
        Map<String, String> filters
) {
    public static ReportRequest of(UUID tenantId, UUID userId, ReportType reportType,
                                   Instant dateFrom, Instant dateTo, Map<String, String> filters) {
        return new ReportRequest(tenantId, userId, reportType, dateFrom, dateTo,
                filters != null ? filters : Map.of());
    }
}
