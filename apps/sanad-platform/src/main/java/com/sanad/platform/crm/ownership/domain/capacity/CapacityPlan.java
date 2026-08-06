package com.sanad.platform.crm.ownership.domain.capacity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Capacity plan entity (CRM-008).
 *
 * <p>Defines team capacity for a specific time period.
 * Tracks maximum capacity and allocated capacity in hours.
 */
public record CapacityPlan(
        UUID id,
        UUID tenantId,
        UUID teamId,
        LocalDate periodStart,
        LocalDate periodEnd,
        int maxCapacity,
        int allocatedCapacity,
        CapacityStatus status,
        UUID createdBy,
        UUID updatedBy,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public CapacityPlan {
        if (tenantId == null) throw new IllegalArgumentException("tenantId required");
        if (teamId == null) throw new IllegalArgumentException("teamId required");
        if (periodStart == null) throw new IllegalArgumentException("periodStart required");
        if (periodEnd == null) throw new IllegalArgumentException("periodEnd required");
        if (periodEnd.isBefore(periodStart)) throw new IllegalArgumentException("periodEnd must be after periodStart");
        if (maxCapacity <= 0) throw new IllegalArgumentException("maxCapacity must be positive");
        if (allocatedCapacity < 0) throw new IllegalArgumentException("allocatedCapacity cannot be negative");
        if (status == null) status = CapacityStatus.DRAFT;
    }

    public int remainingCapacity() { return maxCapacity - allocatedCapacity; }

    public double utilizationPercentage() {
        return maxCapacity > 0 ? (double) allocatedCapacity / maxCapacity * 100 : 0;
    }

    public boolean isDraft() { return status == CapacityStatus.DRAFT; }
    public boolean isActive() { return status == CapacityStatus.ACTIVE; }
    public boolean isCompleted() { return status == CapacityStatus.COMPLETED; }
}
