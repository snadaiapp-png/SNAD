package com.sanad.platform.management.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Strategic Objective — the "O" in OKR.
 *
 * <p>Represents a qualitative, time-bound goal. Status state machine:
 * <pre>
 *   DRAFT → ACTIVE → AT_RISK / OFF_TRACK → ACHIEVED → CLOSED
 *                                        ↘ CANCELLED
 * </pre>
 */
public record StrategicObjective(
        UUID id,
        UUID tenantId,
        UUID parentId,
        String code,
        String title,
        String description,
        Status status,
        Priority priority,
        UUID ownerUserId,
        LocalDate periodStart,
        LocalDate periodEnd,
        int progressPct,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public enum Status {
        DRAFT, ACTIVE, AT_RISK, OFF_TRACK, ACHIEVED, CLOSED, CANCELLED
    }

    public enum Priority {
        LOW, NORMAL, HIGH, CRITICAL
    }

    public static StrategicObjective create(
            UUID tenantId, String code, String title, String description,
            Priority priority, UUID ownerUserId,
            LocalDate periodStart, LocalDate periodEnd) {
        if (periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException("periodEnd must be >= periodStart");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        var now = Instant.now();
        return new StrategicObjective(
                UUID.randomUUID(), tenantId, null, code, title, description,
                Status.DRAFT, priority, ownerUserId,
                periodStart, periodEnd, 0, 0, now, now
        );
    }

    public StrategicObjective activate() {
        requireStatus(Status.DRAFT, "activate");
        return withStatus(Status.ACTIVE);
    }

    public StrategicObjective markAtRisk() {
        requireStatus(Status.ACTIVE, "markAtRisk");
        return withStatus(Status.AT_RISK);
    }

    public StrategicObjective markOffTrack() {
        if (status != Status.ACTIVE && status != Status.AT_RISK) {
            throw new IllegalStateException(
                    "Cannot mark OFF_TRACK from " + status + " (requires ACTIVE or AT_RISK)");
        }
        return withStatus(Status.OFF_TRACK);
    }

    public StrategicObjective achieve() {
        if (status == Status.CLOSED || status == Status.CANCELLED) {
            throw new IllegalStateException("Cannot achieve from terminal status " + status);
        }
        return withStatusAndProgress(Status.ACHIEVED, 100);
    }

    public StrategicObjective close() {
        requireStatus(Status.ACHIEVED, "close");
        return withStatus(Status.CLOSED);
    }

    public StrategicObjective cancel() {
        if (status == Status.CLOSED) {
            throw new IllegalStateException("Cannot cancel CLOSED objective");
        }
        return withStatus(Status.CANCELLED);
    }

    public StrategicObjective withProgress(int newProgressPct) {
        if (newProgressPct < 0 || newProgressPct > 100) {
            throw new IllegalArgumentException("progressPct must be 0-100, got " + newProgressPct);
        }
        return withStatusAndProgress(status, newProgressPct);
    }

    private StrategicObjective withStatus(Status newStatus) {
        return withStatusAndProgress(newStatus, progressPct);
    }

    private StrategicObjective withStatusAndProgress(Status newStatus, int newProgress) {
        return new StrategicObjective(
                id, tenantId, parentId, code, title, description,
                newStatus, priority, ownerUserId, periodStart, periodEnd,
                newProgress, version + 1, createdAt, Instant.now()
        );
    }

    private void requireStatus(Status expected, String action) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Cannot " + action + " from " + status + " (requires " + expected + ")");
        }
    }
}
