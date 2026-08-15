package com.sanad.platform.analytics.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Analytics Report — a report definition with query and optional schedule.
 *
 * <p>State machine: DRAFT → ACTIVE → ARCHIVED
 *                  DRAFT → SCHEDULED → ARCHIVED
 */
public record AnalyticsReport(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        String description,
        ReportType reportType,
        UUID dataSourceId,
        String queryText,
        String parameters,
        String scheduleCron,
        OutputFormat outputFormat,
        Status status,
        Instant lastExecutedAt,
        String lastExecutionStatus,
        UUID createdBy,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public enum ReportType { TABLE, CHART, PIVOT, SUMMARY, CUSTOM }
    public enum OutputFormat { JSON, CSV, PDF, EXCEL }
    public enum Status { DRAFT, ACTIVE, ARCHIVED, SCHEDULED }

    public static AnalyticsReport create(
            UUID tenantId, String code, String name, String description,
            ReportType reportType, UUID dataSourceId, String queryText,
            String parameters, OutputFormat outputFormat, UUID createdBy) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code must not be blank");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        var now = Instant.now();
        return new AnalyticsReport(
                UUID.randomUUID(), tenantId, code, name, description,
                reportType != null ? reportType : ReportType.TABLE,
                dataSourceId, queryText, parameters, null,
                outputFormat != null ? outputFormat : OutputFormat.JSON,
                Status.DRAFT, null, null, createdBy,
                0, now, now
        );
    }

    public AnalyticsReport activate() {
        requireStatus(Status.DRAFT, "activate");
        return withStatus(Status.ACTIVE);
    }

    public AnalyticsReport schedule(String cron) {
        requireStatus(Status.DRAFT, "schedule");
        return new AnalyticsReport(id, tenantId, code, name, description, reportType,
                dataSourceId, queryText, parameters, cron, outputFormat,
                Status.SCHEDULED, lastExecutedAt, lastExecutionStatus, createdBy,
                version + 1, createdAt, Instant.now());
    }

    public AnalyticsReport archive() {
        if (status == Status.ARCHIVED) throw new IllegalStateException("Already archived");
        return withStatus(Status.ARCHIVED);
    }

    public AnalyticsReport markExecuted(String executionStatus) {
        return new AnalyticsReport(id, tenantId, code, name, description, reportType,
                dataSourceId, queryText, parameters, scheduleCron, outputFormat,
                status, Instant.now(), executionStatus, createdBy,
                version + 1, createdAt, Instant.now());
    }

    private AnalyticsReport withStatus(Status newStatus) {
        return new AnalyticsReport(id, tenantId, code, name, description, reportType,
                dataSourceId, queryText, parameters, scheduleCron, outputFormat,
                newStatus, lastExecutedAt, lastExecutionStatus, createdBy,
                version + 1, createdAt, Instant.now());
    }

    private void requireStatus(Status expected, String action) {
        if (status != expected)
            throw new IllegalStateException("Cannot " + action + " from " + status + " (requires " + expected + ")");
    }
}
