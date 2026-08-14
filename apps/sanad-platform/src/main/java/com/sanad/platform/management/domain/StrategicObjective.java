package com.sanad.platform.management.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Strategic Objective — the "O" in OKR.
 *
 * <p>Represents a qualitative, time-bound goal that an organization commits to achieving.
 * Objectives are tenant-scoped, may cascade (parent_id), and aggregate progress from
 * their Key Results.
 *
 * <p>Status state machine:
 * <pre>
 *   DRAFT → ACTIVE → AT_RISK / OFF_TRACK → ACHIEVED → CLOSED
 *                                        ↘ CANCELLED
 * </pre>
 *
 * <p>Immutable record: state transitions are explicit method calls that return
 * a new instance. Persistence is handled by the repository layer.
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

    /** Factory for new objectives — starts in DRAFT with 0% progress. */
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

    /** Transition to ACTIVE — only valid from DRAFT. */
    public StrategicObjective activate() {
        requireStatus(Status.DRAFT, "activate");
        return with(status(Status.ACTIVE));
    }

    /** Mark as AT_RISK — valid from ACTIVE. */
    public StrategicObjective markAtRisk() {
        requireStatus(Status.ACTIVE, "markAtRisk");
        return with(status(Status.AT_RISK));
    }

    /** Mark as OFF_TRACK — valid from ACTIVE or AT_RISK. */
    public StrategicObjective markOffTrack() {
        if (status != Status.ACTIVE && status != Status.AT_RISK) {
            throw new IllegalStateException(
                    "Cannot mark OFF_TRACK from " + status + " (requires ACTIVE or AT_RISK)");
        }
        return with(status(Status.OFF_TRACK));
    }

    /** Mark as ACHIEVED — valid from any non-terminal status. */
    public StrategicObjective achieve() {
        if (status == Status.CLOSED || status == Status.CANCELLED) {
            throw new IllegalStateException("Cannot achieve from terminal status " + status);
        }
        return with(status(Status.ACHIEVED).progressPct(100));
    }

    /** Close — valid from ACHIEVED. */
    public StrategicObjective close() {
        requireStatus(Status.ACHIEVED, "close");
        return with(status(Status.CLOSED));
    }

    /** Cancel — valid from any non-terminal status. */
    public StrategicObjective cancel() {
        if (status == Status.CLOSED) {
            throw new IllegalStateException("Cannot cancel CLOSED objective");
        }
        return with(status(Status.CANCELLED));
    }

    /** Update progress from aggregated Key Results. */
    public StrategicObjective withProgress(int newProgressPct) {
        if (newProgressPct < 0 || newProgressPct > 100) {
            throw new IllegalArgumentException("progressPct must be 0-100, got " + newProgressPct);
        }
        return with(progressPct(newProgressPct));
    }

    private StrategicObjective with(Mutator m) {
        return new StrategicObjective(
                id, tenantId, parentId, code, title, description,
                m.status != null ? m.status : status,
                priority, ownerUserId, periodStart, periodEnd,
                m.progressPct != null ? m.progressPct : progressPct,
                version + 1, createdAt, Instant.now()
        );
    }

    private void requireStatus(Status expected, String action) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Cannot " + action + " from " + status + " (requires " + expected + ")");
        }
    }

    private static final class Mutator {
        Status status;
        Integer progressPct;
    }

    private static Mutator status(Status s) {
        var m = new Mutator();
        m.status = s;
        return m;
    }

    private static Mutator progressPct(int p) {
        var m = new Mutator();
        m.progressPct = p;
        return m;
    }
}
