package com.sanad.platform.management.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * KPI Definition — a reusable, tenant-scoped metric catalog entry.
 *
 * <p>A KPI Definition is the <em>type</em> of a metric (e.g., "Monthly Recurring Revenue",
 * "Customer Churn Rate"). It does NOT hold values — those are stored in
 * {@link KpiMeasurement}. Targets per period are stored in {@link KpiTarget}.
 *
 * <p>Definitions can be linked to Key Results: a Key Result tracks progress towards
 * an Objective, while a KPI Definition provides the underlying measurement source.
 */
public record KpiDefinition(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        String description,
        String category,
        KeyResult.MetricUnit metricUnit,
        KeyResult.Direction direction,
        String formula,
        String sourceSystem,
        Status status,
        UUID ownerUserId,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public enum Status {
        ACTIVE, INACTIVE, DEPRECATED
    }

    public static KpiDefinition create(
            UUID tenantId, String code, String name, String description,
            String category, KeyResult.MetricUnit unit, KeyResult.Direction direction,
            String formula, String sourceSystem, UUID ownerUserId) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        var now = Instant.now();
        return new KpiDefinition(
                UUID.randomUUID(), tenantId, code, name, description, category,
                unit, direction, formula, sourceSystem, Status.ACTIVE,
                ownerUserId, 0, now, now
        );
    }

    public KpiDefinition deactivate() {
        if (status == Status.DEPRECATED) {
            throw new IllegalStateException("KPI already DEPRECATED");
        }
        return withStatus(Status.INACTIVE);
    }

    public KpiDefinition deprecate() {
        return withStatus(Status.DEPRECATED);
    }

    public KpiDefinition reactivate() {
        if (status == Status.DEPRECATED) {
            throw new IllegalStateException("Cannot reactivate DEPRECATED KPI");
        }
        return withStatus(Status.ACTIVE);
    }

    private KpiDefinition withStatus(Status newStatus) {
        return new KpiDefinition(
                id, tenantId, code, name, description, category, metricUnit,
                direction, formula, sourceSystem, newStatus, ownerUserId,
                version + 1, createdAt, Instant.now()
        );
    }
}
