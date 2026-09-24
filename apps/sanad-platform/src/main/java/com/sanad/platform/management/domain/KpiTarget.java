package com.sanad.platform.management.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * KPI Target — a per-period target value for a KPI Definition.
 *
 * <p>Holds the target value plus optional minimum (OFF_TRACK threshold) and
 * stretch (over-achievement) values for a specific time period.
 */
public record KpiTarget(
        UUID id,
        UUID tenantId,
        UUID kpiDefinitionId,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal targetValue,
        BigDecimal minimumValue,
        BigDecimal stretchValue,
        UUID ownerUserId,
        Status status,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public enum Status {
        ACTIVE, CLOSED, CANCELLED
    }

    public static KpiTarget create(
            UUID tenantId, UUID kpiDefinitionId,
            LocalDate periodStart, LocalDate periodEnd,
            BigDecimal targetValue, BigDecimal minimumValue, BigDecimal stretchValue,
            UUID ownerUserId) {
        if (periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException("periodEnd must be >= periodStart");
        }
        if (targetValue == null) {
            throw new IllegalArgumentException("targetValue must not be null");
        }
        var now = Instant.now();
        return new KpiTarget(
                UUID.randomUUID(), tenantId, kpiDefinitionId, periodStart, periodEnd,
                targetValue, minimumValue, stretchValue, ownerUserId,
                Status.ACTIVE, 0, now, now
        );
    }

    public KpiTarget close() {
        requireStatus(Status.ACTIVE, "close");
        return withStatus(Status.CLOSED);
    }

    public KpiTarget cancel() {
        requireStatus(Status.ACTIVE, "cancel");
        return withStatus(Status.CANCELLED);
    }

    private void requireStatus(Status expected, String action) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Cannot " + action + " from " + status + " (requires " + expected + ")");
        }
    }

    private KpiTarget withStatus(Status newStatus) {
        return new KpiTarget(
                id, tenantId, kpiDefinitionId, periodStart, periodEnd,
                targetValue, minimumValue, stretchValue, ownerUserId,
                newStatus, version + 1, createdAt, Instant.now()
        );
    }
}
