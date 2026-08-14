package com.sanad.platform.management.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Strategic Initiative — a program of work that advances a Strategic Objective.
 *
 * <p>Initiatives are the "how" — concrete projects with owners, budgets, and timelines
 * that drive Key Results towards their targets.
 */
public record StrategicInitiative(
        UUID id,
        UUID tenantId,
        UUID objectiveId,
        String code,
        String name,
        String description,
        Status status,
        UUID ownerUserId,
        LocalDate startDate,
        LocalDate targetEndDate,
        LocalDate actualEndDate,
        int progressPct,
        Long budgetMinor,
        long spentMinor,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public enum Status {
        PLANNED, IN_PROGRESS, ON_HOLD, COMPLETED, CANCELLED, FAILED
    }

    public static StrategicInitiative create(
            UUID tenantId, UUID objectiveId, String code, String name, String description,
            UUID ownerUserId, LocalDate startDate, LocalDate targetEndDate, Long budgetMinor) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (targetEndDate != null && startDate != null && targetEndDate.isBefore(startDate)) {
            throw new IllegalArgumentException("targetEndDate must be >= startDate");
        }
        var now = Instant.now();
        return new StrategicInitiative(
                UUID.randomUUID(), tenantId, objectiveId, code, name, description,
                Status.PLANNED, ownerUserId, startDate, targetEndDate, null,
                0, budgetMinor, 0, 0, now, now
        );
    }

    public StrategicInitiative start() {
        requireStatus(Status.PLANNED, "start");
        return withStatus(Status.IN_PROGRESS);
    }

    public StrategicInitiative hold() {
        requireStatus(Status.IN_PROGRESS, "hold");
        return withStatus(Status.ON_HOLD);
    }

    public StrategicInitiative resume() {
        requireStatus(Status.ON_HOLD, "resume");
        return withStatus(Status.IN_PROGRESS);
    }

    public StrategicInitiative complete() {
        if (status != Status.IN_PROGRESS && status != Status.ON_HOLD) {
            throw new IllegalStateException("Cannot complete from " + status);
        }
        return new StrategicInitiative(
                id, tenantId, objectiveId, code, name, description,
                Status.COMPLETED, ownerUserId, startDate, targetEndDate, LocalDate.now(),
                100, budgetMinor, spentMinor, version + 1, createdAt, Instant.now()
        );
    }

    public StrategicInitiative cancel() {
        if (status == Status.COMPLETED || status == Status.FAILED) {
            throw new IllegalStateException("Cannot cancel from terminal status " + status);
        }
        return withStatus(Status.CANCELLED);
    }

    public StrategicInitiative fail() {
        if (status == Status.COMPLETED) {
            throw new IllegalStateException("Cannot fail COMPLETED initiative");
        }
        return new StrategicInitiative(
                id, tenantId, objectiveId, code, name, description,
                Status.FAILED, ownerUserId, startDate, targetEndDate, LocalDate.now(),
                progressPct, budgetMinor, spentMinor, version + 1, createdAt, Instant.now()
        );
    }

    public StrategicInitiative updateProgress(int newProgressPct) {
        if (newProgressPct < 0 || newProgressPct > 100) {
            throw new IllegalArgumentException("progressPct must be 0-100");
        }
        return new StrategicInitiative(
                id, tenantId, objectiveId, code, name, description,
                status, ownerUserId, startDate, targetEndDate, actualEndDate,
                newProgressPct, budgetMinor, spentMinor, version + 1, createdAt, Instant.now()
        );
    }

    public StrategicInitiative recordSpend(long additionalSpendMinor) {
        if (additionalSpendMinor < 0) {
            throw new IllegalArgumentException("additionalSpendMinor must be >= 0");
        }
        return new StrategicInitiative(
                id, tenantId, objectiveId, code, name, description,
                status, ownerUserId, startDate, targetEndDate, actualEndDate,
                progressPct, budgetMinor, spentMinor + additionalSpendMinor,
                version + 1, createdAt, Instant.now()
        );
    }

    private void requireStatus(Status expected, String action) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Cannot " + action + " from " + status + " (requires " + expected + ")");
        }
    }

    private StrategicInitiative withStatus(Status newStatus) {
        return new StrategicInitiative(
                id, tenantId, objectiveId, code, name, description,
                newStatus, ownerUserId, startDate, targetEndDate, actualEndDate,
                progressPct, budgetMinor, spentMinor, version + 1, createdAt, Instant.now()
        );
    }
}
