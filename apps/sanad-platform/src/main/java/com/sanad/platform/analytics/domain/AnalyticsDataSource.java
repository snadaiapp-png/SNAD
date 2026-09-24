package com.sanad.platform.analytics.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Analytics Data Source — a registered data source for analytics queries.
 *
 * <p>State machine: PENDING → ACTIVE → INACTIVE
 *                  PENDING → ERROR
 */
public record AnalyticsDataSource(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        String description,
        SourceType sourceType,
        String module,
        String configuration,
        Status status,
        Instant lastTestedAt,
        String lastTestStatus,
        UUID createdBy,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public enum SourceType { CRM, FINANCE, AI, WORKFLOW, MANAGEMENT, DATABASE, API, EXTERNAL }
    public enum Status { PENDING, ACTIVE, INACTIVE, ERROR }

    public static AnalyticsDataSource create(
            UUID tenantId, String code, String name, String description,
            SourceType sourceType, String module, String configuration, UUID createdBy) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code must not be blank");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        var now = Instant.now();
        return new AnalyticsDataSource(
                UUID.randomUUID(), tenantId, code, name, description,
                sourceType, module, configuration,
                Status.PENDING, null, null, createdBy,
                0, now, now
        );
    }

    public AnalyticsDataSource activate() {
        if (status != Status.PENDING && status != Status.INACTIVE && status != Status.ERROR)
            throw new IllegalStateException("Cannot activate from " + status);
        return withStatus(Status.ACTIVE);
    }

    public AnalyticsDataSource deactivate() {
        requireStatus(Status.ACTIVE, "deactivate");
        return withStatus(Status.INACTIVE);
    }

    public AnalyticsDataSource markError() {
        return withStatus(Status.ERROR);
    }

    private AnalyticsDataSource withStatus(Status newStatus) {
        return new AnalyticsDataSource(id, tenantId, code, name, description,
                sourceType, module, configuration, newStatus,
                lastTestedAt, lastTestStatus, createdBy,
                version + 1, createdAt, Instant.now());
    }

    private void requireStatus(Status expected, String action) {
        if (status != expected)
            throw new IllegalStateException("Cannot " + action + " from " + status + " (requires " + expected + ")");
    }
}
