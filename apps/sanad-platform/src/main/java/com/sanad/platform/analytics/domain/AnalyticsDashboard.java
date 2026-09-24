package com.sanad.platform.analytics.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Analytics Dashboard — a dashboard configuration.
 *
 * <p>State machine: DRAFT → ACTIVE → INACTIVE → ARCHIVED
 */
public record AnalyticsDashboard(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        String description,
        DashboardType dashboardType,
        String configuration,
        Status status,
        UUID createdBy,
        long versionLock,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public enum DashboardType { STANDARD, EXECUTIVE, OPERATIONAL, CUSTOM }
    public enum Status { DRAFT, ACTIVE, INACTIVE, ARCHIVED }

    public static AnalyticsDashboard create(
            UUID tenantId, String code, String name, String description,
            DashboardType dashboardType, String configuration, UUID createdBy) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code must not be blank");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        var now = Instant.now();
        return new AnalyticsDashboard(
                UUID.randomUUID(), tenantId, code, name, description,
                dashboardType != null ? dashboardType : DashboardType.STANDARD,
                configuration, Status.DRAFT, createdBy,
                0, 0, now, now
        );
    }

    public AnalyticsDashboard activate() {
        if (status != Status.DRAFT && status != Status.INACTIVE)
            throw new IllegalStateException("Cannot activate from " + status);
        return withStatus(Status.ACTIVE);
    }

    public AnalyticsDashboard deactivate() {
        requireStatus(Status.ACTIVE, "deactivate");
        return withStatus(Status.INACTIVE);
    }

    public AnalyticsDashboard archive() {
        requireStatus(Status.INACTIVE, "archive");
        return withStatus(Status.ARCHIVED);
    }

    private AnalyticsDashboard withStatus(Status newStatus) {
        return new AnalyticsDashboard(id, tenantId, code, name, description, dashboardType,
                configuration, newStatus, createdBy,
                versionLock + 1, version + 1, createdAt, Instant.now());
    }

    private void requireStatus(Status expected, String action) {
        if (status != expected)
            throw new IllegalStateException("Cannot " + action + " from " + status + " (requires " + expected + ")");
    }
}
